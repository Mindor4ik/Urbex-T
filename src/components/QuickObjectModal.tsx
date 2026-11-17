import { useEffect, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { X } from 'lucide-react';
import { isValidCoordinate } from '../core/geo';
import { CATEGORY_LABELS, STATUS_META } from '../core/labels';
import type {
  ObjectCategory,
  ReliabilityStatus,
  UrbexObject,
} from '../core/types';

interface QuickObjectModalProps {
  open: boolean;
  initialLat: number;
  initialLon: number;
  editingObject?: UrbexObject | null;
  authorNodeId: string;
  onClose: () => void;
  onSave: (obj: UrbexObject) => Promise<void>;
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

export function QuickObjectModal({
  open,
  initialLat,
  initialLon,
  editingObject,
  authorNodeId,
  onClose,
  onSave,
}: QuickObjectModalProps) {
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<ObjectCategory>('industrial');
  const [status, setStatus] = useState<ReliabilityStatus>('unverified');
  const [latStr, setLatStr] = useState('');
  const [lonStr, setLonStr] = useState('');
  const [elevationStr, setElevationStr] = useState('140');
  const [description, setDescription] = useState('');
  const [accessNotes, setAccessNotes] = useState('');
  const [hazardsStr, setHazardsStr] = useState('');
  const [photoUrl, setPhotoUrl] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setError(null);
    if (editingObject) {
      setTitle(editingObject.title);
      setCategory(editingObject.category);
      setStatus(editingObject.status);
      setLatStr(editingObject.lat.toFixed(6));
      setLonStr(editingObject.lon.toFixed(6));
      setElevationStr(String(editingObject.elevationM));
      setDescription(editingObject.description);
      setAccessNotes(editingObject.accessNotes);
      setHazardsStr(editingObject.hazards.join(', '));
      setPhotoUrl(editingObject.photoUrl ?? '');
    } else {
      setTitle('');
      setCategory('industrial');
      setStatus('unverified');
      setLatStr(initialLat.toFixed(6));
      setLonStr(initialLon.toFixed(6));
      setElevationStr('140');
      setDescription('');
      setAccessNotes('');
      setHazardsStr('');
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

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const trimmedTitle = title.trim();
    const trimmedDesc = description.trim();
    const lat = Number(latStr.replace(',', '.'));
    const lon = Number(lonStr.replace(',', '.'));
    const elevation = Number(elevationStr);

    if (trimmedTitle.length < 2) {
      setError('Укажите название объекта (минимум 2 символа).');
      return;
    }
    if (!isValidCoordinate(lat, lon)) {
      setError('Некорректные координаты WGS-84 (широта -90..90, долгота -180..180).');
      return;
    }
    if (trimmedDesc.length < 4) {
      setError('Добавьте краткое описание объекта.');
      return;
    }

    setSaving(true);
    const now = Date.now();
    const hazards = hazardsStr
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);

    const record: UrbexObject = {
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
      createdAt: editingObject?.createdAt ?? now,
      updatedAt: now,
      authorNodeId: editingObject?.authorNodeId ?? authorNodeId,
    };

    try {
      await onSave(record);
      onClose();
    } catch {
      setError('Не удалось сохранить объект в локальную базу.');
    } finally {
      setSaving(false);
    }
  };

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
            className="w-full sm:max-w-xl max-h-[88vh] overflow-y-auto bg-[#131816] border border-[#252E2A] rounded-xl p-5 text-[#E6EBE8] shadow-2xl"
          >
            <div className="flex items-center justify-between gap-4 pb-4 border-b border-[#252E2A]">
          <h2 id="modal-object-title" className="text-lg font-semibold">
            {editingObject ? 'Редактирование объекта' : 'Новая точка объекта'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Закрыть окно объекта"
            className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {error && (
            <div
              role="alert"
              className="p-3 rounded-lg bg-[#381B19] border border-[#C7564E]/50 text-sm text-[#F1ACA7]"
            >
              {error}
            </div>
          )}

          <div>
            <label htmlFor="obj-title" className="block text-xs font-medium text-[#98A49E] mb-1.5">
              Название объекта
            </label>
            <input
              id="obj-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Например: Водонапорная башня депо №2"
              className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="obj-category" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Категория
              </label>
              <select
                id="obj-category"
                value={category}
                onChange={(e) => setCategory(e.target.value as ObjectCategory)}
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {CATEGORY_LABELS[cat]}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="obj-status" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Статус достоверности
              </label>
              <select
                id="obj-status"
                value={status}
                onChange={(e) => setStatus(e.target.value as ReliabilityStatus)}
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
              >
                {STATUSES.map((st) => (
                  <option key={st} value={st}>
                    {STATUS_META[st].glyph} {STATUS_META[st].label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label htmlFor="obj-lat" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Широта (Lat)
              </label>
              <input
                id="obj-lat"
                type="text"
                value={latStr}
                onChange={(e) => setLatStr(e.target.value)}
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
              />
            </div>
            <div>
              <label htmlFor="obj-lon" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Долгота (Lon)
              </label>
              <input
                id="obj-lon"
                type="text"
                value={lonStr}
                onChange={(e) => setLonStr(e.target.value)}
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
              />
            </div>
            <div>
              <label htmlFor="obj-elev" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Высота (м)
              </label>
              <input
                id="obj-elev"
                type="number"
                value={elevationStr}
                onChange={(e) => setElevationStr(e.target.value)}
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] font-mono tabular-nums text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
              />
            </div>
          </div>

          <div>
            <label htmlFor="obj-desc" className="block text-xs font-medium text-[#98A49E] mb-1.5">
              Описание и состояние конструкций
            </label>
            <textarea
              id="obj-desc"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Год постройки, материал, сохранность перекрытий..."
              className="w-full p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
          </div>

          <div>
            <label htmlFor="obj-access" className="block text-xs font-medium text-[#98A49E] mb-1.5">
              Заметки по подходу и периметру
            </label>
            <input
              id="obj-access"
              type="text"
              value={accessNotes}
              onChange={(e) => setAccessNotes(e.target.value)}
              placeholder="Тропа с северной стороны, состояние ограждения..."
              className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="obj-hazards" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Факторы риска (через запятую)
              </label>
              <input
                id="obj-hazards"
                type="text"
                value={hazardsStr}
                onChange={(e) => setHazardsStr(e.target.value)}
                placeholder="Провалы пола, сырость"
                className="w-full h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
              />
            </div>

            <div>
              <label htmlFor="obj-photo" className="block text-xs font-medium text-[#98A49E] mb-1.5">
                Фотоснимок (локальный файл)
              </label>
              <input
                id="obj-photo"
                type="file"
                accept="image/*"
                onChange={(e) => handlePhotoUpload(e.target.files?.[0])}
                className="w-full h-11 px-2 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#98A49E] file:mr-2 file:px-2 file:py-1 file:rounded file:border-0 file:bg-[#1A201E] file:text-[#E6EBE8]"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[#252E2A]">
            <button
              type="button"
              onClick={onClose}
              className="min-h-[44px] px-4 py-2 rounded-lg border border-[#252E2A] text-sm font-medium text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
            >
              Отмена
            </button>
            <button
              type="submit"
              disabled={saving}
              className="min-h-[44px] px-5 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-sm font-semibold hover:bg-[#6DB386] disabled:opacity-50 transition-colors"
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
