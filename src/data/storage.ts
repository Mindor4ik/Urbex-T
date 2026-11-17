import { createSeedPayload } from './seed';
import type {
  AppSettings,
  ArchiveItem,
  BaseArticle,
  MeshMessage,
  UrbexDbPayload,
  UrbexObject,
  UserProfile,
} from '../core/types';

const DB_NAME = 'urbex_t_db';
const DB_VERSION = 1;

const STORES = {
  objects: 'objects',
  meshMessages: 'meshMessages',
  baseArticles: 'baseArticles',
  archiveItems: 'archiveItems',
  profile: 'profile',
  settings: 'settings',
} as const;

type StoreName = (typeof STORES)[keyof typeof STORES];

let memoryFallback: UrbexDbPayload | null = null;

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
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('Failed to open IndexedDB'));
  });
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

export async function replaceFullPayload(payload: UrbexDbPayload): Promise<void> {
  try {
    const db = await openDatabase();
    const tx = db.transaction(Object.values(STORES), 'readwrite');
    for (const storeName of Object.values(STORES)) {
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
    memoryFallback = structuredClone(payload);
  }
}

export async function loadOrSeedDatabase(): Promise<UrbexDbPayload> {
  try {
    const db = await openDatabase();
    const [objects, meshMessages, baseArticles, archiveItems, profiles, settingsList] =
      await Promise.all([
        getAllFromStore<UrbexObject>(db, STORES.objects),
        getAllFromStore<MeshMessage>(db, STORES.meshMessages),
        getAllFromStore<BaseArticle>(db, STORES.baseArticles),
        getAllFromStore<ArchiveItem>(db, STORES.archiveItems),
        getAllFromStore<UserProfile>(db, STORES.profile),
        getAllFromStore<AppSettings>(db, STORES.settings),
      ]);

    if (profiles.length === 0 || settingsList.length === 0) {
      const seeded = createSeedPayload();
      db.close();
      await replaceFullPayload(seeded);
      return seeded;
    }

    db.close();
    const loadedSettings = settingsList[0];
    return {
      objects: objects.sort((a, b) => b.updatedAt - a.updatedAt),
      meshMessages: meshMessages.sort((a, b) => a.timestamp - b.timestamp),
      baseArticles,
      archiveItems: archiveItems.sort((a, b) => b.createdAt - a.createdAt),
      profile: profiles[0],
      settings: {
        ...loadedSettings,
        meshEnabled: loadedSettings.meshEnabled ?? false,
        mapLayer: loadedSettings.mapLayer ?? 'satellite',
      },
    };
  } catch {
    if (!memoryFallback) {
      memoryFallback = createSeedPayload();
    }
    return structuredClone(memoryFallback);
  }
}

export async function saveObjectRecord(record: UrbexObject): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.objects, record);
    db.close();
  } catch {
    if (memoryFallback) {
      const idx = memoryFallback.objects.findIndex((o) => o.id === record.id);
      if (idx >= 0) memoryFallback.objects[idx] = record;
      else memoryFallback.objects.unshift(record);
    }
  }
}

export async function deleteObjectRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await deleteFromStore(db, STORES.objects, id);
    db.close();
  } catch {
    if (memoryFallback) {
      memoryFallback.objects = memoryFallback.objects.filter((o) => o.id !== id);
    }
  }
}

export async function saveMeshMessageRecord(msg: MeshMessage): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.meshMessages, msg);
    db.close();
  } catch {
    if (memoryFallback) {
      memoryFallback.meshMessages.push(msg);
    }
  }
}

export async function saveBaseArticleRecord(article: BaseArticle): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.baseArticles, article);
    db.close();
  } catch {
    if (memoryFallback) {
      const idx = memoryFallback.baseArticles.findIndex((a) => a.id === article.id);
      if (idx >= 0) memoryFallback.baseArticles[idx] = article;
      else memoryFallback.baseArticles.unshift(article);
    }
  }
}

export async function deleteBaseArticleRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await deleteFromStore(db, STORES.baseArticles, id);
    db.close();
  } catch {
    if (memoryFallback) {
      memoryFallback.baseArticles = memoryFallback.baseArticles.filter((a) => a.id !== id);
    }
  }
}

export async function saveArchiveItemRecord(item: ArchiveItem): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.archiveItems, item);
    db.close();
  } catch {
    if (memoryFallback) {
      const idx = memoryFallback.archiveItems.findIndex((a) => a.id === item.id);
      if (idx >= 0) memoryFallback.archiveItems[idx] = item;
      else memoryFallback.archiveItems.unshift(item);
    }
  }
}

export async function deleteArchiveItemRecord(id: string): Promise<void> {
  try {
    const db = await openDatabase();
    await deleteFromStore(db, STORES.archiveItems, id);
    db.close();
  } catch {
    if (memoryFallback) {
      memoryFallback.archiveItems = memoryFallback.archiveItems.filter((a) => a.id !== id);
    }
  }
}

export async function saveProfileRecord(profile: UserProfile): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.profile, profile);
    db.close();
  } catch {
    if (memoryFallback) memoryFallback.profile = profile;
  }
}

export async function saveSettingsRecord(settings: AppSettings): Promise<void> {
  try {
    const db = await openDatabase();
    await putInStore(db, STORES.settings, settings);
    db.close();
  } catch {
    if (memoryFallback) memoryFallback.settings = settings;
  }
}
