import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Download,
  FileAudio,
  FileText,
  FileVideo,
  Image as ImageIcon,
  Plus,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import {
  ARCHIVE_TYPE_LABELS,
  formatBytes,
  formatShortDate,
} from '../../core/labels';
import type {
  ArchiveItem,
  ArchiveMediaType,
  UrbexObject,
} from '../../core/types';
import { ObjectImage } from '../../components/ObjectImage';

interface ArchiveViewProps {
  items: ArchiveItem[];
  objects: UrbexObject[];
  onAddItem: (item: ArchiveItem) => Promise<void>;
  onDeleteItem: (id: string) => Promise<void>;
}

const TYPE_FILTERS: Array<{ id: 'all' | ArchiveMediaType; label: string }> = [
  { id: 'all', label: 'Все' },
  { id: 'photo', label: 'Фото' },
  { id: 'video', label: 'Видео' },
  { id: 'audio', label: 'Аудио' },
  { id: 'note', label: 'Заметки' },
  { id: 'document', label: 'Документы' },
];

function detectTypeFromMime(mime: string, name: string): ArchiveMediaType {
  if (mime.startsWith('image/')) return 'photo';
  if (mime.startsWith('video/')) return 'video';
  if (mime.startsWith('audio/')) return 'audio';
  if (name.endsWith('.txt') || name.endsWith('.md')) return 'note';
  return 'document';
}

export default function ArchiveView({
  items,
  objects,
  onAddItem,
  onDeleteItem,
}: ArchiveViewProps) {
  const [typeFilter, setTypeFilter] = useState<'all' | ArchiveMediaType>('all');
  const [openedId, setOpenedId] = useState<string | null>(null);
  const [addingNote, setAddingNote] = useState(false);
  const [noteTitle, setNoteTitle] = useState('');
  const [noteBody, setNoteBody] = useState('');
  const [noteObjectId, setNoteObjectId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    if (typeFilter === 'all') return items;
    return items.filter((item) => item.type === typeFilter);
  }, [items, typeFilter]);

  const openedItem = useMemo(
    () => (openedId ? items.find((i) => i.id === openedId) ?? null : null),
    [items, openedId]
  );

  useEffect(() => {
    if (!openedItem && !addingNote) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpenedId(null);
        setAddingNote(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [openedItem, addingNote]);

  const handleFileUpload = (file: File | undefined) => {
    if (!file) return;
    if (file.size > 8 * 1024 * 1024) {
      setError('Файл превышает 8 МБ. Для экономии памяти PDA выберите файл меньшего размера.');
      return;
    }
    setError(null);
    const reader = new FileReader();
    reader.onload = async () => {
      const now = Date.now();
      const mediaType = detectTypeFromMime(file.type, file.name);
      const isText =
        file.type.startsWith('text/') ||
        file.name.endsWith('.md') ||
        file.name.endsWith('.txt');
      const item: ArchiveItem = {
        id: `arch-${now.toString(36)}`,
        title: file.name,
        type: mediaType,
        mimeType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
        dataUrl: !isText && typeof reader.result === 'string' ? reader.result : undefined,
        textContent: isText && typeof reader.result === 'string' ? reader.result : undefined,
        notes: 'Импортировано с устройства в локальный архив.',
        createdAt: now,
        updatedAt: now,
      };
      await onAddItem(item);
      setOpenedId(item.id);
    };
    if (
      file.type.startsWith('text/') ||
      file.name.endsWith('.md') ||
      file.name.endsWith('.txt')
    ) {
      reader.readAsText(file);
    } else {
      reader.readAsDataURL(file);
    }
  };

  const handleCreateNote = async (e: FormEvent) => {
    e.preventDefault();
    if (noteTitle.trim().length < 2 || noteBody.trim().length < 2) {
      setError('Введите заголовок и текст полевой заметки.');
      return;
    }
    const now = Date.now();
    const text = noteBody.trim();
    const item: ArchiveItem = {
      id: `arch-${now.toString(36)}`,
      title: noteTitle.trim(),
      type: 'note',
      objectId: noteObjectId || undefined,
      mimeType: 'text/plain',
      sizeBytes: new TextEncoder().encode(text).length,
      textContent: text,
      notes: 'Полевая заметка PDA',
      createdAt: now,
      updatedAt: now,
    };
    await onAddItem(item);
    setOpenedId(item.id);
    setAddingNote(false);
    setNoteTitle('');
    setNoteBody('');
    setNoteObjectId('');
    setError(null);
  };

  const handleDownloadItem = (item: ArchiveItem) => {
    if (item.dataUrl) {
      const a = document.createElement('a');
      a.href = item.dataUrl;
      a.download = item.title;
      document.body.appendChild(a);
      a.click();
      a.remove();
      return;
    }
    if (item.textContent) {
      const blob = new Blob([item.textContent], { type: 'text/plain;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = item.title.endsWith('.txt') ? item.title : `${item.title}.txt`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    }
  };

  const renderIcon = (t: ArchiveMediaType) => {
    if (t === 'photo') return <ImageIcon className="w-5 h-5 text-[#6DB386]" />;
    if (t === 'video') return <FileVideo className="w-5 h-5 text-[#DF9E43]" />;
    if (t === 'audio') return <FileAudio className="w-5 h-5 text-[#DF9E43]" />;
    return <FileText className="w-5 h-5 text-[#98A49E]" />;
  };

  return (
    <div className="space-y-4">
      {/* Filter & Upload Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 bg-[#131816] border border-[#252E2A] rounded-xl p-3">
        <div className="flex flex-wrap items-center gap-1 bg-[#0C0F0E] p-1 rounded-lg border border-[#252E2A]">
          {TYPE_FILTERS.map((f) => {
            const active = typeFilter === f.id;
            return (
              <motion.button
                key={f.id}
                whileTap={{ scale: 0.96 }}
                type="button"
                onClick={() => setTypeFilter(f.id)}
                className={`relative min-h-[36px] px-3 py-1 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
                  active
                    ? 'text-[#E6EBE8]'
                    : 'text-[#98A49E] hover:text-[#E6EBE8]'
                }`}
              >
                {active && (
                  <motion.span
                    layoutId="archive-type-filter"
                    transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                    className="absolute inset-0 rounded-md bg-[#1A201E] border border-[#323E39]"
                  />
                )}
                <span className="relative z-10">{f.label}</span>
              </motion.button>
            );
          })}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <motion.button
            whileTap={{ scale: 0.96 }}
            type="button"
            onClick={() => {
              setError(null);
              setAddingNote(true);
            }}
            className="min-h-[44px] px-3.5 py-2 rounded-lg border border-[#252E2A] bg-[#0C0F0E] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1.5 whitespace-nowrap"
          >
            <Plus className="w-4 h-4" />
            <span>Полевая заметка</span>
          </motion.button>

          <label className="min-h-[44px] px-4 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors cursor-pointer flex items-center gap-1.5 whitespace-nowrap">
            <Upload className="w-4 h-4" />
            <span>Добавить файл</span>
            <input
              type="file"
              accept="image/*,video/*,audio/*,.txt,.md,.pdf,.json"
              onChange={(e) => handleFileUpload(e.target.files?.[0])}
              className="sr-only"
            />
          </label>
        </div>
      </div>

      <AnimatePresence>
        {error && !addingNote && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.16 }}
            className="p-3 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs text-[#F1ACA7]"
          >
            {error}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Full-Width Archive Grid */}
      {filtered.length === 0 ? (
        <div className="p-10 rounded-xl bg-[#131816] border border-[#252E2A] text-sm text-[#98A49E] text-center">
          В выбранной категории нет материалов.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
          {filtered.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setOpenedId(item.id)}
              className="w-full text-left flex items-center gap-3.5 p-3.5 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] hover:-translate-y-0.5 active:scale-[0.985] transition-all"
            >
              {item.type === 'photo' && item.dataUrl ? (
                <ObjectImage
                  src={item.dataUrl}
                  alt={item.title}
                  compact
                  className="w-14 h-14 rounded-lg shrink-0 border border-[#252E2A]"
                />
              ) : (
                <div className="w-14 h-14 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center shrink-0">
                  {renderIcon(item.type)}
                </div>
              )}

              <div className="min-w-0 flex-1 space-y-1">
                <div className="text-sm font-semibold text-[#E6EBE8] truncate">
                  {item.title}
                </div>
                <div className="flex flex-wrap items-center gap-1.5 text-xs text-[#98A49E] font-mono tabular-nums">
                  <span>{ARCHIVE_TYPE_LABELS[item.type]}</span>
                  <span aria-hidden="true">·</span>
                  <span>{formatBytes(item.sizeBytes)}</span>
                  <span aria-hidden="true">·</span>
                  <span>{formatShortDate(item.createdAt)}</span>
                </div>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* Separate Centered Modal Window for Viewing Selected File/Item */}
      <AnimatePresence>
        {openedItem && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => setOpenedId(null)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-label={`Просмотр файла: ${openedItem.title}`}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.97, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              onClick={(e) => e.stopPropagation()}
              className="w-full max-w-3xl max-h-[88vh] flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden shadow-2xl"
            >
              <div className="flex items-start justify-between gap-3 px-5 py-4 border-b border-[#252E2A]">
                <div className="min-w-0">
                  <div className="text-xs text-[#98A49E] font-mono tabular-nums">
                    {ARCHIVE_TYPE_LABELS[openedItem.type]} · {formatBytes(openedItem.sizeBytes)} ·{' '}
                    {formatShortDate(openedItem.createdAt)}
                  </div>
                  <h2 className="text-lg font-semibold text-[#E6EBE8] mt-0.5 break-words">
                    {openedItem.title}
                  </h2>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  {(openedItem.dataUrl || openedItem.textContent) && (
                    <button
                      type="button"
                      onClick={() => handleDownloadItem(openedItem)}
                      aria-label={`Скачать ${openedItem.title}`}
                      className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#5B9D72]"
                    >
                      <Download className="w-4 h-4" />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={async () => {
                      const targetId = openedItem.id;
                      setOpenedId(null);
                      await onDeleteItem(targetId);
                    }}
                    aria-label={`Удалить материал ${openedItem.title}`}
                    className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860]"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setOpenedId(null)}
                    aria-label="Закрыть окно просмотра"
                    className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>

              <div className="p-5 overflow-y-auto space-y-4">
                {openedItem.type === 'photo' && (
                  <ObjectImage
                    src={openedItem.dataUrl}
                    alt={openedItem.title}
                    className="w-full max-h-[60vh] rounded-lg border border-[#252E2A]"
                  />
                )}

                {openedItem.type === 'video' && openedItem.dataUrl && (
                  <video
                    src={openedItem.dataUrl}
                    controls
                    className="w-full max-h-[60vh] rounded-lg border border-[#252E2A] bg-black"
                  />
                )}

                {openedItem.type === 'audio' && openedItem.dataUrl && (
                  <audio src={openedItem.dataUrl} controls className="w-full" />
                )}

                {openedItem.textContent && (
                  <pre className="p-4 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8] whitespace-pre-wrap font-sans leading-relaxed">
                    {openedItem.textContent}
                  </pre>
                )}

                <div className="text-xs text-[#98A49E]">{openedItem.notes}</div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Separate Centered Modal Window for Creating Field Note */}
      <AnimatePresence>
        {addingNote && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => setAddingNote(false)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-label="Новая полевая заметка в архив"
          >
            <motion.form
              initial={{ opacity: 0, scale: 0.97, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              onClick={(e) => e.stopPropagation()}
              onSubmit={handleCreateNote}
              className="w-full max-w-xl bg-[#131816] border border-[#252E2A] rounded-xl p-5 space-y-4 shadow-2xl"
            >
              <div className="flex items-center justify-between gap-3 pb-3 border-b border-[#252E2A]">
                <h2 className="text-base font-semibold text-[#E6EBE8]">
                  Новая полевая заметка в архив
                </h2>
                <button
                  type="button"
                  onClick={() => setAddingNote(false)}
                  aria-label="Закрыть окно заметки"
                  className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {error && (
                <div className="p-3 rounded-lg bg-[#381B19] border border-[#C7564E] text-xs text-[#F1ACA7]">
                  {error}
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <input
                  type="text"
                  value={noteTitle}
                  onChange={(e) => setNoteTitle(e.target.value)}
                  placeholder="Название заметки"
                  aria-label="Название заметки"
                  className="h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8]"
                />
                <select
                  value={noteObjectId}
                  onChange={(e) => setNoteObjectId(e.target.value)}
                  aria-label="Привязка к объекту"
                  className="h-11 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8]"
                >
                  <option value="">Без привязки к объекту</option>
                  {objects.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.title}
                    </option>
                  ))}
                </select>
              </div>

              <textarea
                rows={5}
                value={noteBody}
                onChange={(e) => setNoteBody(e.target.value)}
                placeholder="Наблюдения, замеры, маршрутные пометки..."
                aria-label="Текст заметки"
                className="w-full p-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-sm text-[#E6EBE8]"
              />

              <div className="flex justify-end gap-2 pt-2 border-t border-[#252E2A]">
                <button
                  type="button"
                  onClick={() => setAddingNote(false)}
                  className="min-h-[44px] px-4 py-2 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
                >
                  Отмена
                </button>
                <button
                  type="submit"
                  className="min-h-[44px] px-5 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold"
                >
                  Сохранить заметку
                </button>
              </div>
            </motion.form>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
