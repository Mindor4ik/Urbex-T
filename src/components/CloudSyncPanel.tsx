import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  CheckCircle2,
  Cloud,
  CloudOff,
  Eye,
  EyeOff,
  FolderSync,
  Lock,
  RefreshCw,
  Server,
  ShieldCheck,
} from 'lucide-react';
import { CLOUD_SYNC_PROVIDERS, DEFAULT_CLOUD_SYNC_CONFIG } from '../core/cloudSync';
import type {
  CloudSyncConfig,
  CloudSyncProvider,
  ConflictStrategy,
} from '../core/types';

interface CloudSyncPanelProps {
  config?: CloudSyncConfig;
  isSyncing: boolean;
  syncDirectoryName: string | null;
  onChangeConfig: (next: CloudSyncConfig) => Promise<void>;
  onSelectSyncDirectory: () => Promise<string | null>;
  onSyncNow: (opts?: { manual?: boolean }) => Promise<{ ok: boolean; message: string }>;
}

export function CloudSyncPanel({
  config,
  isSyncing,
  syncDirectoryName,
  onChangeConfig,
  onSelectSyncDirectory,
  onSyncNow,
}: CloudSyncPanelProps) {
  const current: CloudSyncConfig = {
    ...DEFAULT_CLOUD_SYNC_CONFIG,
    ...(config ?? {}),
  };

  const [showToken, setShowToken] = useState(false);
  const [showPassphrase, setShowPassphrase] = useState(false);
  const [actionMsg, setActionMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const selectedProviderMeta = CLOUD_SYNC_PROVIDERS.find((p) => p.id === current.provider);

  const update = (patch: Partial<CloudSyncConfig>) => {
    void onChangeConfig({
      ...current,
      ...patch,
    });
  };

  const handleSyncClick = async () => {
    setActionMsg(null);
    const res = await onSyncNow({ manual: true });
    setActionMsg({ ok: res.ok, text: res.message });
  };

  const handlePickFolder = async () => {
    try {
      const picked = await onSelectSyncDirectory();
      if (!picked) {
        setActionMsg({
          ok: false,
          text: 'File System Access API недоступен в текущем окне. На Android выберите системную папку SAF или используйте скачивание снимка.',
        });
      } else {
        setActionMsg({ ok: true, text: `Подключена папка «${picked}».` });
      }
    } catch {
      setActionMsg({ ok: false, text: 'Выбор папки отменён пользователем.' });
    }
  };

  return (
    <section
      data-testid="cloud-sync-panel"
      className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3.5"
    >
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
        <div className="flex items-start gap-2.5 min-w-0">
          <Cloud className="w-5 h-5 text-[#6DB386] shrink-0 mt-0.5" />
          <div className="min-w-0">
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Бесплатная автосинхронизация (облако / диск)
            </h2>
            <p className="text-[11px] sm:text-xs text-[#98A49E]">
              Автоматическое слияние и резервное копирование базы на бесплатные сервисы со сквозным шифрованием AES-256-GCM. Токены и ключи хранятся только локально.
            </p>
          </div>
        </div>

        <label className="flex items-center gap-2 shrink-0 px-3 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] cursor-pointer select-none">
          <input
            type="checkbox"
            checked={current.enabled}
            onChange={(e) => update({ enabled: e.target.checked })}
            aria-label="Включить автосинхронизацию"
            className="accent-[#5B9D72]"
          />
          <span>{current.enabled ? 'Автосинхр. ВКЛ' : 'Автосинхр. ВЫКЛ'}</span>
        </label>
      </div>

      {/* Free Provider Selector Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {CLOUD_SYNC_PROVIDERS.map((prov) => {
          const active = current.provider === prov.id;
          return (
            <button
              key={prov.id}
              type="button"
              onClick={() =>
                update({
                  provider: prov.id,
                  remoteEndpoint:
                    prov.id === 'webdav' && !current.remoteEndpoint
                      ? 'https://webdav.yandex.ru/UrbexT'
                      : current.remoteEndpoint,
                })
              }
              aria-pressed={active}
              className={`p-3 rounded-xl border text-left transition-colors space-y-1 ${
                active
                  ? 'bg-[#17211C] border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#323E39]'
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8]">
                  {prov.label}
                </span>
                <span className="px-1.5 py-0.5 rounded bg-[#131816] border border-[#252E2A] text-[10px] font-mono text-[#6DB386] shrink-0">
                  {prov.costLabel}
                </span>
              </div>
              <p className="text-[11px] leading-snug text-[#98A49E]">{prov.summary}</p>
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => update({ provider: 'none' as CloudSyncProvider, enabled: false })}
          className={`sm:col-span-2 px-3 py-2 rounded-lg border text-xs flex items-center justify-center gap-1.5 transition-colors ${
            current.provider === 'none'
              ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
              : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
          }`}
        >
          <CloudOff className="w-3.5 h-3.5" />
          <span>Только локальное хранение (облако не использовать)</span>
        </button>
      </div>

      {/* Provider Specific Configuration */}
      {current.provider !== 'none' && (
        <div className="p-3.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-3">
          {selectedProviderMeta && (
            <div className="p-2.5 rounded-lg bg-[#131816] border border-[#252E2A] text-[11px] text-[#98A49E] flex items-start gap-2">
              <Server className="w-4 h-4 text-[#DF9E43] shrink-0 mt-0.5" />
              <span>{selectedProviderMeta.setupHint}</span>
            </div>
          )}

          {current.provider === 'github_gist' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">
                  GitHub Token (scope: gist)
                </label>
                <div className="relative">
                  <input
                    type={showToken ? 'text' : 'password'}
                    value={current.authToken}
                    onChange={(e) => update({ authToken: e.target.value })}
                    placeholder="ghp_xxxxxxxxxxxx"
                    aria-label="GitHub Token"
                    className="w-full h-9 pl-3 pr-9 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowToken((v) => !v)}
                    aria-label={showToken ? 'Скрыть токен' : 'Показать токен'}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[#98A49E] hover:text-[#E6EBE8]"
                  >
                    {showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">
                  Gist ID (необязательно — создастся сам)
                </label>
                <input
                  type="text"
                  value={current.gistId}
                  onChange={(e) => update({ gistId: e.target.value })}
                  placeholder="Автосоздание секретного Gist"
                  aria-label="Gist ID"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                />
              </div>
            </div>
          )}

          {current.provider === 'webdav' && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              <div className="sm:col-span-3">
                <label className="block text-[11px] text-[#98A49E] mb-1">
                  URL папки WebDAV (Яндекс.Диск / Nextcloud / ownCloud)
                </label>
                <input
                  type="url"
                  value={current.remoteEndpoint}
                  onChange={(e) => update({ remoteEndpoint: e.target.value })}
                  placeholder="https://webdav.yandex.ru/UrbexT"
                  aria-label="URL WebDAV"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                />
              </div>
              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">Логин</label>
                <input
                  type="text"
                  value={current.username}
                  onChange={(e) => update({ username: e.target.value })}
                  placeholder="user@yandex.ru"
                  aria-label="Логин WebDAV"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="block text-[11px] text-[#98A49E] mb-1">Пароль приложения</label>
                <div className="relative">
                  <input
                    type={showToken ? 'text' : 'password'}
                    value={current.authToken}
                    onChange={(e) => update({ authToken: e.target.value })}
                    placeholder="Пароль приложения для WebDAV"
                    aria-label="Пароль WebDAV"
                    className="w-full h-9 pl-3 pr-9 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowToken((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[#98A49E] hover:text-[#E6EBE8]"
                  >
                    {showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>
          )}

          {current.provider === 'custom_http' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">
                  Endpoint URL (GET / PUT JSON)
                </label>
                <input
                  type="url"
                  value={current.remoteEndpoint}
                  onChange={(e) => update({ remoteEndpoint: e.target.value })}
                  placeholder="https://your-worker.workers.dev/urbex-sync"
                  aria-label="HTTP Endpoint URL"
                  className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                />
              </div>
              <div>
                <label className="block text-[11px] text-[#98A49E] mb-1">
                  Bearer Token (если требуется)
                </label>
                <div className="relative">
                  <input
                    type={showToken ? 'text' : 'password'}
                    value={current.authToken}
                    onChange={(e) => update({ authToken: e.target.value })}
                    placeholder="Опционально"
                    aria-label="Bearer Token"
                    className="w-full h-9 pl-3 pr-9 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
                  />
                  <button
                    type="button"
                    onClick={() => setShowToken((v) => !v)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-[#98A49E] hover:text-[#E6EBE8]"
                  >
                    {showToken ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>
          )}

          {current.provider === 'local_folder' && (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 p-2.5 rounded-lg bg-[#131816] border border-[#252E2A]">
              <div className="min-w-0">
                <div className="text-xs font-medium text-[#E6EBE8]">
                  Папка моста синхронизации
                </div>
                <div className="text-[11px] text-[#98A49E] truncate">
                  {syncDirectoryName
                    ? `Подключена папка: ${syncDirectoryName}`
                    : 'Папка не выбрана — можно выбрать директорию Drive/Syncthing или выгружать файл вручную.'}
                </div>
              </div>
              <button
                type="button"
                onClick={handlePickFolder}
                className="min-h-[34px] px-3 py-1.5 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-medium text-[#E6EBE8] hover:bg-[#274032] flex items-center gap-1.5 shrink-0"
              >
                <FolderSync className="w-3.5 h-3.5 text-[#6DB386]" />
                <span>Выбрать папку</span>
              </button>
            </div>
          )}

          {/* File name & E2EE Passphrase */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-2 border-t border-[#252E2A]">
            <div>
              <label className="block text-[11px] text-[#98A49E] mb-1">
                Имя снимка базы в облаке
              </label>
              <input
                type="text"
                value={current.remoteFileName}
                onChange={(e) => update({ remoteFileName: e.target.value })}
                placeholder="urbex-t-sync.urbexdb"
                aria-label="Имя файла в облаке"
                className="w-full h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
              />
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] text-[#98A49E] flex items-center gap-1">
                  <Lock className="w-3 h-3 text-[#6DB386]" />
                  <span>Сквозное шифрование E2EE</span>
                </label>
                <input
                  type="checkbox"
                  checked={current.encryptBeforeUpload}
                  onChange={(e) => update({ encryptBeforeUpload: e.target.checked })}
                  aria-label="Шифровать базу перед загрузкой"
                  className="accent-[#5B9D72]"
                />
              </div>
              <div className="relative">
                <input
                  type={showPassphrase ? 'text' : 'password'}
                  disabled={!current.encryptBeforeUpload}
                  value={current.encryptionPassphrase}
                  onChange={(e) => update({ encryptionPassphrase: e.target.value })}
                  placeholder={
                    current.encryptBeforeUpload
                      ? 'Пароль шифрования AES-256-GCM'
                      : 'Отключено (открытый .urbexdb)'
                  }
                  aria-label="Пароль шифрования облачной синхронизации"
                  className="w-full h-9 pl-3 pr-9 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8] disabled:opacity-45"
                />
                <button
                  type="button"
                  disabled={!current.encryptBeforeUpload}
                  onClick={() => setShowPassphrase((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-[#98A49E] hover:text-[#E6EBE8] disabled:opacity-40"
                >
                  {showPassphrase ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
          </div>

          {/* Schedule & Conflict Controls */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-2 border-t border-[#252E2A]">
            <div>
              <label className="block text-[11px] text-[#98A49E] mb-1">Интервал фонового опроса</label>
              <select
                value={current.autoSyncIntervalMin}
                onChange={(e) => update({ autoSyncIntervalMin: Number(e.target.value) })}
                aria-label="Интервал автосинхронизации"
                className="w-full h-9 px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8]"
              >
                <option value={15}>Каждые 15 мин</option>
                <option value={30}>Каждые 30 мин</option>
                <option value={60}>Каждый час</option>
                <option value={180}>Каждые 3 часа</option>
              </select>
            </div>

            <div>
              <label className="block text-[11px] text-[#98A49E] mb-1">Стратегия слияния</label>
              <select
                value={current.conflictStrategy}
                onChange={(e) => update({ conflictStrategy: e.target.value as ConflictStrategy })}
                aria-label="Стратегия слияния облачной синхронизации"
                className="w-full h-9 px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
              >
                <option value="keep_newer">Оставлять новее</option>
                <option value="skip_existing">Не затирать свои</option>
                <option value="overwrite">Приоритет облака</option>
              </select>
            </div>

            <div className="flex flex-col justify-center gap-1.5 text-xs text-[#E6EBE8]">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={current.autoSyncOnChange}
                  onChange={(e) => update({ autoSyncOnChange: e.target.checked })}
                  className="accent-[#5B9D72]"
                />
                <span>Автосинк при изменениях</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={current.wifiOnly}
                  onChange={(e) => update({ wifiOnly: e.target.checked })}
                  className="accent-[#5B9D72]"
                />
                <span>Только по Wi-Fi</span>
              </label>
            </div>
          </div>

          {/* Sync Execute & Status Footer */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pt-2 border-t border-[#252E2A]">
            <div className="flex items-center gap-2 text-xs text-[#98A49E] min-w-0">
              <ShieldCheck
                className={`w-4 h-4 shrink-0 ${
                  current.lastSyncStatus === 'error'
                    ? 'text-[#DC6860]'
                    : current.lastSyncStatus === 'success'
                      ? 'text-[#6DB386]'
                      : 'text-[#6B7771]'
                }`}
              />
              <span className="truncate">
                {current.lastSyncMessage || 'Готово к синхронизации с выбранным сервисом'}
              </span>
            </div>

            <button
              type="button"
              disabled={isSyncing}
              onClick={handleSyncClick}
              data-testid="cloud-sync-now-btn"
              className="min-h-[36px] px-4 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] disabled:opacity-50 flex items-center justify-center gap-1.5 shrink-0"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
              <span>{isSyncing ? 'Синхронизация...' : 'Синхронизировать сейчас'}</span>
            </button>
          </div>

          <AnimatePresence>
            {actionMsg && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                className={`p-2.5 rounded-lg border text-xs flex items-center gap-2 ${
                  actionMsg.ok
                    ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                    : 'bg-[#381B19] border-[#C7564E] text-[#F1ACA7]'
                }`}
              >
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                <span>{actionMsg.text}</span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </section>
  );
}
