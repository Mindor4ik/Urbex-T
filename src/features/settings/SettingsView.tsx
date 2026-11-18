import { useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  BatteryCharging,
  CheckCircle2,
  Download,
  HardDrive,
  Maximize2,
  Minimize2,
  Radio,
  RefreshCw,
  Sliders,
  Smartphone,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { PDA_ORIENTATION_LABELS, POWER_PROFILE_META } from '../../core/labels';
import type {
  AppSettings,
  ConflictStrategy,
  CoordinateFormat,
  MapLayerMode,
  ObjectsViewMode,
  PdaOrientation,
  PowerProfile,
  UrbexDbBundle,
} from '../../core/types';
import type { ParseUrbexDbResult } from '../../core/urbexdb';

interface SettingsViewProps {
  settings: AppSettings;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  onUpdateSettings: (next: AppSettings) => Promise<void>;
  onRemoveSector: (sectorId: string) => Promise<void>;
  onExportUrbexDb: () => string | null;
  onInspectUrbexDb: (raw: string) => ParseUrbexDbResult;
  onApplyImport: (
    bundle: UrbexDbBundle,
    strategy: ConflictStrategy
  ) => Promise<{ added: number; updated: number; skipped: number } | null>;
  onResetSeed: () => Promise<void>;
}

export default function SettingsView({
  settings,
  isFullscreen = false,
  onToggleFullscreen,
  onUpdateSettings,
  onRemoveSector,
  onExportUrbexDb,
  onInspectUrbexDb,
  onApplyImport,
  onResetSeed,
}: SettingsViewProps) {
  const [importCandidate, setImportCandidate] = useState<UrbexDbBundle | null>(null);
  const [importWarnings, setImportWarnings] = useState<string[]>([]);
  const [importError, setImportError] = useState<string | null>(null);
  const [importResultMsg, setImportResultMsg] = useState<string | null>(null);
  const [conflictStrategy, setConflictStrategy] = useState<ConflictStrategy>('keep_newer');
  const [confirmResetOpen, setConfirmResetOpen] = useState(false);

  const handlePowerChange = async (profile: PowerProfile) => {
    const meta = POWER_PROFILE_META[profile];
    await onUpdateSettings({
      ...settings,
      powerProfile: profile,
      gpsIntervalMs: meta.intervalMs,
      updatedAt: Date.now(),
    });
  };

  const handleExportDownload = () => {
    const content = onExportUrbexDb();
    if (!content) return;
    const blob = new Blob([content], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const dateStamp = new Date().toISOString().slice(0, 10);
    a.href = url;
    a.download = `urbex-backup-${dateStamp}.urbexdb`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    setImportResultMsg('Резервная копия .urbexdb сформирована и загружена.');
  };

  const handleFileSelect = (file: File | undefined) => {
    if (!file) return;
    setImportError(null);
    setImportResultMsg(null);
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== 'string') return;
      const res = onInspectUrbexDb(reader.result);
      if (!res.ok) {
        setImportCandidate(null);
        setImportError(res.error);
        return;
      }
      setImportCandidate(res.bundle);
      setImportWarnings(res.warnings);
    };
    reader.readAsText(file);
  };

  const handleConfirmImport = async () => {
    if (!importCandidate) return;
    const stats = await onApplyImport(importCandidate, conflictStrategy);
    setImportCandidate(null);
    if (stats) {
      setImportResultMsg(
        `Импорт завершён: добавлено ${stats.added}, обновлено ${stats.updated}, пропущено ${stats.skipped}.`
      );
    }
  };

  return (
    <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-3 sm:gap-4">
      {/* Left: Power, GPS, Meshtastic & Memory Settings */}
      <div className="space-y-3 sm:space-y-4">
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 sm:w-5 sm:h-5 text-[#5B9D72]" />
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Модуль связи Meshtastic
            </h2>
          </div>

          <label className="flex items-center justify-between gap-3 p-2.5 sm:p-3.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#323E39] transition-colors cursor-pointer">
            <div>
              <div className="text-xs sm:text-sm font-medium text-[#E6EBE8]">
                Включить модуль Meshtastic
              </div>
              <div className="text-[11px] sm:text-xs text-[#6B7771]">
                Активирует каналы связи, поиск реальных LoRa-узлов по USB/BLE и ретрансляцию пакетов. По умолчанию выключен для экономии заряда.
              </div>
            </div>
            <span className="relative inline-flex shrink-0">
              <input
                type="checkbox"
                checked={settings.meshEnabled}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    meshEnabled: e.target.checked,
                    updatedAt: Date.now(),
                  })
                }
                aria-label="Включить модуль Meshtastic"
                className="sr-only peer"
              />
              <span
                className={`w-12 h-7 rounded-full border p-0.5 flex items-center transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-[#5B9D72] ${
                  settings.meshEnabled
                    ? 'bg-[#5B9D72] border-[#6DB386] justify-end'
                    : 'bg-[#1A201E] border-[#323E39] justify-start'
                }`}
              >
                <motion.span
                  layout
                  transition={{ type: 'spring', stiffness: 500, damping: 32 }}
                  className={`w-5 h-5 rounded-full shadow ${
                    settings.meshEnabled ? 'bg-[#0C0F0E]' : 'bg-[#98A49E]'
                  }`}
                />
              </span>
            </span>
          </label>
        </section>

        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Smartphone className="w-4 h-4 sm:w-5 sm:h-5 text-[#DF9E43]" />
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Режим КПК / PDA и экран
            </h2>
          </div>

          <label className="flex items-center justify-between gap-3 p-2.5 sm:p-3.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#323E39] transition-colors cursor-pointer">
            <div>
              <div className="text-xs sm:text-sm font-medium text-[#E6EBE8]">
                Режим PDA (терминал)
              </div>
              <div className="text-[11px] sm:text-xs text-[#6B7771]">
                Включает панель телеметрии PDA в шапке (время, заряд, режим экрана), полноэкранную работу и быстрый терминал в разделе МЕНЮ. В Android APK также открывает возможность назначить Urbex-T главным экраном (Launcher).
              </div>
            </div>
            <span className="relative inline-flex shrink-0">
              <input
                type="checkbox"
                checked={Boolean(settings.pdaModeEnabled)}
                onChange={(e) => {
                  const nextEnabled = e.target.checked;
                  onUpdateSettings({
                    ...settings,
                    pdaModeEnabled: nextEnabled,
                    updatedAt: Date.now(),
                  });
                }}
                aria-label="Режим PDA"
                className="sr-only peer"
              />
              <span
                className={`w-12 h-7 rounded-full border p-0.5 flex items-center transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-[#5B9D72] ${
                  settings.pdaModeEnabled
                    ? 'bg-[#5B9D72] border-[#6DB386] justify-end'
                    : 'bg-[#1A201E] border-[#323E39] justify-start'
                }`}
              >
                <motion.span
                  layout
                  transition={{ type: 'spring', stiffness: 500, damping: 32 }}
                  className={`w-5 h-5 rounded-full shadow ${
                    settings.pdaModeEnabled ? 'bg-[#0C0F0E]' : 'bg-[#98A49E]'
                  }`}
                />
              </span>
            </span>
          </label>

          <div className="space-y-1.5">
            <div className="text-xs font-medium text-[#98A49E]">
              Ориентация экрана PDA
            </div>
            <div className="flex flex-wrap gap-1.5 sm:gap-2">
              {(['portrait', 'landscape', 'auto'] as PdaOrientation[]).map((orient) => {
                const active = (settings.pdaOrientation ?? 'portrait') === orient;
                return (
                  <button
                    key={orient}
                    type="button"
                    onClick={() =>
                      onUpdateSettings({
                        ...settings,
                        pdaOrientation: orient,
                        updatedAt: Date.now(),
                      })
                    }
                    className={`flex-1 min-w-[96px] min-h-[34px] lg:min-h-[40px] px-2.5 py-1.5 rounded-lg border text-xs font-medium whitespace-nowrap transition-colors ${
                      active
                        ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                        : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                    }`}
                  >
                    {PDA_ORIENTATION_LABELS[orient]}
                  </button>
                );
              })}
            </div>
          </div>

          {onToggleFullscreen && (
            <button
              type="button"
              onClick={onToggleFullscreen}
              className="w-full min-h-[36px] lg:min-h-[42px] px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-2 transition-colors"
            >
              {isFullscreen ? (
                <>
                  <Minimize2 className="w-4 h-4 text-[#DF9E43]" />
                  <span>Выйти из полноэкранного режима</span>
                </>
              ) : (
                <>
                  <Maximize2 className="w-4 h-4 text-[#DF9E43]" />
                  <span>Развернуть PDA во весь экран</span>
                </>
              )}
            </button>
          )}
        </section>

        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
          <div className="flex items-center gap-2">
            <BatteryCharging className="w-4 h-4 sm:w-5 sm:h-5 text-[#5B9D72]" />
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Энергопотребление, карта и GPS
            </h2>
          </div>

          <div className="grid grid-cols-2 gap-1.5 sm:gap-2">
            {(Object.keys(POWER_PROFILE_META) as PowerProfile[]).map((p) => {
              const meta = POWER_PROFILE_META[p];
              const active = settings.powerProfile === p;
              return (
                <motion.button
                  key={p}
                  whileTap={{ scale: 0.98 }}
                  type="button"
                  onClick={() => handlePowerChange(p)}
                  className={`p-2.5 sm:p-3 rounded-lg border text-left transition-colors ${
                    active
                      ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  <div className="text-sm font-medium">{meta.label}</div>
                  <div className="text-xs text-[#6B7771] mt-0.5">{meta.summary}</div>
                </motion.button>
              );
            })}
          </div>

          <div className="pt-2 border-t border-[#252E2A] grid grid-cols-2 gap-2 sm:gap-3">
            <div>
              <label htmlFor="set-map-layer" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                Слой карты
              </label>
              <select
                id="set-map-layer"
                value={settings.mapLayer}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    mapLayer: e.target.value as MapLayerMode,
                    updatedAt: Date.now(),
                  })
                }
                className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
              >
                <option value="satellite">Спутниковая</option>
                <option value="offline_vector">Офлайн-вектор</option>
              </select>
            </div>

            <div>
              <label htmlFor="set-coord-fmt" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                Формат координат
              </label>
              <select
                id="set-coord-fmt"
                value={settings.coordinateFormat}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    coordinateFormat: e.target.value as CoordinateFormat,
                    updatedAt: Date.now(),
                  })
                }
                className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
              >
                <option value="dd">Десятичные (DD)</option>
                <option value="dms">Градусы (DMS)</option>
                <option value="utm">Сетка UTM</option>
              </select>
            </div>

            <div>
              <label htmlFor="set-page-size" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                Пагинация списков
              </label>
              <select
                id="set-page-size"
                value={settings.pageSize}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    pageSize: Number(e.target.value),
                    updatedAt: Date.now(),
                  })
                }
                className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono text-xs sm:text-sm text-[#E6EBE8]"
              >
                <option value={8}>8 записей</option>
                <option value={12}>12 записей</option>
                <option value={24}>24 записи</option>
                <option value={50}>50 записей</option>
              </select>
            </div>

            <div>
              <label htmlFor="set-default-view" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                Вид объектов по умолчанию
              </label>
              <select
                id="set-default-view"
                value={settings.defaultObjectsView ?? 'cards'}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    defaultObjectsView: e.target.value as ObjectsViewMode,
                    updatedAt: Date.now(),
                  })
                }
                className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
              >
                <option value="list">Список</option>
                <option value="compact">Компакт</option>
                <option value="cards">Карточки</option>
              </select>
            </div>
          </div>

          <div className="pt-2 border-t border-[#252E2A] space-y-2">
            <div className="flex items-center gap-2 text-xs font-semibold text-[#98A49E]">
              <Sliders className="w-4 h-4 text-[#6DB386]" />
              <span>Отображение карты, зон и экрана</span>
            </div>

            <div>
              <label htmlFor="set-zone-radius" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                Радиус круговой зоны по умолчанию
              </label>
              <select
                id="set-zone-radius"
                value={settings.defaultZoneRadiusM ?? 80}
                onChange={(e) =>
                  onUpdateSettings({
                    ...settings,
                    defaultZoneRadiusM: Number(e.target.value),
                    updatedAt: Date.now(),
                  })
                }
                className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono text-xs sm:text-sm text-[#E6EBE8]"
              >
                <option value={30}>30 м (локальная точка)</option>
                <option value={80}>80 м (цех / корпус)</option>
                <option value={150}>150 м (промзона)</option>
                <option value={300}>300 м (крупный комплекс)</option>
              </select>
            </div>

            {(
              [
                {
                  key: 'showObjectZones',
                  label: 'Показывать зоны объектов на карте',
                  desc: 'Отображает размеченные полигоны периметров и позволяет выбирать объект касанием внутри зоны',
                  checked: settings.showObjectZones !== false,
                },
                {
                  key: 'showObjectLabels',
                  label: 'Подписи объектов на карте',
                  desc: 'При выключении остаются только цветные маркеры без текстовых плашек',
                  checked: settings.showObjectLabels !== false,
                },
                {
                  key: 'autoCenterGps',
                  label: 'Автослежение по GPS на карте',
                  desc: 'Карта автоматически смещается за текущей позицией при получении нового фикса',
                  checked: Boolean(settings.autoCenterGps),
                },
                {
                  key: 'keepScreenAwake',
                  label: 'Не выключать экран на карте',
                  desc: 'Удерживает экран активным во время полевой разведки (Wake Lock)',
                  checked: Boolean(settings.keepScreenAwake),
                },
                {
                  key: 'lowMemoryMode',
                  label: 'Режим экономии RAM',
                  desc: 'Ограничивает кэш миниатюр и отключает декоративные изолинии при низкой памяти',
                  checked: settings.lowMemoryMode,
                },
              ] as const
            ).map((item) => (
              <label
                key={item.key}
                className="flex items-center justify-between gap-3 p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#323E39] transition-colors cursor-pointer"
              >
                <div>
                  <div className="text-sm font-medium text-[#E6EBE8]">{item.label}</div>
                  <div className="text-xs text-[#6B7771]">{item.desc}</div>
                </div>
                <span className="relative inline-flex shrink-0">
                  <input
                    type="checkbox"
                    checked={item.checked}
                    onChange={(e) =>
                      onUpdateSettings({
                        ...settings,
                        [item.key]: e.target.checked,
                        updatedAt: Date.now(),
                      })
                    }
                    aria-label={item.label}
                    className="sr-only peer"
                  />
                  <span
                    className={`w-12 h-7 rounded-full border p-0.5 flex items-center transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-[#5B9D72] ${
                      item.checked
                        ? 'bg-[#5B9D72] border-[#6DB386] justify-end'
                        : 'bg-[#1A201E] border-[#323E39] justify-start'
                    }`}
                  >
                    <motion.span
                      layout
                      transition={{ type: 'spring', stiffness: 500, damping: 32 }}
                      className={`w-5 h-5 rounded-full shadow ${
                        item.checked ? 'bg-[#0C0F0E]' : 'bg-[#98A49E]'
                      }`}
                    />
                  </span>
                </span>
              </label>
            ))}
          </div>
        </section>

        {/* Cached Offline Sectors */}
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
          <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
            Кэшированные офлайн-секторы ({settings.offlineSectors.length})
          </h2>
          {settings.offlineSectors.length === 0 ? (
            <p className="text-xs text-[#98A49E]">Нет сохранённых секторов карты.</p>
          ) : (
            <div className="divide-y divide-[#252E2A]">
              <AnimatePresence initial={false}>
                {settings.offlineSectors.map((sec) => (
                  <motion.div
                    key={sec.id}
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.16 }}
                    className="overflow-hidden"
                  >
                    <div className="py-2 flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <div className="text-xs sm:text-sm font-medium text-[#E6EBE8] truncate">{sec.name}</div>
                        <div className="text-[11px] font-mono tabular-nums text-[#98A49E]">
                          R={sec.radiusKm} км · Z{sec.zoomMin}–{sec.zoomMax} · {sec.sizeMb} МБ
                        </div>
                      </div>
                      <motion.button
                        whileTap={{ scale: 0.9 }}
                        type="button"
                        onClick={() => onRemoveSector(sec.id)}
                        aria-label={`Удалить сектор ${sec.name}`}
                        className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860] transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </motion.button>
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          )}
        </section>
      </div>

      {/* Right: .urbexdb Backup, Integrity Check & Conflict Merge */}
      <div className="space-y-3 sm:space-y-4">
        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4 sm:w-5 sm:h-5 text-[#D39239]" />
            <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
              Резервное копирование (.urbexdb)
            </h2>
          </div>

          <p className="text-[11px] sm:text-xs text-[#98A49E] leading-relaxed">
            Экспорт и восстановление полной автономной базы (объекты, сообщения Mesh, база знаний, архив, профиль и настройки) с проверкой версии формата и контрольной суммы целостности.
          </p>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleExportDownload}
              className="min-h-[36px] lg:min-h-[42px] px-3.5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" />
              <span>Экспортировать .urbexdb</span>
            </button>

            <label className="min-h-[36px] lg:min-h-[42px] px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm font-medium text-[#E6EBE8] hover:border-[#5B9D72] cursor-pointer flex items-center gap-1.5">
              <Upload className="w-4 h-4 text-[#D39239]" />
              <span>Импорт .urbexdb</span>
              <input
                type="file"
                accept=".urbexdb,.json,application/json"
                onChange={(e) => handleFileSelect(e.target.files?.[0])}
                className="sr-only"
              />
            </label>
          </div>

          <AnimatePresence>
            {importError && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.16 }}
                role="alert"
                className="p-2.5 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs text-[#F1ACA7]"
              >
                {importError}
              </motion.div>
            )}
          </AnimatePresence>

          <AnimatePresence>
            {importResultMsg && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.16 }}
                className="p-2.5 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs text-[#E6EBE8] flex items-center gap-2"
              >
                <CheckCircle2 className="w-4 h-4 text-[#6DB386] shrink-0" />
                <span>{importResultMsg}</span>
              </motion.div>
            )}
          </AnimatePresence>
        </section>

        <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
          <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">Сброс и восстановление</h2>
          <p className="text-[11px] sm:text-xs text-[#98A49E]">
            Возвращает локальную базу к исходному полевому набору сектора (при сбоях или очистке кэша).
          </p>

          <AnimatePresence mode="wait">
            {!confirmResetOpen ? (
              <motion.button
                key="reset-trigger"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                whileTap={{ scale: 0.96 }}
                transition={{ duration: 0.14 }}
                type="button"
                onClick={() => setConfirmResetOpen(true)}
                className="min-h-[36px] lg:min-h-[42px] px-3.5 py-1.5 rounded-lg border border-[#C7564E]/60 text-xs font-medium text-[#F1ACA7] hover:bg-[#381B19] transition-colors flex items-center gap-2"
              >
                <RefreshCw className="w-4 h-4" />
                <span>Сбросить базу к исходной</span>
              </motion.button>
            ) : (
              <motion.div
                key="reset-confirm"
                initial={{ opacity: 0, scale: 0.98 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.98 }}
                transition={{ duration: 0.14 }}
                className="p-2.5 rounded-lg bg-[#381B19] border border-[#C7564E] flex items-center justify-between gap-3"
              >
                <span className="text-xs text-[#F1ACA7]">Подтвердить полный сброс?</span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setConfirmResetOpen(false)}
                    className="min-h-[34px] px-3 py-1 rounded bg-[#1A201E] text-xs text-[#E6EBE8]"
                  >
                    Отмена
                  </button>
                  <button
                    type="button"
                    onClick={async () => {
                      await onResetSeed();
                      setConfirmResetOpen(false);
                      setImportResultMsg('Локальная база восстановлена к исходному состоянию.');
                    }}
                    className="min-h-[34px] px-3 py-1 rounded bg-[#C7564E] text-[#0C0F0E] text-xs font-semibold"
                  >
                    Сбросить
                  </button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </section>
      </div>

      {/* Centered Modal Window for .urbexdb Import Verification */}
      <AnimatePresence>
        {importCandidate && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => setImportCandidate(null)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-label="Импорт резервной копии .urbexdb"
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.97, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              onClick={(e) => e.stopPropagation()}
              className="w-full max-w-lg max-h-[92dvh] overflow-y-auto bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3 shadow-2xl"
            >
              <div className="flex items-center justify-between gap-3 pb-2.5 border-b border-[#252E2A]">
                <div className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Контроль целостности пройден
                </div>
                <button
                  type="button"
                  onClick={() => setImportCandidate(null)}
                  aria-label="Закрыть окно импорта"
                  className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-mono tabular-nums text-[#98A49E] space-y-1">
                <div>Формат: {importCandidate.manifest.format} v{importCandidate.manifest.version}</div>
                <div>Узел: {importCandidate.manifest.deviceNodeId} · Сумма: {importCandidate.manifest.checksum}</div>
                <div>
                  Состав: {importCandidate.manifest.counts.objects} объектов,{' '}
                  {importCandidate.manifest.counts.archiveItems} архив,{' '}
                  {importCandidate.manifest.counts.meshMessages} пакетов
                </div>
              </div>

              {importWarnings.length > 0 && (
                <p className="text-xs text-[#DF9E43]">{importWarnings.join(' ')}</p>
              )}

              <div>
                <label htmlFor="conflict-strategy" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                  Разрешение конфликтов по ID
                </label>
                <select
                  id="conflict-strategy"
                  value={conflictStrategy}
                  onChange={(e) => setConflictStrategy(e.target.value as ConflictStrategy)}
                  className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
                >
                  <option value="keep_newer">Оставлять более новую запись (по времени)</option>
                  <option value="skip_existing">Пропускать существующие записи</option>
                  <option value="overwrite">Перезаписать текущие записи из файла</option>
                </select>
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-[#252E2A]">
                <button
                  type="button"
                  onClick={() => setImportCandidate(null)}
                  className="min-h-[36px] sm:min-h-[40px] px-4 py-1.5 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
                >
                  Отмена
                </button>
                <button
                  type="button"
                  onClick={handleConfirmImport}
                  className="min-h-[36px] sm:min-h-[40px] px-5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold"
                >
                  Применить импорт
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
