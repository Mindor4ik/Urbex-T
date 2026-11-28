import { useEffect, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  CheckCircle2,
  Crosshair,
  Download,
  Link2,
  MapPin,
  Move,
  Pentagon,
  Plus,
  Star,
  Trash2,
  X,
  ZoomIn,
} from 'lucide-react';
import {
  buildZoneCircle,
  computePolygonAreaM2,
  formatArea,
  isValidCoordinate,
  offsetCoordinate,
} from '../core/geo';
import {
  downloadImageUrlToDataUrl,
  estimateDataUrlBytes,
  isEmbeddedDataUrl,
  optimizeImageFileToDataUrl,
} from '../core/imageCache';
import {
  CATEGORY_DEFAULT_ICON,
  CATEGORY_LABELS,
  CATEGORY_SUBTYPES,
  ENTRY_DIFFICULTY_LABELS,
  formatBytes,
  getObjectPhotos,
  normalizeObjectIcon,
  normalizeObjectSubtype,
  normalizeZoneColor,
  normalizeZonePattern,
  normalizeZoneStyle,
  OBJECT_ICON_META,
  OBJECT_ICONS,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
  ZONE_COLOR_PRESETS,
  ZONE_PATTERN_LABELS,
  ZONE_PATTERN_ORDER,
  ZONE_STYLE_LABELS,
  ZONE_STYLE_ORDER,
} from '../core/labels';
import type {
  EntryDifficulty,
  GeoPoint,
  ObjectCategory,
  ObjectIconId,
  ObjectParam,
  ReliabilityStatus,
  SecurityLevel,
  UrbexObject,
  ZonePattern,
  ZoneStyle,
} from '../core/types';
import { ObjectGlyph } from './ObjectGlyph';
import { PhotoZoomModal } from './PhotoZoomModal';
import { UrbexSelect } from './UrbexSelect';
import { ZoneLinePreview, ZonePatternPreview } from './ZoneStylePreview';

export interface QuickObjectDraft {
  title: string;
  category: ObjectCategory;
  subtype: string;
  icon?: ObjectIconId;
  status: ReliabilityStatus;
  securityLevel: SecurityLevel;
  entryDifficulty: EntryDifficulty;
  floorsOrDepth: string;
  latStr: string;
  lonStr: string;
  elevationStr: string;
  description: string;
  accessNotes: string;
  hazardsStr: string;
  photos: string[];
  customParams: ObjectParam[];
  zone: GeoPoint[];
  zoneColor: string;
  zoneStyle: ZoneStyle;
  zonePattern: ZonePattern;
  zoneHatched: boolean;
  editingObject: UrbexObject | null;
}

interface QuickObjectModalProps {
  open: boolean;
  initialLat: number;
  initialLon: number;
  defaultZoneRadiusM?: number;
  editingObject?: UrbexObject | null;
  draftOverride?: QuickObjectDraft | null;
  authorNodeId: string;
  onClose: () => void;
  onSave: (obj: UrbexObject) => Promise<void>;
  onOpenZoneEditor?: (obj: UrbexObject) => Promise<void>;
  onOpenPointMover?: (draft: QuickObjectDraft) => void;
}

const CATEGORIES: ObjectCategory[] = [
  'industrial',
  'bunker',
  'civilian',
  'infrastructure',
  'underground',
  'military',
  'energy',
  'medical',
  'transport',
  'hydro',
  'mining',
  'ruins',
];

const STATUSES: ReliabilityStatus[] = [
  'verified',
  'unverified',
  'contested',
  'hazardous',
];

const SECURITY_LEVELS: SecurityLevel[] = ['none', 'low', 'medium', 'high'];
const ENTRY_DIFFICULTIES: EntryDifficulty[] = ['easy', 'moderate', 'hard', 'technical'];

export function QuickObjectModal({
  open,
  initialLat,
  initialLon,
  defaultZoneRadiusM = 80,
  editingObject,
  draftOverride = null,
  authorNodeId,
  onClose,
  onSave,
  onOpenZoneEditor,
  onOpenPointMover,
}: QuickObjectModalProps) {
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<ObjectCategory>('industrial');
  const [subtype, setSubtype] = useState('');
  const [icon, setIcon] = useState<ObjectIconId | undefined>(undefined);
  const [status, setStatus] = useState<ReliabilityStatus>('unverified');
  const [securityLevel, setSecurityLevel] = useState<SecurityLevel>('none');
  const [entryDifficulty, setEntryDifficulty] = useState<EntryDifficulty>('moderate');
  const [floorsOrDepth, setFloorsOrDepth] = useState('');
  const [latStr, setLatStr] = useState(initialLat.toFixed(6));
  const [lonStr, setLonStr] = useState(initialLon.toFixed(6));
  const [elevationStr, setElevationStr] = useState('140');
  const [description, setDescription] = useState('');
  const [accessNotes, setAccessNotes] = useState('');
  const [hazardsStr, setHazardsStr] = useState('');
  const [photos, setPhotos] = useState<string[]>([]);
  const [photoUrlDraft, setPhotoUrlDraft] = useState('');
  const [photoStatusMsg, setPhotoStatusMsg] = useState<string | null>(null);
  const [fetchingPhoto, setFetchingPhoto] = useState(false);
  const [zoomPhotoIdx, setZoomPhotoIdx] = useState<number | null>(null);
  const [customParams, setCustomParams] = useState<ObjectParam[]>([]);
  const [paramKeyDraft, setParamKeyDraft] = useState('');
  const [paramValueDraft, setParamValueDraft] = useState('');
  const [zone, setZone] = useState<GeoPoint[]>([]);
  const [zoneColor, setZoneColor] = useState<string>('');
  const [zoneStyle, setZoneStyle] = useState<ZoneStyle>('solid');
  const [zonePattern, setZonePattern] = useState<ZonePattern>('none');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (draftOverride) {
      setTitle(draftOverride.title);
      setCategory(draftOverride.category);
      setSubtype(draftOverride.subtype);
      setIcon(normalizeObjectIcon(draftOverride.icon));
      setStatus(draftOverride.status);
      setSecurityLevel(draftOverride.securityLevel);
      setEntryDifficulty(draftOverride.entryDifficulty);
      setFloorsOrDepth(draftOverride.floorsOrDepth);
      setLatStr(draftOverride.latStr);
      setLonStr(draftOverride.lonStr);
      setElevationStr(draftOverride.elevationStr);
      setDescription(draftOverride.description);
      setAccessNotes(draftOverride.accessNotes);
      setHazardsStr(draftOverride.hazardsStr);
      setPhotos(draftOverride.photos);
      setPhotoUrlDraft('');
      setPhotoStatusMsg(null);
      setCustomParams(draftOverride.customParams);
      setZone(draftOverride.zone);
      setZoneColor(draftOverride.zoneColor);
      setZoneStyle(draftOverride.zoneStyle);
      setZonePattern(
        normalizeZonePattern(draftOverride.zonePattern, draftOverride.zoneHatched)
      );
    } else if (editingObject) {
      setTitle(editingObject.title);
      setCategory(editingObject.category);
      setSubtype(editingObject.subtype ?? '');
      setIcon(normalizeObjectIcon(editingObject.icon));
      setStatus(editingObject.status);
      setSecurityLevel(editingObject.securityLevel ?? 'none');
      setEntryDifficulty(editingObject.entryDifficulty ?? 'moderate');
      setFloorsOrDepth(editingObject.floorsOrDepth ?? '');
      setLatStr(editingObject.lat.toFixed(6));
      setLonStr(editingObject.lon.toFixed(6));
      setElevationStr(String(editingObject.elevationM));
      setDescription(editingObject.description);
      setAccessNotes(editingObject.accessNotes);
      setHazardsStr(editingObject.hazards.join(', '));
      setPhotos(getObjectPhotos(editingObject));
      setPhotoUrlDraft('');
      setPhotoStatusMsg(null);
      setCustomParams(editingObject.customParams ?? []);
      setZone(editingObject.zone ?? []);
      setZoneColor(normalizeZoneColor(editingObject.zoneColor) ?? '');
      setZoneStyle(normalizeZoneStyle(editingObject.zoneStyle));
      setZonePattern(
        normalizeZonePattern(editingObject.zonePattern, Boolean(editingObject.zoneHatched))
      );
    } else {
      setTitle('');
      setCategory('industrial');
      setSubtype('');
      setIcon(undefined);
      setStatus('unverified');
      setSecurityLevel('none');
      setEntryDifficulty('moderate');
      setFloorsOrDepth('');
      setLatStr(initialLat.toFixed(6));
      setLonStr(initialLon.toFixed(6));
      setElevationStr('140');
      setDescription('');
      setAccessNotes('');
      setHazardsStr('');
      setPhotos([]);
      setPhotoUrlDraft('');
      setPhotoStatusMsg(null);
      setCustomParams([]);
      setZone([]);
      setZoneColor('');
      setZoneStyle('solid');
      setZonePattern('none');
    }
    setParamKeyDraft('');
    setParamValueDraft('');
    setError(null);
    setZoomPhotoIdx(null);
  }, [open, editingObject, draftOverride, initialLat, initialLon]);

  useEffect(() => {
    if (!open || zoomPhotoIdx !== null) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, zoomPhotoIdx, onClose]);

  const appendUniquePhotos = (incoming: string[]) => {
    setPhotos((prev) => {
      const seen = new Set(prev);
      const next = [...prev];
      for (const item of incoming) {
        const clean = item.trim();
        if (clean && !seen.has(clean)) {
          seen.add(clean);
          next.push(clean);
        }
      }
      return next;
    });
  };

  const handlePhotoUpload = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    const files = Array.from(fileList);
    for (const file of files) {
      if (file.size > 10 * 1024 * 1024) {
        setError(`Файл «${file.name}» превышает 10 МБ.`);
        return;
      }
    }
    setError(null);
    try {
      const added: string[] = [];
      let totalBytes = 0;
      for (const file of files) {
        const res = await optimizeImageFileToDataUrl(file);
        added.push(res.dataUrl);
        totalBytes += res.sizeBytes;
      }
      appendUniquePhotos(added);
      setPhotoUrlDraft('');
      setPhotoStatusMsg(
        added.length > 1
          ? `Добавлено фото: ${added.length} (${formatBytes(totalBytes)})`
          : `Фото добавлено в базу (${formatBytes(totalBytes)})`
      );
    } catch {
      setError('Не удалось обработать один из выбранных файлов.');
    }
  };

  const handleDownloadUrlToDb = async (urlInput?: string): Promise<string | undefined> => {
    const candidate = (urlInput ?? photoUrlDraft).trim();
    if (!candidate) return undefined;
    setError(null);
    setFetchingPhoto(true);
    setPhotoStatusMsg(null);
    try {
      const res = await downloadImageUrlToDataUrl(candidate);
      appendUniquePhotos([res.dataUrl]);
      if (res.embedded) {
        setPhotoUrlDraft('');
        setPhotoStatusMsg(`Фото скачано и добавлено в базу (${formatBytes(res.sizeBytes)})`);
      } else {
        setPhotoUrlDraft('');
        setPhotoStatusMsg(res.warning ?? 'Добавлена внешняя ссылка на фото.');
      }
      return res.dataUrl;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось загрузить фото по ссылке.');
      return undefined;
    } finally {
      setFetchingPhoto(false);
    }
  };

  const handlePromoteCover = (idx: number) => {
    if (idx <= 0 || idx >= photos.length) return;
    setPhotos((prev) => {
      const picked = prev[idx];
      return [picked, ...prev.filter((_, i) => i !== idx)];
    });
  };

  const handleRemovePhoto = (idx: number) => {
    setPhotos((prev) => prev.filter((_, i) => i !== idx));
    setPhotoStatusMsg(null);
  };

  const handleAddParam = () => {
    const k = paramKeyDraft.trim();
    const v = paramValueDraft.trim();
    if (!k || !v) return;
    setCustomParams((prev) => [...prev, { key: k, value: v }]);
    setParamKeyDraft('');
    setParamValueDraft('');
  };

  const buildValidatedRecord = (overridePhotos?: string[]): UrbexObject | null => {
    const trimmedTitle = title.trim();
    const trimmedDesc = description.trim();
    const lat = Number(latStr.replace(',', '.'));
    const lon = Number(lonStr.replace(',', '.'));
    const elevation = Number(elevationStr);

    if (trimmedTitle.length < 2) {
      setError('Укажите название объекта (минимум 2 символа).');
      return null;
    }
    if (!isValidCoordinate(lat, lon)) {
      setError('Некорректные координаты WGS-84 (широта -90..90, долгота -180..180).');
      return null;
    }
    if (trimmedDesc.length < 4) {
      setError('Добавьте краткое описание объекта.');
      return null;
    }

    const now = Date.now();
    const hazards = hazardsStr
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);

    const pendingParams =
      paramKeyDraft.trim() && paramValueDraft.trim()
        ? [...customParams, { key: paramKeyDraft.trim(), value: paramValueDraft.trim() }]
        : customParams;

    const finalPhotos = Array.from(
      new Set((overridePhotos ?? photos).map((p) => p.trim()).filter(Boolean))
    );

    const resolvedEditing = draftOverride?.editingObject ?? editingObject ?? null;

    return {
      id: resolvedEditing?.id ?? `obj-${now.toString(36)}`,
      title: trimmedTitle,
      category,
      subtype: normalizeObjectSubtype(subtype),
      icon,
      status,
      lat: Number(lat.toFixed(6)),
      lon: Number(lon.toFixed(6)),
      elevationM: Number.isFinite(elevation) ? Math.round(elevation) : 0,
      description: trimmedDesc,
      accessNotes: accessNotes.trim() || 'Подходы уточняются на местности.',
      hazards,
      photoUrl: finalPhotos[0],
      photos: finalPhotos.length > 0 ? finalPhotos : undefined,
      securityLevel,
      entryDifficulty,
      floorsOrDepth: floorsOrDepth.trim() || undefined,
      customParams: pendingParams,
      zone,
      zoneColor: normalizeZoneColor(zoneColor),
      zoneStyle,
      zonePattern,
      zoneHatched: zonePattern !== 'none' && zonePattern !== 'no_fill',
      createdAt: resolvedEditing?.createdAt ?? now,
      updatedAt: now,
      authorNodeId: resolvedEditing?.authorNodeId ?? authorNodeId,
    };
  };

  const captureDraft = (): QuickObjectDraft => ({
    title,
    category,
    subtype,
    icon,
    status,
    securityLevel,
    entryDifficulty,
    floorsOrDepth,
    latStr,
    lonStr,
    elevationStr,
    description,
    accessNotes,
    hazardsStr,
    photos,
    customParams,
    zone,
    zoneColor,
    zoneStyle,
    zonePattern,
    zoneHatched: zonePattern !== 'none' && zonePattern !== 'no_fill',
    editingObject: draftOverride?.editingObject ?? editingObject ?? null,
  });

  const handleNudgeCoords = (northM: number, eastM: number) => {
    const curLat = Number(latStr.replace(',', '.'));
    const curLon = Number(lonStr.replace(',', '.'));
    const baseLat = isValidCoordinate(curLat, curLon) ? curLat : initialLat;
    const baseLon = isValidCoordinate(curLat, curLon) ? curLon : initialLon;
    const moved = offsetCoordinate(baseLat, baseLon, northM, eastM);
    setLatStr(moved.lat.toFixed(6));
    setLonStr(moved.lon.toFixed(6));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    let nextPhotos = [...photos];
    const pendingUrl = photoUrlDraft.trim();
    if (pendingUrl && !nextPhotos.includes(pendingUrl)) {
      const downloaded = await handleDownloadUrlToDb(pendingUrl);
      if (!downloaded) return;
      if (!nextPhotos.includes(downloaded)) {
        nextPhotos = [...nextPhotos, downloaded];
      }
    }

    const record = buildValidatedRecord(nextPhotos);
    if (!record) return;

    setSaving(true);
    try {
      await onSave(record);
      onClose();
    } catch {
      setError('Не удалось сохранить объект в локальную базу.');
    } finally {
      setSaving(false);
    }
  };

  const handleDrawZoneOnMap = async () => {
    const record = buildValidatedRecord();
    if (!record || !onOpenZoneEditor) return;
    setSaving(true);
    try {
      await onOpenZoneEditor(record);
      onClose();
    } catch {
      setError('Не удалось открыть редактор зоны на карте.');
    } finally {
      setSaving(false);
    }
  };

  const zoneAreaM2 = computePolygonAreaM2(zone);
  const totalEmbeddedBytes = photos.reduce(
    (sum, item) => sum + (isEmbeddedDataUrl(item) ? estimateDataUrlBytes(item) : 0),
    0
  );

  return (
    <>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-2 sm:p-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-object-title"
          >
            <motion.div
              initial={{ opacity: 0, y: 8, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 6, scale: 0.97 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              className="w-full sm:max-w-2xl [@media(orientation:landscape)_and_(min-width:560px)]:max-w-[820px] max-h-[90dvh] flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden text-[#E6EBE8] shadow-2xl"
            >
              {/* Pinned Header */}
              <div className="shrink-0 flex items-center justify-between gap-3 px-3.5 py-2 sm:px-5 sm:py-3 border-b border-[#252E2A]">
                <h2 id="modal-object-title" className="text-sm sm:text-base font-semibold truncate">
                  {editingObject ? 'Редактирование объекта' : 'Новая точка объекта'}
                </h2>
                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Закрыть окно объекта"
                  className="min-h-[34px] min-w-[34px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
                >
                  <X className="w-4 h-4 sm:w-5 sm:h-5" />
                </button>
              </div>

              {/* Flex Form Container: Scrollable Middle + Pinned Footer */}
              <form onSubmit={handleSubmit} className="flex-1 min-h-0 flex flex-col overflow-hidden">
                <div className="flex-1 min-h-0 overflow-y-auto px-3.5 py-2.5 sm:px-5 sm:py-3.5 space-y-2.5 sm:space-y-3">
                  {error && (
                    <div
                      role="alert"
                      className="p-2.5 rounded-lg bg-[#381B19] border border-[#C7564E]/50 text-xs text-[#F1ACA7]"
                    >
                      {error}
                    </div>
                  )}

                  <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1.5fr_1fr] gap-2 sm:gap-3">
                    <div>
                      <label htmlFor="obj-title" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                        Название объекта
                      </label>
                      <input
                        id="obj-title"
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder="Например: Водонапорная башня депо №2"
                        className="w-full h-9 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                      />
                    </div>

                    <div>
                      <label htmlFor="obj-floors" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                        Этажность / глубина
                      </label>
                      <input
                        id="obj-floors"
                        type="text"
                        value={floorsOrDepth}
                        onChange={(e) => setFloorsOrDepth(e.target.value)}
                        placeholder="Напр.: -14 м / 2 яруса"
                        className="w-full h-9 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1fr_1.24fr_1fr_0.86fr] gap-2">
                    <UrbexSelect
                      compact
                      label="Категория"
                      value={category}
                      onChange={(val) => setCategory(val)}
                      options={CATEGORIES.map((cat) => ({
                        value: cat,
                        label: CATEGORY_LABELS[cat],
                      }))}
                    />

                    <UrbexSelect
                      compact
                      label="Достоверность"
                      value={status}
                      onChange={(val) => setStatus(val)}
                      options={STATUSES.map((st) => ({
                        value: st,
                        label: STATUS_META[st].label,
                        dotColor: STATUS_META[st].colorHex,
                      }))}
                    />

                    <UrbexSelect
                      compact
                      label="Охрана / режим"
                      value={securityLevel}
                      onChange={(val) => setSecurityLevel(val)}
                      options={SECURITY_LEVELS.map((lv) => ({
                        value: lv,
                        label: SECURITY_LEVEL_LABELS[lv],
                      }))}
                    />

                    <UrbexSelect
                      compact
                      label="Доступ"
                      value={entryDifficulty}
                      onChange={(val) => setEntryDifficulty(val)}
                      options={ENTRY_DIFFICULTIES.map((df) => ({
                        value: df,
                        label: ENTRY_DIFFICULTY_LABELS[df],
                      }))}
                    />
                  </div>

                  {/* Object Subtype Input + Category Presets */}
                  <div className="p-2.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <label htmlFor="obj-subtype" className="text-[11px] font-medium text-[#98A49E]">
                        Подтип объекта
                      </label>
                      {subtype.trim() && (
                        <button
                          type="button"
                          onClick={() => setSubtype('')}
                          className="text-[10px] text-[#98A49E] hover:text-[#DC6860] transition-colors"
                        >
                          Сбросить подтип
                        </button>
                      )}
                    </div>
                    <input
                      id="obj-subtype"
                      type="text"
                      value={subtype}
                      onChange={(e) => setSubtype(e.target.value)}
                      placeholder="Выберите пресет ниже или введите свой подтип..."
                      className="w-full h-8 px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                    />
                    <div
                      role="group"
                      aria-label="Быстрые подтипы категории"
                      className="flex items-center gap-1.5 overflow-x-auto pb-0.5"
                    >
                      {CATEGORY_SUBTYPES[category].map((preset) => {
                        const isActive = subtype.trim().toLowerCase() === preset.label.toLowerCase();
                        return (
                          <button
                            key={preset.label}
                            type="button"
                            onClick={() => {
                              if (isActive) {
                                setSubtype('');
                              } else {
                                setSubtype(preset.label);
                                setIcon(preset.icon);
                              }
                            }}
                            aria-pressed={isActive}
                            className={`shrink-0 h-[28px] px-2 rounded-lg border text-[11px] flex items-center gap-1.5 transition-colors ${
                              isActive
                                ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                                : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#323E39]'
                            }`}
                          >
                            <ObjectGlyph icon={preset.icon} className="w-3.5 h-3.5 shrink-0" />
                            <span className="whitespace-nowrap">{preset.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* Object Icon Picker (Auto + 72 Icons) */}
                  <div className="p-2.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[11px] font-medium text-[#98A49E]">
                        Иконка объекта на карте ({OBJECT_ICONS.length})
                      </span>
                      <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-[#6DB386]">
                        <ObjectGlyph
                          icon={icon ?? CATEGORY_DEFAULT_ICON[category]}
                          className="w-3.5 h-3.5"
                        />
                        <span>
                          {icon
                            ? OBJECT_ICON_META[icon].label
                            : `Авто (${OBJECT_ICON_META[CATEGORY_DEFAULT_ICON[category]].label})`}
                        </span>
                      </span>
                    </div>
                    <div
                      role="group"
                      aria-label="Выбор иконки объекта"
                      className="flex flex-wrap items-center gap-1 max-h-[156px] overflow-y-auto pr-1"
                    >
                      <button
                        type="button"
                        onClick={() => setIcon(undefined)}
                        aria-pressed={!icon}
                        className={`h-[32px] px-2.5 rounded-lg border text-[11px] font-medium transition-colors flex items-center gap-1 ${
                          !icon
                            ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8] shadow-[0_0_10px_rgba(91,157,114,0.22)]'
                            : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#323E39]'
                        }`}
                      >
                        <ObjectGlyph icon={CATEGORY_DEFAULT_ICON[category]} className="w-3.5 h-3.5" />
                        <span>Авто</span>
                      </button>
                      {OBJECT_ICONS.map((iconId) => {
                        const active = icon === iconId;
                        const label = OBJECT_ICON_META[iconId].label;
                        return (
                          <button
                            key={iconId}
                            type="button"
                            onClick={() => setIcon(iconId)}
                            title={label}
                            aria-label={`Иконка: ${label}`}
                            aria-pressed={active}
                            className={`w-[32px] h-[32px] rounded-lg border flex items-center justify-center transition-colors ${
                              active
                                ? 'bg-[#203328] border-[#5B9D72] text-[#6DB386] shadow-[0_0_10px_rgba(91,157,114,0.25)]'
                                : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#5B9D72]/60'
                            }`}
                          >
                            <ObjectGlyph icon={iconId} className="w-4 h-4" />
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <div className="grid grid-cols-3 gap-2">
                      <div>
                        <label htmlFor="obj-lat" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                          Широта (Lat)
                        </label>
                        <input
                          id="obj-lat"
                          type="text"
                          value={latStr}
                          onChange={(e) => setLatStr(e.target.value)}
                          className="w-full h-9 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                        />
                      </div>
                      <div>
                        <label htmlFor="obj-lon" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                          Долгота (Lon)
                        </label>
                        <input
                          id="obj-lon"
                          type="text"
                          value={lonStr}
                          onChange={(e) => setLonStr(e.target.value)}
                          className="w-full h-9 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                        />
                      </div>
                      <div>
                        <label htmlFor="obj-elev" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                          Высота (м)
                        </label>
                        <input
                          id="obj-elev"
                          type="number"
                          value={elevationStr}
                          onChange={(e) => setElevationStr(e.target.value)}
                          className="w-full h-9 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                        />
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A]">
                      <div className="flex items-center gap-1">
                        <span className="text-[11px] text-[#98A49E] mr-1">Сдвиг 5 м:</span>
                        <button
                          type="button"
                          onClick={() => handleNudgeCoords(5, 0)}
                          aria-label="Сдвинуть точку на 5 метров на север"
                          className="w-7 h-7 rounded bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
                        >
                          <ArrowUp className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleNudgeCoords(-5, 0)}
                          aria-label="Сдвинуть точку на 5 метров на юг"
                          className="w-7 h-7 rounded bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
                        >
                          <ArrowDown className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleNudgeCoords(0, -5)}
                          aria-label="Сдвинуть точку на 5 метров на запад"
                          className="w-7 h-7 rounded bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleNudgeCoords(0, 5)}
                          aria-label="Сдвинуть точку на 5 метров на восток"
                          className="w-7 h-7 rounded bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
                        >
                          <ArrowRight className="w-3.5 h-3.5" />
                        </button>
                      </div>

                      {onOpenPointMover && (
                        <button
                          type="button"
                          onClick={() => onOpenPointMover(captureDraft())}
                          className="min-h-[30px] px-2.5 py-1 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-medium text-[#E6EBE8] hover:bg-[#274032] flex items-center gap-1.5"
                        >
                          <Move className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span>Двигать на карте</span>
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2 sm:gap-3">
                    <div>
                      <label htmlFor="obj-desc" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                        Описание и состояние конструкций
                      </label>
                      <textarea
                        id="obj-desc"
                        rows={2}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="Год постройки, материал, сохранность перекрытий..."
                        className="w-full p-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                      />
                    </div>

                    <div>
                      <label htmlFor="obj-access" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                        Заметки по подходу и периметру
                      </label>
                      <textarea
                        id="obj-access"
                        rows={2}
                        value={accessNotes}
                        onChange={(e) => setAccessNotes(e.target.value)}
                        placeholder="Тропа с северной стороны, состояние ограждения..."
                        className="w-full p-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                      />
                    </div>
                  </div>

                  {/* Zone & Custom Key-Value Parameters */}
                  <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2 sm:gap-3">
                    <div className="p-2.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-1.5">
                      <div className="flex flex-wrap items-center justify-between gap-1.5">
                        <div className="flex items-center gap-1.5">
                          <Pentagon className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span className="text-xs font-semibold text-[#E6EBE8]">Зона объекта</span>
                        </div>
                        <span className="text-[11px] font-mono tabular-nums text-[#98A49E]">
                          {zone.length >= 3
                            ? `${zone.length} тчк · ${formatArea(zoneAreaM2)}`
                            : zone.length > 0
                              ? `${zone.length} тчк (нужно ≥3)`
                              : 'Не задана'}
                        </span>
                      </div>

                      <div className="flex flex-wrap items-center gap-1.5">
                        {onOpenZoneEditor && (
                          <button
                            type="button"
                            onClick={handleDrawZoneOnMap}
                            className="min-h-[32px] px-2.5 py-1 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-medium text-[#E6EBE8] hover:bg-[#274032] flex items-center gap-1"
                          >
                            <MapPin className="w-3.5 h-3.5 text-[#6DB386]" />
                            <span>{zone.length >= 3 ? 'На карте' : 'На карте'}</span>
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => {
                            const lat = Number(latStr.replace(',', '.'));
                            const lon = Number(lonStr.replace(',', '.'));
                            if (!isValidCoordinate(lat, lon)) {
                              setError('Укажите корректные координаты для построения круговой зоны.');
                              return;
                            }
                            setZone(buildZoneCircle(lat, lon, defaultZoneRadiusM));
                          }}
                          className="min-h-[32px] px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72]"
                        >
                          Круг R={defaultZoneRadiusM} м
                        </button>
                        {zone.length > 0 && (
                          <button
                            type="button"
                            onClick={() => setZone([])}
                            className="min-h-[32px] px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#F1ACA7] hover:border-[#C7564E]"
                          >
                            Очистить
                          </button>
                        )}
                      </div>

                      {/* Zone Line Style, Interior Pattern & Manual Color */}
                      <div className="pt-1.5 border-t border-[#252E2A]/70 space-y-2">
                        {(() => {
                          const previewHex =
                            normalizeZoneColor(zoneColor) ?? STATUS_META[status].colorHex;
                          return (
                            <>
                              <div className="space-y-1">
                                <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider text-[#98A49E]">
                                  <span>Линия границы</span>
                                  <span className="text-[#E6EBE8] normal-case">{ZONE_STYLE_LABELS[zoneStyle]}</span>
                                </div>
                                <div className="flex flex-wrap items-center gap-1">
                                  {ZONE_STYLE_ORDER.map((st) => {
                                    const active = zoneStyle === st;
                                    return (
                                      <button
                                        key={st}
                                        type="button"
                                        onClick={() => setZoneStyle(st)}
                                        aria-pressed={active}
                                        className={`min-h-[28px] px-2 py-1 rounded-lg border text-[11px] font-medium transition-colors flex items-center gap-1.5 ${
                                          active
                                            ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                                            : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                                        }`}
                                      >
                                        <ZoneLinePreview style={st} color={active ? previewHex : '#98A49E'} />
                                        <span>{ZONE_STYLE_LABELS[st]}</span>
                                      </button>
                                    );
                                  })}
                                </div>
                              </div>

                              <div className="space-y-1">
                                <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider text-[#98A49E]">
                                  <span>Стиль заливки зоны</span>
                                  <span className="text-[#E6EBE8] normal-case">{ZONE_PATTERN_LABELS[zonePattern]}</span>
                                </div>
                                <div className="flex flex-wrap items-center gap-1">
                                  {ZONE_PATTERN_ORDER.map((pat) => {
                                    const active = zonePattern === pat;
                                    return (
                                      <button
                                        key={pat}
                                        type="button"
                                        onClick={() => setZonePattern(pat)}
                                        aria-pressed={active}
                                        className={`min-h-[28px] px-2 py-1 rounded-lg border text-[11px] font-medium transition-colors flex items-center gap-1.5 ${
                                          active
                                            ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                                            : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                                        }`}
                                      >
                                        <ZonePatternPreview pattern={pat} color={active ? previewHex : '#98A49E'} />
                                        <span>{ZONE_PATTERN_LABELS[pat]}</span>
                                      </button>
                                    );
                                  })}
                                </div>
                              </div>
                            </>
                          );
                        })()}

                        <div className="flex flex-wrap items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => setZoneColor('')}
                            aria-pressed={!zoneColor}
                            className={`min-h-[26px] px-2 py-0.5 rounded border text-[11px] transition-colors ${
                              !zoneColor
                                ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                                : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                            }`}
                          >
                            Авто
                          </button>
                          {ZONE_COLOR_PRESETS.map((preset) => {
                            const active = zoneColor.toUpperCase() === preset.hex;
                            return (
                              <button
                                key={preset.hex}
                                type="button"
                                onClick={() => setZoneColor(preset.hex)}
                                title={preset.label}
                                aria-label={`Цвет зоны ${preset.label}`}
                                aria-pressed={active}
                                className={`w-6 h-6 rounded-full border-2 transition-transform ${
                                  active ? 'border-[#E6EBE8] scale-110' : 'border-[#252E2A] hover:scale-105'
                                }`}
                                style={{ backgroundColor: preset.hex }}
                              />
                            );
                          })}
                          <label
                            title="Свой цвет зоны (HEX)"
                            className="flex items-center gap-1 px-1.5 py-0.5 rounded border border-[#252E2A] bg-[#131816] text-[10px] font-mono text-[#98A49E] cursor-pointer hover:border-[#5B9D72]"
                          >
                            <input
                              type="color"
                              value={normalizeZoneColor(zoneColor) ?? STATUS_META[status].colorHex}
                              onChange={(e) => setZoneColor(e.target.value.toUpperCase())}
                              aria-label="Выбрать произвольный цвет зоны"
                              className="w-4 h-4 bg-transparent border-0 p-0 cursor-pointer"
                            />
                            <span>{zoneColor || 'Свой'}</span>
                          </label>
                        </div>
                      </div>
                    </div>

                    <div className="p-2.5 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-1.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-[#E6EBE8]">
                          Параметры ({customParams.length})
                        </span>
                        <div className="flex flex-wrap gap-1">
                          {[
                            { k: 'O2', v: '20.6%' },
                            { k: 'Радиация', v: '0.14 мкЗв/ч' },
                            { k: 'Год', v: '1978' },
                          ].map((preset) => (
                            <button
                              key={preset.k}
                              type="button"
                              onClick={() => {
                                setParamKeyDraft(preset.k);
                                setParamValueDraft(preset.v);
                              }}
                              className="px-1.5 py-0.5 rounded bg-[#131816] border border-[#252E2A] text-[10px] text-[#98A49E] hover:text-[#E6EBE8]"
                            >
                              +{preset.k}
                            </button>
                          ))}
                        </div>
                      </div>

                      {customParams.length > 0 && (
                        <div className="space-y-1 max-h-16 overflow-y-auto">
                          {customParams.map((p, idx) => (
                            <div
                              key={`${p.key}-${idx}`}
                              className="flex items-center justify-between gap-2 px-2 py-0.5 rounded bg-[#131816] border border-[#252E2A] text-[11px]"
                            >
                              <div className="min-w-0 truncate">
                                <span className="text-[#98A49E]">{p.key}: </span>
                                <span className="font-mono text-[#E6EBE8]">{p.value}</span>
                              </div>
                              <button
                                type="button"
                                onClick={() =>
                                  setCustomParams((prev) => prev.filter((_, i) => i !== idx))
                                }
                                aria-label={`Удалить параметр ${p.key}`}
                                className="p-0.5 text-[#98A49E] hover:text-[#DC6860]"
                              >
                                <Trash2 className="w-3 h-3" />
                              </button>
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="flex items-center gap-1.5">
                        <input
                          type="text"
                          value={paramKeyDraft}
                          onChange={(e) => setParamKeyDraft(e.target.value)}
                          placeholder="Параметр"
                          aria-label="Название параметра"
                          className="flex-1 min-w-0 h-8 px-2 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771]"
                        />
                        <input
                          type="text"
                          value={paramValueDraft}
                          onChange={(e) => setParamValueDraft(e.target.value)}
                          placeholder="Значение"
                          aria-label="Значение параметра"
                          className="flex-1 min-w-0 h-8 px-2 rounded-lg bg-[#131816] border border-[#252E2A] text-xs font-mono text-[#E6EBE8] placeholder:text-[#6B7771]"
                        />
                        <button
                          type="button"
                          onClick={handleAddParam}
                          aria-label="Добавить параметр"
                          className="h-8 w-8 rounded-lg bg-[#1A201E] border border-[#323E39] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center justify-center shrink-0"
                        >
                          <Plus className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Hazards + Photo (File OR URL saved offline to DB) */}
                  <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2 sm:gap-3">
                    <div>
                      <label htmlFor="obj-hazards" className="block text-[11px] font-medium text-[#98A49E] mb-1">
                        Факторы риска (через запятую)
                      </label>
                      <input
                        id="obj-hazards"
                        type="text"
                        value={hazardsStr}
                        onChange={(e) => setHazardsStr(e.target.value)}
                        placeholder="Провалы пола, сырость"
                        className="w-full h-9 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                      />
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between gap-2">
                        <label htmlFor="obj-photo-url" className="block text-[11px] font-medium text-[#98A49E]">
                          Фотографии объекта ({photos.length})
                        </label>
                        {totalEmbeddedBytes > 0 && (
                          <span className="text-[10px] font-mono text-[#6DB386]">
                            В БД: {formatBytes(totalEmbeddedBytes)}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-1.5">
                        <div className="relative flex-1 min-w-0">
                          <Link2 className="w-3.5 h-3.5 text-[#6B7771] absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                          <input
                            id="obj-photo-url"
                            type="url"
                            value={photoUrlDraft}
                            onChange={(e) => setPhotoUrlDraft(e.target.value)}
                            placeholder="https://... добавить фото по ссылке"
                            className="w-full h-9 pl-7 pr-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                          />
                        </div>
                        <button
                          type="button"
                          disabled={fetchingPhoto || !photoUrlDraft.trim()}
                          onClick={() => void handleDownloadUrlToDb()}
                          className="h-9 px-2.5 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-medium text-[#E6EBE8] hover:bg-[#274032] disabled:opacity-40 flex items-center gap-1 whitespace-nowrap shrink-0"
                        >
                          <Download className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span>{fetchingPhoto ? '...' : 'В базу'}</span>
                        </button>
                        <label
                          title="Выбрать одно или несколько фото с устройства"
                          className="h-9 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] text-xs text-[#E6EBE8] cursor-pointer flex items-center gap-1 whitespace-nowrap shrink-0"
                        >
                          <Plus className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span>Файлы</span>
                          <input
                            id="obj-photo"
                            type="file"
                            accept="image/*"
                            multiple
                            onChange={(e) => {
                              void handlePhotoUpload(e.target.files);
                              e.target.value = '';
                            }}
                            className="sr-only"
                          />
                        </label>
                      </div>

                      {photoStatusMsg && (
                        <div className="text-[11px] text-[#6DB386] flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3 shrink-0" />
                          <span>{photoStatusMsg}</span>
                        </div>
                      )}

                      {photos.length > 0 && (
                        <div className="flex items-center gap-2 p-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] overflow-x-auto">
                          {photos.map((src, idx) => (
                            <div
                              key={`${idx}-${src.slice(0, 28)}`}
                              className={`relative w-20 h-16 rounded-lg overflow-hidden border shrink-0 group ${
                                idx === 0 ? 'border-[#5B9D72]' : 'border-[#252E2A]'
                              }`}
                            >
                              <button
                                type="button"
                                onClick={() => setZoomPhotoIdx(idx)}
                                className="w-full h-full block"
                                aria-label={`Увеличить фото ${idx + 1}`}
                              >
                                <img
                                  src={src}
                                  alt={`Фото ${idx + 1}`}
                                  referrerPolicy="no-referrer"
                                  className="w-full h-full object-cover"
                                />
                                <span className="absolute inset-0 bg-black/35 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                                  <ZoomIn className="w-3.5 h-3.5 text-white" />
                                </span>
                              </button>

                              {idx === 0 ? (
                                <span className="pointer-events-none absolute top-1 left-1 px-1 py-0.5 rounded bg-[#0C0F0E]/90 border border-[#5B9D72] text-[9px] font-medium text-[#6DB386]">
                                  Обложка
                                </span>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => handlePromoteCover(idx)}
                                  title="Сделать обложкой"
                                  aria-label={`Сделать обложкой фото ${idx + 1}`}
                                  className="absolute top-1 left-1 w-5 h-5 rounded bg-[#0C0F0E]/85 border border-[#323E39] hover:border-[#DF9E43] text-[#98A49E] hover:text-[#DF9E43] flex items-center justify-center"
                                >
                                  <Star className="w-3 h-3" />
                                </button>
                              )}

                              <button
                                type="button"
                                onClick={() => handleRemovePhoto(idx)}
                                title="Удалить фото"
                                aria-label={`Удалить фото ${idx + 1}`}
                                className="absolute top-1 right-1 w-5 h-5 rounded bg-[#0C0F0E]/85 border border-[#323E39] hover:border-[#C7564E] text-[#98A49E] hover:text-[#DC6860] flex items-center justify-center"
                              >
                                <X className="w-3 h-3" />
                              </button>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Pinned Footer — Never overflows or gets pushed off-screen */}
                <div className="shrink-0 flex items-center justify-end gap-2 px-3.5 py-2.5 sm:px-5 border-t border-[#252E2A] bg-[#131816]">
                  <button
                    type="button"
                    onClick={onClose}
                    className="min-h-[36px] sm:min-h-[40px] px-4 py-1.5 rounded-lg border border-[#252E2A] text-xs sm:text-sm font-medium text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
                  >
                    Отмена
                  </button>
                  <button
                    type="submit"
                    disabled={saving || fetchingPhoto}
                    className="min-h-[36px] sm:min-h-[40px] px-5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] disabled:opacity-50 transition-colors"
                  >
                    {saving || fetchingPhoto ? 'Сохранение...' : 'Сохранить'}
                  </button>
                </div>
              </form>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <PhotoZoomModal
        src={zoomPhotoIdx !== null ? photos[zoomPhotoIdx] ?? null : null}
        photos={zoomPhotoIdx !== null ? photos : undefined}
        initialIndex={zoomPhotoIdx ?? 0}
        title={title || 'Фото объекта'}
        onClose={() => setZoomPhotoIdx(null)}
      />
    </>
  );
}
