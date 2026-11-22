import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import {
  BatteryCharging,
  Check,
  CheckCircle2,
  Code2,
  Database,
  Download,
  Edit3,
  HardDrive,
  Layers,
  Map as MapIcon,
  Maximize2,
  Minimize2,
  Plus,
  Radio,
  RefreshCw,
  Shield,
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
  DatabaseWorkspaceMeta,
  MapLayerMode,
  ObjectsViewMode,
  PdaOrientation,
  PowerProfile,
  SettingsCategory,
  UrbexDbBundle,
} from '../../core/types';
import type { ParseUrbexDbResult } from '../../core/urbexdb';
import { CloudSyncPanel } from '../../components/CloudSyncPanel';
import { DatabaseTemplateModal } from '../../components/DatabaseTemplateModal';
import { RadioChannelsPanel } from '../../components/RadioChannelsPanel';
import { UrbexSelect } from '../../components/UrbexSelect';

export interface WorkspaceSummaryItem {
  meta: DatabaseWorkspaceMeta;
  counts: {
    objects: number;
    archiveItems: number;
    baseArticles: number;
    meshMessages: number;
  };
}

interface SettingsViewProps {
  settings: AppSettings;
  initialCategory?: SettingsCategory;
  onCategoryChange?: (cat: SettingsCategory) => void;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  onUpdateSettings: (next: AppSettings) => Promise<void>;
  onRemoveSector: (sectorId: string) => Promise<void>;
  onExportUrbexDb: (targetDbId?: string) => string | null;
  onInspectUrbexDb: (raw: string) => ParseUrbexDbResult;
  onApplyImport: (
    bundle: UrbexDbBundle,
    strategy: ConflictStrategy
  ) => Promise<{ added: number; updated: number; skipped: number } | null>;
  onResetSeed: () => Promise<void>;
  activeDbId: string;
  mountedDbIds: string[];
  workspaceSummaries: WorkspaceSummaryItem[];
  onSwitchDatabase: (targetDbId: string, preserveMultiMount?: boolean) => Promise<void>;
  onToggleMountDatabase: (targetDbId: string) => Promise<void>;
  onCreateDatabase: (params: {
    name: string;
    regionLabel: string;
    description?: string;
    mode: 'empty' | 'seed';
    activateImmediately?: boolean;
  }) => Promise<DatabaseWorkspaceMeta | null>;
  onRenameDatabase: (dbId: string, name: string, regionLabel: string) => Promise<void>;
  onDeleteDatabase: (dbId: string) => Promise<void>;
  onImportIntoNewDatabase: (
    name: string,
    regionLabel: string,
    bundle: UrbexDbBundle
  ) => Promise<DatabaseWorkspaceMeta>;
  isSyncing: boolean;
  syncDirectoryName: string | null;
  onSelectSyncDirectory: () => Promise<string | null>;
  onSyncNow: (opts?: { manual?: boolean }) => Promise<{ ok: boolean; message: string }>;
}

const SUBMENUS: Array<{
  id: SettingsCategory;
  label: string;
  subtitle: string;
  icon: typeof Database;
}> = [
  { id: 'databases', label: 'Базы и облако', subtitle: 'Регионы, автосинк', icon: Database },
  { id: 'map_gps', label: 'Карта и GPS', subtitle: 'Питание, сетка, HUD', icon: MapIcon },
  { id: 'radio_mesh', label: 'Радио и Mesh', subtitle: 'Частоты, CTCSS, шифр', icon: Radio },
  { id: 'pda_ui', label: 'PDA и экран', subtitle: 'Редактор, кэш, защита', icon: Smartphone },
];

function SettingToggleRow({
  label,
  desc,
  checked,
  onChange,
}: {
  label: string;
  desc: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between gap-3 p-2.5 sm:p-3.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#323E39] transition-colors cursor-pointer">
      <div>
        <div className="text-xs sm:text-sm font-medium text-[#E6EBE8]">{label}</div>
        <div className="text-[11px] sm:text-xs text-[#6B7771]">{desc}</div>
      </div>
      <span className="relative inline-flex shrink-0">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-label={label}
          className="sr-only peer"
        />
        <span
          className={`w-12 h-7 rounded-full border p-0.5 flex items-center transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-[#5B9D72] ${
            checked
              ? 'bg-[#5B9D72] border-[#6DB386] justify-end'
              : 'bg-[#1A201E] border-[#323E39] justify-start'
          }`}
        >
          <motion.span
            layout
            transition={{ type: 'spring', stiffness: 500, damping: 32 }}
            className={`w-5 h-5 rounded-full shadow ${
              checked ? 'bg-[#0C0F0E]' : 'bg-[#98A49E]'
            }`}
          />
        </span>
      </span>
    </label>
  );
}

export default function SettingsView({
  settings,
  initialCategory = 'databases',
  onCategoryChange,
  isFullscreen = false,
  onToggleFullscreen,
  onUpdateSettings,
  onRemoveSector,
  onExportUrbexDb,
  onInspectUrbexDb,
  onApplyImport,
  onResetSeed,
  activeDbId,
  mountedDbIds,
  workspaceSummaries,
  onSwitchDatabase,
  onToggleMountDatabase,
  onCreateDatabase,
  onRenameDatabase,
  onDeleteDatabase,
  onImportIntoNewDatabase,
  isSyncing,
  syncDirectoryName,
  onSelectSyncDirectory,
  onSyncNow,
}: SettingsViewProps) {
  const [activeCategory, setActiveCategory] = useState<SettingsCategory>(initialCategory);

  useEffect(() => {
    setActiveCategory(initialCategory);
  }, [initialCategory]);

  const selectCategory = (cat: SettingsCategory) => {
    setActiveCategory(cat);
    onCategoryChange?.(cat);
  };

  const [importCandidate, setImportCandidate] = useState<UrbexDbBundle | null>(null);
  const [importWarnings, setImportWarnings] = useState<string[]>([]);
  const [importError, setImportError] = useState<string | null>(null);
  const [importResultMsg, setImportResultMsg] = useState<string | null>(null);
  const [conflictStrategy, setConflictStrategy] = useState<ConflictStrategy>('keep_newer');
  const [importMode, setImportMode] = useState<'merge' | 'new_db'>('merge');
  const [importNewDbName, setImportNewDbName] = useState('');
  const [importNewDbRegion, setImportNewDbRegion] = useState('');
  const [confirmResetOpen, setConfirmResetOpen] = useState(false);

  // Database Manager local states
  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [creatingDbOpen, setCreatingDbOpen] = useState(false);
  const [newDbName, setNewDbName] = useState('');
  const [newDbRegion, setNewDbRegion] = useState('');
  const [newDbMode, setNewDbMode] = useState<'empty' | 'seed'>('empty');
  const [renamingDbId, setRenamingDbId] = useState<string | null>(null);
  const [renameName, setRenameName] = useState('');
  const [renameRegion, setRenameRegion] = useState('');
  const [confirmDeleteDbId, setConfirmDeleteDbId] = useState<string | null>(null);

  const handlePowerChange = async (profile: PowerProfile) => {
    const meta = POWER_PROFILE_META[profile];
    await onUpdateSettings({
      ...settings,
      powerProfile: profile,
      gpsIntervalMs: meta.intervalMs,
      updatedAt: Date.now(),
    });
  };

  const handleExportDownload = (targetDbId?: string, fileSlug?: string) => {
    const content = onExportUrbexDb(targetDbId);
    if (!content) return;
    const blob = new Blob([content], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const dateStamp = new Date().toISOString().slice(0, 10);
    const safeSlug = fileSlug
      ? `-${fileSlug.toLowerCase().replace(/[^a-zа-я0-9_-]+/gi, '-').replace(/^-|-$/g, '')}`
      : '';
    a.href = url;
    a.download = `urbex-backup${safeSlug}-${dateStamp}.urbexdb`;
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
      const baseName = file.name.replace(/\.(urbexdb|json)$/i, '').trim() || 'Импортированная база';
      setImportCandidate(res.bundle);
      setImportWarnings(res.warnings);
      setImportMode('merge');
      setImportNewDbName(baseName);
      setImportNewDbRegion(res.bundle.payload.objects[0]?.title.split(' ')[0] || baseName);
    };
    reader.readAsText(file);
  };

  const handleConfirmImport = async () => {
    if (!importCandidate) return;
    if (importMode === 'new_db') {
      const created = await onImportIntoNewDatabase(
        importNewDbName.trim() || 'Импортированная база',
        importNewDbRegion.trim() || importNewDbName.trim() || 'Регион',
        importCandidate
      );
      setImportCandidate(null);
      setImportResultMsg(
        `Создана и активирована новая база «${created.name}» (${importCandidate.payload.objects.length} объектов).`
      );
      return;
    }

    const stats = await onApplyImport(importCandidate, conflictStrategy);
    setImportCandidate(null);
    if (stats) {
      setImportResultMsg(
        `Импорт завершён: добавлено ${stats.added}, обновлено ${stats.updated}, пропущено ${stats.skipped}.`
      );
    }
  };

  const handleCreateDbSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newDbName.trim()) return;
    const created = await onCreateDatabase({
      name: newDbName.trim(),
      regionLabel: newDbRegion.trim() || newDbName.trim(),
      mode: newDbMode,
      activateImmediately: true,
    });
    if (created) {
      setCreatingDbOpen(false);
      setNewDbName('');
      setNewDbRegion('');
      setNewDbMode('empty');
      setImportResultMsg(`Создана и выбрана база «${created.name}».`);
    }
  };

  const activeDbMeta = workspaceSummaries.find((w) => w.meta.id === activeDbId)?.meta;

  return (
    <div className="space-y-3 sm:space-y-4">
      {/* Categorized Settings Submenu Navigation */}
      <nav
        aria-label="Категории настроек"
        data-testid="settings-submenu-tabs"
        className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 bg-[#131816] border border-[#252E2A] p-1.5 rounded-xl"
      >
        {SUBMENUS.map((sub) => {
          const Icon = sub.icon;
          const active = activeCategory === sub.id;
          return (
            <button
              key={sub.id}
              type="button"
              onClick={() => selectCategory(sub.id)}
              aria-current={active ? 'page' : undefined}
              data-testid={`settings-tab-${sub.id}`}
              className={`relative min-h-[48px] px-3 py-2 rounded-lg text-left transition-colors overflow-hidden flex items-center gap-2.5 ${
                active ? 'text-[#E6EBE8]' : 'text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#0C0F0E]/60'
              }`}
            >
              {active && (
                <motion.span
                  layoutId="settings-submenu-pill"
                  transition={{ type: 'spring', stiffness: 440, damping: 32 }}
                  className="absolute inset-0 rounded-lg bg-[#1A201E] border border-[#5B9D72]"
                />
              )}
              <Icon
                className={`relative z-10 w-4 h-4 shrink-0 ${
                  active ? 'text-[#6DB386]' : 'text-[#6B7771]'
                }`}
              />
              <div className="relative z-10 min-w-0">
                <div className="text-xs sm:text-sm font-semibold leading-tight truncate">
                  {sub.label}
                </div>
                <div className="text-[10px] text-[#6B7771] truncate">{sub.subtitle}</div>
              </div>
            </button>
          );
        })}
      </nav>

      {/* Active Submenu Panel */}
      <div>
        {activeCategory === 'databases' && (
          <motion.div
            key="submenu-databases"
            initial={{ opacity: 0.92, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            className="space-y-3 sm:space-y-4"
          >
            {/* Multi-Database Region Manager */}
            <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                <div className="flex items-start gap-2.5 min-w-0">
                  <Database className="w-5 h-5 text-[#6DB386] shrink-0 mt-0.5" />
                  <div className="min-w-0">
                    <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                      Менеджер баз данных (регионы и секторы)
                    </h2>
                    <p className="text-[11px] sm:text-xs text-[#98A49E]">
                      Активная (запись):{' '}
                      <span className="text-[#E6EBE8] font-medium">{activeDbMeta?.name || '—'}</span>
                      {' · '}Подключено в вид:{' '}
                      <span className="font-mono text-[#6DB386]">{mountedDbIds.length}</span>
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => setTemplateModalOpen(true)}
                    className="min-h-[34px] px-3 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center gap-1.5"
                  >
                    <Code2 className="w-3.5 h-3.5 text-[#DF9E43]" />
                    <span>Схема и шаблон .urbexdb</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCreatingDbOpen((v) => !v)}
                    className="min-h-[34px] px-3 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] flex items-center gap-1.5"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Новая база</span>
                  </button>
                </div>
              </div>

              <AnimatePresence>
                {creatingDbOpen && (
                  <motion.form
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.16 }}
                    onSubmit={handleCreateDbSubmit}
                    className="overflow-hidden"
                  >
                    <div className="p-3 rounded-lg bg-[#0C0F0E] border border-[#5B9D72]/60 space-y-2.5">
                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                        <input
                          type="text"
                          required
                          value={newDbName}
                          onChange={(e) => setNewDbName(e.target.value)}
                          placeholder="Название (напр. Тюмень · Заброшки)"
                          aria-label="Название новой базы данных"
                          className="h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                        />
                        <input
                          type="text"
                          value={newDbRegion}
                          onChange={(e) => setNewDbRegion(e.target.value)}
                          placeholder="Регион / город (напр. Тюмень)"
                          aria-label="Регион новой базы данных"
                          className="h-9 px-3 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                        />
                        <UrbexSelect
                          compact
                          ariaLabel="Начальное наполнение базы"
                          value={newDbMode}
                          onChange={(val) => setNewDbMode(val)}
                          options={[
                            { value: 'empty', label: 'Чистая база (0 объектов)' },
                            { value: 'seed', label: 'Копия стартового сектора' },
                          ]}
                        />
                      </div>
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setCreatingDbOpen(false)}
                          className="min-h-[32px] px-3 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
                        >
                          Отмена
                        </button>
                        <button
                          type="submit"
                          className="min-h-[32px] px-4 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold"
                        >
                          Создать и переключить
                        </button>
                      </div>
                    </div>
                  </motion.form>
                )}
              </AnimatePresence>

              {/* Workspace Cards Grid */}
              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:720px)]:grid-cols-2 gap-2.5">
                {workspaceSummaries.map((ws) => {
                  const isActive = ws.meta.id === activeDbId;
                  const isMounted = mountedDbIds.includes(ws.meta.id);
                  const isRenaming = renamingDbId === ws.meta.id;
                  const isConfirmDelete = confirmDeleteDbId === ws.meta.id;

                  return (
                    <div
                      key={ws.meta.id}
                      className={`p-3 rounded-xl border transition-colors space-y-2.5 ${
                        isActive
                          ? 'bg-[#17211C] border-[#5B9D72]'
                          : isMounted
                            ? 'bg-[#111916] border-[#3D634C]'
                            : 'bg-[#0C0F0E] border-[#252E2A]'
                      }`}
                    >
                      {isRenaming ? (
                        <div className="space-y-2">
                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            <input
                              type="text"
                              value={renameName}
                              onChange={(e) => setRenameName(e.target.value)}
                              aria-label="Новое название базы"
                              className="h-8 px-2.5 rounded bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                            />
                            <input
                              type="text"
                              value={renameRegion}
                              onChange={(e) => setRenameRegion(e.target.value)}
                              aria-label="Новый регион базы"
                              className="h-8 px-2.5 rounded bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8]"
                            />
                          </div>
                          <div className="flex justify-end gap-1.5">
                            <button
                              type="button"
                              onClick={() => setRenamingDbId(null)}
                              className="min-h-[28px] px-2.5 rounded border border-[#252E2A] text-[11px] text-[#98A49E]"
                            >
                              Отмена
                            </button>
                            <button
                              type="button"
                              onClick={async () => {
                                await onRenameDatabase(ws.meta.id, renameName, renameRegion);
                                setRenamingDbId(null);
                              }}
                              className="min-h-[28px] px-3 rounded bg-[#5B9D72] text-[#0C0F0E] text-[11px] font-semibold flex items-center gap-1"
                            >
                              <Check className="w-3 h-3" />
                              <span>Сохранить</span>
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                                {ws.meta.name}
                              </span>
                              {isActive && (
                                <span className="px-1.5 py-0.5 rounded bg-[#203328] border border-[#5B9D72] text-[10px] font-medium text-[#6DB386]">
                                  Активная
                                </span>
                              )}
                            </div>
                            <div className="text-[11px] text-[#98A49E] font-mono tabular-nums mt-0.5">
                              {ws.meta.regionLabel} · {ws.counts.objects} об. · {ws.counts.archiveItems} арх. ·{' '}
                              {ws.counts.baseArticles} баз.зн.
                            </div>
                          </div>

                          <label
                            className={`shrink-0 flex items-center gap-1.5 px-2 py-1 rounded-lg border text-[11px] select-none ${
                              isActive
                                ? 'border-[#323E39] bg-[#0C0F0E]/60 text-[#6DB386] opacity-85 cursor-default'
                                : isMounted
                                  ? 'border-[#5B9D72] bg-[#203328] text-[#E6EBE8] cursor-pointer'
                                  : 'border-[#252E2A] bg-[#131816] text-[#98A49E] hover:text-[#E6EBE8] cursor-pointer'
                            }`}
                          >
                            <input
                              type="checkbox"
                              checked={isMounted}
                              disabled={isActive}
                              onChange={() => onToggleMountDatabase(ws.meta.id)}
                              className="accent-[#5B9D72]"
                            />
                            <Layers className="w-3 h-3" />
                            <span>В вид</span>
                          </label>
                        </div>
                      )}

                      <div className="flex flex-wrap items-center justify-between gap-1.5 pt-1 border-t border-[#252E2A]/80">
                        <div className="flex flex-wrap items-center gap-1.5">
                          {!isActive && (
                            <button
                              type="button"
                              onClick={() => onSwitchDatabase(ws.meta.id, false)}
                              className="min-h-[30px] px-2.5 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-[11px] font-semibold hover:bg-[#6DB386]"
                            >
                              Переключить
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => {
                              setRenamingDbId(ws.meta.id);
                              setRenameName(ws.meta.name);
                              setRenameRegion(ws.meta.regionLabel);
                            }}
                            className="min-h-[30px] px-2 py-1 rounded-lg border border-[#252E2A] bg-[#131816] text-[11px] text-[#98A49E] hover:text-[#E6EBE8] flex items-center gap-1"
                          >
                            <Edit3 className="w-3 h-3" />
                            <span className="hidden sm:inline">Имя</span>
                          </button>
                          <button
                            type="button"
                            onClick={() => handleExportDownload(ws.meta.id, ws.meta.regionLabel)}
                            className="min-h-[30px] px-2 py-1 rounded-lg border border-[#252E2A] bg-[#131816] text-[11px] text-[#98A49E] hover:text-[#E6EBE8] flex items-center gap-1"
                          >
                            <Download className="w-3 h-3 text-[#DF9E43]" />
                            <span>.urbexdb</span>
                          </button>
                        </div>

                        {workspaceSummaries.length > 1 &&
                          (isConfirmDelete ? (
                            <div className="flex items-center gap-1">
                              <button
                                type="button"
                                onClick={() => setConfirmDeleteDbId(null)}
                                className="min-h-[28px] px-2 rounded bg-[#1A201E] text-[11px] text-[#E6EBE8]"
                              >
                                Отмена
                              </button>
                              <button
                                type="button"
                                onClick={async () => {
                                  await onDeleteDatabase(ws.meta.id);
                                  setConfirmDeleteDbId(null);
                                }}
                                className="min-h-[28px] px-2 rounded bg-[#C7564E] text-[#0C0F0E] text-[11px] font-semibold"
                              >
                                Удалить
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setConfirmDeleteDbId(ws.meta.id)}
                              aria-label={`Удалить базу ${ws.meta.name}`}
                              className="min-h-[30px] min-w-[30px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860]"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* Free Cloud Auto-Sync Engine */}
            <CloudSyncPanel
              config={settings.cloudSync}
              isSyncing={isSyncing}
              syncDirectoryName={syncDirectoryName}
              onChangeConfig={(nextCloud) =>
                onUpdateSettings({
                  ...settings,
                  cloudSync: nextCloud,
                  updatedAt: Date.now(),
                })
              }
              onSelectSyncDirectory={onSelectSyncDirectory}
              onSyncNow={onSyncNow}
            />

            {/* Backup + Reset Split Row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-4">
              <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
                <div className="flex items-center gap-2">
                  <HardDrive className="w-4 h-4 sm:w-5 sm:h-5 text-[#D39239]" />
                  <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                    Ручное резервирование (.urbexdb)
                  </h2>
                </div>
                <p className="text-[11px] sm:text-xs text-[#98A49E] leading-relaxed">
                  Экспорт и восстановление полной автономной базы с проверкой схемы и контрольной суммы (секреты облачной синхронизации исключаются из файла).
                </p>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => handleExportDownload()}
                    className="min-h-[36px] px-3.5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] flex items-center gap-1.5"
                  >
                    <Download className="w-4 h-4" />
                    <span>Экспорт активной БД</span>
                  </button>

                  <label className="min-h-[36px] px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm font-medium text-[#E6EBE8] hover:border-[#5B9D72] cursor-pointer flex items-center gap-1.5">
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
                      className="p-2.5 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs text-[#E6EBE8] flex items-center gap-2"
                    >
                      <CheckCircle2 className="w-4 h-4 text-[#6DB386] shrink-0" />
                      <span>{importResultMsg}</span>
                    </motion.div>
                  )}
                </AnimatePresence>
              </section>

              <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Сброс и восстановление
                </h2>
                <p className="text-[11px] sm:text-xs text-[#98A49E]">
                  Возвращает активную базу к исходному полевому набору сектора (при сбоях или очистке кэша).
                </p>
                <AnimatePresence mode="wait">
                  {!confirmResetOpen ? (
                    <motion.button
                      key="reset-trigger"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      whileTap={{ scale: 0.96 }}
                      type="button"
                      onClick={() => setConfirmResetOpen(true)}
                      className="min-h-[36px] px-3.5 py-1.5 rounded-lg border border-[#C7564E]/60 text-xs font-medium text-[#F1ACA7] hover:bg-[#381B19] transition-colors flex items-center gap-2"
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
                            setImportResultMsg('Активная локальная база восстановлена к исходному состоянию.');
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
          </motion.div>
        )}

        {activeCategory === 'map_gps' && (
          <motion.div
            key="submenu-map-gps"
            initial={{ opacity: 0.92, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-4"
          >
            <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
              <div className="flex items-center gap-2">
                <BatteryCharging className="w-4 h-4 sm:w-5 sm:h-5 text-[#5B9D72]" />
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Профиль питания GPS и базовая карта
                </h2>
              </div>

              <UrbexSelect<PowerProfile>
                label="Профиль питания GPS"
                ariaLabel="Профиль питания GPS"
                value={settings.powerProfile}
                options={(Object.keys(POWER_PROFILE_META) as PowerProfile[]).map((p) => ({
                  value: p,
                  label: POWER_PROFILE_META[p].label,
                  description: POWER_PROFILE_META[p].summary,
                }))}
                onChange={(nextProfile) => handlePowerChange(nextProfile)}
              />
              <p className="text-[11px] text-[#6B7771]">
                {POWER_PROFILE_META[settings.powerProfile].summary}
              </p>

              <div className="pt-2 border-t border-[#252E2A] grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <UrbexSelect<MapLayerMode>
                  label="Слой карты"
                  ariaLabel="Слой карты"
                  compact
                  value={settings.mapLayer}
                  options={[
                    { value: 'satellite', label: 'Спутниковая' },
                    { value: 'offline_vector', label: 'Офлайн-вектор' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      mapLayer: val,
                      updatedAt: Date.now(),
                    })
                  }
                />

                <UrbexSelect<CoordinateFormat>
                  label="Формат координат"
                  ariaLabel="Формат координат"
                  compact
                  value={settings.coordinateFormat}
                  options={[
                    { value: 'dd', label: 'Десятичные (DD)' },
                    { value: 'dms', label: 'Градусы (DMS)' },
                    { value: 'utm', label: 'Сетка UTM' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      coordinateFormat: val,
                      updatedAt: Date.now(),
                    })
                  }
                />

                <UrbexSelect<number>
                  label="Радиус круговой зоны"
                  ariaLabel="Радиус круговой зоны"
                  compact
                  value={settings.defaultZoneRadiusM ?? 80}
                  options={[
                    { value: 30, label: '30 м (локальная точка)' },
                    { value: 80, label: '80 м (цех / корпус)' },
                    { value: 150, label: '150 м (промзона)' },
                    { value: 300, label: '300 м (крупный комплекс)' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      defaultZoneRadiusM: Number(val),
                      updatedAt: Date.now(),
                    })
                  }
                />

                <UrbexSelect<number>
                  label="Порог оповещения «Опасен»"
                  ariaLabel="Порог оповещения Опасен"
                  compact
                  value={settings.hazardProximityAlertM ?? 150}
                  options={[
                    { value: 0, label: 'Выкл (не предупреждать)' },
                    { value: 50, label: '50 м (впритык)' },
                    { value: 150, label: '150 м (стандарт)' },
                    { value: 300, label: '300 м (ранний периметр)' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      hazardProximityAlertM: Number(val),
                      updatedAt: Date.now(),
                    })
                  }
                />
              </div>
            </section>

            <div className="space-y-3 sm:space-y-4">
              <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
                <div className="flex items-center gap-2">
                  <Sliders className="w-4 h-4 sm:w-5 sm:h-5 text-[#6DB386]" />
                  <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                    Слои и индикация карты
                  </h2>
                </div>

                <SettingToggleRow
                  label="Показывать зоны объектов на карте"
                  desc="Отображает размеченные полигоны периметров и позволяет выбирать объект касанием внутри зоны."
                  checked={settings.showObjectZones !== false}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, showObjectZones: v, updatedAt: Date.now() })
                  }
                />
                <SettingToggleRow
                  label="Подписи объектов на карте"
                  desc="При выключении остаются только компактные цветные маркеры без текстовых плашек."
                  checked={settings.showObjectLabels !== false}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, showObjectLabels: v, updatedAt: Date.now() })
                  }
                />
                <SettingToggleRow
                  label="Метрическая координатная сетка 500 м"
                  desc="Накладывает тонкую сетку 500×500 м поверх карты для визуальной оценки дистанций."
                  checked={settings.showMapGrid !== false}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, showMapGrid: v, updatedAt: Date.now() })
                  }
                />
                <SettingToggleRow
                  label="Индикатор курса / азимута (HUD)"
                  desc="Показывает азимут движения и число спутников/точность на верхней панели карты."
                  checked={settings.showCompassHud !== false}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, showCompassHud: v, updatedAt: Date.now() })
                  }
                />
                <SettingToggleRow
                  label="Автослежение по GPS на карте"
                  desc="Карта автоматически смещается за текущей позицией при получении нового фикса."
                  checked={Boolean(settings.autoCenterGps)}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, autoCenterGps: v, updatedAt: Date.now() })
                  }
                />
              </section>

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
                          className="overflow-hidden"
                        >
                          <div className="py-2 flex items-center justify-between gap-3">
                            <div className="min-w-0">
                              <div className="text-xs sm:text-sm font-medium text-[#E6EBE8] truncate">
                                {sec.name}
                              </div>
                              <div className="text-[11px] font-mono tabular-nums text-[#98A49E]">
                                R={sec.radiusKm} км · Z{sec.zoomMin}–{sec.zoomMax} · {sec.sizeMb} МБ
                              </div>
                            </div>
                            <button
                              type="button"
                              onClick={() => onRemoveSector(sec.id)}
                              aria-label={`Удалить сектор ${sec.name}`}
                              className="min-h-[34px] min-w-[34px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860]"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>
                  </div>
                )}
              </section>
            </div>
          </motion.div>
        )}

        {activeCategory === 'radio_mesh' && (
          <motion.div
            key="submenu-radio-mesh"
            initial={{ opacity: 0.92, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            className="space-y-3 sm:space-y-4"
          >
            <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5">
              <div className="flex items-center gap-2">
                <Radio className="w-4 h-4 sm:w-5 sm:h-5 text-[#5B9D72]" />
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Вкладки раздела «Связь» и модуль Meshtastic
                </h2>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-3 gap-2.5">
                <SettingToggleRow
                  label="Вкладка «Пакеты Meshtastic»"
                  desc="Включает LoRa-пакеты, поиск USB/BLE-узлов и показ вкладки Meshtastic в «Связи». Выключите, чтобы оставить только частоты."
                  checked={settings.meshEnabled}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, meshEnabled: v, updatedAt: Date.now() })
                  }
                />
                <SettingToggleRow
                  label="Вкладка «Частоты и шифрование»"
                  desc="Показывает частотный план радиостанций (CTCSS/DCS, шифр, CHIRP) во вкладке «Связь». Отключите, чтобы оставить только Meshtastic."
                  checked={settings.radioChannelsTabEnabled !== false}
                  onChange={(v) =>
                    onUpdateSettings({
                      ...settings,
                      radioChannelsTabEnabled: v,
                      updatedAt: Date.now(),
                    })
                  }
                />
                <SettingToggleRow
                  label="Автообмен метками в Mesh"
                  desc="Автоматически отправлять обновления карточек объектов и аварийные маяки в активный LoRa-канал."
                  checked={settings.autoSyncOnMesh}
                  onChange={(v) =>
                    onUpdateSettings({ ...settings, autoSyncOnMesh: v, updatedAt: Date.now() })
                  }
                />
              </div>
            </section>

            {/* Radio Station Frequencies, CTCSS/DCS, Encryption Keys & CHIRP CSV */}
            <RadioChannelsPanel
              channels={settings.radioChannels ?? []}
              onSaveChannels={(nextChannels) =>
                onUpdateSettings({
                  ...settings,
                  radioChannels: nextChannels,
                  updatedAt: Date.now(),
                })
              }
            />
          </motion.div>
        )}

        {activeCategory === 'pda_ui' && (
          <motion.div
            key="submenu-pda-ui"
            initial={{ opacity: 0.92, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.14, ease: 'easeOut' }}
            className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-4"
          >
            <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3">
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 sm:w-5 sm:h-5 text-[#DF9E43]" />
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Режим КПК / PDA и ориентация
                </h2>
              </div>

              <SettingToggleRow
                label="Режим PDA (терминал)"
                desc="Включает телеметрию PDA в шапке (время, заряд, режим экрана), быстрый лаунчер «Прил.» и экранное управление."
                checked={Boolean(settings.pdaModeEnabled)}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, pdaModeEnabled: v, updatedAt: Date.now() })
                }
              />

              <SettingToggleRow
                label="Включить режим редактора"
                desc="Открывает кнопки создания, редактирования и удаления объектов, зон, статей и архива."
                checked={Boolean(settings.editorEnabled)}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, editorEnabled: v, updatedAt: Date.now() })
                }
              />

              <UrbexSelect<PdaOrientation>
                label="Ориентация экрана PDA"
                ariaLabel="Ориентация экрана PDA"
                compact
                value={settings.pdaOrientation ?? 'portrait'}
                options={(['portrait', 'landscape', 'auto'] as PdaOrientation[]).map((orient) => ({
                  value: orient,
                  label: PDA_ORIENTATION_LABELS[orient],
                }))}
                onChange={(orient) =>
                  onUpdateSettings({
                    ...settings,
                    pdaOrientation: orient,
                    updatedAt: Date.now(),
                  })
                }
              />

              {onToggleFullscreen && (
                <button
                  type="button"
                  onClick={onToggleFullscreen}
                  className="w-full min-h-[36px] px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-2 transition-colors"
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
                <Shield className="w-4 h-4 sm:w-5 sm:h-5 text-[#6DB386]" />
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Отображение, кэш и защита
                </h2>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                <UrbexSelect<ObjectsViewMode>
                  label="Вид объектов"
                  ariaLabel="Вид объектов по умолчанию"
                  compact
                  value={settings.defaultObjectsView ?? 'cards'}
                  options={[
                    { value: 'list', label: 'Список' },
                    { value: 'compact', label: 'Компакт' },
                    { value: 'cards', label: 'Карточки' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      defaultObjectsView: val,
                      updatedAt: Date.now(),
                    })
                  }
                />

                <UrbexSelect<number>
                  label="Пагинация списков"
                  ariaLabel="Пагинация списков"
                  compact
                  value={settings.pageSize}
                  options={[
                    { value: 8, label: '8 записей' },
                    { value: 12, label: '12 записей' },
                    { value: 24, label: '24 записи' },
                    { value: 50, label: '50 записей' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      pageSize: Number(val),
                      updatedAt: Date.now(),
                    })
                  }
                />

                <UrbexSelect<number>
                  label="Лимит кэша фото"
                  ariaLabel="Лимит кэша фото"
                  compact
                  value={settings.imageCacheLimitMb}
                  options={[
                    { value: 32, label: '32 МБ' },
                    { value: 64, label: '64 МБ' },
                    { value: 128, label: '128 МБ' },
                    { value: 256, label: '256 МБ' },
                  ]}
                  onChange={(val) =>
                    onUpdateSettings({
                      ...settings,
                      imageCacheLimitMb: Number(val),
                      updatedAt: Date.now(),
                    })
                  }
                />
              </div>

              <SettingToggleRow
                label="Не выключать экран на карте"
                desc="Удерживает экран активным во время полевой разведки (Wake Lock)."
                checked={Boolean(settings.keepScreenAwake)}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, keepScreenAwake: v, updatedAt: Date.now() })
                }
              />
              <SettingToggleRow
                label="Подтверждать удаление записей"
                desc="Запрашивать подтверждение перед удалением объектов и материалов архива."
                checked={settings.confirmBeforeDelete !== false}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, confirmBeforeDelete: v, updatedAt: Date.now() })
                }
              />
              <SettingToggleRow
                label="Тактильный отклик (вибрация)"
                desc="Лёгкий виброотклик при переключении вкладок и критических действиях."
                checked={settings.hapticFeedback !== false}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, hapticFeedback: v, updatedAt: Date.now() })
                }
              />
              <SettingToggleRow
                label="Маскировать координаты на экране (OPSEC)"
                desc="Скрывает точные цифры координат на карте (показывает ***.**°) при демонстрации или съёмке."
                checked={Boolean(settings.maskCoordinatesOnScreen)}
                onChange={(v) =>
                  onUpdateSettings({
                    ...settings,
                    maskCoordinatesOnScreen: v,
                    updatedAt: Date.now(),
                  })
                }
              />
              <SettingToggleRow
                label="Режим экономии RAM"
                desc="Ограничивает кэш миниатюр и отключает декоративные изолинии при низкой памяти."
                checked={settings.lowMemoryMode}
                onChange={(v) =>
                  onUpdateSettings({ ...settings, lowMemoryMode: v, updatedAt: Date.now() })
                }
              />
            </section>
          </motion.div>
        )}
      </div>

      {/* Centered Modal Window for .urbexdb Import Verification */}
      {typeof document !== 'undefined' &&
        createPortal(
          <AnimatePresence>
            {importCandidate && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.14 }}
                onClick={() => setImportCandidate(null)}
                className="fixed inset-0 z-[70] flex items-center justify-center bg-black/80 backdrop-blur-[2px] p-3 sm:p-6"
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
                  className="w-full max-w-lg max-h-[90dvh] flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden shadow-2xl"
                >
                  <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-3 border-b border-[#252E2A]">
                    <div className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                      Импорт базы .urbexdb
                    </div>
                    <button
                      type="button"
                      onClick={() => setImportCandidate(null)}
                      aria-label="Закрыть окно импорта"
                      className="min-h-[34px] min-w-[34px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  </div>

                  <div className="flex-1 min-h-0 overflow-y-auto p-3.5 sm:p-5 space-y-3">
                    <div className="p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-mono tabular-nums text-[#98A49E] space-y-1">
                      <div>
                        Формат: {importCandidate.manifest.format} v{importCandidate.manifest.version}
                      </div>
                      <div>
                        Узел: {importCandidate.manifest.deviceNodeId} · Сумма:{' '}
                        {importCandidate.manifest.checksum}
                      </div>
                      <div>
                        Состав: {importCandidate.manifest.counts.objects} объектов,{' '}
                        {importCandidate.manifest.counts.archiveItems} архив,{' '}
                        {importCandidate.manifest.counts.meshMessages} пакетов
                      </div>
                    </div>

                    {importWarnings.length > 0 && (
                      <p className="text-xs text-[#DF9E43]">{importWarnings.join(' ')}</p>
                    )}

                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setImportMode('merge')}
                        className={`min-h-[36px] px-3 py-1.5 rounded-lg border text-xs font-medium ${
                          importMode === 'merge'
                            ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                            : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
                        }`}
                      >
                        Объединить с активной
                      </button>
                      <button
                        type="button"
                        onClick={() => setImportMode('new_db')}
                        className={`min-h-[36px] px-3 py-1.5 rounded-lg border text-xs font-medium ${
                          importMode === 'new_db'
                            ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                            : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
                        }`}
                      >
                        Создать новую отдельную БД
                      </button>
                    </div>

                    {importMode === 'merge' ? (
                      <UrbexSelect<ConflictStrategy>
                        label="Разрешение конфликтов по ID"
                        ariaLabel="Разрешение конфликтов по ID"
                        value={conflictStrategy}
                        options={[
                          { value: 'keep_newer', label: 'Оставлять более новую запись (по времени)' },
                          { value: 'skip_existing', label: 'Пропускать существующие записи' },
                          { value: 'overwrite', label: 'Перезаписать текущие записи из файла' },
                        ]}
                        onChange={(val) => setConflictStrategy(val)}
                      />
                    ) : (
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                        <div>
                          <label htmlFor="import-db-name" className="block text-[11px] text-[#98A49E] mb-1">
                            Название новой БД
                          </label>
                          <input
                            id="import-db-name"
                            type="text"
                            value={importNewDbName}
                            onChange={(e) => setImportNewDbName(e.target.value)}
                            className="w-full h-9 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8]"
                          />
                        </div>
                        <div>
                          <label htmlFor="import-db-region" className="block text-[11px] text-[#98A49E] mb-1">
                            Регион
                          </label>
                          <input
                            id="import-db-region"
                            type="text"
                            value={importNewDbRegion}
                            onChange={(e) => setImportNewDbRegion(e.target.value)}
                            className="w-full h-9 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8]"
                          />
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="shrink-0 flex justify-end gap-2 px-4 py-3 border-t border-[#252E2A] bg-[#131816]">
                    <button
                      type="button"
                      onClick={() => setImportCandidate(null)}
                      className="min-h-[36px] px-4 py-1.5 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
                    >
                      Отмена
                    </button>
                    <button
                      type="button"
                      onClick={handleConfirmImport}
                      className="min-h-[36px] px-5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold"
                    >
                      {importMode === 'new_db' ? 'Создать БД из файла' : 'Применить импорт'}
                    </button>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>,
          document.body
        )}

      <DatabaseTemplateModal
        open={templateModalOpen}
        onClose={() => setTemplateModalOpen(false)}
        onCreateFromTemplate={async (bundle) => {
          const created = await onImportIntoNewDatabase(
            'Тюмень · Сектор ТЭЦ-1',
            'Тюмень',
            bundle
          );
          setImportResultMsg(`Создана тестовая база «${created.name}» из шаблона .urbexdb.`);
        }}
      />
    </div>
  );
}
