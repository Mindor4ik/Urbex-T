import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';

export interface UrbexSelectOption<T extends string | number = string> {
  value: T;
  label: string;
  description?: string;
  badge?: string;
  dotColor?: string;
  disabled?: boolean;
}

interface UrbexSelectProps<T extends string | number = string> {
  value: T;
  options: ReadonlyArray<UrbexSelectOption<T>>;
  onChange: (value: T) => void;
  label?: string;
  placeholder?: string;
  ariaLabel?: string;
  disabled?: boolean;
  compact?: boolean;
  className?: string;
  triggerClassName?: string;
  menuMaxHeightClass?: string;
}

interface MenuCoords {
  left: number;
  width: number;
  top?: number;
  bottom?: number;
  openUp: boolean;
  maxHeight: number;
}

export function UrbexSelect<T extends string | number = string>({
  value,
  options,
  onChange,
  label,
  placeholder = 'Выбрать...',
  ariaLabel,
  disabled = false,
  compact = false,
  className = '',
  triggerClassName = '',
  menuMaxHeightClass = 'max-h-64',
}: UrbexSelectProps<T>) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState<number>(-1);
  const [coords, setCoords] = useState<MenuCoords | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const labelId = useId();
  const listboxId = useId();

  const selectedOption = useMemo(
    () => options.find((opt) => String(opt.value) === String(value)) ?? null,
    [options, value]
  );

  const selectedIndex = useMemo(
    () => options.findIndex((opt) => String(opt.value) === String(value)),
    [options, value]
  );

  const updatePosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const viewportH = window.innerHeight;
    const viewportW = window.innerWidth;
    const spaceBelow = viewportH - rect.bottom - 12;
    const spaceAbove = rect.top - 12;
    const openUp = spaceBelow < 210 && spaceAbove > spaceBelow;
    const maxHeight = Math.max(140, Math.min(280, openUp ? spaceAbove : spaceBelow));
    const clampedWidth = Math.min(Math.max(rect.width, 210), viewportW - 16);
    const left = Math.min(Math.max(8, rect.left), Math.max(8, viewportW - clampedWidth - 8));

    setCoords({
      left,
      width: clampedWidth,
      top: openUp ? undefined : rect.bottom + 6,
      bottom: openUp ? viewportH - rect.top + 6 : undefined,
      openUp,
      maxHeight,
    });
  }, []);

  useEffect(() => {
    if (!open) return;
    updatePosition();
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);

    const handlePointerDown = (e: MouseEvent | TouchEvent) => {
      const target = e.target as Node | null;
      if (!target) return;
      if (triggerRef.current?.contains(target)) return;
      if (menuRef.current?.contains(target)) return;
      setOpen(false);
    };

    const handleScrollOrResize = () => {
      updatePosition();
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown, { passive: true });
    window.addEventListener('resize', handleScrollOrResize);
    window.addEventListener('scroll', handleScrollOrResize, true);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
      window.removeEventListener('resize', handleScrollOrResize);
      window.removeEventListener('scroll', handleScrollOrResize, true);
    };
  }, [open, selectedIndex, updatePosition]);

  useEffect(() => {
    if (!open || activeIndex < 0) return;
    optionRefs.current[activeIndex]?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex]);

  const handleSelect = (opt: UrbexSelectOption<T>) => {
    if (opt.disabled) return;
    onChange(opt.value);
    setOpen(false);
    triggerRef.current?.focus();
  };

  const moveActive = (delta: number) => {
    if (options.length === 0) return;
    let next = activeIndex < 0 ? 0 : activeIndex;
    for (let step = 0; step < options.length; step++) {
      next = (next + delta + options.length) % options.length;
      if (!options[next]?.disabled) {
        setActiveIndex(next);
        return;
      }
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (disabled) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) {
        setOpen(true);
      } else {
        moveActive(1);
      }
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (!open) {
        setOpen(true);
      } else {
        moveActive(-1);
      }
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (!open) {
        setOpen(true);
      } else if (activeIndex >= 0 && options[activeIndex]) {
        handleSelect(options[activeIndex]);
      }
    } else if (e.key === 'Escape' && open) {
      e.preventDefault();
      setOpen(false);
    } else if (e.key === 'Tab' && open) {
      setOpen(false);
    }
  };

  return (
    <div className={`w-full ${className}`}>
      {label && (
        <span
          id={labelId}
          className="mb-1.5 block text-[11px] font-medium text-[#98A49E]"
        >
          {label}
        </span>
      )}
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listboxId : undefined}
        aria-labelledby={label ? labelId : undefined}
        aria-label={!label ? ariaLabel ?? placeholder : undefined}
        onClick={() => !disabled && setOpen((prev) => !prev)}
        onKeyDown={handleKeyDown}
        className={`group flex w-full items-center justify-between gap-2 rounded-lg border text-left transition-all ${
          compact ? 'min-h-[38px] px-2.5 py-1.5 text-[12px]' : 'min-h-[42px] px-3 py-2 text-[13px]'
        } ${
          disabled
            ? 'cursor-not-allowed border-[#252E2A] bg-[#0C0F0E]/60 text-[#6B7771] opacity-60'
            : open
              ? 'border-[#6DB386] bg-[#1A201E] text-[#E6EBE8] shadow-[0_0_16px_rgba(91,157,114,0.28)]'
              : 'border-[#252E2A] bg-[#0C0F0E] text-[#E6EBE8] hover:border-[#323E39] hover:shadow-[0_0_12px_rgba(91,157,114,0.14)]'
        } ${triggerClassName}`}
      >
        <span className="flex min-w-0 flex-1 items-center gap-2">
          {selectedOption?.dotColor && (
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full shadow-[0_0_8px_currentColor]"
              style={{ backgroundColor: selectedOption.dotColor, color: selectedOption.dotColor }}
            />
          )}
          <span className="truncate font-medium">
            {selectedOption ? selectedOption.label : placeholder}
          </span>
          {selectedOption?.badge && (
            <span className="shrink-0 rounded bg-[#203328] px-1.5 py-0.5 text-[10px] font-medium text-[#6DB386]">
              {selectedOption.badge}
            </span>
          )}
        </span>
        <ChevronDown
          size={compact ? 15 : 16}
          className={`shrink-0 text-[#98A49E] transition-transform duration-150 ${
            open ? 'rotate-180 text-[#6DB386]' : 'group-hover:text-[#E6EBE8]'
          }`}
        />
      </button>

      {open &&
        coords &&
        typeof document !== 'undefined' &&
        createPortal(
          <div
            ref={menuRef}
            id={listboxId}
            role="listbox"
            aria-labelledby={label ? labelId : undefined}
            style={{
              position: 'fixed',
              left: coords.left,
              width: coords.width,
              top: coords.top,
              bottom: coords.bottom,
              maxHeight: coords.maxHeight,
              zIndex: 9999,
            }}
            onClick={(e) => e.stopPropagation()}
            className={`overflow-y-auto rounded-xl border border-[#323E39] bg-[#131816]/98 p-1 shadow-[0_0_24px_rgba(91,157,114,0.22),0_16px_38px_rgba(0,0,0,0.65)] backdrop-blur-md ${menuMaxHeightClass}`}
          >
            {options.map((opt, idx) => {
              const isSelected = String(opt.value) === String(value);
              const isActive = idx === activeIndex;
              return (
                <button
                  key={String(opt.value)}
                  ref={(el) => {
                    optionRefs.current[idx] = el;
                  }}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  disabled={opt.disabled}
                  onMouseEnter={() => setActiveIndex(idx)}
                  onClick={() => handleSelect(opt)}
                  className={`flex w-full items-start justify-between gap-2 rounded-lg px-2.5 py-2 text-left transition-colors ${
                    opt.disabled
                      ? 'cursor-not-allowed opacity-45'
                      : isSelected
                        ? 'bg-[#203328] text-[#E6EBE8] shadow-[inset_0_0_0_1px_rgba(91,157,114,0.45)]'
                        : isActive
                          ? 'bg-[#1A201E] text-[#E6EBE8]'
                          : 'text-[#C8D1CC] hover:bg-[#1A201E] hover:text-[#E6EBE8]'
                  }`}
                >
                  <span className="flex min-w-0 flex-1 items-start gap-2">
                    {opt.dotColor && (
                      <span
                        className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ backgroundColor: opt.dotColor }}
                      />
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-1.5">
                        <span className={`truncate text-[12px] ${isSelected ? 'font-semibold text-[#E6EBE8]' : 'font-medium'}`}>
                          {opt.label}
                        </span>
                        {opt.badge && (
                          <span className="shrink-0 rounded bg-[#0C0F0E] px-1.5 py-0.5 text-[9px] font-medium text-[#6DB386] border border-[#252E2A]">
                            {opt.badge}
                          </span>
                        )}
                      </span>
                      {opt.description && (
                        <span className="mt-0.5 block text-[11px] leading-snug text-[#98A49E]">
                          {opt.description}
                        </span>
                      )}
                    </span>
                  </span>
                  {isSelected && (
                    <Check size={14} className="mt-0.5 shrink-0 text-[#6DB386]" />
                  )}
                </button>
              );
            })}
          </div>,
          document.body
        )}
    </div>
  );
}
