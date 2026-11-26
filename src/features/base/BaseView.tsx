import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { BookOpen, Pin, Plus, Search, Trash2, X } from 'lucide-react';
import { formatShortDate } from '../../core/labels';
import { tileEnterMotion } from '../../core/motion';
import type { BaseArticle } from '../../core/types';
import { UrbexSelect } from '../../components/UrbexSelect';

interface BaseViewProps {
  articles: BaseArticle[];
  editorEnabled?: boolean;
  onSaveArticle: (article: BaseArticle) => Promise<void>;
  onDeleteArticle: (id: string) => Promise<void>;
}

const SECTION_LABELS: Record<BaseArticle['section'], string> = {
  safety: 'Безопасность',
  navigation: 'Навигация',
  comms: 'Связь',
  architecture: 'Форматы и PDA',
  field_note: 'Личная заметка',
};

export default function BaseView({
  articles,
  editorEnabled = false,
  onSaveArticle,
  onDeleteArticle,
}: BaseViewProps) {
  const [query, setQuery] = useState('');
  const [sectionFilter, setSectionFilter] = useState<'all' | BaseArticle['section']>('all');
  const [openedId, setOpenedId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [newTitle, setNewTitle] = useState('');
  const [newSection, setNewSection] = useState<BaseArticle['section']>('field_note');
  const [newSummary, setNewSummary] = useState('');
  const [newContent, setNewContent] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return [...articles]
      .sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.updatedAt - a.updatedAt)
      .filter((art) => {
        if (sectionFilter !== 'all' && art.section !== sectionFilter) return false;
        if (!q) return true;
        return (
          art.title.toLowerCase().includes(q) ||
          art.summary.toLowerCase().includes(q) ||
          art.content.toLowerCase().includes(q)
        );
      });
  }, [articles, query, sectionFilter]);

  const openedArticle = useMemo(
    () => (openedId ? articles.find((a) => a.id === openedId) ?? null : null),
    [articles, openedId]
  );

  useEffect(() => {
    if (!editorEnabled) {
      setCreating(false);
    }
  }, [editorEnabled]);

  useEffect(() => {
    if (!openedArticle && !creating) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpenedId(null);
        setCreating(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [openedArticle, creating]);

  const handleCreateSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (newTitle.trim().length < 2 || newContent.trim().length < 4) {
      setFormError('Заполните заголовок и текст справочной записи.');
      return;
    }
    const now = Date.now();
    const article: BaseArticle = {
      id: `base-${now.toString(36)}`,
      title: newTitle.trim(),
      section: newSection,
      summary: newSummary.trim() || newContent.trim().slice(0, 90),
      content: newContent.trim(),
      updatedAt: now,
      pinned: false,
    };
    await onSaveArticle(article);
    setOpenedId(article.id);
    setCreating(false);
    setNewTitle('');
    setNewSummary('');
    setNewContent('');
    setFormError(null);
  };

  return (
    <div className="space-y-2.5 sm:space-y-3.5">
      {/* Toolbar */}
      <motion.div
        {...tileEnterMotion(0, 8)}
        className="bg-[#131816] border border-[#252E2A] rounded-xl p-2 sm:p-2.5 space-y-2"
      >
        <div className="flex items-center gap-2">
          <div className="relative flex-1 min-w-0">
            <Search className="w-4 h-4 text-[#6B7771] absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Поиск по базе знаний"
              placeholder="Поиск по инструкциям и заметкам..."
              className="w-full h-9 lg:h-10 pl-8 pr-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
          </div>

          {editorEnabled && (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.96 }}
              type="button"
              onClick={() => {
                setFormError(null);
                setCreating(true);
              }}
              className="min-h-[36px] lg:min-h-[40px] px-3 lg:px-4 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] transition-colors flex items-center justify-center gap-1 whitespace-nowrap shrink-0"
            >
              <Plus className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              <span className="lg:hidden">Запись</span>
              <span className="hidden lg:inline">Новая запись</span>
            </motion.button>
          )}
        </div>

        <div className="flex items-center gap-1 bg-[#0C0F0E] p-0.5 rounded-lg border border-[#252E2A] overflow-x-auto max-w-full">
          <motion.button
            whileTap={{ scale: 0.96 }}
            type="button"
            onClick={() => setSectionFilter('all')}
            className={`relative min-h-[28px] lg:min-h-[32px] px-2.5 py-0.5 rounded-md text-[11px] sm:text-xs font-medium whitespace-nowrap shrink-0 transition-colors ${
              sectionFilter === 'all'
                ? 'text-[#E6EBE8]'
                : 'text-[#98A49E] hover:text-[#E6EBE8]'
            }`}
          >
            {sectionFilter === 'all' && (
              <motion.span
                layoutId="base-section-filter"
                transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                className="absolute inset-0 rounded-md bg-[#1A201E] border border-[#323E39]"
              />
            )}
            <span className="relative z-10">Все разделы</span>
          </motion.button>
          {(Object.keys(SECTION_LABELS) as Array<BaseArticle['section']>).map((sec) => {
            const active = sectionFilter === sec;
            return (
              <motion.button
                key={sec}
                whileTap={{ scale: 0.96 }}
                type="button"
                onClick={() => setSectionFilter(sec)}
                className={`relative min-h-[28px] lg:min-h-[32px] px-2.5 py-0.5 rounded-md text-[11px] sm:text-xs font-medium whitespace-nowrap shrink-0 transition-colors ${
                  active
                    ? 'text-[#E6EBE8]'
                    : 'text-[#98A49E] hover:text-[#E6EBE8]'
                }`}
              >
                {active && (
                  <motion.span
                    layoutId="base-section-filter"
                    transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                    className="absolute inset-0 rounded-md bg-[#1A201E] border border-[#323E39]"
                  />
                )}
                <span className="relative z-10">{SECTION_LABELS[sec]}</span>
              </motion.button>
            );
          })}
        </div>
      </motion.div>

      {/* Full-Width Articles Grid */}
      {filtered.length === 0 ? (
        <div className="p-8 rounded-xl bg-[#131816] border border-[#252E2A] text-sm text-[#98A49E] text-center">
          Записи не найдены.
        </div>
      ) : (
        <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 xl:grid-cols-3 gap-2.5">
          {filtered.map((art, idx) => (
            <motion.button
              key={art.id}
              {...tileEnterMotion(idx + 1)}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.985 }}
              type="button"
              onClick={() => setOpenedId(art.id)}
              className="w-full text-left p-3 lg:p-4 rounded-xl bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] transition-colors flex flex-col justify-between gap-2"
            >
              <div className="space-y-1">
                <div className="flex items-center justify-between gap-2 text-[11px] sm:text-xs text-[#98A49E]">
                  <span className="flex items-center gap-1.5">
                    <BookOpen className="w-3.5 h-3.5 text-[#5B9D72]" />
                    <span>{SECTION_LABELS[art.section]}</span>
                  </span>
                  <span>
                    {art.pinned ? 'Закреплено · ' : ''}
                    {formatShortDate(art.updatedAt)}
                  </span>
                </div>
                <h3 className="text-sm sm:text-base font-semibold text-[#E6EBE8] leading-snug line-clamp-1">
                  {art.title}
                </h3>
                <p className="text-xs text-[#98A49E] line-clamp-2 leading-relaxed">
                  {art.summary}
                </p>
              </div>
            </motion.button>
          ))}
        </div>
      )}

      {/* Separate Centered Modal Window for Reading Article */}
      <AnimatePresence>
        {openedArticle && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => setOpenedId(null)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-label={`Справочник: ${openedArticle.title}`}
          >
            <motion.article
              initial={{ opacity: 0, scale: 0.97, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              onClick={(e) => e.stopPropagation()}
              className="w-full max-w-3xl max-h-[92dvh] flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden shadow-2xl"
            >
              <div className="flex items-center justify-between gap-3 px-4 py-2.5 sm:px-5 sm:py-3.5 border-b border-[#252E2A]">
                <div className="space-y-0.5 min-w-0">
                  <div className="flex items-center gap-2 text-[11px] sm:text-xs text-[#98A49E]">
                    <BookOpen className="w-3.5 h-3.5 text-[#5B9D72]" />
                    <span>{SECTION_LABELS[openedArticle.section]}</span>
                    <span aria-hidden="true">·</span>
                    <span>Обновлено {formatShortDate(openedArticle.updatedAt)}</span>
                  </div>
                  <h2 className="text-base sm:text-xl font-semibold text-[#E6EBE8] truncate">
                    {openedArticle.title}
                  </h2>
                </div>

                <div className="flex items-center gap-1.5 shrink-0">
                  {editorEnabled && (
                    <>
                      <button
                        type="button"
                        onClick={() =>
                          onSaveArticle({
                            ...openedArticle,
                            pinned: !openedArticle.pinned,
                            updatedAt: Date.now(),
                          })
                        }
                        aria-label="Закрепить материал"
                        aria-pressed={openedArticle.pinned}
                        className={`min-h-[36px] min-w-[36px] sm:min-h-[40px] sm:min-w-[40px] flex items-center justify-center rounded-lg border ${
                          openedArticle.pinned
                            ? 'border-[#D39239] bg-[#362816] text-[#DF9E43]'
                            : 'border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                        }`}
                      >
                        <Pin className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={async () => {
                          const targetId = openedArticle.id;
                          setOpenedId(null);
                          await onDeleteArticle(targetId);
                        }}
                        aria-label={`Удалить запись ${openedArticle.title}`}
                        className="min-h-[36px] min-w-[36px] sm:min-h-[40px] sm:min-w-[40px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#DC6860]"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </>
                  )}
                  <button
                    type="button"
                    onClick={() => setOpenedId(null)}
                    aria-label="Закрыть статью"
                    className="min-h-[36px] min-w-[36px] sm:min-h-[40px] sm:min-w-[40px] flex items-center justify-center rounded-lg border border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                  >
                    <X className="w-4 h-4 sm:w-5 sm:h-5" />
                  </button>
                </div>
              </div>

              <div className="p-3.5 sm:p-5 overflow-y-auto whitespace-pre-line text-xs sm:text-sm text-[#E6EBE8] leading-relaxed">
                {openedArticle.content}
              </div>
            </motion.article>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Separate Centered Modal Window for Creating Article */}
      <AnimatePresence>
        {editorEnabled && creating && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.16 }}
            onClick={() => setCreating(false)}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-[2px] p-3 sm:p-6"
            role="dialog"
            aria-modal="true"
            aria-label="Добавить материал в локальную базу"
          >
            <motion.form
              initial={{ opacity: 0, scale: 0.97, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.97, y: 6 }}
              transition={{ duration: 0.18, ease: 'easeOut' }}
              onClick={(e) => e.stopPropagation()}
              onSubmit={handleCreateSubmit}
              className="w-full max-w-2xl max-h-[92dvh] overflow-y-auto bg-[#131816] border border-[#252E2A] rounded-xl p-3.5 sm:p-5 space-y-2.5 shadow-2xl"
            >
              <div className="flex items-center justify-between gap-3 pb-2.5 border-b border-[#252E2A]">
                <h2 className="text-sm sm:text-base font-semibold text-[#E6EBE8]">
                  Добавить материал в локальную базу
                </h2>
                <button
                  type="button"
                  onClick={() => setCreating(false)}
                  aria-label="Закрыть окно создания"
                  className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {formError && (
                <p className="text-xs text-[#F1ACA7]" role="alert">
                  {formError}
                </p>
              )}

              <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-2 gap-2.5 items-center">
                <input
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="Заголовок материала"
                  aria-label="Заголовок материала"
                  className="h-[42px] px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] focus:outline-none focus:border-[#5B9D72]"
                />
                <UrbexSelect
                  ariaLabel="Раздел базы"
                  value={newSection}
                  onChange={(val) => setNewSection(val)}
                  options={(Object.keys(SECTION_LABELS) as Array<BaseArticle['section']>).map(
                    (sec) => ({
                      value: sec,
                      label: SECTION_LABELS[sec],
                    })
                  )}
                />
              </div>

              <input
                type="text"
                value={newSummary}
                onChange={(e) => setNewSummary(e.target.value)}
                placeholder="Краткая аннотация (необязательно)"
                aria-label="Краткая аннотация"
                className="w-full h-9 lg:h-10 px-3 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
              />

              <textarea
                rows={3}
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                placeholder="Полный текст памятки, чек-листа или технической справки..."
                aria-label="Текст справочника"
                className="w-full p-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8]"
              />

              <div className="flex justify-end gap-2 pt-2 border-t border-[#252E2A]">
                <button
                  type="button"
                  onClick={() => setCreating(false)}
                  className="min-h-[36px] sm:min-h-[40px] px-4 py-1.5 rounded-lg border border-[#252E2A] text-xs text-[#98A49E]"
                >
                  Отмена
                </button>
                <button
                  type="submit"
                  className="min-h-[36px] sm:min-h-[40px] px-5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold"
                >
                  Сохранить в базу
                </button>
              </div>
            </motion.form>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
