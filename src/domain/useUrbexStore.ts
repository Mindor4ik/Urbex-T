import { useCallback, useEffect, useRef, useState } from 'react';
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
  deleteArchiveItemRecord,
  deleteBaseArticleRecord,
  deleteObjectRecord,
  loadOrSeedDatabase,
  replaceFullPayload,
  saveArchiveItemRecord,
  saveBaseArticleRecord,
  saveMeshMessageRecord,
  saveObjectRecord,
  saveProfileRecord,
  saveSettingsRecord,
} from '../data/storage';
import { createSeedPayload } from '../data/seed';

const MESH_BROADCAST_NAME = 'urbex_t_mesh_relay_v2';

type RelayEnvelope =
  | { kind: 'packet'; message: MeshMessage }
  | { kind: 'presence'; node: MeshNode };

export function useUrbexStore() {
  const [data, setData] = useState<UrbexDbPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [storageError, setStorageError] = useState<string | null>(null);
  const [discoveredNodes, setDiscoveredNodes] = useState<MeshNode[]>([]);
  const broadcastRef = useRef<BroadcastChannel | null>(null);

  useEffect(() => {
    let active = true;
    loadOrSeedDatabase()
      .then((payload) => {
        if (active) {
          setData(payload);
          setLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setStorageError('Ошибка чтения локальной базы данных.');
          setData(createSeedPayload());
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const meshEnabled = data?.settings.meshEnabled ?? false;
  const localProfile = data?.profile;

  // Real BroadcastChannel mesh relay: activates ONLY when meshEnabled is turned on in Settings
  useEffect(() => {
    if (!meshEnabled || !localProfile || typeof BroadcastChannel === 'undefined') {
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
        setData((prev) => {
          if (!prev) return prev;
          if (prev.meshMessages.some((m) => m.id === incoming.id)) return prev;
          return {
            ...prev,
            meshMessages: [...prev.meshMessages, incoming].sort(
              (a, b) => a.timestamp - b.timestamp
            ),
          };
        });
        void saveMeshMessageRecord(incoming);
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
  }, [meshEnabled, localProfile]);

  const upsertObject = useCallback(async (obj: UrbexObject) => {
    setData((prev) => {
      if (!prev) return prev;
      const exists = prev.objects.some((o) => o.id === obj.id);
      const objects = exists
        ? prev.objects.map((o) => (o.id === obj.id ? obj : o))
        : [obj, ...prev.objects];
      return {
        ...prev,
        objects: objects.sort((a, b) => b.updatedAt - a.updatedAt),
      };
    });
    await saveObjectRecord(obj);
  }, []);

  const removeObject = useCallback(async (id: string) => {
    setData((prev) =>
      prev ? { ...prev, objects: prev.objects.filter((o) => o.id !== id) } : prev
    );
    await deleteObjectRecord(id);
  }, []);

  const sendMeshPacket = useCallback(
    async (params: {
      channelId: string;
      kind: MeshPacketKind;
      body: string;
      hopLimit: number;
      lat?: number;
      lon?: number;
    }) => {
      if (!data || !data.settings.meshEnabled) return;
      const msg = buildMeshMessage({
        channelId: params.channelId,
        senderNodeId: data.profile.nodeId,
        senderCallsign: data.profile.callsign,
        kind: params.kind,
        body: params.body,
        hopLimit: params.hopLimit,
        lat: params.lat,
        lon: params.lon,
      });

      setData((prev) =>
        prev ? { ...prev, meshMessages: [...prev.meshMessages, msg] } : prev
      );
      await saveMeshMessageRecord(msg);
      broadcastRef.current?.postMessage({ kind: 'packet', message: msg } satisfies RelayEnvelope);
    },
    [data]
  );

  const upsertBaseArticle = useCallback(async (article: BaseArticle) => {
    setData((prev) => {
      if (!prev) return prev;
      const exists = prev.baseArticles.some((a) => a.id === article.id);
      const baseArticles = exists
        ? prev.baseArticles.map((a) => (a.id === article.id ? article : a))
        : [article, ...prev.baseArticles];
      return { ...prev, baseArticles };
    });
    await saveBaseArticleRecord(article);
  }, []);

  const removeBaseArticle = useCallback(async (id: string) => {
    setData((prev) =>
      prev
        ? { ...prev, baseArticles: prev.baseArticles.filter((a) => a.id !== id) }
        : prev
    );
    await deleteBaseArticleRecord(id);
  }, []);

  const addArchiveItem = useCallback(async (item: ArchiveItem) => {
    setData((prev) =>
      prev ? { ...prev, archiveItems: [item, ...prev.archiveItems] } : prev
    );
    await saveArchiveItemRecord(item);
  }, []);

  const removeArchiveItem = useCallback(async (id: string) => {
    setData((prev) =>
      prev
        ? { ...prev, archiveItems: prev.archiveItems.filter((a) => a.id !== id) }
        : prev
    );
    await deleteArchiveItemRecord(id);
  }, []);

  const updateProfile = useCallback(async (profile: UserProfile) => {
    setData((prev) => (prev ? { ...prev, profile } : prev));
    await saveProfileRecord(profile);
  }, []);

  const updateSettings = useCallback(async (settings: AppSettings) => {
    setData((prev) => (prev ? { ...prev, settings } : prev));
    await saveSettingsRecord(settings);
  }, []);

  const addOfflineSector = useCallback(
    async (sector: OfflineMapSector) => {
      if (!data) return;
      const updated: AppSettings = {
        ...data.settings,
        offlineSectors: [sector, ...data.settings.offlineSectors],
        updatedAt: Date.now(),
      };
      await updateSettings(updated);
    },
    [data, updateSettings]
  );

  const removeOfflineSector = useCallback(
    async (sectorId: string) => {
      if (!data) return;
      const updated: AppSettings = {
        ...data.settings,
        offlineSectors: data.settings.offlineSectors.filter((s) => s.id !== sectorId),
        updatedAt: Date.now(),
      };
      await updateSettings(updated);
    },
    [data, updateSettings]
  );

  const exportUrbexDbString = useCallback((): string | null => {
    if (!data) return null;
    return serializeUrbexDb(data, data.profile.nodeId);
  }, [data]);

  const inspectUrbexDbFile = useCallback((raw: string) => {
    return parseAndVerifyUrbexDb(raw);
  }, []);

  const applyUrbexDbImport = useCallback(
    async (bundle: UrbexDbBundle, strategy: ConflictStrategy) => {
      if (!data) return null;
      const merged = mergeUrbexDbPayloads(data, bundle.payload, strategy);
      setData(merged.payload);
      await replaceFullPayload(merged.payload);
      return merged.stats;
    },
    [data]
  );

  const resetToSeed = useCallback(async () => {
    const seeded = createSeedPayload();
    setData(seeded);
    await replaceFullPayload(seeded);
  }, []);

  return {
    data,
    loading,
    storageError,
    discoveredNodes,
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
    exportUrbexDbString,
    inspectUrbexDbFile,
    applyUrbexDbImport,
    resetToSeed,
  };
}
