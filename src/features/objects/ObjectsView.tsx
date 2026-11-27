import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowLeft,
  Camera,
  Edit3,
  LayoutGrid,
  LayoutList,
  List,
  MapPin,
  Move,
  Pentagon,
  Plus,
  Search,
  Trash2,
  ZoomIn,
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
import { tileEnterMotion } from '../../core/motion';
import {
  CATEGORY_LABELS,
  ENTRY_DIFFICULTY_LABELS,
  formatShortDate,
  getEffectiveObjectIcon,
  getEffectiveZoneColor,
  getEffectiveZonePattern,
  getObjectPhotos,
  normalizeZoneStyle,
  OBJECT_ICON_META,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
  ZONE_PATTERN_LABELS,
  ZONE_STYLE_LABELS,
} from '../../core/labels';
import type {
  CoordinateFormat,
  DbItemSource,
  GpsFix,
  ObjectsViewMode,
  ReliabilityStatus,
  UrbexObject,
} from '../../core/types';
import { ObjectGlyph } from '../../components/ObjectGlyph';
import { ObjectImage } from '../../components/ObjectImage';
import { PhotoZoomModal } from '../../components/PhotoZoomModal';
import { UrbexSelect } from '../../components/UrbexSelect';

interface ObjectsViewProps {
  objects: UrbexObject[];
  objectSources?: Record<string, DbItemSource>;
  mountedCount?: number;
  gpsFix: GpsFix;
  coordFormat: CoordinateFormat;
  maskCoordinatesOnScreen?: boolean;
  pageSize: number;
  defaultViewMode?: ObjectsViewMode;
  editorEnabled?: boolean;
  confirmBeforeDelete?: boolean;
  selectedObjectId: string | null;
  onSelectObject: (id: string | null) => void;
  onFocusObjectOnMap?: (obj: UrbexObject) => void;
  onCreateNew: () => void;
  onEditObject: (obj: UrbexObject) => void;
  onEditZoneOnMap: (obj: UrbexObject) => void;
  onMovePointOnMap?: (obj: UrbexObject) => void;
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
  objectSources,
  mountedCount = 1,
  gpsFix,
  coordFormat,
  maskCoordinatesOnScreen = false,
  pageSize,
  defaultViewMode = 'cards',
  editorEnabled = false,
  confirmBeforeDelete = true,
  selectedObjectId,
  onSelectObject,
  onFocusObjectOnMap,
  onCreateNew,
  onEditObject,
  onEditZoneOnMap,
  onMovePointOnMap,
  onDeleteObject,
  onUpdateStatus,
}: ObjectsViewProps) {
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | ReliabilityStatus>('all');
  const [viewMode, setViewMode] = useState<ObjectsViewMode>(defaultViewMode);
  const [visibleLimit, setVisibleLimit] = useState(pageSize);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);
  const [activePhotoIndex, setActivePhotoIndex] = useState(0);
  const [zoomGallery, setZoomGallery] = useState<{
    photos: string[];
    initialIndex: number;
    title: string;
  } | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return objects.filter((obj) => {
      if (statusFilter !== 'all' && obj.status !== statusFilter) return false;
      if (!q) return true;
      return (
        obj.title.toLowerCase().includes(q) ||
        (obj.subtype?.toLowerCase().includes(q) ?? false) ||
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

  const activePhotos = useMemo(() => getObjectPhotos(activeObj), [activeObj]);

  useEffect(() => {
    setActivePhotoIndex(0);
  }, [activeObj?.id]);

  const clampedPhotoIdx =
    activePhotos.length > 0 ? Math.min(activePhotoIndex, activePhotos.length - 1) : 0;

  const activeSource = activeObj && objectSources ? objectSources[activeObj.id] : undefined;

  return (
    <>
      <AnimatePresence mode="wait" initial={false}>
      {activeObj ? (
        /* Full-Area Object Detail View */
        <motion.section
          key={`detail-${activeObj.id}`}
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: 14 }}
          transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
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

            {editorEnabled && (
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
                  onClick={async () => {
                    if (!confirmBeforeDelete) {
                      await onDeleteObject(activeObj.id);
                      onSelectObject(null);
                      return;
                    }
                    setConfirmDeleteId(activeObj.id);
                  }}
                  aria-label={`Удалить объект ${activeObj.title}`}
                  className="min-h-[34px] lg:min-h-[40px] px-2.5 sm:px-3.5 py-1.5 rounded-lg border border-[#252E2A] bg-[#0C0F0E] text-xs font-medium text-[#98A49E] hover:text-[#DC6860] hover:bg-[#381B19]/50 flex items-center gap-1.5 whitespace-nowrap"
                >
                  <Trash2 className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                  <span>Удалить</span>
                </button>
              </div>
            )}
          </div>

          <AnimatePresence>
            {editorEnabled && confirmDeleteId === activeObj.id && (
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
              <span className="inline-flex items-center gap-1.5">
                <ObjectGlyph
                  icon={getEffectiveObjectIcon(activeObj)}
                  color={STATUS_META[activeObj.status].colorHex}
                  className="w-3.5 h-3.5"
                />
                <span className={STATUS_META[activeObj.status].textClass}>
                  {STATUS_META[activeObj.status].label}
                </span>
              </span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E]">
                {CATEGORY_LABELS[activeObj.category]}
                {activeObj.subtype ? ` · ${activeObj.subtype}` : ''}
              </span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E]">{OBJECT_ICON_META[getEffectiveObjectIcon(activeObj)].label}</span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E] font-mono tabular-nums">
                Обновлён {formatShortDate(activeObj.updatedAt)}
              </span>
              {mountedCount > 1 && activeSource && (
                <>
                  <span className="text-[#6B7771]" aria-hidden="true">·</span>
                  <span className="px-2 py-0.5 rounded bg-[#0C0F0E] border border-[#252E2A] text-[11px] text-[#6DB386]">
                    БД: {activeSource.dbName}
                  </span>
                </>
              )}
            </div>
            <h2 className="text-lg sm:text-xl font-semibold text-[#E6EBE8] leading-snug">
              {activeObj.title}
            </h2>
          </div>

          {/* Full-Area Hero Photo Gallery + Telemetry Grid */}
          <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1.15fr_1fr] gap-3.5 sm:gap-4">
            <div className="space-y-2">
              <div className="relative group">
                <ObjectImage
                  src={activePhotos[clampedPhotoIdx]}
                  alt={activeObj.title}
                  fallbackIcon={getEffectiveObjectIcon(activeObj)}
                  className="w-full h-44 lg:h-72 rounded-xl border border-[#252E2A]"
                />
                {activePhotos.length > 1 && (
                  <span className="pointer-events-none absolute top-2.5 left-2.5 px-2 py-0.5 rounded-md bg-[#0C0F0E]/85 border border-[#323E39] font-mono tabular-nums text-[11px] text-[#E6EBE8] backdrop-blur-sm">
                    {clampedPhotoIdx + 1} / {activePhotos.length}
                  </span>
                )}
                {activePhotos.length > 0 && (
                  <button
                    type="button"
                    onClick={() =>
                      setZoomGallery({
                        photos: activePhotos,
                        initialIndex: clampedPhotoIdx,
                        title: activeObj.title,
                      })
                    }
                    aria-label={`Приблизить фото ${activeObj.title}`}
                    className="absolute bottom-2.5 right-2.5 min-h-[34px] px-2.5 py-1 rounded-lg bg-[#0C0F0E]/85 border border-[#323E39] hover:border-[#5B9D72] text-xs font-medium text-[#E6EBE8] flex items-center gap-1.5 backdrop-blur-sm transition-colors"
                  >
                    <ZoomIn className="w-3.5 h-3.5 text-[#6DB386]" />
                    <span>Приблизить</span>
                  </button>
                )}
              </div>

              {activePhotos.length > 1 && (
                <div
                  className="flex items-center gap-2 overflow-x-auto pb-0.5"
                  aria-label="Галерея фотографий объекта"
                >
                  {activePhotos.map((src, idx) => (
                    <button
                      key={`${idx}-${src.slice(0, 24)}`}
                      type="button"
                      onClick={() => setActivePhotoIndex(idx)}
                      aria-label={`Показать фото ${idx + 1} из ${activePhotos.length}`}
                      aria-pressed={idx === clampedPhotoIdx}
                      className={`w-16 h-12 rounded-lg overflow-hidden border-2 shrink-0 transition-all ${
                        idx === clampedPhotoIdx
                          ? 'border-[#5B9D72]'
                          : 'border-[#252E2A] opacity-70 hover:opacity-100'
                      }`}
                    >
                      <img
                        src={src}
                        alt={`${activeObj.title} — фото ${idx + 1}`}
                        referrerPolicy="no-referrer"
                        className="w-full h-full object-cover"
                      />
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-2.5 p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] text-xs">
                <div>
                  <div className="text-[#6B7771] mb-0.5">Координаты (на карту)</div>
                  <div className="space-y-1.5">
                    <button
                      type="button"
                      onClick={() => onFocusObjectOnMap?.(activeObj)}
                      title="Показать объект на карте"
                      aria-label={`Перейти на карту к координатам ${activeObj.title}`}
                      className="w-full text-left font-mono tabular-nums text-[#E6EBE8] hover:text-[#6DB386] flex items-center gap-1 rounded-md px-1.5 py-1 -mx-1.5 -my-1 hover:bg-[#131816] border border-transparent hover:border-[#5B9D72]/50 transition-colors cursor-pointer"
                    >
                      <MapPin className="w-3.5 h-3.5 text-[#D39239] shrink-0" />
                      <span className="break-all underline decoration-[#5B9D72]/50 underline-offset-2">
                        {formatCoordinates(activeObj.lat, activeObj.lon, coordFormat, maskCoordinatesOnScreen)}
                      </span>
                    </button>
                    {editorEnabled && onMovePointOnMap && (
                      <button
                        type="button"
                        onClick={() => onMovePointOnMap(activeObj)}
                        className="min-h-[30px] px-2.5 py-1 rounded-lg bg-[#203328] border border-[#5B9D72] text-[11px] font-medium text-[#E6EBE8] hover:bg-[#274032] inline-flex items-center gap-1.5"
                      >
                        <Move className="w-3 h-3 text-[#6DB386]" />
                        <span>Двигать точку</span>
                      </button>
                    )}
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
                {editorEnabled ? (
                  <UrbexSelect
                    label="Статус достоверности"
                    value={activeObj.status}
                    onChange={(st) => void onUpdateStatus(activeObj, st)}
                    options={(
                      ['verified', 'unverified', 'contested', 'hazardous'] as ReliabilityStatus[]
                    ).map((st) => ({
                      value: st,
                      label: STATUS_META[st].label,
                      dotColor: STATUS_META[st].colorHex,
                    }))}
                  />
                ) : (
                  <>
                    <div className="text-xs font-medium text-[#98A49E]">
                      Статус достоверности
                    </div>
                    <div className="px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] inline-flex items-center gap-1.5">
                      <span style={{ color: STATUS_META[activeObj.status].colorHex }}>
                        {STATUS_META[activeObj.status].glyph}
                      </span>
                      <span>{STATUS_META[activeObj.status].label}</span>
                    </div>
                  </>
                )}
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
              <div className="space-y-1.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Pentagon className="w-4 h-4 text-[#6DB386]" />
                    <h3 className="text-xs font-semibold text-[#E6EBE8]">Зона объекта на карте</h3>
                  </div>
                  {activeObj.zone && activeObj.zone.length >= 3 && (() => {
                    const effPattern = getEffectiveZonePattern(activeObj);
                    return (
                      <span
                        className="inline-flex items-center gap-1.5 text-[11px] font-mono text-[#98A49E]"
                        title="Цвет и стиль зоны"
                      >
                        <span
                          className="w-3 h-3 rounded-full border border-[#323E39]"
                          style={{ backgroundColor: getEffectiveZoneColor(activeObj) }}
                        />
                        <span>{ZONE_STYLE_LABELS[normalizeZoneStyle(activeObj.zoneStyle)]}</span>
                        {effPattern !== 'none' && (
                          <span className="text-[#6DB386]">· {ZONE_PATTERN_LABELS[effPattern]}</span>
                        )}
                      </span>
                    );
                  })()}
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
                    Периметр не размечен.
                  </p>
                )}
              </div>

              {editorEnabled && (
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
              )}
            </div>
          </div>
        </motion.section>
      ) : (
        /* Full-Area Multi-View Browser */
        <motion.div
          key="objects-browser"
          initial={{ opacity: 0, x: -16 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -12 }}
          transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
          className="space-y-2.5 sm:space-y-3.5"
        >
          {/* Search, View Mode Switcher, Create CTA & Status Filter */}
          <motion.div
            {...tileEnterMotion(0, 8)}
            className="bg-[#131816] border border-[#252E2A] rounded-xl p-2 sm:p-2.5 space-y-2"
          >
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

                {editorEnabled && (
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
                )}
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
          </motion.div>

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
              {paginated.map((obj, idx) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                const photos = getObjectPhotos(obj);
                return (
                  <motion.article
                    key={obj.id}
                    {...tileEnterMotion(idx + 1)}
                    className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] hover:bg-[#1A201E] transition-colors"
                  >
                    <button
                      type="button"
                      onClick={() => {
                        onSelectObject(obj.id);
                        setConfirmDeleteId(null);
                      }}
                      aria-label={`Открыть объект ${obj.title}`}
                      className="relative shrink-0 rounded-md cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72]"
                    >
                      <ObjectImage
                        src={photos[0]}
                        alt={obj.title}
                        fallbackIcon={getEffectiveObjectIcon(obj)}
                        compact
                        className="w-12 h-10 rounded-md border border-[#252E2A]"
                      />
                      {photos.length > 1 && (
                        <span className="absolute bottom-0.5 right-0.5 px-1 rounded bg-[#0C0F0E]/85 text-[9px] font-mono text-[#6DB386]">
                          {photos.length}
                        </span>
                      )}
                    </button>
                    <div className="min-w-0 flex-1 space-y-0.5">
                      <button
                        type="button"
                        onClick={() => {
                          onSelectObject(obj.id);
                          setConfirmDeleteId(null);
                        }}
                        className="w-full flex items-center justify-between gap-2 text-left cursor-pointer"
                      >
                        <span className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                          {obj.title}
                        </span>
                        <span className="font-mono tabular-nums text-[11px] text-[#E6EBE8] shrink-0">
                          {formatDistance(dist)}
                        </span>
                      </button>
                      <div className="flex items-center justify-between gap-2 text-[11px]">
                        <button
                          type="button"
                          onClick={() => onFocusObjectOnMap?.(obj)}
                          title="Показать на карте"
                          className="font-mono tabular-nums text-[#98A49E] hover:text-[#6DB386] flex items-center gap-1 truncate cursor-pointer"
                        >
                          <MapPin className="w-3 h-3 text-[#D39239] shrink-0" />
                          <span className="truncate">
                            {formatCoordinates(obj.lat, obj.lon, coordFormat, maskCoordinatesOnScreen)}
                          </span>
                        </button>
                        <span className={`${statusMeta.textClass} shrink-0 whitespace-nowrap`}>
                          {statusMeta.glyph} {statusMeta.label}
                        </span>
                      </div>
                    </div>
                  </motion.article>
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
              {paginated.map((obj, idx) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                const photos = getObjectPhotos(obj);
                return (
                  <motion.article
                    key={obj.id}
                    {...tileEnterMotion(idx + 1)}
                    whileHover={{ y: -2 }}
                    className="w-full flex gap-2.5 sm:gap-3 p-2.5 sm:p-3 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] transition-colors"
                  >
                    <button
                      type="button"
                      onClick={() => {
                        onSelectObject(obj.id);
                        setConfirmDeleteId(null);
                      }}
                      aria-label={`Открыть объект ${obj.title}`}
                      className="relative shrink-0 rounded-lg cursor-pointer"
                    >
                      <ObjectImage
                        src={photos[0]}
                        alt={obj.title}
                        fallbackIcon={getEffectiveObjectIcon(obj)}
                        compact
                        className="w-16 h-16 lg:w-20 lg:h-20 rounded-lg border border-[#252E2A]"
                      />
                      {photos.length > 1 && (
                        <span className="absolute bottom-1 right-1 px-1 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[9px] font-mono text-[#6DB386] flex items-center gap-0.5">
                          <Camera className="w-2.5 h-2.5" />
                          <span>{photos.length}</span>
                        </span>
                      )}
                    </button>
                    <div className="min-w-0 flex-1 flex flex-col justify-between gap-1">
                      <button
                        type="button"
                        onClick={() => {
                          onSelectObject(obj.id);
                          setConfirmDeleteId(null);
                        }}
                        className="w-full text-left cursor-pointer"
                      >
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
                      </button>
                      <div className="flex items-center justify-between gap-2 text-[10px] sm:text-[11px] font-mono tabular-nums text-[#6B7771]">
                        <button
                          type="button"
                          onClick={() => onFocusObjectOnMap?.(obj)}
                          title="Показать объект на карте"
                          className="text-[#98A49E] hover:text-[#6DB386] flex items-center gap-1 truncate cursor-pointer"
                        >
                          <MapPin className="w-3 h-3 text-[#D39239] shrink-0" />
                          <span className="truncate">
                            {formatCoordinates(obj.lat, obj.lon, coordFormat, maskCoordinatesOnScreen)}
                          </span>
                        </button>
                        <span className="shrink-0 truncate max-w-[46%]">
                          {CATEGORY_LABELS[obj.category]}
                          {obj.subtype ? ` · ${obj.subtype}` : ''}
                        </span>
                      </div>
                    </div>
                  </motion.article>
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
              {paginated.map((obj, idx) => {
                const statusMeta = STATUS_META[obj.status];
                const dist = haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon);
                const srcInfo = mountedCount > 1 && objectSources ? objectSources[obj.id] : undefined;
                const photos = getObjectPhotos(obj);
                return (
                  <motion.article
                    key={obj.id}
                    {...tileEnterMotion(idx + 1)}
                    whileHover={{ y: -2 }}
                    className="w-full text-left flex flex-col rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] transition-colors overflow-hidden"
                  >
                    <div className="relative">
                      <button
                        type="button"
                        onClick={() => {
                          onSelectObject(obj.id);
                          setConfirmDeleteId(null);
                        }}
                        aria-label={`Открыть объект ${obj.title}`}
                        className="block w-full text-left cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72]"
                      >
                        <ObjectImage
                          src={photos[0]}
                          alt={obj.title}
                          fallbackIcon={getEffectiveObjectIcon(obj)}
                          className="w-full h-28 lg:h-40 border-b border-[#252E2A]"
                        />
                      </button>
                      {srcInfo && (
                        <span className="pointer-events-none absolute top-2 left-2 px-1.5 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[10px] font-medium text-[#6DB386] max-w-[55%] truncate backdrop-blur-sm">
                          {srcInfo.dbName}
                        </span>
                      )}
                      {photos.length > 1 && (
                        <span className="pointer-events-none absolute bottom-2 left-2 px-1.5 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[10px] font-mono text-[#E6EBE8] flex items-center gap-1 backdrop-blur-sm">
                          <Camera className="w-3 h-3 text-[#6DB386]" />
                          <span>{photos.length}</span>
                        </span>
                      )}
                      {photos.length > 0 && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setZoomGallery({
                              photos,
                              initialIndex: 0,
                              title: obj.title,
                            });
                          }}
                          aria-label={`Приблизить фото ${obj.title}`}
                          title="Приблизить фото"
                          className="absolute top-2 right-2 min-h-[30px] min-w-[30px] px-2 rounded-lg bg-[#0C0F0E]/85 border border-[#323E39] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center gap-1 text-[11px] backdrop-blur-sm transition-colors"
                        >
                          <ZoomIn className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span className="hidden sm:inline">Зум</span>
                        </button>
                      )}
                    </div>
                    <div className="p-3 lg:p-4 flex-1 flex flex-col justify-between gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          onSelectObject(obj.id);
                          setConfirmDeleteId(null);
                        }}
                        className="space-y-1 w-full text-left cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72]"
                      >
                        <div className="flex items-center justify-between gap-2 text-[11px] sm:text-xs">
                          <span className={statusMeta.textClass}>
                            {statusMeta.glyph} {statusMeta.label}
                          </span>
                          <span className="text-[#98A49E] truncate max-w-[58%] text-right">
                            {CATEGORY_LABELS[obj.category]}
                            {obj.subtype ? ` · ${obj.subtype}` : ''}
                          </span>
                        </div>
                        <h3 className="text-sm sm:text-base font-semibold text-[#E6EBE8] leading-snug line-clamp-1">
                          {obj.title}
                        </h3>
                        <p className="text-xs text-[#98A49E] line-clamp-2 leading-relaxed">
                          {obj.description}
                        </p>
                      </button>

                      <div className="w-full pt-2 border-t border-[#252E2A] flex items-center justify-between gap-2 text-[11px] font-mono tabular-nums">
                        <button
                          type="button"
                          onClick={() => onFocusObjectOnMap?.(obj)}
                          title="Показать объект на карте"
                          className="text-[#98A49E] hover:text-[#6DB386] flex items-center gap-1 truncate cursor-pointer"
                        >
                          <MapPin className="w-3 h-3 text-[#D39239] shrink-0" />
                          <span className="truncate underline decoration-[#5B9D72]/40 underline-offset-2">
                            {formatCoordinates(obj.lat, obj.lon, coordFormat, maskCoordinatesOnScreen)}
                          </span>
                        </button>
                        <span className="text-[#E6EBE8] shrink-0">
                          {formatDistance(dist)}
                        </span>
                      </div>
                    </div>
                  </motion.article>
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
        </motion.div>
      )}
      </AnimatePresence>

      <PhotoZoomModal
        src={zoomGallery ? zoomGallery.photos[zoomGallery.initialIndex] ?? null : null}
        photos={zoomGallery?.photos}
        initialIndex={zoomGallery?.initialIndex ?? 0}
        title={zoomGallery?.title}
        onClose={() => setZoomGallery(null)}
      />
    </>
  );
}
