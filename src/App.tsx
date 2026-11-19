import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion, MotionConfig } from 'motion/react';
import { AppShell } from './components/AppShell';
import { QuickObjectModal } from './components/QuickObjectModal';
import { MESH_CHANNELS } from './data/seed';
import { useLocationEngine } from './domain/useLocationEngine';
import { useUrbexStore } from './domain/useUrbexStore';
import type { MapCameraState, MapFocusTarget } from './features/map/MapView';
import type {
  GeoPoint,
  MapLayerMode,
  ModuleId,
  ReliabilityStatus,
  UrbexObject,
  ZoneStyle,
} from './core/types';

// Lazy-load each PDA module so memory and CPU stay minimal until requested
const MapView = lazy(() => import('./features/map/MapView'));
const ObjectsView = lazy(() => import('./features/objects/ObjectsView'));
const MeshView = lazy(() => import('./features/mesh/MeshView'));
const BaseView = lazy(() => import('./features/base/BaseView'));
const ArchiveView = lazy(() => import('./features/archive/ArchiveView'));
const ProfileView = lazy(() => import('./features/profile/ProfileView'));
const SettingsView = lazy(() => import('./features/settings/SettingsView'));
const MenuView = lazy(() => import('./features/menu/MenuView'));
const AppsView = lazy(() => import('./features/apps/AppsView'));

function ModuleFallback() {
  return (
    <div className="m-4 bg-[#131816] border border-[#252E2A] rounded-xl p-8 text-sm text-[#98A49E]">
      Загрузка модуля...
    </div>
  );
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
  } = useUrbexStore();

  const [activeModule, setActiveModule] = useState<ModuleId>('map');
  const [selectedObjectId, setSelectedObjectId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalCoords, setModalCoords] = useState<{ lat: number; lon: number } | null>(null);
  const [editingObject, setEditingObject] = useState<UrbexObject | null>(null);
  const [zoneEditingObjectId, setZoneEditingObjectId] = useState<string | null>(null);
  const [mapCamera, setMapCamera] = useState<MapCameraState | null>(null);
  const [mapFocusTarget, setMapFocusTarget] = useState<MapFocusTarget | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(() =>
    typeof document !== 'undefined' ? Boolean(document.fullscreenElement) : false
  );

  const powerProfile = data?.settings.powerProfile ?? 'balanced';
  const gpsIntervalMs = data?.settings.gpsIntervalMs ?? 10_000;
  const meshEnabled = data?.settings.meshEnabled ?? false;
  const keepScreenAwake = data?.settings.keepScreenAwake ?? false;
  const pdaModeEnabled = Boolean(data?.settings.pdaModeEnabled);
  const pdaOrientation = data?.settings.pdaOrientation ?? 'portrait';
  const editorEnabled = Boolean(data?.settings.editorEnabled);
  const requiresGps =
    activeModule === 'map' || (activeModule === 'mesh' && meshEnabled);

  const location = useLocationEngine(powerProfile, gpsIntervalMs, requiresGps);

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
    if (!pdaModeEnabled && activeModule === 'apps') {
      setActiveModule('map');
    }
  }, [pdaModeEnabled, activeModule]);

  useEffect(() => {
    if (!editorEnabled) {
      setModalOpen(false);
      setEditingObject(null);
      setZoneEditingObjectId(null);
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

  const handleSelectModule = useCallback((mod: ModuleId) => {
    setActiveModule(mod);
    if (mod !== 'objects') {
      setSelectedObjectId(null);
    }
    if (mod !== 'map') {
      setZoneEditingObjectId(null);
    }
  }, []);

  const handleOpenCreateModal = useCallback(
    (lat?: number, lon?: number) => {
      if (!editorEnabled) return;
      setEditingObject(null);
      setModalCoords({
        lat: lat ?? location.fix.lat,
        lon: lon ?? location.fix.lon,
      });
      setModalOpen(true);
    },
    [editorEnabled, location.fix.lat, location.fix.lon]
  );

  const handleEditObject = useCallback(
    (obj: UrbexObject) => {
      if (!editorEnabled) return;
      setEditingObject(obj);
      setModalCoords({ lat: obj.lat, lon: obj.lon });
      setModalOpen(true);
    },
    [editorEnabled]
  );

  const handleStartZoneEditForObject = useCallback(
    (obj: UrbexObject) => {
      if (!editorEnabled) return;
      setZoneEditingObjectId(obj.id);
      setActiveModule('map');
    },
    [editorEnabled]
  );

  const handleOpenZoneEditorFromModal = useCallback(
    async (obj: UrbexObject) => {
      if (!editorEnabled) return;
      await upsertObject(obj);
      setSelectedObjectId(obj.id);
      setZoneEditingObjectId(obj.id);
      setActiveModule('map');
    },
    [editorEnabled, upsertObject]
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

  const handleOpenObjectInDatabase = useCallback((objectId: string) => {
    setSelectedObjectId(objectId);
    setActiveModule('objects');
  }, []);

  const handleFocusObjectOnMap = useCallback((obj: UrbexObject) => {
    setZoneEditingObjectId(null);
    setSelectedObjectId(obj.id);
    setMapFocusTarget({
      objectId: obj.id,
      lat: obj.lat,
      lon: obj.lon,
      token: Date.now(),
    });
    setMapCamera({
      lat: obj.lat,
      lon: obj.lon,
      zoomIdx: 4,
    });
    setActiveModule('map');
  }, []);

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
        <motion.div
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: [0.6, 1, 0.6], scale: 1 }}
          transition={{ duration: 1.4, repeat: Infinity, ease: 'easeInOut' }}
          className="text-sm text-[#98A49E]"
        >
          Инициализация автономной базы Urbex-T...
        </motion.div>
      </div>
    );
  }

  const isMapFullBleed = activeModule === 'map';

  return (
    <MotionConfig reducedMotion="user">
      <AppShell
        activeModule={activeModule}
        meshEnabled={meshEnabled}
        editorEnabled={editorEnabled}
        pdaModeEnabled={pdaModeEnabled}
        pdaOrientation={pdaOrientation}
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

        <Suspense fallback={<ModuleFallback />}>
          <motion.div
            key={activeModule}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
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
                  savedCamera={mapCamera}
                  onCameraChange={setMapCamera}
                  focusTarget={mapFocusTarget}
                  zoneEditingObject={editorEnabled ? zoneEditingObject : null}
                  onEditZoneForObject={handleStartZoneEditForObject}
                  onSaveObjectZone={handleSaveObjectZone}
                  onCancelZoneEdit={() => setZoneEditingObjectId(null)}
                  onRequestSingleFix={location.requestSingleFix}
                  onSetManualFix={location.setManualFix}
                  onCreateAtCoords={(lat, lon) => handleOpenCreateModal(lat, lon)}
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
                  onOpenDbManager={() => handleSelectModule('settings')}
                  gpsFix={location.fix}
                  coordFormat={data.settings.coordinateFormat}
                  pageSize={data.settings.pageSize}
                  defaultViewMode={data.settings.defaultObjectsView}
                  editorEnabled={editorEnabled}
                  selectedObjectId={selectedObjectId}
                  onSelectObject={setSelectedObjectId}
                  onFocusObjectOnMap={handleFocusObjectOnMap}
                  onCreateNew={() => handleOpenCreateModal()}
                  onEditObject={handleEditObject}
                  onEditZoneOnMap={handleStartZoneEditForObject}
                  onDeleteObject={removeObject}
                  onUpdateStatus={handleUpdateObjectStatus}
                />
              )}

              {activeModule === 'mesh' && (
                <MeshView
                  meshEnabled={data.settings.meshEnabled}
                  channels={MESH_CHANNELS}
                  nodes={discoveredNodes}
                  messages={data.meshMessages}
                  profile={data.profile}
                  gpsFix={location.fix}
                  coordFormat={data.settings.coordinateFormat}
                  onSendPacket={sendMeshPacket}
                  onOpenSettings={() => handleSelectModule('settings')}
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
        </Suspense>

        <QuickObjectModal
          open={modalOpen}
          initialLat={modalCoords?.lat ?? location.fix.lat}
          initialLon={modalCoords?.lon ?? location.fix.lon}
          defaultZoneRadiusM={data.settings.defaultZoneRadiusM ?? 80}
          editingObject={editingObject}
          authorNodeId={data.profile.nodeId}
          onClose={() => setModalOpen(false)}
          onOpenZoneEditor={handleOpenZoneEditorFromModal}
          onSave={async (obj) => {
            await upsertObject(obj);
            setSelectedObjectId(obj.id);
          }}
        />
      </AppShell>
    </MotionConfig>
  );
}
