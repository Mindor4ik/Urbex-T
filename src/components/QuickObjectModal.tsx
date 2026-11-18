import { useEffect, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { MapPin, Pentagon, Plus, Trash2, X } from 'lucide-react';
import {
  buildZoneCircle,
  computePolygonAreaM2,
  computePolygonPerimeterM,
  formatArea,
  formatDistance,
  isValidCoordinate,
} from '../core/geo';
import {
  CATEGORY_LABELS,
  ENTRY_DIFFICULTY_LABELS,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
} from '../core/labels';
import type {
  EntryDifficulty,
  GeoPoint,
  ObjectCategory,
  ObjectParam,
  ReliabilityStatus,
  SecurityLevel,
  UrbexObject,
} from '../core/types';

interface QuickObjectModalProps {
  open: boolean;
  initialLat: number;
  initialLon: number;
  defaultZoneRadiusM?: number;
  editingObject?: UrbexObject | null;
  authorNodeId: string;
  onClose: () => void;
  onSave: (obj: UrbexObject) => Promise<void>;
  onOpenZoneEditor?: (obj: UrbexObject) => Promise<void>;
}

const CATEGORIES: ObjectCategory[] = [
  'industrial',
  'bunker',
  'civilian',
  'infrastructure',
  'underground',
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
  authorNodeId,
  onClose,
  onSave,
  onOpenZoneEditor,
}: QuickObjectModalProps) {
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<ObjectCategory>('industrial');
  const [status, setStatus] = useState<ReliabilityStatus>('unverified');
  const [securityLevel, setSecurityLevel] = useState<SecurityLevel>('none');
  const [entryDifficulty, setEntryDifficulty] = useState<EntryDifficulty>('moderate');
  const [floorsOrDepth, setFloorsOrDepth] = useState('');
  const [latStr, setLatStr] = useState('');
  const [lonStr, setLonStr] = useState('');
  const [elevationStr, setElevationStr] = useState('140');
  const [description, setDescription] = useState('');
  const [accessNotes, setAccessNotes] = useState('');
  const [hazardsStr, setHazardsStr] = useState('');
  const [customParams, setCustomParams] = useState<ObjectParam[]>([]);
  const [paramKeyDraft, setParamKeyDraft] = useState('');
  const [paramValueDraft, setParamValueDraft] = useState('');
  const [zone, setZone] = useState<GeoPoint[]>([]);
  const [photoUrl, setPhotoUrl] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setError(null);
    setParamKeyDraft('');
    setParamValueDraft('');
    if (editingObject) {
      setTitle(editingObject.title);
      setCategory(editingObject.category);
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
      setCustomParams(editingObject.customParams ?? []);
      setZone(editingObject.zone ?? []);
      setPhotoUrl(editingObject.photoUrl ?? '');
    } else {
      setTitle('');
      setCategory('industrial');
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
      setCustomParams([]);
      setZone([]);
      setPhotoUrl('');
    }
  }, [open, editingObject, initialLat, initialLon]);

  const handlePhotoUpload = (file: File | undefined) => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setPhotoUrl(reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleAddParam = () => {
    const k = paramKeyDraft.trim();
    const v = paramValueDraft.trim();
    if (!k || !v) return;
    setCustomParams((prev) => [...prev, { key: k, value: v }]);
    setParamKeyDraft('');
    setParamValueDraft('');
  };

  const buildValidatedRecord = (): UrbexObject | null => {
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

    return {
      id: editingObject?.id ?? `obj-${now.toString(36)}`,
      title: trimmedTitle,
      category,
      status,
      lat: Number(lat.toFixed(6)),
      lon: Number(lon.toFixed(6)),
      elevationM: Number.isFinite(elevation) ? Math.round(elevation) : 0,
      description: trimmedDesc,
      accessNotes: accessNotes.trim() || 'Подходы уточняются на местности.',
      hazards,
      photoUrl: photoUrl || undefined,
      securityLevel,
      entryDifficulty,
      floorsOrDepth: floorsOrDepth.trim() || undefined,
      customParams: pendingParams,
      zone,
      createdAt: editingObject?.createdAt ?? now,
      updatedAt: now,
      authorNodeId: editingObject?.authorNodeId ?? authorNodeId,
    };
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const record = buildValidatedRecord();
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
  const zonePerimeterM = computePolygonPerimeterM(zone);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.16 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-object-title"
        >
          <motion.div
            initial={{ opacity: 0, y: 8, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 6, scale: 0.97 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            className="w-full sm:max-w-2xl [@media(orientation:landscape)_and_(min-width:560px)]:max-w-[800px] max-h-[92dvh] overflow-y-auto bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 text-[#E6EBE8] shadow-2xl"
          >
            <div className="flex items-center justify-between gap-3 pb-2.5 sm:pb-3.5 border-b border-[#252E2A]">
              <h2 id="modal-object-title" className="text-base sm:text-lg font-semibold">
                {editingObject ? 'Редактирование объекта' : 'Новая точка объекта'}
              </h2>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть окно объекта"
                className="min-h-[36px] min-w-[36px] sm:min-h-[40px] sm:min-w-[40px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="mt-3 sm:mt-4 space-y-2.5 sm:space-y-3.5">
              {error && (
                <div
                  role="alert"
                  className="p-2.5 sm:p-3 rounded-lg bg-[#381B19] border border-[#C7564E]/50 text-xs sm:text-sm text-[#F1ACA7]"
                >
                  {error}
                </div>
              )}

              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1.5fr_1fr] gap-2.5 sm:gap-3">
                <div>
                  <label htmlFor="obj-title" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Название объекта
                  </label>
                  <input
                    id="obj-title"
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Например: Водонапорная башня депо №2"
                    className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>

                <div>
                  <label htmlFor="obj-floors" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Этажность / глубина
                  </label>
                  <input
                    id="obj-floors"
                    type="text"
                    value={floorsOrDepth}
                    onChange={(e) => setFloorsOrDepth(e.target.value)}
                    placeholder="Напр.: -14 м / 2 яруса"
                    className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-[1fr_1.24fr_1fr_0.86fr] gap-2 sm:gap-2.5">
                <div>
                  <label htmlFor="obj-category" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Категория
                  </label>
                  <select
                    id="obj-category"
                    value={category}
                    onChange={(e) => setCategory(e.target.value as ObjectCategory)}
                    className="w-full h-9 lg:h-10 px-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  >
                    {CATEGORIES.map((cat) => (
                      <option key={cat} value={cat}>
                        {CATEGORY_LABELS[cat]}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="obj-status" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Достоверность
                  </label>
                  <select
                    id="obj-status"
                    value={status}
                    onChange={(e) => setStatus(e.target.value as ReliabilityStatus)}
                    className="w-full h-9 lg:h-10 px-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  >
                    {STATUSES.map((st) => (
                      <option key={st} value={st}>
                        {STATUS_META[st].glyph} {STATUS_META[st].label}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="obj-security" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Охрана / режим
                  </label>
                  <select
                    id="obj-security"
                    value={securityLevel}
                    onChange={(e) => setSecurityLevel(e.target.value as SecurityLevel)}
                    className="w-full h-9 lg:h-10 px-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  >
                    {SECURITY_LEVELS.map((lv) => (
                      <option key={lv} value={lv}>
                        {SECURITY_LEVEL_LABELS[lv]}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label htmlFor="obj-difficulty" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Доступ
                  </label>
                  <select
                    id="obj-difficulty"
                    value={entryDifficulty}
                    onChange={(e) => setEntryDifficulty(e.target.value as EntryDifficulty)}
                    className="w-full h-9 lg:h-10 px-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  >
                    {ENTRY_DIFFICULTIES.map((df) => (
                      <option key={df} value={df}>
                        {ENTRY_DIFFICULTY_LABELS[df]}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2 sm:gap-2.5">
                <div>
                  <label htmlFor="obj-lat" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Широта (Lat)
                  </label>
                  <input
                    id="obj-lat"
                    type="text"
                    value={latStr}
                    onChange={(e) => setLatStr(e.target.value)}
                    className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>
                <div>
                  <label htmlFor="obj-lon" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Долгота (Lon)
                  </label>
                  <input
                    id="obj-lon"
                    type="text"
                    value={lonStr}
                    onChange={(e) => setLonStr(e.target.value)}
                    className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>
                <div>
                  <label htmlFor="obj-elev" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Высота (м)
                  </label>
                  <input
                    id="obj-elev"
                    type="number"
                    value={elevationStr}
                    onChange={(e) => setElevationStr(e.target.value)}
                    className="w-full h-9 lg:h-10 px-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5 sm:gap-3">
                <div>
                  <label htmlFor="obj-desc" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Описание и состояние конструкций
                  </label>
                  <textarea
                    id="obj-desc"
                    rows={2}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Год постройки, материал, сохранность перекрытий..."
                    className="w-full p-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>

                <div>
                  <label htmlFor="obj-access" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Заметки по подходу и периметру
                  </label>
                  <textarea
                    id="obj-access"
                    rows={2}
                    value={accessNotes}
                    onChange={(e) => setAccessNotes(e.target.value)}
                    placeholder="Тропа с северной стороны, состояние ограждения..."
                    className="w-full p-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>
              </div>

              {/* Zone & Custom Key-Value Parameters side-by-side in landscape */}
              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5 sm:gap-3">
                <div className="p-2.5 sm:p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-1.5">
                    <div className="flex items-center gap-1.5">
                      <Pentagon className="w-3.5 h-3.5 text-[#6DB386]" />
                      <span className="text-xs font-semibold text-[#E6EBE8]">
                        Зона объекта
                      </span>
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
                        className="min-h-[34px] px-2.5 py-1 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs font-medium text-[#E6EBE8] hover:bg-[#274032] flex items-center gap-1.5"
                      >
                        <MapPin className="w-3.5 h-3.5 text-[#6DB386]" />
                        <span>{zone.length >= 3 ? 'Изменить на карте' : 'На карте'}</span>
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
                      className="min-h-[34px] px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72]"
                    >
                      Круг R={defaultZoneRadiusM} м
                    </button>
                    {zone.length > 0 && (
                      <button
                        type="button"
                        onClick={() => setZone([])}
                        className="min-h-[34px] px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#F1ACA7] hover:border-[#C7564E]"
                      >
                        Очистить
                      </button>
                    )}
                  </div>
                </div>

                <div className="p-2.5 sm:p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2">
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
                    <div className="space-y-1 max-h-20 overflow-y-auto">
                      {customParams.map((p, idx) => (
                        <div
                          key={`${p.key}-${idx}`}
                          className="flex items-center justify-between gap-2 px-2 py-1 rounded-lg bg-[#131816] border border-[#252E2A] text-xs"
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
                            <Trash2 className="w-3.5 h-3.5" />
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
                      className="flex-1 min-w-0 h-8 px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] text-xs text-[#E6EBE8] placeholder:text-[#6B7771]"
                    />
                    <input
                      type="text"
                      value={paramValueDraft}
                      onChange={(e) => setParamValueDraft(e.target.value)}
                      placeholder="Значение"
                      aria-label="Значение параметра"
                      className="flex-1 min-w-0 h-8 px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] text-xs font-mono text-[#E6EBE8] placeholder:text-[#6B7771]"
                    />
                    <button
                      type="button"
                      onClick={handleAddParam}
                      aria-label="Добавить параметр"
                      className="min-h-[32px] px-2.5 rounded-lg bg-[#1A201E] border border-[#323E39] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center justify-center shrink-0"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5 sm:gap-3">
                <div>
                  <label htmlFor="obj-hazards" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Факторы риска (через запятую)
                  </label>
                  <input
                    id="obj-hazards"
                    type="text"
                    value={hazardsStr}
                    onChange={(e) => setHazardsStr(e.target.value)}
                    placeholder="Провалы пола, сырость"
                    className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
                  />
                </div>

                <div>
                  <label htmlFor="obj-photo" className="block text-[11px] sm:text-xs font-medium text-[#98A49E] mb-1">
                    Фотоснимок (локальный файл)
                  </label>
                  <input
                    id="obj-photo"
                    type="file"
                    accept="image/*"
                    onChange={(e) => handlePhotoUpload(e.target.files?.[0])}
                    className="w-full h-9 lg:h-10 px-2 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#98A49E] file:mr-2 file:px-2 file:py-0.5 file:rounded file:border-0 file:bg-[#1A201E] file:text-[#E6EBE8]"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-2.5 border-t border-[#252E2A]">
                <button
                  type="button"
                  onClick={onClose}
                  className="min-h-[38px] sm:min-h-[42px] px-4 py-1.5 rounded-lg border border-[#252E2A] text-xs sm:text-sm font-medium text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
                >
                  Отмена
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="min-h-[38px] sm:min-h-[42px] px-5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] disabled:opacity-50 transition-colors"
                >
                  {saving ? 'Сохранение...' : 'Сохранить'}
                </button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

