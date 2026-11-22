import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion, MotionConfig } from 'motion/react';
import { AppShell } from './components/AppShell';
import { QuickObjectModal, type QuickObjectDraft } from './components/QuickObjectModal';
import { translateZone } from './core/geo';
import {
  isCommsModuleVisible,
  isValidModuleId,
  isValidSettingsCategory,
  popTabHistory,
  pushTabHistory,
  sanitizeActiveModule,
} from './core/navigation';
import { MESH_CHANNELS } from './data/seed';
import { useLocationEngine } from './domain/useLocationEngine';
import { useUrbexStore } from './domain/useUrbexStore';
import ArchiveView from './features/archive/ArchiveView';
import AppsView from './features/apps/AppsView';
import BaseView from './features/base/BaseView';
import MapView, {
  type MapCameraState,
  type MapFocusTarget,
  type PointMoveResult,
  type PointMoveTarget,
} from './features/map/MapView';
import MenuView from './features/menu/MenuView';
import MeshView from './features/mesh/MeshView';
import ObjectsView from './features/objects/ObjectsView';
import ProfileView from './features/profile/ProfileView';
import SettingsView from './features/settings/SettingsView';
import type {
  GeoPoint,
  MapLayerMode,
  ModuleId,
  ReliabilityStatus,
  SettingsCategory,
  UrbexObject,
  ZoneStyle,
} from './core/types';

const ACTIVE_MODULE_KEY = 'urbex_active_module_v1';
const SETTINGS_CATEGORY_KEY = 'urbex_settings_category_v1';
const TAB_HISTORY_KEY = 'urbex_tab_history_v1';

function readStoredModule(): ModuleId {
  if (typeof window === 'undefined') return 'map';
  try {
    const raw = window.localStorage.getItem(ACTIVE_MODULE_KEY);
    return isValidModuleId(raw) ? raw : 'map';
  } catch {
    return 'map';
  }
}

function readStoredSettingsCategory(): SettingsCategory {
  if (typeof window === 'undefined') return 'databases';
  try {
    const raw = window.localStorage.getItem(SETTINGS_CATEGORY_KEY);
    return isValidSettingsCategory(raw) ? raw : 'databases';
  } catch {
    return 'databases';
  }
}

function readStoredTabHistory(initialMod: ModuleId): ModuleId[] {
  if (typeof window === 'undefined') return [initialMod];
  try {
    const raw = window.localStorage.getItem(TAB_HISTORY_KEY);
    if (!raw) return initialMod === 'map' ? ['map'] : ['map', initialMod];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [initialMod];
    const valid = parsed.filter((item): item is ModuleId => isValidModuleId(item));
    return pushTabHistory(valid.length > 0 ? valid : ['map'], initialMod);
  } catch {
    return [initialMod];
  }
}

function persistNavState(mod: ModuleId, history: readonly ModuleId[], cat: SettingsCategory) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(ACTIVE_MODULE_KEY, mod);
    window.localStorage.setItem(SETTINGS_CATEGORY_KEY, cat);
    window.localStorage.setItem(TAB_HISTORY_KEY, JSON.stringify(history));
  } catch {
    // Ignore storage quota / private-mode restrictions
  }
}

export default function App() {
  const {
    data,
    loading,
    storageError,
    discoveredNodes,
    activeWorkspaceMeta,
    workspaceSummaries,
    activeDbId,
    mountedDbIds,
    objectSources,
    archiveSources,
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
    isSyncing,
    syncDirectoryName,
    selectSyncDirectory,
    syncNow,
  } = useUrbexStore();

  const [activeModule, setActiveModule] = useState<ModuleId>(() => readStoredModule());
  const [settingsCategory, setSettingsCategory] = useState<SettingsCategory>(() =>
    readStoredSettingsCategory()
  );
  const tabHistoryRef = useRef<ModuleId[]>(readStoredTabHistory(readStoredModule()));
  const activeModuleRef = useRef<ModuleId>(activeModule);
  activeModuleRef.current = activeModule;
  const settingsCategoryRef = useRef<SettingsCategory>(settingsCategory);
  settingsCategoryRef.current = settingsCategory;

  const [selectedObjectId, setSelectedObjectId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalCoords, setModalCoords] = useState<{ lat: number; lon: number } | null>(null);
  const [editingObject, setEditingObject] = useState<UrbexObject | null>(null);
  const [modalDraftOverride, setModalDraftOverride] = useState<QuickObjectDraft | null>(null);
  const [zoneEditingObjectId, setZoneEditingObjectId] = useState<string | null>(null);
  const [pointMoveSession, setPointMoveSession] = useState<{
    target: PointMoveTarget;
    modalDraft?: QuickObjectDraft | null;
  } | null>(null);
  const mapCameraRef = useRef<MapCameraState | null>(null);
  const mapCursorCoordsRef = useRef<{ lat: number; lon: number } | null>(null);
  const [mapFocusTarget, setMapFocusTarget] = useState<MapFocusTarget | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(() =>
    typeof document !== 'undefined' ? Boolean(document.fullscreenElement) : false
  );

  const powerProfile = data?.settings.powerProfile ?? 'balanced';
  const gpsIntervalMs = data?.settings.gpsIntervalMs ?? 10_000;
  const meshEnabled = data?.settings.meshEnabled ?? false;
  const radioChannelsTabEnabled = data?.settings.radioChannelsTabEnabled !== false;
  const commsVisible = isCommsModuleVisible(meshEnabled, radioChannelsTabEnabled);
  const keepScreenAwake = data?.settings.keepScreenAwake ?? false;
  const pdaModeEnabled = Boolean(data?.settings.pdaModeEnabled);
  const pdaOrientation = data?.settings.pdaOrientation ?? 'portrait';
  const editorEnabled = Boolean(data?.settings.editorEnabled);
  const requiresGps =
    activeModule === 'map' || (activeModule === 'mesh' && meshEnabled);

  const location = useLocationEngine(powerProfile, gpsIntervalMs, requiresGps);

  // Seed browser history entry & listen for Back swipe / browser Back button
  useEffect(() => {
    if (typeof window === 'undefined') return;
    try {
      window.history.replaceState(
        {
          urbexModule: activeModuleRef.current,
          urbexSettingsCategory: settingsCategoryRef.current,
        },
        ''
      );
    } catch {
      // Ignore history API sandbox restrictions
    }

    const handlePopState = (event: PopStateEvent) => {
      const state = event.state as
        | { urbexModule?: string; urbexSettingsCategory?: string }
        | null;
      let nextMod: ModuleId | null = null;
      if (state && isValidModuleId(state.urbexModule)) {
        nextMod = state.urbexModule;
        const { remaining } = popTabHistory(tabHistoryRef.current);
        tabHistoryRef.current =
          remaining.length > 0 && remaining[remaining.length - 1] === nextMod
            ? remaining
            : pushTabHistory(remaining, nextMod);
      } else {
        const popped = popTabHistory(tabHistoryRef.current);
        if (popped.previous) {
          nextMod = popped.previous;
          tabHistoryRef.current = popped.remaining;
        }
      }

      if (!nextMod) return;

      const sanitized = sanitizeActiveModule(nextMod, {
        pdaModeEnabled,
        meshEnabled,
        radioChannelsTabEnabled,
      });
      const nextCat =
        state && isValidSettingsCategory(state.urbexSettingsCategory)
          ? state.urbexSettingsCategory
          : settingsCategoryRef.current;

      setActiveModule(sanitized);
      if (state && isValidSettingsCategory(state.urbexSettingsCategory)) {
        setSettingsCategory(nextCat);
      }
      if (sanitized !== 'objects') setSelectedObjectId(null);
      if (sanitized !== 'map') {
        setZoneEditingObjectId(null);
        setPointMoveSession(null);
      }
      persistNavState(sanitized, tabHistoryRef.current, nextCat);
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [pdaModeEnabled, meshEnabled, radioChannelsTabEnabled]);

  useEffect(() => {
    const onFsChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  useEffect(() => {
    const orientationApi = (
      screen as Screen & {
        orientation?: {
          lock?: (orientation: string) => Promise<void>;
          unlock?: () => void;
        };
      }
    ).orientation;
    if (!orientationApi) return;
    if (!pdaModeEnabled || pdaOrientation === 'auto') {
      try {
        orientationApi.unlock?.();
      } catch {
        // Ignore browser orientation unlock restrictions
      }
      return;
    }
    const target = pdaOrientation === 'landscape' ? 'landscape' : 'portrait';
    orientationApi.lock?.(target)?.catch(() => {});
  }, [pdaModeEnabled, pdaOrientation]);

  useEffect(() => {
    if (loading || !data) return;
    const sanitized = sanitizeActiveModule(activeModule, {
      pdaModeEnabled,
      meshEnabled,
      radioChannelsTabEnabled,
    });
    if (sanitized !== activeModule) {
      tabHistoryRef.current = pushTabHistory(tabHistoryRef.current, sanitized);
      setActiveModule(sanitized);
      persistNavState(sanitized, tabHistoryRef.current, settingsCategoryRef.current);
    }
  }, [loading, data, pdaModeEnabled, meshEnabled, radioChannelsTabEnabled, activeModule]);

  useEffect(() => {
    if (!editorEnabled) {
      setModalOpen(false);
      setEditingObject(null);
      setModalDraftOverride(null);
      setZoneEditingObjectId(null);
      setPointMoveSession(null);
    }
  }, [editorEnabled]);

  const handleToggleFullscreen = useCallback(() => {
    if (typeof document === 'undefined') return;
    if (document.fullscreenElement) {
      document.exitFullscreen?.().catch(() => {});
    } else {
      document.documentElement.requestFullscreen?.().catch(() => {});
    }
  }, []);

  // Screen Wake Lock when keepScreenAwake or PDA mode is enabled
  useEffect(() => {
    if ((!keepScreenAwake && !pdaModeEnabled) || activeModule !== 'map') return;
    let sentinel: { release: () => Promise<void> } | null = null;
    let cancelled = false;
    const nav = navigator as Navigator & {
      wakeLock?: { request: (type: 'screen') => Promise<{ release: () => Promise<void> }> };
    };
    if (nav.wakeLock) {
      nav.wakeLock
        .request('screen')
        .then((s) => {
          if (cancelled) {
            s.release().catch(() => {});
          } else {
            sentinel = s;
          }
        })
        .catch(() => {});
    }
    return () => {
      cancelled = true;
      sentinel?.release().catch(() => {});
    };
  }, [keepScreenAwake, pdaModeEnabled, activeModule]);

  const zoneEditingObject = useMemo(
    () =>
      zoneEditingObjectId && data
        ? data.objects.find((o) => o.id === zoneEditingObjectId) ?? null
        : null,
    [zoneEditingObjectId, data]
  );

  const navigateToModule = useCallback(
    (mod: ModuleId, nextCategory?: SettingsCategory) => {
      const cat = nextCategory ?? settingsCategoryRef.current;
      if (nextCategory) {
        setSettingsCategory(nextCategory);
      }
      if (mod !== activeModuleRef.current) {
        tabHistoryRef.current = pushTabHistory(tabHistoryRef.current, mod);
        if (typeof window !== 'undefined') {
          try {
            window.history.pushState(
              { urbexModule: mod, urbexSettingsCategory: cat },
              ''
            );
          } catch {
            // Ignore sandbox history restrictions
          }
        }
      }
      setActiveModule(mod);
      if (mod !== 'objects') {
        setSelectedObjectId(null);
      }
      if (mod !== 'map') {
        setZoneEditingObjectId(null);
        setPointMoveSession(null);
      }
      persistNavState(mod, tabHistoryRef.current, cat);
    },
    []
  );

  const handleSelectModule = useCallback(
    (mod: ModuleId) => {
      navigateToModule(mod);
    },
    [navigateToModule]
  );

  const handleOpenSettingsCategory = useCallback(
    (category: SettingsCategory) => {
      navigateToModule('settings', category);
    },
    [navigateToModule]
  );

  const handleSettingsCategoryChange = useCallback((cat: SettingsCategory) => {
    setSettingsCategory(cat);
    persistNavState(activeModuleRef.current, tabHistoryRef.current, cat);
  }, []);

  const handleCameraChange = useCallback((cam: MapCameraState) => {
    mapCameraRef.current = cam;
  }, []);

  const handleCursorChange = useCallback((coords: { lat: number; lon: number }) => {
    mapCursorCoordsRef.current = coords;
  }, []);

  const handleOpenCreateModal = useCallback(
    (lat?: number, lon?: number) => {
      if (!editorEnabled) return;
      const fallbackCoords =
        activeModule === 'map' && mapCursorCoordsRef.current
          ? mapCursorCoordsRef.current
          : { lat: location.fix.lat, lon: location.fix.lon };
      setEditingObject(null);
      setModalDraftOverride(null);
      setModalCoords({
        lat: lat ?? fallbackCoords.lat,
        lon: lon ?? fallbackCoords.lon,
      });
      setModalOpen(true);
    },
    [editorEnabled, activeModule, location.fix.lat, location.fix.lon]
  );

  const handleEditObject = useCallback(
    (obj: UrbexObject) => {
      if (!editorEnabled) return;
      setEditingObject(obj);
      setModalDraftOverride(null);
      setModalCoords({ lat: obj.lat, lon: obj.lon });
      setModalOpen(true);
    },
    [editorEnabled]
  );

  const handleStartZoneEditForObject = useCallback(
    (obj: UrbexObject) => {
      if (!editorEnabled) return;
      setPointMoveSession(null);
      setZoneEditingObjectId(obj.id);
      navigateToModule('map');
    },
    [editorEnabled, navigateToModule]
  );

  const handleStartPointMoveForObject = useCallback(
    (obj: UrbexObject) => {
      if (!editorEnabled) return;
      setZoneEditingObjectId(null);
      setPointMoveSession({
        target: {
          objectId: obj.id,
          title: obj.title,
          category: obj.category,
          icon: obj.icon,
          status: obj.status,
          lat: obj.lat,
          lon: obj.lon,
          zone: obj.zone,
          zoneColor: obj.zoneColor,
          zoneStyle: obj.zoneStyle,
          zoneHatched: obj.zoneHatched,
          isModalDraft: false,
          token: Date.now(),
        },
        modalDraft: null,
      });
      navigateToModule('map');
    },
    [editorEnabled, navigateToModule]
  );

  const handleOpenPointMoverFromModal = useCallback(
    (draft: QuickObjectDraft) => {
      if (!editorEnabled) return;
      const parsedLat = Number(draft.latStr.replace(',', '.'));
      const parsedLon = Number(draft.lonStr.replace(',', '.'));
      const lat = Number.isFinite(parsedLat) && parsedLat >= -90 && parsedLat <= 90 ? parsedLat : location.fix.lat;
      const lon = Number.isFinite(parsedLon) && parsedLon >= -180 && parsedLon <= 180 ? parsedLon : location.fix.lon;
      setModalOpen(false);
      setZoneEditingObjectId(null);
      setPointMoveSession({
        target: {
          objectId: draft.editingObject?.id,
          title: draft.title || draft.editingObject?.title || 'Новая точка',
          category: draft.category,
          icon: draft.icon,
          status: draft.status,
          lat,
          lon,
          zone: draft.zone,
          zoneColor: draft.zoneColor,
          zoneStyle: draft.zoneStyle,
          zoneHatched: draft.zoneHatched,
          isModalDraft: true,
          token: Date.now(),
        },
        modalDraft: draft,
      });
      navigateToModule('map');
    },
    [editorEnabled, location.fix.lat, location.fix.lon, navigateToModule]
  );

  const handleSavePointMove = useCallback(
    async (result: PointMoveResult) => {
      if (!editorEnabled || !pointMoveSession) return;
      const { target, modalDraft } = pointMoveSession;
      if (modalDraft) {
        setModalDraftOverride({
          ...modalDraft,
          latStr: result.lat.toFixed(6),
          lonStr: result.lon.toFixed(6),
          zone: result.zone ?? modalDraft.zone,
        });
        setEditingObject(modalDraft.editingObject);
        setModalCoords({ lat: result.lat, lon: result.lon });
        setPointMoveSession(null);
        setModalOpen(true);
        return;
      }

      if (target.objectId && data) {
        const existing = data.objects.find((o) => o.id === target.objectId);
        if (existing) {
          const dLat = result.lat - existing.lat;
          const dLon = result.lon - existing.lon;
          await upsertObject({
            ...existing,
            lat: result.lat,
            lon: result.lon,
            zone: result.shiftedZone
              ? translateZone(existing.zone, dLat, dLon)
              : existing.zone,
            updatedAt: Date.now(),
          });
        }
      }
      setPointMoveSession(null);
    },
    [editorEnabled, pointMoveSession, data, upsertObject]
  );

  const handleCancelPointMove = useCallback(() => {
    if (!pointMoveSession) return;
    const { modalDraft } = pointMoveSession;
    setPointMoveSession(null);
    if (modalDraft) {
      setModalDraftOverride(modalDraft);
      setEditingObject(modalDraft.editingObject);
      setModalOpen(true);
    }
  }, [pointMoveSession]);

  const handleOpenZoneEditorFromModal = useCallback(
    async (obj: UrbexObject) => {
      if (!editorEnabled) return;
      await upsertObject(obj);
      setSelectedObjectId(obj.id);
      setZoneEditingObjectId(obj.id);
      navigateToModule('map');
    },
    [editorEnabled, upsertObject, navigateToModule]
  );

  const handleSaveObjectZone = useCallback(
    async (
      objectId: string,
      updates: {
        zone: GeoPoint[];
        zoneColor?: string;
        zoneStyle: ZoneStyle;
        zoneHatched: boolean;
      }
    ) => {
      if (!editorEnabled || !data) return;
      const target = data.objects.find((o) => o.id === objectId);
      if (!target) return;
      await upsertObject({
        ...target,
        zone: updates.zone,
        zoneColor: updates.zoneColor,
        zoneStyle: updates.zoneStyle,
        zoneHatched: updates.zoneHatched,
        updatedAt: Date.now(),
      });
      setZoneEditingObjectId(null);
    },
    [editorEnabled, data, upsertObject]
  );

  const handleOpenObjectInDatabase = useCallback(
    (objectId: string) => {
      setSelectedObjectId(objectId);
      navigateToModule('objects');
    },
    [navigateToModule]
  );

  const handleFocusObjectOnMap = useCallback(
    (obj: UrbexObject) => {
      setZoneEditingObjectId(null);
      setPointMoveSession(null);
      setSelectedObjectId(obj.id);
      setMapFocusTarget({
        objectId: obj.id,
        lat: obj.lat,
        lon: obj.lon,
        token: Date.now(),
      });
      mapCameraRef.current = {
        lat: obj.lat,
        lon: obj.lon,
        zoomIdx: 4,
      };
      navigateToModule('map');
    },
    [navigateToModule]
  );

  const handleUpdateObjectStatus = useCallback(
    async (obj: UrbexObject, status: ReliabilityStatus) => {
      if (!editorEnabled) return;
      await upsertObject({
        ...obj,
        status,
        updatedAt: Date.now(),
      });
    },
    [editorEnabled, upsertObject]
  );

  const handleChangeMapLayer = useCallback(
    async (layer: MapLayerMode) => {
      if (!data) return;
      await updateSettings({
        ...data.settings,
        mapLayer: layer,
        updatedAt: Date.now(),
      });
    },
    [data, updateSettings]
  );

  if (loading || !data) {
    return (
      <div className="min-h-screen bg-[#0C0F0E] text-[#E6EBE8] flex items-center justify-center p-6">
        <div className="text-sm text-[#98A49E]">
          Инициализация автономной базы Urbex-T...
        </div>
      </div>
    );
  }

  const isMapFullBleed = activeModule === 'map';

  return (
    <MotionConfig reducedMotion="user">
      <AppShell
        activeModule={activeModule}
        meshEnabled={meshEnabled}
        radioChannelsTabEnabled={radioChannelsTabEnabled}
        editorEnabled={editorEnabled}
        hideQuickAddPoint={
          activeModule === 'map' &&
          (Boolean(zoneEditingObjectId) || Boolean(pointMoveSession) || modalOpen)
        }
        pdaModeEnabled={pdaModeEnabled}
        pdaOrientation={pdaOrientation}
        hapticFeedback={data.settings.hapticFeedback !== false}
        isFullscreen={isFullscreen}
        onToggleFullscreen={handleToggleFullscreen}
        fullBleed={isMapFullBleed}
        onSelectModule={handleSelectModule}
        onQuickAddPoint={() => handleOpenCreateModal()}
      >
        <AnimatePresence>
          {storageError && (
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.16 }}
              role="alert"
              className="m-4 mb-0 p-3 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs text-[#F1ACA7]"
            >
              {storageError}
            </motion.div>
          )}
        </AnimatePresence>

        <motion.div
          key={activeModule}
          initial={{ opacity: 0.94 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.12, ease: 'easeOut' }}
          className={isMapFullBleed ? 'flex-1 flex flex-col' : undefined}
        >
              {activeModule === 'map' && (
                <MapView
                  objects={data.objects}
                  gpsFix={location.fix}
                  isPollingGps={location.isPolling}
                  gpsError={location.errorMsg}
                  settings={data.settings}
                  editorEnabled={editorEnabled}
                  savedCamera={mapCameraRef.current}
                  onCameraChange={handleCameraChange}
                  onCursorChange={handleCursorChange}
                  hideHud={modalOpen}
                  focusTarget={mapFocusTarget}
                  zoneEditingObject={editorEnabled ? zoneEditingObject : null}
                  onEditZoneForObject={handleStartZoneEditForObject}
                  onSaveObjectZone={handleSaveObjectZone}
                  onCancelZoneEdit={() => setZoneEditingObjectId(null)}
                  pointMoveTarget={editorEnabled ? pointMoveSession?.target ?? null : null}
                  onMovePointForObject={handleStartPointMoveForObject}
                  onSavePointMove={handleSavePointMove}
                  onCancelPointMove={handleCancelPointMove}
                  onRequestSingleFix={location.requestSingleFix}
                  onSetManualFix={location.setManualFix}
                  onOpenObjectInDatabase={handleOpenObjectInDatabase}
                  onAddOfflineSector={addOfflineSector}
                  onChangeMapLayer={handleChangeMapLayer}
                />
              )}

              {activeModule === 'objects' && (
                <ObjectsView
                  objects={data.objects}
                  objectSources={objectSources}
                  activeWorkspaceName={activeWorkspaceMeta?.name}
                  mountedCount={mountedDbIds.length}
                  onOpenDbManager={() => handleOpenSettingsCategory('databases')}
                  gpsFix={location.fix}
                  coordFormat={data.settings.coordinateFormat}
                  pageSize={data.settings.pageSize}
                  defaultViewMode={data.settings.defaultObjectsView}
                  editorEnabled={editorEnabled}
                  confirmBeforeDelete={data.settings.confirmBeforeDelete !== false}
                  selectedObjectId={selectedObjectId}
                  onSelectObject={setSelectedObjectId}
                  onFocusObjectOnMap={handleFocusObjectOnMap}
                  onCreateNew={() => handleOpenCreateModal()}
                  onEditObject={handleEditObject}
                  onEditZoneOnMap={handleStartZoneEditForObject}
                  onMovePointOnMap={handleStartPointMoveForObject}
                  onDeleteObject={removeObject}
                  onUpdateStatus={handleUpdateObjectStatus}
                />
              )}

              {activeModule === 'mesh' && commsVisible && (
                <MeshView
                  meshEnabled={data.settings.meshEnabled}
                  radioChannelsTabEnabled={radioChannelsTabEnabled}
                  channels={MESH_CHANNELS}
                  nodes={discoveredNodes}
                  messages={data.meshMessages}
                  radioChannels={data.settings.radioChannels ?? []}
                  profile={data.profile}
                  gpsFix={location.fix}
                  coordFormat={data.settings.coordinateFormat}
                  onSendPacket={sendMeshPacket}
                  onSaveRadioChannels={(nextChannels) =>
                    updateSettings({
                      ...data.settings,
                      radioChannels: nextChannels,
                      updatedAt: Date.now(),
                    })
                  }
                  onOpenSettings={() => handleOpenSettingsCategory('radio_mesh')}
                />
              )}

              {activeModule === 'base' && (
                <BaseView
                  articles={data.baseArticles}
                  editorEnabled={editorEnabled}
                  onSaveArticle={upsertBaseArticle}
                  onDeleteArticle={removeBaseArticle}
                />
              )}

              {activeModule === 'archive' && (
                <ArchiveView
                  items={data.archiveItems}
                  archiveSources={archiveSources}
                  mountedCount={mountedDbIds.length}
                  objects={data.objects}
                  editorEnabled={editorEnabled}
                  onAddItem={addArchiveItem}
                  onDeleteItem={removeArchiveItem}
                />
              )}

              {activeModule === 'profile' && (
                <ProfileView
                  profile={data.profile}
                  stats={{
                    objectsCount: data.objects.length,
                    archiveCount: data.archiveItems.length,
                    meshCount: data.meshMessages.length,
                    articlesCount: data.baseArticles.length,
                  }}
                  onSaveProfile={updateProfile}
                />
              )}

              {activeModule === 'settings' && (
                <SettingsView
                  settings={data.settings}
                  initialCategory={settingsCategory}
                  onCategoryChange={handleSettingsCategoryChange}
                  isFullscreen={isFullscreen}
                  onToggleFullscreen={handleToggleFullscreen}
                  onUpdateSettings={updateSettings}
                  onRemoveSector={removeOfflineSector}
                  onExportUrbexDb={exportUrbexDbString}
                  onInspectUrbexDb={inspectUrbexDbFile}
                  onApplyImport={applyUrbexDbImport}
                  onResetSeed={resetToSeed}
                  activeDbId={activeDbId}
                  mountedDbIds={mountedDbIds}
                  workspaceSummaries={workspaceSummaries}
                  onSwitchDatabase={switchDatabase}
                  onToggleMountDatabase={toggleMountDatabase}
                  onCreateDatabase={createDatabase}
                  onRenameDatabase={renameDatabase}
                  onDeleteDatabase={deleteDatabase}
                  onImportIntoNewDatabase={importIntoNewDatabase}
                  isSyncing={isSyncing}
                  syncDirectoryName={syncDirectoryName}
                  onSelectSyncDirectory={selectSyncDirectory}
                  onSyncNow={syncNow}
                />
              )}

              {activeModule === 'menu' && (
                <MenuView
                  profile={data.profile}
                  settings={data.settings}
                  activeDbName={activeWorkspaceMeta?.name}
                  mountedCount={mountedDbIds.length}
                  stats={{
                    objectsCount: data.objects.length,
                    archiveCount: data.archiveItems.length,
                    meshCount: data.meshMessages.length,
                    articlesCount: data.baseArticles.length,
                  }}
                  onSelectModule={handleSelectModule}
                  onOpenSettingsCategory={handleOpenSettingsCategory}
                />
              )}

              {activeModule === 'apps' && pdaModeEnabled && (
                <AppsView
                  settings={data.settings}
                  isFullscreen={isFullscreen}
                  onToggleFullscreen={handleToggleFullscreen}
                  onUpdateSettings={updateSettings}
                  onSelectModule={handleSelectModule}
                  stats={{
                    objectsCount: data.objects.length,
                    archiveCount: data.archiveItems.length,
                    meshCount: data.meshMessages.length,
                    articlesCount: data.baseArticles.length,
                  }}
                />
              )}
          </motion.div>

        <QuickObjectModal
          open={modalOpen}
          initialLat={modalCoords?.lat ?? location.fix.lat}
          initialLon={modalCoords?.lon ?? location.fix.lon}
          defaultZoneRadiusM={data.settings.defaultZoneRadiusM ?? 80}
          editingObject={editingObject}
          draftOverride={modalDraftOverride}
          authorNodeId={data.profile.nodeId}
          onClose={() => {
            setModalOpen(false);
            setModalDraftOverride(null);
          }}
          onOpenZoneEditor={handleOpenZoneEditorFromModal}
          onOpenPointMover={handleOpenPointMoverFromModal}
          onSave={async (obj) => {
            await upsertObject(obj);
            setModalDraftOverride(null);
            setSelectedObjectId(obj.id);
          }}
        />
      </AppShell>
    </MotionConfig>
  );
}
