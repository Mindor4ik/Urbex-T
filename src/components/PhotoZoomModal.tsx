import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import { ChevronLeft, ChevronRight, Minus, Plus, RotateCcw, X } from 'lucide-react';

interface PhotoZoomModalProps {
  src: string | null;
  photos?: string[];
  initialIndex?: number;
  title?: string;
  onClose: () => void;
}

const MIN_SCALE = 1;
const MAX_SCALE = 5;

function clampScale(val: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, Number(val.toFixed(2))));
}

export function PhotoZoomModal({
  src,
  photos,
  initialIndex = 0,
  title,
  onClose,
}: PhotoZoomModalProps) {
  const gallery = useMemo(() => {
    if (photos && photos.length > 0) {
      return photos;
    }
    return src ? [src] : [];
  }, [photos, src]);

  const isOpen = Boolean(src) || Boolean(photos && photos.length > 0);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);

  const dragStartRef = useRef<{ x: number; y: number; origX: number; origY: number } | null>(null);
  const pinchStartRef = useRef<{ dist: number; scale: number } | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    if (photos && photos.length > 0) {
      const clampedIdx = Math.max(0, Math.min(photos.length - 1, initialIndex));
      setCurrentIndex(clampedIdx);
    } else {
      setCurrentIndex(0);
    }
  }, [isOpen, src, photos, initialIndex]);

  const activeSrc = gallery[currentIndex] ?? src ?? null;

  useEffect(() => {
    setScale(1);
    setOffset({ x: 0, y: 0 });
  }, [activeSrc]);

  const goPrev = useCallback(() => {
    if (gallery.length <= 1) return;
    setCurrentIndex((prev) => (prev - 1 + gallery.length) % gallery.length);
  }, [gallery.length]);

  const goNext = useCallback(() => {
    if (gallery.length <= 1) return;
    setCurrentIndex((prev) => (prev + 1) % gallery.length);
  }, [gallery.length]);

  const adjustScale = useCallback((next: number) => {
    const clamped = clampScale(next);
    setScale(clamped);
    if (clamped <= 1) {
      setOffset({ x: 0, y: 0 });
    }
  }, []);

  useEffect(() => {
    if (!isOpen || !activeSrc) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
      if (e.key === '+' || e.key === '=') adjustScale(scale + 0.5);
      if (e.key === '-') adjustScale(scale - 0.5);
      if (e.key === '0') adjustScale(1);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isOpen, activeSrc, onClose, goPrev, goNext, adjustScale, scale]);

  const scalePct = Math.round(scale * 100);
  const isZoomed = scale > 1.02;

  const modalContent = (
    <AnimatePresence>
      {isOpen && activeSrc && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.14 }}
          className="fixed inset-0 z-[80] flex flex-col bg-[#050706]/96 backdrop-blur-md select-none"
          role="dialog"
          aria-modal="true"
          aria-label={title ? `Просмотр фото: ${title}` : 'Просмотр фото'}
          onClick={onClose}
        >
          {/* Clean Top Header: Title + Photo Counter + Single Close Button */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="shrink-0 flex items-center justify-between gap-3 px-3 sm:px-5 pt-[max(0.625rem,env(safe-area-inset-top))] pb-2.5 bg-[#0C0F0E]/92 border-b border-[#252E2A]"
          >
            <div className="min-w-0 flex-1 flex items-center gap-2.5">
              <h2 className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                {title || 'Просмотр снимка'}
              </h2>
              {gallery.length > 1 && (
                <span className="px-2 py-0.5 rounded-md bg-[#1A201E] border border-[#323E39] font-mono tabular-nums text-[11px] font-semibold text-[#6DB386] shrink-0">
                  {currentIndex + 1} / {gallery.length}
                </span>
              )}
              <span className="px-2 py-0.5 rounded-md bg-[#131816] border border-[#252E2A] font-mono tabular-nums text-[11px] text-[#98A49E] shrink-0">
                {scalePct}%
              </span>
            </div>

            <button
              type="button"
              onClick={onClose}
              aria-label="Закрыть просмотр фото"
              className="w-10 h-10 shrink-0 flex items-center justify-center rounded-xl bg-[#131816] border border-[#252E2A] text-[#C8D1CC] hover:text-[#E6EBE8] hover:border-[#C7564E] hover:bg-[#231514] transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Maximized Edge-to-Edge Interactive Canvas */}
          <div
            onClick={(e) => e.stopPropagation()}
            onWheel={(e) => {
              e.preventDefault();
              const delta = e.deltaY < 0 ? 0.35 : -0.35;
              adjustScale(scale + delta);
            }}
            onDoubleClick={() => {
              if (scale > 1.05) {
                adjustScale(1);
              } else {
                adjustScale(2.5);
              }
            }}
            onPointerDown={(e) => {
              if (scale <= 1) return;
              (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
              setDragging(true);
              dragStartRef.current = {
                x: e.clientX,
                y: e.clientY,
                origX: offset.x,
                origY: offset.y,
              };
            }}
            onPointerMove={(e) => {
              if (!dragging || !dragStartRef.current) return;
              const dx = e.clientX - dragStartRef.current.x;
              const dy = e.clientY - dragStartRef.current.y;
              setOffset({
                x: dragStartRef.current.origX + dx,
                y: dragStartRef.current.origY + dy,
              });
            }}
            onPointerUp={() => {
              setDragging(false);
              dragStartRef.current = null;
            }}
            onPointerCancel={() => {
              setDragging(false);
              dragStartRef.current = null;
            }}
            onTouchStart={(e) => {
              if (e.touches.length === 2) {
                const dx = e.touches[0].clientX - e.touches[1].clientX;
                const dy = e.touches[0].clientY - e.touches[1].clientY;
                pinchStartRef.current = {
                  dist: Math.hypot(dx, dy),
                  scale,
                };
              }
            }}
            onTouchMove={(e) => {
              if (e.touches.length === 2 && pinchStartRef.current) {
                const dx = e.touches[0].clientX - e.touches[1].clientX;
                const dy = e.touches[0].clientY - e.touches[1].clientY;
                const dist = Math.hypot(dx, dy);
                if (pinchStartRef.current.dist > 0) {
                  adjustScale(pinchStartRef.current.scale * (dist / pinchStartRef.current.dist));
                }
              }
            }}
            onTouchEnd={() => {
              pinchStartRef.current = null;
            }}
            className={`relative flex-1 min-h-0 flex items-center justify-center overflow-hidden touch-none p-2 sm:p-4 ${
              scale > 1 ? (dragging ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-zoom-in'
            }`}
          >
            <img
              src={activeSrc}
              alt={title || 'Фото объекта'}
              referrerPolicy="no-referrer"
              draggable={false}
              style={{
                transform: `translate3d(${offset.x}px, ${offset.y}px, 0) scale(${scale})`,
                transition: dragging ? 'none' : 'transform 120ms ease-out',
              }}
              className="max-w-full max-h-full object-contain rounded-lg shadow-2xl select-none pointer-events-none"
            />

            {gallery.length > 1 && (
              <>
                <button
                  type="button"
                  onClick={goPrev}
                  aria-label="Предыдущее фото"
                  className="absolute left-2.5 sm:left-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-[#0C0F0E]/88 border border-[#323E39] text-[#E6EBE8] hover:border-[#5B9D72] flex items-center justify-center shadow-lg transition-colors"
                >
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <button
                  type="button"
                  onClick={goNext}
                  aria-label="Следующее фото"
                  className="absolute right-2.5 sm:right-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-[#0C0F0E]/88 border border-[#323E39] text-[#E6EBE8] hover:border-[#5B9D72] flex items-center justify-center shadow-lg transition-colors"
                >
                  <ChevronRight className="w-5 h-5" />
                </button>
              </>
            )}
          </div>

          {/* Dedicated Bottom Gallery + Zoom Controls Dock */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="shrink-0 bg-[#0C0F0E]/94 border-t border-[#252E2A] px-3 sm:px-5 pt-2.5 pb-[max(0.75rem,env(safe-area-inset-bottom))] space-y-2"
          >
            {gallery.length > 1 && (
              <div className="flex items-center justify-center gap-2 overflow-x-auto pb-0.5">
                {gallery.map((thumb, idx) => (
                  <button
                    key={`${idx}-${thumb.slice(0, 24)}`}
                    type="button"
                    onClick={() => setCurrentIndex(idx)}
                    aria-label={`Выбрать фото ${idx + 1}`}
                    className={`w-12 h-10 rounded-lg overflow-hidden border-2 shrink-0 transition-all ${
                      idx === currentIndex
                        ? 'border-[#5B9D72] scale-105 shadow-[0_0_10px_rgba(91,157,114,0.3)]'
                        : 'border-[#252E2A] opacity-65 hover:opacity-100'
                    }`}
                  >
                    <img
                      src={thumb}
                      alt={`Миниатюра ${idx + 1}`}
                      referrerPolicy="no-referrer"
                      className="w-full h-full object-cover"
                    />
                  </button>
                ))}
              </div>
            )}

            <div className="flex items-center justify-between gap-3 max-w-xl mx-auto">
              <span className="hidden sm:inline text-[11px] font-mono text-[#6B7771]">
                Двойной клик или колесо для зума
              </span>

              <div className="mx-auto sm:mx-0 inline-flex items-center gap-1.5 p-1 rounded-xl bg-[#131816] border border-[#252E2A]">
                <button
                  type="button"
                  onClick={() => adjustScale(scale - 0.5)}
                  disabled={scale <= MIN_SCALE}
                  aria-label="Уменьшить масштаб"
                  className="w-10 h-9 flex items-center justify-center rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] disabled:opacity-35 transition-colors"
                >
                  <Minus className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => adjustScale(1)}
                  aria-label="Сбросить масштаб"
                  className={`h-9 px-3 rounded-lg border font-mono tabular-nums text-xs font-semibold flex items-center gap-1.5 transition-colors ${
                    isZoomed
                      ? 'bg-[#203328] border-[#5B9D72] text-[#6DB386]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72]'
                  }`}
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>1:1 · {scalePct}%</span>
                </button>
                <button
                  type="button"
                  onClick={() => adjustScale(scale + 0.5)}
                  disabled={scale >= MAX_SCALE}
                  aria-label="Увеличить масштаб"
                  className="w-10 h-9 flex items-center justify-center rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] disabled:opacity-35 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(modalContent, document.body);
}
