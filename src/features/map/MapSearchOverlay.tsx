import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Building2,
  Crosshair,
  Loader2,
  MapPin,
  Radar,
  Search,
  X,
} from 'lucide-react';
import {
  mergeMapSearchResults,
  parseCoordinateSearch,
  searchMapLocationsOnline,
  searchOfflineMapLocations,
  type MapSearchResult,
} from '../../core/mapSearch';
import { EASE_OUT_TACTICAL } from '../../core/motion';
import type { UrbexObject } from '../../core/types';

interface MapSearchOverlayProps {
  objects: readonly UrbexObject[];
  onSelectResult: (result: MapSearchResult) => void;
  onClose: () => void;
}

const KIND_BADGE: Record<MapSearchResult['kind'], string> = {
  coord: 'Коорд.',
  object: 'Объект',
  city: 'Город',
  place: 'Место',
};

export function MapSearchOverlay({
  objects,
  onSelectResult,
  onClose,
}: MapSearchOverlayProps) {
  const [query, setQuery] = useState('');
  const [onlineResults, setOnlineResults] = useState<MapSearchResult[]>([]);
  const [isSearchingOnline, setIsSearchingOnline] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const id = window.setTimeout(() => {
      inputRef.current?.focus();
    }, 40);
    return () => window.clearTimeout(id);
  }, []);

  const localResults = useMemo(
    () => searchOfflineMapLocations(query, objects, 8),
    [query, objects]
  );

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2 || parseCoordinateSearch(trimmed)) {
      setOnlineResults([]);
      setIsSearchingOnline(false);
      return;
    }

    const controller = new AbortController();
    setIsSearchingOnline(true);

    const timer = window.setTimeout(async () => {
      try {
        const remote = await searchMapLocationsOnline(trimmed, controller.signal);
        if (!controller.signal.aborted) {
          setOnlineResults(remote);
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsSearchingOnline(false);
        }
      }
    }, 340);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  const combinedResults = useMemo(
    () =>
      query.trim().length >= 2
        ? mergeMapSearchResults(localResults, onlineResults, 9)
        : localResults,
    [query, localResults, onlineResults]
  );

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
      return;
    }
    if (e.key === 'Enter' && combinedResults.length > 0) {
      e.preventDefault();
      onSelectResult(combinedResults[0]);
    }
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.92, x: 10, y: -4 }}
      animate={{ opacity: 1, scale: 1, x: 0, y: 0 }}
      exit={{ opacity: 0, scale: 0.92, x: 10, y: -4 }}
      transition={{ duration: 0.18, ease: EASE_OUT_TACTICAL }}
      style={{ transformOrigin: 'top right' }}
      data-testid="map-search-panel"
      role="dialog"
      aria-label="Поиск по карте"
      className="absolute top-2 right-[52px] sm:top-3 sm:right-[60px] w-[calc(100vw-60px)] sm:w-[344px] max-w-[360px] z-30 rounded-xl bg-[#131816]/96 backdrop-blur-md border border-[#252E2A] shadow-2xl overflow-hidden flex flex-col max-h-[calc(100%-96px)]"
    >
      <div className="p-2 border-b border-[#252E2A] flex items-center gap-1.5 bg-[#0C0F0E]/80">
        <Search className="w-4 h-4 text-[#6DB386] shrink-0 ml-1" aria-hidden="true" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Город, место или координаты..."
          aria-label="Поиск города, места или координат"
          className="flex-1 min-w-0 bg-transparent text-xs sm:text-[13px] text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none py-1"
        />
        <AnimatePresence initial={false}>
          {isSearchingOnline && (
            <motion.span
              key="search-spinner"
              initial={{ opacity: 0, scale: 0.7 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.7 }}
              transition={{ duration: 0.12 }}
              className="shrink-0 flex items-center"
            >
              <Loader2
                className="w-3.5 h-3.5 text-[#6DB386] animate-spin"
                aria-label="Поиск онлайн"
              />
            </motion.span>
          )}
        </AnimatePresence>
        <AnimatePresence initial={false}>
          {query && (
            <motion.button
              key="search-clear"
              initial={{ opacity: 0, scale: 0.75 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.75 }}
              transition={{ duration: 0.12 }}
              type="button"
              onClick={() => {
                setQuery('');
                inputRef.current?.focus();
              }}
              aria-label="Очистить запрос"
              className="min-h-[28px] min-w-[28px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]"
            >
              <X className="w-3.5 h-3.5" />
            </motion.button>
          )}
        </AnimatePresence>
        <button
          type="button"
          onClick={onClose}
          aria-label="Закрыть поиск по карте"
          className="min-h-[28px] px-2 rounded-lg text-[11px] font-medium text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] shrink-0 transition-colors"
        >
          Закрыть
        </button>
      </div>

      <motion.div
        layout
        transition={{ duration: 0.16, ease: EASE_OUT_TACTICAL }}
        className="overflow-y-auto divide-y divide-[#1D2522] max-h-[46dvh]"
      >
        {combinedResults.length > 0 ? (
          combinedResults.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelectResult(item)}
              className="w-full px-3 py-2 text-left hover:bg-[#1A221E] active:bg-[#203328] transition-colors flex items-start gap-2.5"
            >
              <span className="mt-0.5 shrink-0">
                {item.kind === 'city' ? (
                  <Building2 className="w-4 h-4 text-[#6DB386]" />
                ) : item.kind === 'coord' ? (
                  <Crosshair className="w-4 h-4 text-[#DF9E43]" />
                ) : item.kind === 'object' ? (
                  <Radar className="w-4 h-4 text-[#6DB386]" />
                ) : (
                  <MapPin className="w-4 h-4 text-[#98A49E]" />
                )}
              </span>

              <span className="min-w-0 flex-1">
                <span className="flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold text-[#E6EBE8] truncate">
                    {item.title}
                  </span>
                  <span
                    className={`shrink-0 px-1.5 py-0.5 rounded text-[9.5px] font-mono border ${
                      item.source === 'online'
                        ? 'bg-[#172124] border-[#2C4249] text-[#8AC4D0]'
                        : item.kind === 'coord'
                          ? 'bg-[#251E12] border-[#4A381D] text-[#DF9E43]'
                          : 'bg-[#18261E] border-[#2C4737] text-[#6DB386]'
                    }`}
                  >
                    {item.source === 'online' ? 'OSM' : KIND_BADGE[item.kind]}
                  </span>
                </span>
                <span className="block text-[11px] text-[#98A49E] truncate mt-0.5">
                  {item.subtitle}
                </span>
              </span>
            </button>
          ))
        ) : (
          <div className="px-3 py-4 text-center text-[11.5px] text-[#98A49E]">
            {isSearchingOnline
              ? 'Ищем населённые пункты и места...'
              : 'Ничего не найдено. Введите название города или координаты (например: 55.7558, 37.6173).'}
          </div>
        )}
      </motion.div>

      <div className="px-3 py-1.5 bg-[#0C0F0E]/90 border-t border-[#252E2A] flex items-center justify-between gap-2 text-[10px] text-[#6B7771]">
        <span>
          {query.trim().length === 0
            ? 'Города, объекты или широта/долгота'
            : isSearchingOnline
              ? 'Уточняем через OSM Nominatim...'
              : 'Офлайн-база + OSM Nominatim'}
        </span>
        <span className="font-mono">Enter ↵</span>
      </div>
    </motion.div>
  );
}
