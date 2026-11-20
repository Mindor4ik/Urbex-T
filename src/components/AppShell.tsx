import { type ReactNode, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Archive,
  ArrowLeft,
  BookOpen,
  Building2,
  LayoutGrid,
  Map as MapIcon,
  Maximize2,
  Menu,
  Minimize2,
  Plus,
  Radio,
  Settings,
  User,
} from 'lucide-react';
import { PDA_ORIENTATION_LABELS } from '../core/labels';
import type { ModuleId, PdaOrientation } from '../core/types';

interface AppShellProps {
  activeModule: ModuleId;
  meshEnabled: boolean;
  editorEnabled?: boolean;
  pdaModeEnabled?: boolean;
  pdaOrientation?: PdaOrientation;
  hapticFeedback?: boolean;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  fullBleed?: boolean;
  onSelectModule: (mod: ModuleId) => void;
  onQuickAddPoint: () => void;
  children: ReactNode;
}

const PRIMARY_NAV: Array<{ id: ModuleId; label: string; subtitle: string; icon: typeof MapIcon }> = [
  { id: 'map', label: 'КАРТА', subtitle: 'Сектор', icon: MapIcon },
  { id: 'objects', label: 'ОБЪЕКТЫ', subtitle: 'Реестр', icon: Building2 },
  { id: 'mesh', label: 'СВЯЗЬ', subtitle: 'Mesh', icon: Radio },
  { id: 'base', label: 'БАЗА', subtitle: 'Знания', icon: BookOpen },
  { id: 'archive', label: 'АРХИВ', subtitle: 'Файлы', icon: Archive },
];

const APPS_NAV = {
  id: 'apps' as ModuleId,
  label: 'ПРИЛ.',
  subtitle: 'Программы',
  icon: LayoutGrid,
};

const SECONDARY_NAV: Array<{ id: ModuleId; label: string; subtitle: string; icon: typeof User }> = [
  { id: 'profile', label: 'ПРОФИЛЬ', subtitle: 'Оператор', icon: User },
  { id: 'settings', label: 'НАСТРОЙКИ', subtitle: 'Система', icon: Settings },
];

export function AppShell({
  activeModule,
  meshEnabled,
  editorEnabled = false,
  pdaModeEnabled = false,
  pdaOrientation = 'portrait',
  hapticFeedback = true,
  isFullscreen = false,
  onToggleFullscreen,
  fullBleed = false,
  onSelectModule,
  onQuickAddPoint,
  children,
}: AppShellProps) {
  const [clockText, setClockText] = useState(() =>
    new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
  );
  const [batteryPct, setBatteryPct] = useState<number | null>(null);
  const [isDesktop, setIsDesktop] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia('(min-width: 1024px)').matches : false
  );
  const [isLandscapeMobile, setIsLandscapeMobile] = useState(() =>
    typeof window !== 'undefined'
      ? window.matchMedia('(max-width: 1023px) and (orientation: landscape) and (min-width: 560px)').matches
      : false
  );

  useEffect(() => {
    const desktopMq = window.matchMedia('(min-width: 1024px)');
    const landscapeMq = window.matchMedia(
      '(max-width: 1023px) and (orientation: landscape) and (min-width: 560px)'
    );
    const update = () => {
      setIsDesktop(desktopMq.matches);
      setIsLandscapeMobile(landscapeMq.matches);
    };
    update();
    desktopMq.addEventListener('change', update);
    landscapeMq.addEventListener('change', update);
    return () => {
      desktopMq.removeEventListener('change', update);
      landscapeMq.removeEventListener('change', update);
    };
  }, []);

  useEffect(() => {
    const updateClock = () =>
      setClockText(new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }));
    updateClock();
    const id = window.setInterval(updateClock, 15_000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    const nav = navigator as Navigator & {
      getBattery?: () => Promise<{
        level: number;
        addEventListener: (event: string, cb: () => void) => void;
        removeEventListener: (event: string, cb: () => void) => void;
      }>;
    };
    if (!nav.getBattery) return;
    let cleanup: (() => void) | undefined;
    nav
      .getBattery()
      .then((bat) => {
        const update = () => setBatteryPct(Math.round(bat.level * 100));
        update();
        bat.addEventListener('levelchange', update);
        cleanup = () => bat.removeEventListener('levelchange', update);
      })
      .catch(() => {});
    return () => cleanup?.();
  }, []);

  const handleNavigate = (id: ModuleId) => {
    if (hapticFeedback && id !== activeModule && typeof navigator !== 'undefined') {
      navigator.vibrate?.(10);
    }
    onSelectModule(id);
  };

  const isRailLayout = isDesktop || isLandscapeMobile;
  const visibleDesktopPrimary = [
    ...PRIMARY_NAV.filter((item) => item.id !== 'mesh' || meshEnabled),
    ...(pdaModeEnabled ? [APPS_NAV] : []),
  ];
  const visibleCompactNav = [
    PRIMARY_NAV[0],
    PRIMARY_NAV[1],
    ...(meshEnabled ? [PRIMARY_NAV[2]] : []),
    PRIMARY_NAV[4],
  ];
  const isMenuSectionActive =
    activeModule === 'menu' ||
    activeModule === 'base' ||
    activeModule === 'profile' ||
    activeModule === 'settings';
  const menuSubTitle =
    activeModule === 'settings'
      ? 'Настройки'
      : activeModule === 'profile'
        ? 'Профиль оператора'
        : activeModule === 'base'
          ? 'База знаний'
          : null;

  return (
    <div
      className="min-h-screen flex flex-col bg-[#0C0F0E] text-[#E6EBE8]"
      data-pda-mode={pdaModeEnabled ? 'on' : 'off'}
      data-pda-orientation={pdaOrientation}
    >
      {/* Strict 3-Zone Top Bar (compact height in Landscape phone PDA mode) */}
      <header
        className={`sticky top-0 z-30 bg-[#0C0F0E]/95 backdrop-blur-sm border-b border-[#252E2A] px-2.5 sm:px-4 lg:px-6 flex items-center justify-between gap-2 sm:gap-5 ${
          isLandscapeMobile ? 'h-10' : 'h-12 sm:h-14'
        }`}
      >
        {/* Zone 1: Single-element brand wordmark */}
        <motion.button
          type="button"
          whileTap={{ scale: 0.97 }}
          onClick={() => handleNavigate('map')}
          className="text-base sm:text-lg font-bold tracking-tight text-[#E6EBE8] whitespace-nowrap shrink-0 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72] rounded"
        >
          Urbex-T
        </motion.button>

        {/* Zone 2: Concise single-line text nav links (Desktop >= 1024px) */}
        <nav
          aria-label="Основные разделы PDA"
          className="hidden lg:flex items-center gap-6"
        >
          <AnimatePresence initial={false}>
            {visibleDesktopPrimary.map((item) => {
              const active = activeModule === item.id;
              return (
                <motion.button
                  key={item.id}
                  initial={item.id === 'mesh' || item.id === 'apps' ? { opacity: 0, width: 0 } : false}
                  animate={{ opacity: 1, width: 'auto' }}
                  exit={{ opacity: 0, width: 0 }}
                  transition={{ duration: 0.16, ease: 'easeOut' }}
                  type="button"
                  onClick={() => handleNavigate(item.id)}
                  aria-current={active ? 'page' : undefined}
                  className={`relative py-1.5 text-sm font-medium whitespace-nowrap shrink-0 overflow-hidden transition-colors ${
                    active ? 'text-[#E6EBE8]' : 'text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  {item.label}
                  {active && (
                    <motion.span
                      layoutId="top-nav-indicator"
                      transition={{ type: 'spring', stiffness: 450, damping: 34 }}
                      className="absolute left-0 right-0 bottom-0 h-0.5 bg-[#5B9D72] rounded-full"
                    />
                  )}
                </motion.button>
              );
            })}
          </AnimatePresence>
        </nav>

        {/* Zone 3: Live PDA Telemetry (when enabled) + Primary +Точка Action */}
        <div className="flex items-center gap-2 shrink-0">
          {pdaModeEnabled && (
            <div
              data-testid="pda-topbar-telemetry"
              className={`flex items-center gap-1.5 rounded-lg bg-[#131816] border border-[#252E2A] text-[11px] font-mono tabular-nums text-[#98A49E] ${
                isLandscapeMobile ? 'px-2 py-0.5' : 'px-2.5 py-1'
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-[#6DB386]" aria-hidden="true" />
              <span className="text-[#E6EBE8] font-medium">{clockText}</span>
              {batteryPct !== null && (
                <>
                  <span className="text-[#6B7771]" aria-hidden="true">·</span>
                  <span className="text-[#6DB386]">{batteryPct}%</span>
                </>
              )}
              <span className="hidden sm:inline text-[#6B7771]" aria-hidden="true">·</span>
              <span className="hidden sm:inline text-[#DF9E43]">
                {PDA_ORIENTATION_LABELS[pdaOrientation]}
              </span>
              {onToggleFullscreen && (
                <button
                  type="button"
                  onClick={onToggleFullscreen}
                  aria-label={isFullscreen ? 'Выйти из полноэкранного режима PDA' : 'Развернуть PDA на весь экран'}
                  className="ml-0.5 p-0.5 sm:p-1 rounded hover:bg-[#1A201E] text-[#98A49E] hover:text-[#E6EBE8] transition-colors"
                >
                  {isFullscreen ? <Minimize2 className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
                </button>
              )}
            </div>
          )}

          {editorEnabled && (
            <motion.button
              type="button"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.95 }}
              onClick={onQuickAddPoint}
              className={`${
                isLandscapeMobile ? 'min-h-[30px] px-2.5 py-0.5' : 'min-h-[38px] sm:min-h-[40px] px-3.5 py-1.5'
              } rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] transition-colors flex items-center gap-1 whitespace-nowrap shrink-0`}
            >
              <Plus className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              <span>Точка</span>
            </motion.button>
          )}
        </div>
      </header>

      {/* Main PDA Workspace */}
      <div
        className={`flex-1 flex w-full ${
          fullBleed ? 'max-w-none' : 'max-w-[1440px] mx-auto'
        }`}
      >
        {/* Slim Tactical Landscape Rail (Phone Landscape < 1024px) */}
        {isLandscapeMobile && !isDesktop && (
          <aside
            aria-label="Боковая панель управления PDA"
            data-testid="landscape-tactical-rail"
            className="w-[66px] shrink-0 sticky top-10 h-[calc(100dvh-2.5rem)] overflow-y-auto flex flex-col items-center justify-between py-1.5 px-1 border-r border-[#252E2A] bg-[#131816]"
          >
            <div className="w-full flex flex-col items-center gap-1">
              {visibleCompactNav.map((item) => {
                const Icon = item.icon;
                const active = activeModule === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleNavigate(item.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`w-full min-h-[40px] px-1 py-1 rounded-xl flex flex-col items-center justify-center gap-0.5 transition-colors ${
                      active
                        ? 'bg-[#203328] border border-[#5B9D72]/50 text-[#6DB386]'
                        : 'border border-transparent text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]'
                    }`}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span className="text-[9px] font-semibold tracking-tight leading-none truncate max-w-full">
                      {item.label}
                    </span>
                  </button>
                );
              })}

              <button
                type="button"
                onClick={() => handleNavigate('menu')}
                aria-current={isMenuSectionActive ? 'page' : undefined}
                aria-label="Меню разделов"
                className={`w-full min-h-[40px] px-1 py-1 rounded-xl flex flex-col items-center justify-center gap-0.5 transition-colors ${
                  isMenuSectionActive
                    ? 'bg-[#2A2218] border border-[#D39239]/50 text-[#DF9E43]'
                    : 'border border-transparent text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]'
                }`}
              >
                <Menu className="w-4 h-4 shrink-0" />
                <span className="text-[9px] font-semibold leading-none">МЕНЮ</span>
              </button>

              {pdaModeEnabled && (
                <button
                  type="button"
                  onClick={() => handleNavigate('apps')}
                  aria-current={activeModule === 'apps' ? 'page' : undefined}
                  aria-label="Приложения PDA"
                  className={`w-full min-h-[40px] px-1 py-1 rounded-xl flex flex-col items-center justify-center gap-0.5 transition-colors ${
                    activeModule === 'apps'
                      ? 'bg-[#203328] border border-[#5B9D72]/50 text-[#6DB386]'
                      : 'border border-transparent text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E]'
                  }`}
                >
                  <LayoutGrid className="w-4 h-4 shrink-0" />
                  <span className="text-[9px] font-semibold leading-none">ПРИЛ.</span>
                </button>
              )}
            </div>
          </aside>
        )}

        {/* Full Desktop Left Module Rail (>= 1024px) */}
        {isDesktop && (
          <aside
            aria-label="Модули терминала"
            className="flex w-52 lg:w-56 shrink-0 sticky top-14 max-h-[calc(100vh-3.5rem)] overflow-y-auto flex-col justify-between gap-3 border-r border-[#252E2A] p-3 bg-[#0C0F0E]"
          >
            <div className="space-y-1">
              <AnimatePresence initial={false}>
                {visibleDesktopPrimary.map((item) => {
                  const Icon = item.icon;
                  const active = activeModule === item.id;
                  return (
                    <motion.button
                      key={item.id}
                      initial={item.id === 'mesh' || item.id === 'apps' ? { opacity: 0, height: 0, marginBottom: 0 } : false}
                      animate={{ opacity: 1, height: 'auto', marginBottom: 4 }}
                      exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                      transition={{ duration: 0.18, ease: 'easeOut' }}
                      type="button"
                      onClick={() => handleNavigate(item.id)}
                      aria-current={active ? 'page' : undefined}
                      className={`relative w-full min-h-[40px] lg:min-h-[42px] px-3 py-2 rounded-lg flex items-center justify-between gap-2 text-left overflow-hidden transition-colors ${
                        active
                          ? 'text-[#E6EBE8] border border-[#323E39]'
                          : 'text-[#98A49E] border border-transparent hover:text-[#E6EBE8] hover:bg-[#131816]'
                      }`}
                    >
                      {active && (
                        <motion.span
                          layoutId="rail-active-bg"
                          transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                          className="absolute inset-0 bg-[#1A201E] -z-0 rounded-lg"
                        />
                      )}
                      <span className="relative z-10 flex items-center gap-2 shrink-0">
                        <Icon
                          className={`w-4 h-4 shrink-0 ${active ? 'text-[#5B9D72]' : 'text-[#6B7771]'}`}
                        />
                        <span className="text-xs font-semibold tracking-wide">
                          {item.label}
                        </span>
                      </span>
                      <span className="relative z-10 text-xs text-[#6B7771] truncate">{item.subtitle}</span>
                    </motion.button>
                  );
                })}
              </AnimatePresence>
            </div>

            <div className="pt-3 border-t border-[#252E2A] space-y-1">
              {SECONDARY_NAV.map((item) => {
                const Icon = item.icon;
                const active = activeModule === item.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => handleNavigate(item.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`relative w-full min-h-[40px] lg:min-h-[42px] px-3 py-2 rounded-lg flex items-center justify-between gap-2 text-left overflow-hidden transition-colors ${
                      active
                        ? 'text-[#E6EBE8] border border-[#323E39]'
                        : 'text-[#98A49E] border border-transparent hover:text-[#E6EBE8] hover:bg-[#131816]'
                    }`}
                  >
                    {active && (
                      <motion.span
                        layoutId="rail-active-bg"
                        transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                        className="absolute inset-0 bg-[#1A201E] -z-0 rounded-lg"
                      />
                    )}
                    <span className="relative z-10 flex items-center gap-2 shrink-0">
                      <Icon
                        className={`w-4 h-4 shrink-0 ${active ? 'text-[#D39239]' : 'text-[#6B7771]'}`}
                      />
                      <span className="text-xs font-semibold tracking-wide">
                        {item.label}
                      </span>
                    </span>
                    <span className="relative z-10 text-xs text-[#6B7771] truncate">{item.subtitle}</span>
                  </button>
                );
              })}
            </div>
          </aside>
        )}

        {/* Active Module Viewport */}
        <main
          className={`flex-1 min-w-0 flex flex-col ${
            fullBleed ? 'p-0' : isDesktop ? 'p-4 lg:p-6 pb-6' : isLandscapeMobile ? 'p-2.5 pb-3' : 'p-4 pb-24'
          }`}
        >
          {!isDesktop && menuSubTitle && (
            <div
              className={`flex items-center justify-between gap-2 border-b border-[#252E2A] ${
                isLandscapeMobile ? 'mb-2 pb-1.5' : 'mb-3 pb-2.5'
              }`}
            >
              <motion.button
                whileTap={{ scale: 0.96 }}
                type="button"
                onClick={() => handleNavigate('menu')}
                className={`${
                  isLandscapeMobile ? 'min-h-[32px] px-2.5 py-1' : 'min-h-[38px] px-3 py-1.5'
                } rounded-lg bg-[#131816] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1.5`}
              >
                <ArrowLeft className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#DF9E43]" />
                <span>Назад в меню</span>
              </motion.button>
              <span className="text-xs font-semibold text-[#98A49E] truncate">
                {menuSubTitle}
              </span>
            </div>
          )}
          {children}
        </main>
      </div>

      {/* Floating Cloud Mobile Bottom Bar (Portrait Mobile) */}
      {!isRailLayout && (
        <div className="fixed bottom-3 left-3 right-3 z-40 flex justify-center pointer-events-none">
          <nav
            aria-label="Нижняя навигация"
            data-testid="mobile-bottom-dock"
            className="pointer-events-auto w-full max-w-md h-[64px] px-1.5 rounded-2xl bg-[#131816]/92 backdrop-blur-xl border border-[#2C3732] shadow-[0_14px_36px_rgba(0,0,0,0.72)] flex items-stretch overflow-hidden"
          >
            <AnimatePresence initial={false}>
              {visibleCompactNav.map((item) => {
                const Icon = item.icon;
                const active = activeModule === item.id;
                return (
                  <motion.button
                    key={item.id}
                    initial={
                      item.id === 'mesh'
                        ? { opacity: 0, scale: 0.75, flexGrow: 0.001 }
                        : false
                    }
                    animate={{ opacity: 1, scale: 1, flexGrow: 1 }}
                    exit={{ opacity: 0, scale: 0.75, flexGrow: 0.001 }}
                    transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                    whileTap={{ scale: 0.9 }}
                    type="button"
                    onClick={() => handleNavigate(item.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`relative min-h-[44px] min-w-0 flex-1 h-full my-auto rounded-xl flex flex-col items-center justify-center gap-0.5 overflow-hidden transition-colors ${
                      active ? 'text-[#6DB386]' : 'text-[#98A49E]'
                    }`}
                  >
                    {active && (
                      <>
                        <motion.span
                          layoutId="bottom-nav-pill"
                          transition={{ type: 'spring', stiffness: 480, damping: 30, mass: 0.85 }}
                          className="absolute inset-1 rounded-xl bg-gradient-to-b from-[#23362C] to-[#17221D] border border-[#5B9D72]/45 shadow-[0_0_16px_rgba(91,157,114,0.22)] -z-0"
                        />
                        <motion.span
                          layoutId="bottom-nav-accent"
                          transition={{ type: 'spring', stiffness: 520, damping: 32 }}
                          className="absolute top-1 left-1/2 -translate-x-1/2 w-6 h-[3px] rounded-full bg-[#6DB386] shadow-[0_0_8px_#6DB386]"
                        />
                      </>
                    )}
                    <motion.span
                      animate={{ y: active ? -1.5 : 0, scale: active ? 1.1 : 1 }}
                      transition={{ type: 'spring', stiffness: 480, damping: 26 }}
                      className="relative z-10 flex items-center justify-center"
                    >
                      <Icon className="w-[18px] h-[18px] shrink-0" />
                    </motion.span>
                    <motion.span
                      animate={{ opacity: active ? 1 : 0.78 }}
                      transition={{ duration: 0.16 }}
                      className={`relative z-10 text-[9.5px] leading-none truncate max-w-full px-0.5 ${
                        active ? 'font-bold tracking-wide' : 'font-medium'
                      }`}
                    >
                      {item.label}
                    </motion.span>
                  </motion.button>
                );
              })}
            </AnimatePresence>

            <motion.button
              whileTap={{ scale: 0.9 }}
              type="button"
              onClick={() => handleNavigate('menu')}
              aria-current={isMenuSectionActive ? 'page' : undefined}
              aria-label="Дополнительные разделы"
              className={`relative min-h-[44px] min-w-0 flex-1 h-full my-auto rounded-xl flex flex-col items-center justify-center gap-0.5 transition-colors ${
                isMenuSectionActive ? 'text-[#DF9E43]' : 'text-[#98A49E]'
              }`}
            >
              {isMenuSectionActive && (
                <>
                  <motion.span
                    layoutId="bottom-nav-pill"
                    transition={{ type: 'spring', stiffness: 480, damping: 30, mass: 0.85 }}
                    className="absolute inset-1 rounded-xl bg-gradient-to-b from-[#33271A] to-[#211A13] border border-[#D39239]/45 shadow-[0_0_16px_rgba(223,158,67,0.2)] -z-0"
                  />
                  <motion.span
                    layoutId="bottom-nav-accent"
                    transition={{ type: 'spring', stiffness: 520, damping: 32 }}
                    className="absolute top-1 left-1/2 -translate-x-1/2 w-6 h-[3px] rounded-full bg-[#DF9E43] shadow-[0_0_8px_#DF9E43]"
                  />
                </>
              )}
              <motion.span
                animate={{ y: isMenuSectionActive ? -1.5 : 0, scale: isMenuSectionActive ? 1.1 : 1 }}
                transition={{ type: 'spring', stiffness: 480, damping: 26 }}
                className="relative z-10 flex items-center justify-center"
              >
                <Menu className="w-[18px] h-[18px] shrink-0" />
              </motion.span>
              <motion.span
                animate={{ opacity: isMenuSectionActive ? 1 : 0.78 }}
                transition={{ duration: 0.16 }}
                className={`relative z-10 text-[9.5px] leading-none ${
                  isMenuSectionActive ? 'font-bold tracking-wide' : 'font-medium'
                }`}
              >
                МЕНЮ
              </motion.span>
            </motion.button>

            <AnimatePresence initial={false}>
              {pdaModeEnabled && (
                <motion.button
                  key="apps"
                  initial={{ opacity: 0, scale: 0.75, flexGrow: 0.001 }}
                  animate={{ opacity: 1, scale: 1, flexGrow: 1 }}
                  exit={{ opacity: 0, scale: 0.75, flexGrow: 0.001 }}
                  transition={{ type: 'spring', stiffness: 420, damping: 32 }}
                  whileTap={{ scale: 0.9 }}
                  type="button"
                  onClick={() => handleNavigate('apps')}
                  aria-current={activeModule === 'apps' ? 'page' : undefined}
                  aria-label="Приложения PDA"
                  className={`relative min-h-[44px] min-w-0 flex-1 h-full my-auto rounded-xl flex flex-col items-center justify-center gap-0.5 overflow-hidden transition-colors ${
                    activeModule === 'apps' ? 'text-[#6DB386]' : 'text-[#98A49E]'
                  }`}
                >
                  {activeModule === 'apps' && (
                    <>
                      <motion.span
                        layoutId="bottom-nav-pill"
                        transition={{ type: 'spring', stiffness: 480, damping: 30, mass: 0.85 }}
                        className="absolute inset-1 rounded-xl bg-gradient-to-b from-[#23362C] to-[#17221D] border border-[#5B9D72]/45 shadow-[0_0_16px_rgba(91,157,114,0.22)] -z-0"
                      />
                      <motion.span
                        layoutId="bottom-nav-accent"
                        transition={{ type: 'spring', stiffness: 520, damping: 32 }}
                        className="absolute top-1 left-1/2 -translate-x-1/2 w-6 h-[3px] rounded-full bg-[#6DB386] shadow-[0_0_8px_#6DB386]"
                      />
                    </>
                  )}
                  <motion.span
                    animate={{ y: activeModule === 'apps' ? -1.5 : 0, scale: activeModule === 'apps' ? 1.1 : 1 }}
                    transition={{ type: 'spring', stiffness: 480, damping: 26 }}
                    className="relative z-10 flex items-center justify-center"
                  >
                    <LayoutGrid className="w-[18px] h-[18px] shrink-0" />
                  </motion.span>
                  <motion.span
                    animate={{ opacity: activeModule === 'apps' ? 1 : 0.78 }}
                    transition={{ duration: 0.16 }}
                    className={`relative z-10 text-[9.5px] leading-none ${
                      activeModule === 'apps' ? 'font-bold tracking-wide' : 'font-medium'
                    }`}
                  >
                    ПРИЛ.
                  </motion.span>
                </motion.button>
              )}
            </AnimatePresence>
          </nav>
        </div>
      )}
    </div>
  );
}
