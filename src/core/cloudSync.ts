import { parseAndVerifyUrbexDb, serializeUrbexDb } from './urbexdb';
import type {
  CloudSyncConfig,
  CloudSyncProvider,
  UrbexDbBundle,
  UrbexDbPayload,
} from './types';

export interface CloudSyncProviderInfo {
  id: CloudSyncProvider;
  label: string;
  costLabel: string;
  summary: string;
  endpointPlaceholder: string;
  setupHint: string;
}

export const CLOUD_SYNC_PROVIDERS: CloudSyncProviderInfo[] = [
  {
    id: 'github_gist',
    label: 'GitHub Secret Gist',
    costLabel: 'Бесплатно · без лимита',
    summary: 'Приватный Gist в вашем аккаунте GitHub по бесплатному токену (scope: gist). Работает прямо из браузера и Android APK.',
    endpointPlaceholder: 'Автосоздание или ID существующего Gist',
    setupHint: 'Создайте бесплатный Personal Access Token (Classic -> галочка gist). Если Gist ID пустой, он создастся при первой синхронизации.',
  },
  {
    id: 'webdav',
    label: 'WebDAV (Яндекс.Диск / Nextcloud)',
    costLabel: 'Бесплатно · свой диск',
    summary: 'Яндекс.Диск (https://webdav.yandex.ru/UrbexT), Nextcloud или ownCloud по паролю приложения.',
    endpointPlaceholder: 'https://webdav.yandex.ru/UrbexT',
    setupHint: 'На Android работает напрямую без ограничений CORS. В браузере сервер WebDAV должен разрешать CORS-заголовки (напр. Nextcloud/NAS).',
  },
  {
    id: 'local_folder',
    label: 'Папка диска (Drive / Syncthing)',
    costLabel: 'Бесплатно · офлайн-мост',
    summary: 'Запись и чтение в выбранную папку устройства (Google Drive, Яндекс.Диск, Syncthing, FolderSync).',
    endpointPlaceholder: 'Выбирается системным диалогом папки',
    setupHint: 'Полностью автономно без токенов: синхронизацию папки с облаком или между устройствами выполняет установленный Drive/Syncthing.',
  },
  {
    id: 'custom_http',
    label: 'Свой HTTP / Worker REST',
    costLabel: 'Бесплатно · свой URL',
    summary: 'Любой бесплатный endpoint (Cloudflare Workers, ntfy/KV, свой сервер), принимающий GET и PUT текста.',
    endpointPlaceholder: 'https://your-worker.workers.dev/urbex-sync',
    setupHint: 'Приложение скачивает текущую копию через GET, объединяет записи и отправляет обновлённый снимок через PUT.',
  },
];

export const DEFAULT_CLOUD_SYNC_CONFIG: CloudSyncConfig = {
  enabled: false,
  provider: 'none',
  autoSyncOnChange: true,
  autoSyncIntervalMin: 30,
  wifiOnly: true,
  encryptBeforeUpload: true,
  encryptionPassphrase: '',
  conflictStrategy: 'keep_newer',
  remoteEndpoint: '',
  authToken: '',
  username: '',
  gistId: '',
  remoteFileName: 'urbex-t-sync.urbexdb',
  lastSyncStatus: 'idle',
};

export const E2EE_ENVELOPE_FORMAT = 'urbex-e2ee-v1' as const;

export interface EncryptedSyncEnvelope {
  format: typeof E2EE_ENVELOPE_FORMAT;
  kdf: 'PBKDF2-SHA256';
  iterations: number;
  saltB64: string;
  ivB64: string;
  cipherB64: string;
  updatedAt: number;
}

/**
 * Strips sensitive cloud credentials and passphrases from a payload before uploading/exporting
 * so API tokens or E2EE passphrases never leave the device inside the database snapshot.
 */
export function sanitizePayloadForSync(payload: UrbexDbPayload): UrbexDbPayload {
  if (!payload.settings.cloudSync) return payload;
  return {
    ...payload,
    settings: {
      ...payload.settings,
      cloudSync: {
        ...payload.settings.cloudSync,
        authToken: '',
        username: '',
        encryptionPassphrase: '',
      },
    },
  };
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToBytes(b64: string): Uint8Array {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    out[i] = binary.charCodeAt(i);
  }
  return out;
}

async function deriveAesKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('WebCrypto API недоступен в текущей среде.');
  }
  const enc = new TextEncoder();
  const baseKey = await subtle.importKey(
    'raw',
    enc.encode(passphrase),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );
  return subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt.buffer as ArrayBuffer,
      iterations,
      hash: 'SHA-256',
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function encryptSyncContent(plaintext: string, passphrase: string): Promise<string> {
  const pass = passphrase.trim();
  if (!pass) {
    throw new Error('Укажите парольную фразу для шифрования AES-256-GCM перед отправкой.');
  }
  const iterations = 100_000;
  const salt = globalThis.crypto.getRandomValues(new Uint8Array(16));
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveAesKey(pass, salt, iterations);
  const encoded = new TextEncoder().encode(plaintext);
  const cipherBuf = await globalThis.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encoded
  );
  const envelope: EncryptedSyncEnvelope = {
    format: E2EE_ENVELOPE_FORMAT,
    kdf: 'PBKDF2-SHA256',
    iterations,
    saltB64: bytesToBase64(salt),
    ivB64: bytesToBase64(iv),
    cipherB64: bytesToBase64(new Uint8Array(cipherBuf)),
    updatedAt: Date.now(),
  };
  return JSON.stringify(envelope, null, 2);
}

export async function decryptSyncContent(rawEnvelope: string, passphrase: string): Promise<string> {
  let parsed: Partial<EncryptedSyncEnvelope>;
  try {
    parsed = JSON.parse(rawEnvelope) as Partial<EncryptedSyncEnvelope>;
  } catch {
    throw new Error('Повреждённый контейнер шифрования.');
  }
  if (
    parsed.format !== E2EE_ENVELOPE_FORMAT ||
    !parsed.saltB64 ||
    !parsed.ivB64 ||
    !parsed.cipherB64
  ) {
    throw new Error('Неподдерживаемый формат E2EE-контейнера.');
  }
  const pass = passphrase.trim();
  if (!pass) {
    throw new Error('Удалённый файл зашифрован (AES-256-GCM). Введите парольную фразу.');
  }
  const salt = base64ToBytes(parsed.saltB64);
  const iv = base64ToBytes(parsed.ivB64);
  const cipher = base64ToBytes(parsed.cipherB64);
  const iterations = parsed.iterations && parsed.iterations >= 10_000 ? parsed.iterations : 100_000;
  const key = await deriveAesKey(pass, salt, iterations);
  try {
    const plainBuf = await globalThis.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv.buffer as ArrayBuffer },
      key,
      cipher.buffer as ArrayBuffer
    );
    return new TextDecoder().decode(plainBuf);
  } catch {
    throw new Error('Неверный пароль расшифровки E2EE или файл повреждён.');
  }
}

export function isEncryptedSyncEnvelope(raw: string): boolean {
  const trimmed = raw.trim();
  return trimmed.startsWith('{') && trimmed.includes(`"${E2EE_ENVELOPE_FORMAT}"`);
}

export async function decodeRemoteSnapshot(
  rawText: string,
  passphrase: string
): Promise<UrbexDbBundle> {
  const plaintext = isEncryptedSyncEnvelope(rawText)
    ? await decryptSyncContent(rawText, passphrase)
    : rawText;
  const parsed = parseAndVerifyUrbexDb(plaintext);
  if (!parsed.ok) {
    throw new Error(parsed.error);
  }
  return parsed.bundle;
}

export async function encodeOutgoingSnapshot(
  payload: UrbexDbPayload,
  deviceNodeId: string,
  config: CloudSyncConfig
): Promise<string> {
  const safePayload = sanitizePayloadForSync(payload);
  const serialized = serializeUrbexDb(safePayload, deviceNodeId);
  if (config.encryptBeforeUpload) {
    return encryptSyncContent(serialized, config.encryptionPassphrase);
  }
  return serialized;
}

export interface CloudSyncExecutionResult {
  remoteBundle: UrbexDbBundle | null;
  uploadedText: string;
  updatedGistId?: string;
  summaryMessage: string;
}

export interface DirectoryHandleLike {
  getFileHandle: (
    name: string,
    opts?: { create?: boolean }
  ) => Promise<{
    getFile: () => Promise<File>;
    createWritable: () => Promise<{
      write: (data: string) => Promise<void>;
      close: () => Promise<void>;
    }>;
  }>;
}

function buildRemoteFileName(config: CloudSyncConfig): string {
  const raw = config.remoteFileName.trim() || 'urbex-t-sync.urbexdb';
  return raw.endsWith('.urbexdb') ? raw : `${raw}.urbexdb`;
}

export async function executeRemotePull(
  config: CloudSyncConfig,
  directoryHandle?: DirectoryHandleLike | null
): Promise<{ bundle: UrbexDbBundle | null; resolvedGistId?: string }> {
  const fileName = buildRemoteFileName(config);

  if (config.provider === 'github_gist') {
    const token = config.authToken.trim();
    if (!token) throw new Error('Укажите GitHub Personal Access Token (scope: gist).');
    const gistId = config.gistId.trim();
    if (!gistId) {
      return { bundle: null };
    }
    const resp = await fetch(`https://api.github.com/gists/${encodeURIComponent(gistId)}`, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/vnd.github+json',
      },
    });
    if (resp.status === 404) {
      return { bundle: null };
    }
    if (!resp.ok) {
      throw new Error(`GitHub Gist вернул HTTP ${resp.status}.`);
    }
    const json = (await resp.json()) as {
      id: string;
      files?: Record<string, { content?: string }>;
    };
    const content =
      json.files?.[fileName]?.content ??
      Object.values(json.files ?? {})[0]?.content;
    if (!content) {
      return { bundle: null, resolvedGistId: json.id };
    }
    const bundle = await decodeRemoteSnapshot(content, config.encryptionPassphrase);
    return { bundle, resolvedGistId: json.id };
  }

  if (config.provider === 'webdav') {
    const base = config.remoteEndpoint.trim().replace(/\/+$/, '');
    if (!base) throw new Error('Укажите URL папки WebDAV (напр. Яндекс.Диск / Nextcloud).');
    const headers: Record<string, string> = {};
    if (config.username.trim() && config.authToken.trim()) {
      headers.Authorization = `Basic ${btoa(`${config.username.trim()}:${config.authToken.trim()}`)}`;
    } else if (config.authToken.trim()) {
      headers.Authorization = `Bearer ${config.authToken.trim()}`;
    }
    const resp = await fetch(`${base}/${encodeURIComponent(fileName)}`, {
      method: 'GET',
      headers,
    });
    if (resp.status === 404) return { bundle: null };
    if (!resp.ok) throw new Error(`WebDAV GET вернул HTTP ${resp.status}.`);
    const text = await resp.text();
    if (!text.trim()) return { bundle: null };
    return { bundle: await decodeRemoteSnapshot(text, config.encryptionPassphrase) };
  }

  if (config.provider === 'custom_http') {
    const url = config.remoteEndpoint.trim();
    if (!url) throw new Error('Укажите URL HTTP endpoint для синхронизации.');
    const headers: Record<string, string> = {};
    if (config.authToken.trim()) {
      headers.Authorization = `Bearer ${config.authToken.trim()}`;
    }
    const resp = await fetch(url, { method: 'GET', headers });
    if (resp.status === 404) return { bundle: null };
    if (!resp.ok) throw new Error(`HTTP endpoint вернул статус ${resp.status}.`);
    const text = await resp.text();
    if (!text.trim()) return { bundle: null };
    return { bundle: await decodeRemoteSnapshot(text, config.encryptionPassphrase) };
  }

  if (config.provider === 'local_folder') {
    if (!directoryHandle) {
      return { bundle: null };
    }
    try {
      const fh = await directoryHandle.getFileHandle(fileName, { create: false });
      const file = await fh.getFile();
      const text = await file.text();
      if (!text.trim()) return { bundle: null };
      return { bundle: await decodeRemoteSnapshot(text, config.encryptionPassphrase) };
    } catch {
      return { bundle: null };
    }
  }

  return { bundle: null };
}

export async function executeRemotePush(
  config: CloudSyncConfig,
  mergedPayload: UrbexDbPayload,
  deviceNodeId: string,
  directoryHandle?: DirectoryHandleLike | null
): Promise<{ updatedGistId?: string }> {
  const fileName = buildRemoteFileName(config);
  const outgoingText = await encodeOutgoingSnapshot(mergedPayload, deviceNodeId, config);

  if (config.provider === 'github_gist') {
    const token = config.authToken.trim();
    if (!token) throw new Error('Укажите GitHub Personal Access Token (scope: gist).');
    const gistId = config.gistId.trim();
    const url = gistId
      ? `https://api.github.com/gists/${encodeURIComponent(gistId)}`
      : 'https://api.github.com/gists';
    const resp = await fetch(url, {
      method: gistId ? 'PATCH' : 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/vnd.github+json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        description: `Urbex-T Autonomous Sync (${deviceNodeId})`,
        public: false,
        files: {
          [fileName]: { content: outgoingText },
        },
      }),
    });
    if (!resp.ok) {
      throw new Error(`Ошибка записи GitHub Gist (HTTP ${resp.status}).`);
    }
    const created = (await resp.json()) as { id?: string };
    return { updatedGistId: created.id ?? gistId };
  }

  if (config.provider === 'webdav') {
    const base = config.remoteEndpoint.trim().replace(/\/+$/, '');
    if (!base) throw new Error('Укажите URL папки WebDAV.');
    const headers: Record<string, string> = {
      'Content-Type': 'application/json; charset=utf-8',
    };
    if (config.username.trim() && config.authToken.trim()) {
      headers.Authorization = `Basic ${btoa(`${config.username.trim()}:${config.authToken.trim()}`)}`;
    } else if (config.authToken.trim()) {
      headers.Authorization = `Bearer ${config.authToken.trim()}`;
    }
    const resp = await fetch(`${base}/${encodeURIComponent(fileName)}`, {
      method: 'PUT',
      headers,
      body: outgoingText,
    });
    if (!resp.ok) {
      throw new Error(`WebDAV PUT вернул HTTP ${resp.status}.`);
    }
    return {};
  }

  if (config.provider === 'custom_http') {
    const url = config.remoteEndpoint.trim();
    if (!url) throw new Error('Укажите URL HTTP endpoint.');
    const headers: Record<string, string> = {
      'Content-Type': 'application/json; charset=utf-8',
    };
    if (config.authToken.trim()) {
      headers.Authorization = `Bearer ${config.authToken.trim()}`;
    }
    const resp = await fetch(url, {
      method: 'PUT',
      headers,
      body: outgoingText,
    });
    if (!resp.ok) {
      throw new Error(`HTTP PUT вернул статус ${resp.status}.`);
    }
    return {};
  }

  if (config.provider === 'local_folder') {
    if (directoryHandle) {
      const fh = await directoryHandle.getFileHandle(fileName, { create: true });
      const writable = await fh.createWritable();
      await writable.write(outgoingText);
      await writable.close();
      return {};
    }
    // Browser fallback when File System Access API is blocked (e.g., iframe): trigger direct file download
    const blob = new Blob([outgoingText], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = config.encryptBeforeUpload ? `${fileName}.e2ee` : fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    return {};
  }

  return {};
}
