import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  DEFAULT_CLOUD_SYNC_CONFIG,
  executeRemotePull,
  executeRemotePush,
  type DirectoryHandleLike,
} from '../core/cloudSync';
import { buildMeshMessage } from '../core/meshtastic';
import {
  mergeUrbexDbPayloads,
  parseAndVerifyUrbexDb,
  serializeUrbexDb,
} from '../core/urbexdb';
import type {
  AppSettings,
  ArchiveItem,
  BaseArticle,
  ConflictStrategy,
  DatabaseWorkspaceMeta,
  DatabaseWorkspaceRecord,
  DbItemSource,
  MeshMessage,
  MeshNode,
  MeshPacketKind,
  OfflineMapSector,
  UrbexDbBundle,
  UrbexDbPayload,
  UrbexObject,
  UserProfile,
} from '../core/types';
import {
  deleteWorkspaceRecord,
  loadMultiDbState,
  replaceFullPayload,
  saveWorkspaceRecord,
  saveWorkspaceSelection,
} from '../data/storage';
import { createEmptyRegionPayload, createSeedPayload } from '../data/seed';

const MESH_BROADCAST_NAME = 'urbex_t_mesh_relay_v2';

type RelayEnvelope =
  | { kind: 'packet'; message: MeshMessage }
  | { kind: 'presence'; node: MeshNode };

export function useUrbexStore() {
  const [workspaces, setWorkspaces] = useState<DatabaseWorkspaceRecord[]>([]);
  const [activeDbId, setActiveDbId] = useState<string>('');
  const [mountedDbIds, setMountedDbIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [storageError, setStorageError] = useState<string | null>(null);
  const [discoveredNodes, setDiscoveredNodes] = useState<MeshNode[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncDirectoryName, setSyncDirectoryName] = useState<string | null>(null);
  const broadcastRef = useRef<BroadcastChannel | null>(null);
  const syncDirectoryRef = useRef<DirectoryHandleLike | null>(null);
  const syncInFlightRef = useRef(false);
  const autoSyncTimerRef = useRef<number | null>(null);
  const scheduleAutoSyncRef = useRef<(dbId: string) => void>(() => {});

  useEffect(() => {
    let active = true;
    loadMultiDbState()
      .then((state) => {
        if (!active) return;
        setWorkspaces(state.workspaces);
        setActiveDbId(state.activeDbId);
        setMountedDbIds(state.mountedDbIds);
        setLoading(false);
      })
      .catch(() => {
        if (!active) return;
        setStorageError('Ошибка чтения локальных баз данных.');
        const now = Date.now();
        const fallbackWs: DatabaseWorkspaceRecord = {
          meta: {
            id: 'db-moscow-main',
            name: 'Москва · Сектор-1',
            regionLabel: 'Москва и область',
            description: 'Резервная база сектора.',
            createdAt: now,
            updatedAt: now,
          },
          payload: createSeedPayload(),
        };
        setWorkspaces([fallbackWs]);
        setActiveDbId(fallbackWs.meta.id);
        setMountedDbIds([fallbackWs.meta.id]);
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const activeWorkspace = useMemo(
    () => workspaces.find((w) => w.meta.id === activeDbId) ?? workspaces[0] ?? null,
    [workspaces, activeDbId]
  );

  const mountedWorkspaces = useMemo(() => {
    const mountedSet = new Set(mountedDbIds);
    const ordered = workspaces.filter((w) => mountedSet.has(w.meta.id));
    if (ordered.length > 0) return ordered;
    return activeWorkspace ? [activeWorkspace] : [];
  }, [workspaces, mountedDbIds, activeWorkspace]);

  // Combined effective view payload across all mounted databases ("использовать несколько")
  const { data, objectSources, archiveSources, articleSources } = useMemo(() => {
    if (!activeWorkspace) {
      return {
        data: null as UrbexDbPayload | null,
        objectSources: {} as Record<string, DbItemSource>,
        archiveSources: {} as Record<string, DbItemSource>,
        articleSources: {} as Record<string, DbItemSource>,
      };
    }

    const objSrc: Record<string, DbItemSource> = {};
    const archSrc: Record<string, DbItemSource> = {};
    const artSrc: Record<string, DbItemSource> = {};

    const objMap = new Map<string, UrbexObject>();
    const archMap = new Map<string, ArchiveItem>();
    const artMap = new Map<string, BaseArticle>();

    // Active DB gets priority first, then additional mounted DBs
    const orderedSources = [
      activeWorkspace,
      ...mountedWorkspaces.filter((w) => w.meta.id !== activeWorkspace.meta.id),
    ];

    for (const ws of orderedSources) {
      const srcInfo: DbItemSource = { dbId: ws.meta.id, dbName: ws.meta.name };
      for (const obj of ws.payload.objects) {
        if (!objMap.has(obj.id)) {
          objMap.set(obj.id, obj);
          objSrc[obj.id] = srcInfo;
        }
      }
      for (const item of ws.payload.archiveItems) {
        if (!archMap.has(item.id)) {
          archMap.set(item.id, item);
          archSrc[item.id] = srcInfo;
        }
      }
      for (const art of ws.payload.baseArticles) {
        if (!artMap.has(art.id)) {
          artMap.set(art.id, art);
          artSrc[art.id] = srcInfo;
        }
      }
    }

    const combined: UrbexDbPayload = {
      objects: Array.from(objMap.values()).sort((a, b) => b.updatedAt - a.updatedAt),
      meshMessages: activeWorkspace.payload.meshMessages,
      baseArticles: Array.from(artMap.values()),
      archiveItems: Array.from(archMap.values()).sort((a, b) => b.createdAt - a.createdAt),
      profile: activeWorkspace.payload.profile,
      settings: activeWorkspace.payload.settings,
    };

    return {
      data: combined,
      objectSources: objSrc,
      archiveSources: archSrc,
      articleSources: artSrc,
    };
  }, [activeWorkspace, mountedWorkspaces]);

  const workspaceSummaries = useMemo(
    () =>
      workspaces.map((ws) => ({
        meta: ws.meta,
        counts: {
          objects: ws.payload.objects.length,
          archiveItems: ws.payload.archiveItems.length,
          baseArticles: ws.payload.baseArticles.length,
          meshMessages: ws.payload.meshMessages.length,
        },
      })),
    [workspaces]
  );

  const mutateWorkspacePayload = useCallback(
    async (
      targetDbId: string,
      updater: (prev: UrbexDbPayload) => UrbexDbPayload,
      options?: { skipAutoSync?: boolean }
    ) => {
      let updatedRecord: DatabaseWorkspaceRecord | null = null;
      setWorkspaces((prev) =>
        prev.map((ws) => {
          if (ws.meta.id !== targetDbId) return ws;
          const nextPayload = updater(ws.payload);
          updatedRecord = {
            meta: { ...ws.meta, updatedAt: Date.now() },
            payload: nextPayload,
          };
          return updatedRecord;
        })
      );
      if (updatedRecord) {
        await saveWorkspaceRecord(updatedRecord);
        if (targetDbId === activeDbId) {
          await replaceFullPayload((updatedRecord as DatabaseWorkspaceRecord).payload);
        }
        if (!options?.skipAutoSync) {
          scheduleAutoSyncRef.current(targetDbId);
        }
      }
    },
    [activeDbId]
  );

  const meshEnabled = activeWorkspace?.payload.settings.meshEnabled ?? false;
  const localProfile = activeWorkspace?.payload.profile;

  // Real BroadcastChannel mesh relay: activates ONLY when meshEnabled is turned on in Settings
  useEffect(() => {
    if (!meshEnabled || !localProfile || !activeDbId || typeof BroadcastChannel === 'undefined') {
      setDiscoveredNodes([]);
      broadcastRef.current?.close();
      broadcastRef.current = null;
      return;
    }

    const channel = new BroadcastChannel(MESH_BROADCAST_NAME);
    broadcastRef.current = channel;

    const announce = () => {
      const selfNode: MeshNode = {
        nodeId: localProfile.nodeId,
        callsign: localProfile.callsign,
        role: localProfile.role,
        snrDb: 0,
        rssiDbm: -60,
        hopsAway: 0,
        lastHeardAt: Date.now(),
        batteryPct: 100,
      };
      channel.postMessage({ kind: 'presence', node: selfNode } satisfies RelayEnvelope);
    };

    channel.onmessage = (event: MessageEvent<RelayEnvelope>) => {
      const env = event.data;
      if (!env) return;
      if (env.kind === 'packet' && env.message?.id) {
        const incoming = env.message;
        void mutateWorkspacePayload(activeDbId, (prev) => {
          if (prev.meshMessages.some((m) => m.id === incoming.id)) return prev;
          return {
            ...prev,
            meshMessages: [...prev.meshMessages, incoming].sort(
              (a, b) => a.timestamp - b.timestamp
            ),
          };
        });
      } else if (env.kind === 'presence' && env.node.nodeId !== localProfile.nodeId) {
        setDiscoveredNodes((prev) => {
          const rest = prev.filter((n) => n.nodeId !== env.node.nodeId);
          return [env.node, ...rest];
        });
      }
    };

    announce();
    return () => {
      channel.close();
      broadcastRef.current = null;
    };
  }, [meshEnabled, localProfile, activeDbId, mutateWorkspacePayload]);

  const upsertObject = useCallback(
    async (obj: UrbexObject) => {
      const ownerDbId = objectSources[obj.id]?.dbId ?? activeDbId;
      if (!ownerDbId) return;
      await mutateWorkspacePayload(ownerDbId, (prev) => {
        const exists = prev.objects.some((o) => o.id === obj.id);
        const objects = exists
          ? prev.objects.map((o) => (o.id === obj.id ? obj : o))
          : [obj, ...prev.objects];
        return {
          ...prev,
          objects: objects.sort((a, b) => b.updatedAt - a.updatedAt),
        };
      });
    },
    [activeDbId, objectSources, mutateWorkspacePayload]
  );

  const removeObject = useCallback(
    async (id: string) => {
      const ownerDbId = objectSources[id]?.dbId ?? activeDbId;
      if (!ownerDbId) return;
      await mutateWorkspacePayload(ownerDbId, (prev) => ({
        ...prev,
        objects: prev.objects.filter((o) => o.id !== id),
      }));
    },
    [activeDbId, objectSources, mutateWorkspacePayload]
  );

  const sendMeshPacket = useCallback(
    async (params: {
      channelId: string;
      kind: MeshPacketKind;
      body: string;
      hopLimit: number;
      lat?: number;
      lon?: number;
    }) => {
      if (!activeWorkspace || !activeWorkspace.payload.settings.meshEnabled) return;
      const msg = buildMeshMessage({
        channelId: params.channelId,
        senderNodeId: activeWorkspace.payload.profile.nodeId,
        senderCallsign: activeWorkspace.payload.profile.callsign,
        kind: params.kind,
        body: params.body,
        hopLimit: params.hopLimit,
        lat: params.lat,
        lon: params.lon,
      });

      await mutateWorkspacePayload(activeWorkspace.meta.id, (prev) => ({
        ...prev,
        meshMessages: [...prev.meshMessages, msg],
      }));
      broadcastRef.current?.postMessage({ kind: 'packet', message: msg } satisfies RelayEnvelope);
    },
    [activeWorkspace, mutateWorkspacePayload]
  );

  const upsertBaseArticle = useCallback(
    async (article: BaseArticle) => {
      const ownerDbId = articleSources[article.id]?.dbId ?? activeDbId;
      if (!ownerDbId) return;
      await mutateWorkspacePayload(ownerDbId, (prev) => {
        const exists = prev.baseArticles.some((a) => a.id === article.id);
        const baseArticles = exists
          ? prev.baseArticles.map((a) => (a.id === article.id ? article : a))
          : [article, ...prev.baseArticles];
        return { ...prev, baseArticles };
      });
    },
    [activeDbId, articleSources, mutateWorkspacePayload]
  );

  const removeBaseArticle = useCallback(
    async (id: string) => {
      const ownerDbId = articleSources[id]?.dbId ?? activeDbId;
      if (!ownerDbId) return;
      await mutateWorkspacePayload(ownerDbId, (prev) => ({
        ...prev,
        baseArticles: prev.baseArticles.filter((a) => a.id !== id),
      }));
    },
    [activeDbId, articleSources, mutateWorkspacePayload]
  );

  const addArchiveItem = useCallback(
    async (item: ArchiveItem) => {
      if (!activeDbId) return;
      await mutateWorkspacePayload(activeDbId, (prev) => ({
        ...prev,
        archiveItems: [item, ...prev.archiveItems],
      }));
    },
    [activeDbId, mutateWorkspacePayload]
  );

  const removeArchiveItem = useCallback(
    async (id: string) => {
      const ownerDbId = archiveSources[id]?.dbId ?? activeDbId;
      if (!ownerDbId) return;
      await mutateWorkspacePayload(ownerDbId, (prev) => ({
        ...prev,
        archiveItems: prev.archiveItems.filter((a) => a.id !== id),
      }));
    },
    [activeDbId, archiveSources, mutateWorkspacePayload]
  );

  const updateProfile = useCallback(
    async (profile: UserProfile) => {
      if (!activeDbId) return;
      await mutateWorkspacePayload(activeDbId, (prev) => ({ ...prev, profile }));
    },
    [activeDbId, mutateWorkspacePayload]
  );

  const updateSettings = useCallback(
    async (settings: AppSettings) => {
      if (!activeDbId) return;
      await mutateWorkspacePayload(activeDbId, (prev) => ({ ...prev, settings }));
    },
    [activeDbId, mutateWorkspacePayload]
  );

  const addOfflineSector = useCallback(
    async (sector: OfflineMapSector) => {
      if (!activeWorkspace) return;
      const updated: AppSettings = {
        ...activeWorkspace.payload.settings,
        offlineSectors: [sector, ...activeWorkspace.payload.settings.offlineSectors],
        updatedAt: Date.now(),
      };
      await updateSettings(updated);
    },
    [activeWorkspace, updateSettings]
  );

  const removeOfflineSector = useCallback(
    async (sectorId: string) => {
      if (!activeWorkspace) return;
      const updated: AppSettings = {
        ...activeWorkspace.payload.settings,
        offlineSectors: activeWorkspace.payload.settings.offlineSectors.filter(
          (s) => s.id !== sectorId
        ),
        updatedAt: Date.now(),
      };
      await updateSettings(updated);
    },
    [activeWorkspace, updateSettings]
  );

  // --- Cloud / Folder Auto-Sync Orchestration ---

  const selectSyncDirectory = useCallback(async (): Promise<string | null> => {
    const win = window as Window & {
      showDirectoryPicker?: (opts?: { mode?: 'read' | 'readwrite' }) => Promise<DirectoryHandleLike & { name?: string }>;
    };
    if (!win.showDirectoryPicker) {
      return null;
    }
    const handle = await win.showDirectoryPicker({ mode: 'readwrite' });
    syncDirectoryRef.current = handle;
    const label = handle.name || 'Выбранная папка';
    setSyncDirectoryName(label);
    return label;
  }, []);

  const syncNow = useCallback(
    async (opts?: { manual?: boolean }): Promise<{ ok: boolean; message: string }> => {
      const targetWs = activeWorkspace;
      if (!targetWs) return { ok: false, message: 'Нет активной базы.' };
      const cfg = {
        ...DEFAULT_CLOUD_SYNC_CONFIG,
        ...(targetWs.payload.settings.cloudSync ?? {}),
      };
      if (cfg.provider === 'none') {
        return { ok: false, message: 'Выберите бесплатный сервис синхронизации в разделе «Базы и облако».' };
      }
      if (!opts?.manual && !cfg.enabled) {
        return { ok: false, message: 'Автосинхронизация отключена.' };
      }
      if (!opts?.manual && cfg.wifiOnly) {
        const navConn = (navigator as Navigator & { connection?: { type?: string } }).connection;
        if (navConn?.type && navConn.type !== 'wifi' && navConn.type !== 'ethernet') {
          return { ok: false, message: 'Ожидание Wi-Fi для автосинхронизации.' };
        }
      }
      if (syncInFlightRef.current) {
        return { ok: false, message: 'Синхронизация уже выполняется.' };
      }

      syncInFlightRef.current = true;
      setIsSyncing(true);
      try {
        const pullRes = await executeRemotePull(cfg, syncDirectoryRef.current);
        let mergedPayload = targetWs.payload;
        let statsNote = '';
        if (pullRes.bundle) {
          const merged = mergeUrbexDbPayloads(
            targetWs.payload,
            pullRes.bundle.payload,
            cfg.conflictStrategy
          );
          mergedPayload = merged.payload;
          statsNote = ` (+${merged.stats.added} / ~${merged.stats.updated})`;
        }

        const effectiveCfg = {
          ...cfg,
          gistId: pullRes.resolvedGistId ?? cfg.gistId,
        };
        const pushRes = await executeRemotePush(
          effectiveCfg,
          mergedPayload,
          mergedPayload.profile.nodeId,
          syncDirectoryRef.current
        );

        const completedAt = Date.now();
        const message =
          cfg.provider === 'local_folder' && !syncDirectoryRef.current
            ? `Снимок сохранён в загрузки${statsNote}`
            : `Синхронизировано в ${new Date(completedAt).toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit',
              })}${statsNote}`;

        const finalSettings: AppSettings = {
          ...mergedPayload.settings,
          cloudSync: {
            ...effectiveCfg,
            gistId: pushRes.updatedGistId ?? effectiveCfg.gistId,
            lastSyncAt: completedAt,
            lastSyncStatus: 'success',
            lastSyncMessage: message,
          },
        };

        await mutateWorkspacePayload(
          targetWs.meta.id,
          () => ({
            ...mergedPayload,
            settings: finalSettings,
          }),
          { skipAutoSync: true }
        );
        return { ok: true, message };
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Сбой синхронизации.';
        await mutateWorkspacePayload(
          targetWs.meta.id,
          (prev) => ({
            ...prev,
            settings: {
              ...prev.settings,
              cloudSync: {
                ...DEFAULT_CLOUD_SYNC_CONFIG,
                ...(prev.settings.cloudSync ?? {}),
                lastSyncAt: Date.now(),
                lastSyncStatus: 'error',
                lastSyncMessage: message,
              },
            },
          }),
          { skipAutoSync: true }
        );
        return { ok: false, message };
      } finally {
        syncInFlightRef.current = false;
        setIsSyncing(false);
      }
    },
    [activeWorkspace, mutateWorkspacePayload]
  );

  useEffect(() => {
    scheduleAutoSyncRef.current = (targetId: string) => {
      if (targetId !== activeDbId) return;
      const cfg = activeWorkspace?.payload.settings.cloudSync;
      if (!cfg?.enabled || !cfg.autoSyncOnChange || cfg.provider === 'none') return;
      // Avoid browser file-download popups on background auto-sync if no directory handle is attached
      if (cfg.provider === 'local_folder' && !syncDirectoryRef.current) return;
      if (autoSyncTimerRef.current !== null) {
        window.clearTimeout(autoSyncTimerRef.current);
      }
      autoSyncTimerRef.current = window.setTimeout(() => {
        autoSyncTimerRef.current = null;
        void syncNow({ manual: false });
      }, 4000);
    };
  }, [activeDbId, activeWorkspace, syncNow]);

  useEffect(() => {
    const cfg = activeWorkspace?.payload.settings.cloudSync;
    if (!cfg?.enabled || cfg.provider === 'none') return;
    if (cfg.provider === 'local_folder' && !syncDirectoryRef.current) return;
    const mins = Math.max(5, cfg.autoSyncIntervalMin || 30);
    const intervalId = window.setInterval(() => {
      if (typeof navigator !== 'undefined' && navigator.onLine === false && cfg.provider !== 'local_folder') {
        return;
      }
      void syncNow({ manual: false });
    }, mins * 60_000);

    const onOnline = () => {
      void syncNow({ manual: false });
    };
    window.addEventListener('online', onOnline);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener('online', onOnline);
    };
  }, [
    activeWorkspace?.payload.settings.cloudSync?.enabled,
    activeWorkspace?.payload.settings.cloudSync?.provider,
    activeWorkspace?.payload.settings.cloudSync?.autoSyncIntervalMin,
    syncNow,
  ]);

  // --- Multi-Database Manager Operations ---

  const carryGlobalSettings = useCallback(
    (targetSettings: AppSettings): AppSettings => {
      if (!activeWorkspace) return targetSettings;
      const cur = activeWorkspace.payload.settings;
      return {
        ...targetSettings,
        editorEnabled: cur.editorEnabled ?? false,
        pdaModeEnabled: cur.pdaModeEnabled ?? false,
        pdaOrientation: cur.pdaOrientation ?? 'portrait',
        confirmBeforeDelete: cur.confirmBeforeDelete ?? true,
        hapticFeedback: cur.hapticFeedback ?? true,
        maskCoordinatesOnScreen: cur.maskCoordinatesOnScreen ?? false,
        cloudSync: cur.cloudSync ?? targetSettings.cloudSync,
      };
    },
    [activeWorkspace]
  );

  const switchDatabase = useCallback(
    async (targetDbId: string, preserveMultiMount = false) => {
      const target = workspaces.find((w) => w.meta.id === targetDbId);
      if (!target) return;
      const syncedSettings = carryGlobalSettings(target.payload.settings);
      const syncedPayload: UrbexDbPayload = {
        ...target.payload,
        settings: syncedSettings,
      };
      const nextMounted = preserveMultiMount
        ? Array.from(new Set([targetDbId, ...mountedDbIds]))
        : [targetDbId];
      if (
        syncedSettings.editorEnabled !== target.payload.settings.editorEnabled ||
        syncedSettings.pdaModeEnabled !== target.payload.settings.pdaModeEnabled ||
        syncedSettings.pdaOrientation !== target.payload.settings.pdaOrientation
      ) {
        const updatedRecord: DatabaseWorkspaceRecord = {
          meta: target.meta,
          payload: syncedPayload,
        };
        setWorkspaces((prev) =>
          prev.map((w) => (w.meta.id === targetDbId ? updatedRecord : w))
        );
        await saveWorkspaceRecord(updatedRecord);
      }
      setActiveDbId(targetDbId);
      setMountedDbIds(nextMounted);
      await saveWorkspaceSelection(targetDbId, nextMounted);
      await replaceFullPayload(syncedPayload);
    },
    [workspaces, mountedDbIds, carryGlobalSettings]
  );

  const toggleMountDatabase = useCallback(
    async (targetDbId: string) => {
      if (targetDbId === activeDbId) return; // primary active DB always remains mounted
      const nextMounted = mountedDbIds.includes(targetDbId)
        ? mountedDbIds.filter((id) => id !== targetDbId)
        : [...mountedDbIds, targetDbId];
      setMountedDbIds(nextMounted);
      await saveWorkspaceSelection(activeDbId, nextMounted);
    },
    [activeDbId, mountedDbIds]
  );

  const createDatabase = useCallback(
    async (params: {
      name: string;
      regionLabel: string;
      description?: string;
      mode: 'empty' | 'seed';
      activateImmediately?: boolean;
    }): Promise<DatabaseWorkspaceMeta | null> => {
      const trimmedName = params.name.trim();
      if (!trimmedName) return null;
      const now = Date.now();
      const id = `db-${now.toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
      const basePayload =
        params.mode === 'seed'
          ? createSeedPayload()
          : createEmptyRegionPayload(
              activeWorkspace?.payload.profile,
              activeWorkspace?.payload.settings
            );
      const payload: UrbexDbPayload = {
        ...basePayload,
        settings: carryGlobalSettings(basePayload.settings),
      };
      const record: DatabaseWorkspaceRecord = {
        meta: {
          id,
          name: trimmedName,
          regionLabel: params.regionLabel.trim() || trimmedName,
          description: params.description?.trim() || 'Региональная локальная база Urbex-T.',
          createdAt: now,
          updatedAt: now,
        },
        payload,
      };
      setWorkspaces((prev) => [...prev, record]);
      await saveWorkspaceRecord(record);

      if (params.activateImmediately !== false) {
        setActiveDbId(id);
        setMountedDbIds([id]);
        await saveWorkspaceSelection(id, [id]);
        await replaceFullPayload(payload);
      }
      return record.meta;
    },
    [activeWorkspace, carryGlobalSettings]
  );

  const renameDatabase = useCallback(
    async (dbId: string, name: string, regionLabel: string) => {
      const trimmed = name.trim();
      if (!trimmed) return;
      let updated: DatabaseWorkspaceRecord | null = null;
      setWorkspaces((prev) =>
        prev.map((ws) => {
          if (ws.meta.id !== dbId) return ws;
          updated = {
            ...ws,
            meta: {
              ...ws.meta,
              name: trimmed,
              regionLabel: regionLabel.trim() || trimmed,
              updatedAt: Date.now(),
            },
          };
          return updated;
        })
      );
      if (updated) {
        await saveWorkspaceRecord(updated);
      }
    },
    []
  );

  const deleteDatabase = useCallback(
    async (dbId: string) => {
      if (workspaces.length <= 1) return;
      const remaining = workspaces.filter((w) => w.meta.id !== dbId);
      setWorkspaces(remaining);
      await deleteWorkspaceRecord(dbId);

      const nextActive = dbId === activeDbId ? remaining[0].meta.id : activeDbId;
      const nextMountedRaw = mountedDbIds.filter((id) => id !== dbId);
      const nextMounted = nextMountedRaw.includes(nextActive)
        ? nextMountedRaw
        : [nextActive, ...nextMountedRaw];

      setActiveDbId(nextActive);
      setMountedDbIds(nextMounted);
      await saveWorkspaceSelection(nextActive, nextMounted);
      const activeRec = remaining.find((w) => w.meta.id === nextActive);
      if (activeRec && dbId === activeDbId) {
        await replaceFullPayload(activeRec.payload);
      }
    },
    [workspaces, activeDbId, mountedDbIds]
  );

  const exportUrbexDbString = useCallback(
    (targetDbId?: string): string | null => {
      const target = targetDbId
        ? workspaces.find((w) => w.meta.id === targetDbId)
        : activeWorkspace;
      if (!target) return null;
      return serializeUrbexDb(target.payload, target.payload.profile.nodeId);
    },
    [workspaces, activeWorkspace]
  );

  const inspectUrbexDbFile = useCallback((raw: string) => {
    return parseAndVerifyUrbexDb(raw);
  }, []);

  const applyUrbexDbImport = useCallback(
    async (bundle: UrbexDbBundle, strategy: ConflictStrategy) => {
      if (!activeWorkspace) return null;
      const merged = mergeUrbexDbPayloads(activeWorkspace.payload, bundle.payload, strategy);
      const preserved: UrbexDbPayload = {
        ...merged.payload,
        settings: carryGlobalSettings(merged.payload.settings),
      };
      await mutateWorkspacePayload(activeWorkspace.meta.id, () => preserved);
      return merged.stats;
    },
    [activeWorkspace, mutateWorkspacePayload, carryGlobalSettings]
  );

  const importIntoNewDatabase = useCallback(
    async (name: string, regionLabel: string, bundle: UrbexDbBundle) => {
      const trimmed = name.trim() || 'Импортированная база';
      const now = Date.now();
      const id = `db-${now.toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
      const payload: UrbexDbPayload = {
        ...bundle.payload,
        settings: carryGlobalSettings(bundle.payload.settings),
      };
      const record: DatabaseWorkspaceRecord = {
        meta: {
          id,
          name: trimmed,
          regionLabel: regionLabel.trim() || trimmed,
          description: `Импорт .urbexdb (${bundle.payload.objects.length} объектов).`,
          createdAt: now,
          updatedAt: now,
        },
        payload,
      };
      setWorkspaces((prev) => [...prev, record]);
      await saveWorkspaceRecord(record);
      setActiveDbId(id);
      setMountedDbIds([id]);
      await saveWorkspaceSelection(id, [id]);
      await replaceFullPayload(record.payload);
      return record.meta;
    },
    [carryGlobalSettings]
  );

  const resetToSeed = useCallback(async () => {
    if (!activeDbId) return;
    const seeded = createSeedPayload();
    const preserved: UrbexDbPayload = {
      ...seeded,
      settings: carryGlobalSettings(seeded.settings),
    };
    await mutateWorkspacePayload(activeDbId, () => preserved);
  }, [activeDbId, mutateWorkspacePayload, carryGlobalSettings]);

  return {
    data,
    loading,
    storageError,
    discoveredNodes,
    isSyncing,
    syncDirectoryName,
    activeWorkspaceMeta: activeWorkspace?.meta ?? null,
    workspaceSummaries,
    activeDbId,
    mountedDbIds,
    objectSources,
    archiveSources,
    articleSources,
    upsertObject,
    removeObject,
    sendMeshPacket,
    upsertBaseArticle,
    removeBaseArticle,
    addArchiveItem,
    removeArchiveItem,
    updateProfile,
    updateSettings,
    addOfflineSector,
    removeOfflineSector,
    selectSyncDirectory,
    syncNow,
    switchDatabase,
    toggleMountDatabase,
    createDatabase,
    renameDatabase,
    deleteDatabase,
    exportUrbexDbString,
    inspectUrbexDbFile,
    applyUrbexDbImport,
    importIntoNewDatabase,
    resetToSeed,
  };
}
