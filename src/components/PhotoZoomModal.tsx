import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import { ChevronLeft, ChevronRight, RotateCcw, X, ZoomIn, ZoomOut } from 'lucide-react';

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

  useEffect(() => {
    if (!isOpen || !activeSrc) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
      if (e.key === '+' || e.key === '=') setScale((s) => clampScale(s + 0.5));
      if (e.key === '-') {
        setScale((s) => {
          const next = clampScale(s - 0.5);
          if (next <= 1) setOffset({ x: 0, y: 0 });
          return next;
        });
      }
      if (e.key === '0') {
        setScale(1);
        setOffset({ x: 0, y: 0 });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isOpen, activeSrc, onClose, goPrev, goNext]);

  const adjustScale = (next: number) => {
    const clamped = clampScale(next);
    setScale(clamped);
    if (clamped <= 1) {
      setOffset({ x: 0, y: 0 });
    }
  };

  const modalContent = (
    <AnimatePresence>
      {isOpen && activeSrc && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.14 }}
          className="fixed inset-0 z-[80] flex flex-col bg-black/92 backdrop-blur-sm select-none"
          role="dialog"
          aria-modal="true"
          aria-label={title ? `Просмотр фото: ${title}` : 'Просмотр фото'}
          onClick={onClose}
        >
          {/* Pinned Top Toolbar */}
          <div
            onClick={(e) => e.stopPropagation()}
            className="shrink-0 flex items-center justify-between gap-2 px-3 sm:px-5 py-2.5 bg-[#0C0F0E]/90 border-b border-[#252E2A]"
          >
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <div className="text-xs sm:text-sm font-semibold text-[#E6EBE8] truncate">
                  {title || 'Просмотр снимка'}
                </div>
                {gallery.length > 1 && (
                  <span className="px-1.5 py-0.5 rounded bg-[#1A201E] border border-[#323E39] font-mono tabular-nums text-[11px] text-[#6DB386] shrink-0">
                    {currentIndex + 1} / {gallery.length}
                  </span>
                )}
              </div>
              <div className="text-[11px] font-mono tabular-nums text-[#98A49E]">
                Масштаб: {Math.round(scale * 100)}% · двойной клик или колесо для зума
              </div>
            </div>

            <div className="flex items-center gap-1.5 shrink-0">
              {gallery.length > 1 && (
                <>
                  <button
                    type="button"
                    onClick={goPrev}
                    aria-label="Предыдущее фото"
                    className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg bg-[#131816] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72]"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={goNext}
                    aria-label="Следующее фото"
                    className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg bg-[#131816] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] mr-1"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={() => adjustScale(scale - 0.5)}
                disabled={scale <= MIN_SCALE}
                aria-label="Уменьшить масштаб"
                className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg bg-[#131816] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] disabled:opacity-40"
              >
                <ZoomOut className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => adjustScale(1)}
                aria-label="Сбросить масштаб"
                className="min-h-[36px] px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] font-mono text-xs text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>1:1</span>
              </button>
              <button
                type="button"
                onClick={() => adjustScale(scale + 0.5)}
                disabled={scale >= MAX_SCALE}
                aria-label="Увеличить масштаб"
                className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg bg-[#131816] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] disabled:opacity-40"
              >
                <ZoomIn className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть просмотр фото"
                className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg bg-[#131816] border border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8] hover:border-[#C7564E] ml-1"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Interactive Zoom Canvas */}
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
            className={`relative flex-1 min-h-0 flex items-center justify-center overflow-hidden touch-none p-2 sm:p-6 ${
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
          </div>

          {gallery.length > 1 && (
            <div
              onClick={(e) => e.stopPropagation()}
              className="shrink-0 flex items-center justify-center gap-2 px-3 py-2 bg-[#0C0F0E]/90 border-t border-[#252E2A] overflow-x-auto"
            >
              {gallery.map((thumb, idx) => (
                <button
                  key={`${idx}-${thumb.slice(0, 24)}`}
                  type="button"
                  onClick={() => setCurrentIndex(idx)}
                  aria-label={`Выбрать фото ${idx + 1}`}
                  className={`w-12 h-10 rounded-md overflow-hidden border-2 shrink-0 transition-all ${
                    idx === currentIndex
                      ? 'border-[#5B9D72] scale-105'
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
        </motion.div>
      )}
    </AnimatePresence>
  );

  if (typeof document === 'undefined') return null;
  return createPortal(modalContent, document.body);
}
