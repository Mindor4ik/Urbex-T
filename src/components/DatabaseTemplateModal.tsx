import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import { Check, Code2, Copy, Database, Download, FileText, X } from 'lucide-react';
import {
  buildUrbexDbTemplateJson,
  parseAndVerifyUrbexDb,
  URBEXDB_SCHEMA_DOCS,
} from '../core/urbexdb';
import type { UrbexDbBundle } from '../core/types';

interface DatabaseTemplateModalProps {
  open: boolean;
  onClose: () => void;
  onCreateFromTemplate?: (bundle: UrbexDbBundle) => Promise<void>;
}

export function DatabaseTemplateModal({
  open,
  onClose,
  onCreateFromTemplate,
}: DatabaseTemplateModalProps) {
  const [tab, setTab] = useState<'template' | 'schema'>('template');
  const [withHints, setWithHints] = useState(false);
  const [copied, setCopied] = useState(false);
  const [creating, setCreating] = useState(false);

  const templateJson = useMemo(() => buildUrbexDbTemplateJson(withHints), [withHints]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(templateJson);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // ignore clipboard errors
    }
  };

  const handleDownloadTemplate = () => {
    const blob = new Blob([templateJson], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'urbex-template.urbexdb';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const handleCreateSampleDb = async () => {
    if (!onCreateFromTemplate) return;
    const parsed = parseAndVerifyUrbexDb(templateJson);
    if (!parsed.ok) return;
    setCreating(true);
    try {
      await onCreateFromTemplate(parsed.bundle);
      onClose();
    } finally {
      setCreating(false);
    }
  };

  const modalContent = (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.14 }}
          onClick={onClose}
          className="fixed inset-0 z-[70] flex items-center justify-center bg-black/80 backdrop-blur-[2px] p-2.5 sm:p-5"
          role="dialog"
          aria-modal="true"
          aria-labelledby="db-schema-title"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.97, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-3xl max-h-[90dvh] flex flex-col bg-[#131816] border border-[#252E2A] rounded-xl overflow-hidden shadow-2xl text-[#E6EBE8]"
          >
            {/* Pinned Header */}
            <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-2.5 sm:px-5 sm:py-3 border-b border-[#252E2A]">
              <div className="min-w-0">
                <h2 id="db-schema-title" className="text-sm sm:text-base font-semibold truncate">
                  Формат .urbexdb: схема и шаблон для кода
                </h2>
                <p className="text-[11px] text-[#98A49E] truncate">
                  Спецификация JSON v1.0.0 для ручного создания и генерации баз (Москва, Тюмень и др.)
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть справку формата базы"
                className="min-h-[34px] min-w-[34px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Pinned Tab Bar */}
            <div className="shrink-0 flex flex-wrap items-center justify-between gap-2 px-4 py-2 bg-[#0C0F0E] border-b border-[#252E2A]">
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => setTab('template')}
                  className={`min-h-[32px] px-3 py-1 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors ${
                    tab === 'template'
                      ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-transparent border-transparent text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  <Code2 className="w-3.5 h-3.5 text-[#6DB386]" />
                  <span>Шаблон JSON</span>
                </button>
                <button
                  type="button"
                  onClick={() => setTab('schema')}
                  className={`min-h-[32px] px-3 py-1 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors ${
                    tab === 'schema'
                      ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-transparent border-transparent text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5 text-[#DF9E43]" />
                  <span>Справочник полей</span>
                </button>
              </div>

              {tab === 'template' && (
                <label className="flex items-center gap-2 px-2.5 py-1 rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] cursor-pointer text-xs text-[#E6EBE8] select-none transition-colors">
                  <input
                    type="checkbox"
                    checked={withHints}
                    onChange={(e) => setWithHints(e.target.checked)}
                    className="w-3.5 h-3.5 accent-[#5B9D72] rounded cursor-pointer"
                  />
                  <span>Подсказки и правила в коде</span>
                </label>
              )}
            </div>

            {/* Scrollable Body */}
            <div className="flex-1 min-h-0 overflow-y-auto p-3.5 sm:p-5 space-y-3">
              {tab === 'template' ? (
                <>
                  <div className="p-2.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#98A49E] leading-relaxed">
                    Вы можете писать файл <span className="font-mono text-[#E6EBE8]">.urbexdb</span>{' '}
                    вручную или генерировать скриптом. Укажите{' '}
                    <span className="font-mono text-[#DF9E43]">"checksum": "manual"</span> — при
                    импорте Urbex-T автоматически пересчитает контрольную сумму, примет{' '}
                    <span className="font-mono text-[#E6EBE8]">photoUrl</span> /{' '}
                    <span className="font-mono text-[#E6EBE8]">photoUri</span> и{' '}
                    {withHints
                      ? 'очистит // и блочные комментарии с подсказками автоматически.'
                      : 'подставит недостающие таблицы по умолчанию.'}
                  </div>

                  <pre className="p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] font-mono text-[11px] leading-relaxed text-[#CDE2D4] overflow-x-auto whitespace-pre">
                    {templateJson}
                  </pre>
                </>
              ) : (
                <div className="space-y-3">
                  {URBEXDB_SCHEMA_DOCS.map((section) => (
                    <div
                      key={section.section}
                      className="p-3 rounded-xl bg-[#0C0F0E] border border-[#252E2A] space-y-2"
                    >
                      <h3 className="text-xs font-semibold text-[#6DB386]">{section.section}</h3>
                      <div className="divide-y divide-[#252E2A]">
                        {section.fields.map((f) => (
                          <div key={f.name} className="py-1.5 flex flex-col sm:flex-row sm:items-baseline gap-1 sm:gap-3 text-xs">
                            <div className="sm:w-44 shrink-0 font-mono text-[#E6EBE8]">{f.name}</div>
                            <div className="sm:w-32 shrink-0 font-mono text-[11px] text-[#DF9E43]">
                              {f.type}
                            </div>
                            <div className="text-[#98A49E] text-[11px] sm:text-xs">{f.desc}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Pinned Footer */}
            <div className="shrink-0 flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 sm:px-5 border-t border-[#252E2A] bg-[#131816]">
              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={handleCopy}
                  className="min-h-[34px] px-3 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1.5"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-[#6DB386]" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copied ? 'Скопировано' : 'Скопировать JSON'}</span>
                </button>
                <button
                  type="button"
                  onClick={handleDownloadTemplate}
                  className="min-h-[34px] px-3 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1.5"
                >
                  <Download className="w-3.5 h-3.5 text-[#DF9E43]" />
                  <span>Скачать template.urbexdb</span>
                </button>
              </div>

              <div className="flex items-center gap-2 ml-auto">
                {onCreateFromTemplate && (
                  <button
                    type="button"
                    disabled={creating}
                    onClick={handleCreateSampleDb}
                    className="min-h-[34px] px-3.5 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] disabled:opacity-50 flex items-center gap-1.5"
                  >
                    <Database className="w-3.5 h-3.5" />
                    <span>{creating ? 'Создание...' : 'Создать БД из шаблона'}</span>
                  </button>
                )}
                <button
                  type="button"
                  onClick={onClose}
                  className="min-h-[34px] px-3 py-1 rounded-lg border border-[#252E2A] text-xs text-[#98A49E] hover:text-[#E6EBE8]"
                >
                  Закрыть
                </button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(modalContent, document.body);
}
