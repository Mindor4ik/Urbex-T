import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowLeft,
  Edit3,
  LayoutGrid,
  LayoutList,
  List,
  MapPin,
  Pentagon,
  Plus,
  Search,
  Trash2,
} from 'lucide-react';
import {
  bearingDeg,
  computePolygonAreaM2,
  computePolygonPerimeterM,
  formatArea,
  formatCoordinates,
  formatDistance,
  haversineDistanceM,
} from '../../core/geo';
import {
  CATEGORY_LABELS,
  ENTRY_DIFFICULTY_LABELS,
  formatShortDate,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
} from '../../core/labels';
import type {
  CoordinateFormat,
  GpsFix,
  ObjectsViewMode,
  ReliabilityStatus,
  UrbexObject,
} from '../../core/types';
import { ObjectImage } from '../../components/ObjectImage';

interface ObjectsViewProps {
  objects: UrbexObject[];
  gpsFix: GpsFix;
  coordFormat: CoordinateFormat;
  pageSize: number;
  defaultViewMode?: ObjectsViewMode;
  selectedObjectId: string | null;
  onSelectObject: (id: string | null) => void;
  onCreateNew: () => void;
  onEditObject: (obj: UrbexObject) => void;
  onEditZoneOnMap: (obj: UrbexObject) => void;
  onDeleteObject: (id: string) => Promise<void>;
  onUpdateStatus: (obj: UrbexObject, status: ReliabilityStatus) => Promise<void>;
}

const STATUS_FILTERS: Array<{ id: 'all' | ReliabilityStatus; label: string }> = [
  { id: 'all', label: 'Все' },
  { id: 'verified', label: 'Подтверждён' },
  { id: 'unverified', label: 'Требует проверки' },
  { id: 'contested', label: 'Спорный' },
  { id: 'hazardous', label: 'Опасен' },
];

const VIEW_MODES: Array<{ id: ObjectsViewMode; label: string; icon: typeof List }> = [
  { id: 'list', label: 'Список', icon: List },
  { id: 'compact', label: 'Компакт', icon: LayoutList },
  { id: 'cards', label: 'Карточки', icon: LayoutGrid },
];

export default function ObjectsView({
  objects,
  gpsFix,
  coordFormat,
  pageSize,
  defaultViewMode = 'cards',
  selectedObjectId,
  onSelectObject,
  onCreateNew,
  onEditObject,
  onEditZoneOnMap,
  onDeleteObject,
  onUpdateStatus,
}: ObjectsViewProps) {
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | ReliabilityStatus>('all');
  const [viewMode, setViewMode] = useState<ObjectsViewMode>(defaultViewMode);
  const [visibleLimit, setVisibleLimit] = useState(pageSize);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return objects.filter((obj) => {
      if (statusFilter !== 'all' && obj.status !== statusFilter) return false;
      if (!q) return true;
      return (
        obj.title.toLowerCase().includes(q) ||
        obj.description.toLowerCase().includes(q) ||
        obj.accessNotes.toLowerCase().includes(q)
      );
    });
  }, [objects, query, statusFilter]);

  const paginated = useMemo(
    () => filtered.slice(0, visibleLimit),
    [filtered, visibleLimit]
  );

  const activeObj = useMemo(
    () => (selectedObjectId ? objects.find((o) => o.id === selectedObjectId) ?? null : null),
    [objects, selectedObjectId]
  );

  return activeObj ? (
        /* Full-Area Object Detail View */
        <motion.section
          key={`detail-${activeObj.id}`}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.14, ease: 'easeOut' }}
          aria-label="Карточка объекта на всю площадь"
          className="bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-3.5 sm:space-y-4"
        >
          {/* Top Action Bar */}
          <div className="flex flex-wrap items-center justify-between gap-2 pb-2.5 sm:pb-3.5 border-b border-[#252E2A]">
            <button
              type="button"
              onClick={() => {
                onSelectObject(null);
                setConfirmDeleteId(null);
              }}
              className="min-h-[34px] lg:min-h-[40px] px-2.5 sm:px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1.5 whitespace-nowrap"
            >
              <ArrowLeft className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              <span>К списку</span>
            </button>

            <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
              <button
                type="button"
                onClick={() => onEditObject(activeObj)}
                aria-label={`Редактировать объект ${activeObj.title}`}
                className="min-h-[34px] lg:min-h-[40px] px-2.5 sm:px-3.5 py-1.5 rounded-lg border border-[#252E2A] bg-[#0C0F0E] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1.5 whitespace-nowrap"
              >
                <Edit3 className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                <span>Редактировать</span>
              </button>
              <button
                type="button"
                onClick={() => setConfirmDeleteId(activeObj.id)}
                aria-label={`Удалить объект ${activeObj.title}`}
                className="min-h-[34px] lg:min-h-[40px] px-2.5 sm:px-3.5 py-1.5 rounded-lg border border-[#252E2A] bg-[#0C0F0E] text-xs font-medium text-[#98A49E] hover:text-[#DC6860] hover:bg-[#381B19]/50 flex items-center gap-1.5 whitespace-nowrap"
              >
                <Trash2 className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                <span>Удалить</span>
              </button>
            </div>
          </div>

          <AnimatePresence>
            {confirmDeleteId === activeObj.id && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.16 }}
                className="overflow-hidden"
              >
                <div className="p-3.5 rounded-lg bg-[#381B19] border border-[#C7564E]/60 flex items-center justify-between gap-3">
                  <span className="text-xs text-[#F1ACA7]">
                    Удалить объект из локальной базы?
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setConfirmDeleteId(null)}
                      className="min-h-[36px] px-3 py-1 rounded text-xs text-[#E6EBE8] bg-[#1A201E]"
                    >
                      Отмена
                    </button>
                    <button
                      type="button"
                      onClick={async () => {
                        await onDeleteObject(activeObj.id);
                        setConfirmDeleteId(null);
                        onSelectObject(null);
                      }}
                      className="min-h-[36px] px-3 py-1 rounded text-xs font-semibold bg-[#C7564E] text-[#0C0F0E]"
                    >
                      Удалить
                    </button>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Header Info */}
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className={STATUS_META[activeObj.status].textClass}>
                {STATUS_META[activeObj.status].glyph} {STATUS_META[activeObj.status].label}
              </span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E]">{CATEGORY_LABELS[activeObj.category]}</span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E] font-mono tabular-nums">
                Обновлён {formatShortDate(activeObj.updatedAt)}
              </span>
            </div>
            <h2 className="text-lg sm:text-xl font-semibold text-[#E6EBE8] leading-snug">
              {activeObj.title}
            </h2>
          </div>

          {/* Full-Area Hero Photo + Telemetry Grid */}
          <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1.15fr_1fr] gap-3.5 sm:gap-4">
            <ObjectImage
              src={activeObj.photoUrl}
              alt={activeObj.title}
              className="w-full h-44 lg:h-72 rounded-xl border border-[#252E2A]"
            />

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-2.5 p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] text-xs">
                <div>
                  <div className="text-[#6B7771] mb-0.5">Координаты</div>
                  <div className="font-mono tabular-nums text-[#E6EBE8] flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-[#D39239] shrink-0" />
                    <span className="break-all">
                      {formatCoordinates(activeObj.lat, activeObj.lon, coordFormat)}
                    </span>
                  </div>
                </div>
                <div>
                  <div className="text-[#6B7771] mb-0.5">Дистанция и азимут</div>
                  <div className="font-mono tabular-nums text-[#E6EBE8]">
                    {formatDistance(
                      haversineDistanceM(gpsFix.lat, gpsFix.lon, activeObj.lat, activeObj.lon)
                    )}{' '}
                    ·{' '}
                    {Math.round(
                      bearingDeg(gpsFix.lat, gpsFix.lon, activeObj.lat, activeObj.lon)
                    )}
                    °
                  </div>
                </div>
                <div>
                  <div className="text-[#6B7771] mb-0.5">Высота</div>
                  <div className="font-mono tabular-nums text-[#E6EBE8]">
                    {activeObj.elevationM} м
                  </div>
                </div>
                <div>
                  <div className="text-[#6B7771] mb-0.5">Узел автора</div>
                  <div className="font-mono tabular-nums text-[#E6EBE8]">
                    {activeObj.authorNodeId}
                  </div>
                </div>
              </div>

              <div className="space-y-1.5">
                <div className="text-xs font-medium text-[#98A49E]">
                  Статус достоверности
                </div>
                <div className="grid grid-cols-2 gap-1.5">
                  {(
                    ['verified', 'unverified', 'contested', 'hazardous'] as ReliabilityStatus[]
                  ).map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => onUpdateStatus(activeObj, st)}
                      className={`min-h-[36px] lg:min-h-[40px] px-2.5 py-1 rounded-lg border text-xs font-medium whitespace-nowrap transition-colors ${
                        activeObj.status === st
                          ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                          : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                      }`}
                    >
                      <span style={{ color: STATUS_META[st].colorHex }} className="mr-1">
                        {STATUS_META[st].glyph}
                      </span>
                      {STATUS_META[st].label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Full Narrative Sections */}
          <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-3.5 pt-2 border-t border-[#252E2A]">
            <div className="space-y-1.5">
              <h3 className="text-xs font-medium text-[#98A49E]">
                Описание и состояние конструкций
              </h3>
              <p className="text-sm text-[#E6EBE8] leading-relaxed">
                {activeObj.description}
              </p>
            </div>

            <div className="space-y-3">
              <div className="space-y-1.5">
                <h3 className="text-xs font-medium text-[#98A49E]">Подход и периметр</h3>
                <p className="text-sm text-[#98A49E] leading-relaxed">
                  {activeObj.accessNotes}
                </p>
              </div>

              {activeObj.hazards.length > 0 && (
                <div className="space-y-1">
                  <h3 className="text-xs font-medium text-[#DF9E43]">Факторы риска</h3>
                  <p className="text-sm text-[#E6EBE8]">
                    {activeObj.hazards.join(' · ')}
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Extended Field Parameters & Map Zone Card */}
          <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-3.5 pt-2 border-t border-[#252E2A]">
            <div className="p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2.5">
              <h3 className="text-xs font-semibold text-[#98A49E]">Тактические параметры</h3>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                <div>
                  <div className="text-[#6B7771] mb-0.5">Охрана</div>
                  <div className="text-[#E6EBE8] font-medium">
                    {SECURITY_LEVEL_LABELS[activeObj.securityLevel ?? 'none']}
                  </div>
                </div>
                <div>
                  <div className="text-[#6B7771] mb-0.5">Сложность доступа</div>
                  <div className="text-[#E6EBE8] font-medium">
                    {ENTRY_DIFFICULTY_LABELS[activeObj.entryDifficulty ?? 'moderate']}
                  </div>
                </div>
                {activeObj.floorsOrDepth && (
                  <div>
                    <div className="text-[#6B7771] mb-0.5">Этажи / глубина</div>
                    <div className="text-[#E6EBE8] font-medium">{activeObj.floorsOrDepth}</div>
                  </div>
                )}
              </div>

              {activeObj.customParams && activeObj.customParams.length > 0 && (
                <div className="pt-2 border-t border-[#252E2A] grid grid-cols-2 gap-2 text-xs">
                  {activeObj.customParams.map((p, idx) => (
                    <div key={`${p.key}-${idx}`} className="bg-[#131816] border border-[#252E2A] rounded-lg px-2.5 py-1.5">
                      <span className="text-[#6B7771]">{p.key}: </span>
                      <span className="font-mono text-[#E6EBE8]">{p.value}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="p-4 rounded-xl bg-[#0C0F0E] border border-[#252E2A] flex flex-col justify-between gap-3">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <Pentagon className="w-4 h-4 text-[#6DB386]" />
                  <h3 className="text-xs font-semibold text-[#E6EBE8]">Зона объекта на карте</h3>
                </div>
                {activeObj.zone && activeObj.zone.length >= 3 ? (
                  <p className="text-xs font-mono tabular-nums text-[#98A49E]">
                    Точек: {activeObj.zone.length} · Площадь:{' '}
                    <span className="text-[#E6EBE8]">
                      {formatArea(computePolygonAreaM2(activeObj.zone))}
                    </span>{' '}
                    · Периметр:{' '}
                    <span className="text-[#E6EBE8]">
                      {formatDistance(computePolygonPerimeterM(activeObj.zone))}
                    </span>
                  </p>
                ) : (
                  <p className="text-xs text-[#98A49E]">
                    Периметр не размечен. Вы можете расставить точки или обвести зону прямо на карте.
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={() => onEditZoneOnMap(activeObj)}
                className="min-h-[42px] px-4 py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-semibold text-[#E6EBE8] hover:bg-[#274032] transition-colors flex items-center justify-center gap-2"
              >
                <Pentagon className="w-4 h-4 text-[#6DB386]" />
                <span>
                  {activeObj.zone && activeObj.zone.length >= 3
                    ? 'Изменить зону на карте'
                    : 'Задать зону на карте'}
                </span>
              </button>
            </div>
          </div>
        </motion.section>
      ) : (
        /* Full-Area Multi-View Browser */
        <div className="space-y-2.5 sm:space-y-3.5">
          {/* Search, View Mode Switcher, Create CTA & Status Filter */}
          <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-2 sm:p-2.5 space-y-2">
            <div className="flex flex-col [@media(orientation:landscape)_and_(min-width:560px)]:flex-row [@media(orientation:landscape)_and_(min-width:560px)]:items-center gap-2">
              <div className="relative flex-1 min-w-0">
                <Search className="w-4 h-4 text-[#6B7771] absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  aria-label="Поиск объектов по названию и заметкам"
                  placeholder="Поиск по названию, описанию, подходу..."
                  className="w-full h-9 lg:h-10 pl-8 pr-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                />
              </div>

              <div className="flex items-center justify-between sm:justify-end gap-1.5 sm:gap-2 shrink-0">
                {/* 3-Mode View Selector: Список / Компакт / Карточки */}
                <div
                  role="group"
                  aria-label="Режим отображения объектов"
                  className="flex items-center gap-0.5 bg-[#0C0F0E] p-0.5 rounded-lg border border-[#252E2A]"
                >
                  {VIEW_MODES.map((m) => {
                    const Icon = m.icon;
                    const active = viewMode === m.id;
                    return (
                      <motion.button
                        key={m.id}
                        whileTap={{ scale: 0.96 }}
                        type="button"
                        onClick={() => setViewMode(m.id)}
                        aria-pressed={active}
                        className={`relative min-h-[30px] lg:min-h-[34px] px-2 lg:px-2.5 py-1 rounded-md text-[11px] sm:text-xs font-medium whitespace-nowrap flex items-center gap-1 transition-colors ${
                          active
                            ? 'text-[#E6EBE8]'
                            : 'text-[#98A49E] hover:text-[#E6EBE8]'
                        }`}
                      >
                        {active && (
                          <motion.span
                            layoutId="objects-view-mode"
                            transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                            className="absolute inset-0 rounded-md bg-[#1A201E] border border-[#5B9D72]"
                          />
                        )}
                        <Icon className="relative z-10 w-3.5 h-3.5 shrink-0" />
                        <span className="relative z-10">{m.label}</span>
                      </motion.button>
                    );
                  })}
                </div>

                <motion.button
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.96 }}
                  type="button"
                  onClick={onCreateNew}
                  className="min-h-[36px] lg:min-h-[40px] px-3 lg:px-4 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] transition-colors flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
                >
                  <Plus className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                  <span className="lg:hidden">Объект</span>
                  <span className="hidden lg:inline">Добавить объект</span>
                </motion.button>
              </div>
            </div>

            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-1 bg-[#0C0F0E] p-0.5 rounded-lg border border-[#252E2A] overflow-x-auto max-w-full">
                {STATUS_FILTERS.map((f) => {
                  const active = statusFilter === f.id;
                  return (
                    <motion.button
                      key={f.id}
                      whileTap={{ scale: 0.96 }}
                      type="button"
                      onClick={() => setStatusFilter(f.id)}
                      className={`relative min-h-[28px] lg:min-h-[32px] px-2.5 py-0.5 rounded-md text-[11px] sm:text-xs font-medium whitespace-nowrap shrink-0 transition-colors ${
                        active
                          ? 'text-[#E6EBE8]'
                          : 'text-[#98A49E] hover:text-[#E6EBE8]'
                      }`}
                    >
                      {active && (
                        <motion.span
                          layoutId="objects-status-filter"
                          transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                          className="absolute inset-0 rounded-md bg-[#1A201E] border border-[#323E39]"
                        />
                      )}
                      <span className="relative z-10">{f.label}</span>
                    </motion.button>
                  );
                })}
              </div>
              <span className="text-[11px] font-mono tabular-nums text-[#6B7771] shrink-0 px-1">
                {filtered.length}
              </span>
            </div>
          </div>

          {/* Objects Collection */}
          {paginated.length === 0 ? (
            <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-10 text-center text-sm text-[#98A49E]">
              Объекты по заданному фильтру не найдены.
            </div>
          ) : viewMode === 'list' ? (
            /* Mode 1: High-Density Operational List (2-column grid in landscape) */
            <motion.div
              key="view-list"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.14, ease: 'easeOut' }}
              className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2"
            >
              {paginated.map((obj) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                return (
                  <button
                    key={obj.id}
                    type="button"
                    onClick={() => {
                      onSelectObject(obj.id);
                      setConfirmDeleteId(null);
                    }}
                    className="w-full text-left flex items-center gap-2.5 px-3 py-2 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] hover:bg-[#1A201E] active:scale-[0.99] transition-all"
                  >
                    <ObjectImage
                      src={obj.photoUrl}
                      alt={obj.title}
                      compact
                      className="w-12 h-10 rounded-md shrink-0 border border-[#252E2A]"
                    />
                    <div className="min-w-0 flex-1 space-y-0.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                          {obj.title}
                        </span>
                        <span className="font-mono tabular-nums text-[11px] text-[#E6EBE8] shrink-0">
                          {formatDistance(dist)}
                        </span>
                      </div>
                      <div className="flex items-center justify-between gap-2 text-[11px]">
                        <span className="font-mono tabular-nums text-[#98A49E] truncate">
                          {formatCoordinates(obj.lat, obj.lon, coordFormat)}
                        </span>
                        <span className={`${statusMeta.textClass} shrink-0 whitespace-nowrap`}>
                          {statusMeta.glyph} {statusMeta.label}
                        </span>
                      </div>
                    </div>
                  </button>
                );
              })}
            </motion.div>
          ) : viewMode === 'compact' ? (
            /* Mode 2: Hybrid Medium Compact Grid ("что-то между") */
            <motion.div
              key="view-compact"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.14, ease: 'easeOut' }}
              className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5"
            >
              {paginated.map((obj) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                return (
                  <button
                    key={obj.id}
                    type="button"
                    onClick={() => {
                      onSelectObject(obj.id);
                      setConfirmDeleteId(null);
                    }}
                    className="w-full text-left flex gap-2.5 sm:gap-3 p-2.5 sm:p-3 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] hover:-translate-y-0.5 active:scale-[0.985] transition-all"
                  >
                    <ObjectImage
                      src={obj.photoUrl}
                      alt={obj.title}
                      compact
                      className="w-16 h-16 lg:w-20 lg:h-20 rounded-lg shrink-0 border border-[#252E2A]"
                    />
                    <div className="min-w-0 flex-1 flex flex-col justify-between gap-1">
                      <div>
                        <div className="flex items-center justify-between gap-2 text-[11px] sm:text-xs">
                          <span className={statusMeta.textClass}>
                            {statusMeta.glyph} {statusMeta.label}
                          </span>
                          <span className="font-mono tabular-nums text-[#E6EBE8]">
                            {formatDistance(dist)}
                          </span>
                        </div>
                        <h3 className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate mt-0.5">
                          {obj.title}
                        </h3>
                        <p className="text-[11px] sm:text-xs text-[#98A49E] line-clamp-1 mt-0.5">
                          {obj.description}
                        </p>
                      </div>
                      <div className="flex items-center justify-between gap-2 text-[10px] sm:text-[11px] font-mono tabular-nums text-[#6B7771]">
                        <span className="truncate">
                          {formatCoordinates(obj.lat, obj.lon, coordFormat)}
                        </span>
                        <span className="shrink-0">{CATEGORY_LABELS[obj.category]}</span>
                      </div>
                    </div>
                  </button>
                );
              })}
            </motion.div>
          ) : (
            /* Mode 3: Full Visual Cards Grid */
            <motion.div
              key="view-cards"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.14, ease: 'easeOut' }}
              className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 xl:grid-cols-3 gap-3"
            >
              {paginated.map((obj) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                return (
                  <button
                    key={obj.id}
                    type="button"
                    onClick={() => {
                      onSelectObject(obj.id);
                      setConfirmDeleteId(null);
                    }}
                    className="w-full text-left flex flex-col rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] hover:-translate-y-0.5 active:scale-[0.985] transition-all overflow-hidden"
                  >
                    <ObjectImage
                      src={obj.photoUrl}
                      alt={obj.title}
                      className="w-full h-28 lg:h-40 border-b border-[#252E2A]"
                    />
                    <div className="p-3 lg:p-4 flex-1 flex flex-col justify-between gap-2">
                      <div className="space-y-1">
                        <div className="flex items-center justify-between gap-2 text-[11px] sm:text-xs">
                          <span className={statusMeta.textClass}>
                            {statusMeta.glyph} {statusMeta.label}
                          </span>
                          <span className="text-[#98A49E]">
                            {CATEGORY_LABELS[obj.category]}
                          </span>
                        </div>
                        <h3 className="text-sm sm:text-base font-semibold text-[#E6EBE8] leading-snug line-clamp-1">
                          {obj.title}
                        </h3>
                        <p className="text-xs text-[#98A49E] line-clamp-2 leading-relaxed">
                          {obj.description}
                        </p>
                      </div>

                      <div className="pt-2 border-t border-[#252E2A] flex items-center justify-between gap-2 text-[11px] font-mono tabular-nums">
                        <span className="text-[#98A49E] truncate">
                          {formatCoordinates(obj.lat, obj.lon, coordFormat)}
                        </span>
                        <span className="text-[#E6EBE8] shrink-0">
                          {formatDistance(dist)}
                        </span>
                      </div>
                    </div>
                  </button>
                );
              })}
            </motion.div>
          )}

          {filtered.length > paginated.length && (
            <button
              type="button"
              onClick={() => setVisibleLimit((v) => v + pageSize)}
              className="w-full min-h-[44px] py-2 rounded-lg border border-[#252E2A] bg-[#131816] text-xs font-medium text-[#98A49E] hover:text-[#E6EBE8]"
            >
              Показать ещё ({filtered.length - paginated.length})
            </button>
          )}
        </div>
  );
}
