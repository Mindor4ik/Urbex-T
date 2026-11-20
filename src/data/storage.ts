import { DEFAULT_CLOUD_SYNC_CONFIG } from '../core/cloudSync';
import { createDefaultRadioChannels } from '../core/radio';
import { createSeedPayload } from './seed';
import type {
  AppSettings,
  ArchiveItem,
  BaseArticle,
  DatabaseWorkspaceMeta,
  DatabaseWorkspaceRecord,
  MeshMessage,
  UrbexDbPayload,
  UrbexObject,
  UserProfile,
} from '../core/types';

const DB_NAME = 'urbex_t_db';
const DB_VERSION = 2;
const DEFAULT_WORKSPACE_ID = 'db-moscow-main';

const STORES = {
  objects: 'objects',
  meshMessages: 'meshMessages',
  baseArticles: 'baseArticles',
  archiveItems: 'archiveItems',
  profile: 'profile',
  settings: 'settings',
  workspaces: 'workspaces',
  workspaceState: 'workspaceState',
} as const;

type StoreName = (typeof STORES)[keyof typeof STORES];

export interface WorkspaceSelectionState {
  id: 'state';
  activeDbId: string;
  mountedDbIds: string[];
}

export interface LoadedMultiDbState {
  workspaces: DatabaseWorkspaceRecord[];
  activeDbId: string;
  mountedDbIds: string[];
}

let memoryWorkspaces: DatabaseWorkspaceRecord[] | null = null;
let memorySelection: WorkspaceSelectionState | null = null;

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORES.objects)) {
        const objStore = db.createObjectStore(STORES.objects, { keyPath: 'id' });
        objStore.createIndex('status', 'status', { unique: false });
        objStore.createIndex('updatedAt', 'updatedAt', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORES.meshMessages)) {
        const meshStore = db.createObjectStore(STORES.meshMessages, { keyPath: 'id' });
        meshStore.createIndex('channelId', 'channelId', { unique: false });
        meshStore.createIndex('timestamp', 'timestamp', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORES.baseArticles)) {
        const baseStore = db.createObjectStore(STORES.baseArticles, { keyPath: 'id' });
        baseStore.createIndex('section', 'section', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORES.archiveItems)) {
        const archStore = db.createObjectStore(STORES.archiveItems, { keyPath: 'id' });
        archStore.createIndex('type', 'type', { unique: false });
        archStore.createIndex('objectId', 'objectId', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORES.profile)) {
        db.createObjectStore(STORES.profile, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORES.settings)) {
        db.createObjectStore(STORES.settings, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORES.workspaces)) {
        db.createObjectStore(STORES.workspaces, { keyPath: 'meta.id' });
      }
      if (!db.objectStoreNames.contains(STORES.workspaceState)) {
        db.createObjectStore(STORES.workspaceState, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('Failed to open IndexedDB'));
  });
}

function normalizeSettings(loadedSettings: AppSettings): AppSettings {
  return {
    ...loadedSettings,
    meshEnabled: loadedSettings.meshEnabled ?? false,
    mapLayer: loadedSettings.mapLayer ?? 'satellite',
    showObjectZones: loadedSettings.showObjectZones ?? true,
    showObjectLabels: loadedSettings.showObjectLabels ?? true,
    showMapGrid: loadedSettings.showMapGrid ?? true,
    showCompassHud: loadedSettings.showCompassHud ?? true,
    defaultObjectsView: loadedSettings.defaultObjectsView ?? 'cards',
    autoCenterGps: loadedSettings.autoCenterGps ?? false,
    keepScreenAwake: loadedSettings.keepScreenAwake ?? false,
    defaultZoneRadiusM: loadedSettings.defaultZoneRadiusM ?? 80,
    hazardProximityAlertM: loadedSettings.hazardProximityAlertM ?? 150,
    confirmBeforeDelete: loadedSettings.confirmBeforeDelete ?? true,
    hapticFeedback: loadedSettings.hapticFeedback ?? true,
    maskCoordinatesOnScreen: loadedSettings.maskCoordinatesOnScreen ?? false,
    pdaModeEnabled: loadedSettings.pdaModeEnabled ?? false,
    pdaOrientation: loadedSettings.pdaOrientation ?? 'portrait',
    editorEnabled: loadedSettings.editorEnabled ?? false,
    radioChannels:
      Array.isArray(loadedSettings.radioChannels) && loadedSettings.radioChannels.length > 0
        ? loadedSettings.radioChannels
        : createDefaultRadioChannels(loadedSettings.updatedAt || Date.now()),
    cloudSync: loadedSettings.cloudSync
      ? { ...DEFAULT_CLOUD_SYNC_CONFIG, ...loadedSettings.cloudSync }
      : { ...DEFAULT_CLOUD_SYNC_CONFIG },
  };
}

function getAllFromStore<T>(db: IDBDatabase, storeName: StoreName): Promise<T[]> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const store = tx.objectStore(storeName);
    const req = store.getAll();
    req.onsuccess = () => resolve((req.result ?? []) as T[]);
    req.onerror = () => reject(req.error);
  });
}

function putInStore<T>(db: IDBDatabase, storeName: StoreName, value: T): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    const store = tx.objectStore(storeName);
    store.put(value);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

function deleteFromStore(db: IDBDatabase, storeName: StoreName, id: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    tx.objectStore(storeName).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

const LEGACY_STORES: StoreName[] = [
  STORES.objects,
  STORES.meshMessages,
  STORES.baseArticles,
  STORES.archiveItems,
  STORES.profile,
  STORES.settings,
];

export async function replaceFullPayload(payload: UrbexDbPayload): Promise<void> {
  try {
    const db = await openDatabase();
    const tx = db.transaction(LEGACY_STORES, 'readwrite');
    for (const storeName of LEGACY_STORES) {
      tx.objectStore(storeName).clear();
    }
    for (const obj of payload.objects) tx.objectStore(STORES.objects).put(obj);
    for (const msg of payload.meshMessages) tx.objectStore(STORES.meshMessages).put(msg);
    for (const art of payload.baseArticles) tx.objectStore(STORES.baseArticles).put(art);
    for (const item of payload.archiveItems) tx.objectStore(STORES.archiveItems).put(item);
    tx.objectStore(STORES.profile).put(payload.profile);
    tx.objectStore(STORES.settings).put(payload.settings);
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  } catch {
    // fallback handled by memoryWorkspaces
  }
}

function buildDefaultWorkspaceRecord(payload: UrbexDbPayload): DatabaseWorkspaceRecord {
  const now = Date.now();
  const meta: DatabaseWorkspaceMeta = {
    id: DEFAULT_WORKSPACE_ID,
    name: 'Москва · Сектор-1',
    regionLabel: 'Москва и область',
    description: 'Основная автономная база объектов и заметок.',
    createdAt: now,
    updatedAt: now,
  };
  return { meta, payload };
}

export async function loadMultiDbState(): Promise<LoadedMultiDbState> {
  try {
    const db = await openDatabase();
    const [
      objects,
      meshMessages,
      baseArticles,
      archiveItems,
      profiles,
      settingsList,
      rawWorkspaces,
      selectionRows,
    ] = await Promise.all([
      getAllFromStore<UrbexObject>(db, STORES.objects),
      getAllFromStore<MeshMessage>(db, STORES.meshMessages),
      getAllFromStore<BaseArticle>(db, STORES.baseArticles),
      getAllFromStore<ArchiveItem>(db, STORES.archiveItems),
      getAllFromStore<UserProfile>(db, STORES.profile),
      getAllFromStore<AppSettings>(db, STORES.settings),
      getAllFromStore<DatabaseWorkspaceRecord>(db, STORES.workspaces),
      getAllFromStore<WorkspaceSelectionState>(db, STORES.workspaceState),
    ]);

    if (rawWorkspaces.length === 0) {
      let basePayload: UrbexDbPayload;
      if (profiles.length === 0 || settingsList.length === 0) {
        basePayload = createSeedPayload();
        db.close();
        await replaceFullPayload(basePayload);
      } else {
        basePayload = {
          objects: objects.sort((a, b) => b.updatedAt - a.updatedAt),
          meshMessages: meshMessages.sort((a, b) => a.timestamp - b.timestamp),
          baseArticles,
          archiveItems: archiveItems.sort((a, b) => b.createdAt - a.createdAt),
          profile: profiles[0],
          settings: normalizeSettings(settingsList[0]),
        };
        db.close();
      }
      const initialWs = buildDefaultWorkspaceRecord(basePayload);
      const sel: WorkspaceSelectionState = {
        id: 'state',
        activeDbId: initialWs.meta.id,
        mountedDbIds: [initialWs.meta.id],
      };
      await saveWorkspaceRecord(initialWs);
      await saveWorkspaceSelection(sel.activeDbId, sel.mountedDbIds);
      return {
        workspaces: [initialWs],
        activeDbId: sel.activeDbId,
        mountedDbIds: sel.mountedDbIds,
      };
    }

    db.close();
    const normalizedWorkspaces = rawWorkspaces
      .map((ws) => ({
        meta: ws.meta,
        payload: {
          ...ws.payload,
          objects: [...ws.payload.objects].sort((a, b) => b.updatedAt - a.updatedAt),
          meshMessages: [...ws.payload.meshMessages].sort((a, b) => a.timestamp - b.timestamp),
          archiveItems: [...ws.payload.archiveItems].sort((a, b) => b.createdAt - a.createdAt),
          settings: normalizeSettings(ws.payload.settings),
        },
      }))
      .sort((a, b) => a.meta.createdAt - b.meta.createdAt);

    const validIds = new Set(normalizedWorkspaces.map((w) => w.meta.id));
    const storedSel = selectionRows[0];
    const activeDbId =
      storedSel && validIds.has(storedSel.activeDbId)
        ? storedSel.activeDbId
        : normalizedWorkspaces[0].meta.id;
    const mountedRaw = (storedSel?.mountedDbIds ?? [activeDbId]).filter((id) => validIds.has(id));
    const mountedDbIds = mountedRaw.includes(activeDbId)
      ? mountedRaw
      : [activeDbId, ...mountedRaw];

    return {
      workspaces: normalizedWorkspaces,
      activeDbId,
      mountedDbIds,
    };
  } catch {
    if (!memoryWorkspaces) {
      const ws = buildDefaultWorkspaceRecord(createSeedPayload());
      memoryWorkspaces = [ws];
      memorySelection = {
        id: 'state',
        activeDbId: ws.meta.id,
        mountedDbIds: [ws.meta.id],
      };
    }
    return {
      workspaces: structuredClone(memoryWorkspaces),
      activeDbId: memorySelection?.activeDbId ?? memoryWorkspaces[0].meta.id,
      mountedDbIds: memorySelection?.mountedDbIds ?? [memoryWorkspaces[0].meta.id],
    };
  }
}

export async function saveWorkspaceRecord(record: DatabaseWorkspaceRecord): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.workspaces, record);
    db.close();
  } catch {
    if (!memoryWorkspaces) memoryWorkspaces = [];
    const idx = memoryWorkspaces.findIndex((w) => w.meta.id === record.meta.id);
    if (idx >= 0) memoryWorkspaces[idx] = structuredClone(record);
    else memoryWorkspaces.push(structuredClone(record));
  }
}

export async function deleteWorkspaceRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await deleteFromStore(db, STORES.workspaces, id);
    db.close();
  } catch {
    if (memoryWorkspaces) {
      memoryWorkspaces = memoryWorkspaces.filter((w) => w.meta.id !== id);
    }
  }
}

export async function saveWorkspaceSelection(
  activeDbId: string,
  mountedDbIds: string[]
): Promise<void> {
  const row: WorkspaceSelectionState = {
    id: 'state',
    activeDbId,
    mountedDbIds,
  };
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.workspaceState, row);
    db.close();
  } catch {
    memorySelection = row;
  }
}
