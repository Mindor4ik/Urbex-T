import { type ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Archive,
  ArrowLeft,
  BookOpen,
  Building2,
  Map as MapIcon,
  Menu,
  Plus,
  Radio,
  Settings,
  User,
} from 'lucide-react';
import type { ModuleId } from '../core/types';

interface AppShellProps {
  activeModule: ModuleId;
  meshEnabled: boolean;
  fullBleed?: boolean;
  onSelectModule: (mod: ModuleId) => void;
  onQuickAddPoint: () => void;
  children: ReactNode;
}

const PRIMARY_NAV: Array<{ id: ModuleId; label: string; subtitle: string; icon: typeof MapIcon }> = [
  { id: 'map', label: 'MAP', subtitle: 'Карта', icon: MapIcon },
  { id: 'objects', label: 'OBJECTS', subtitle: 'Объекты', icon: Building2 },
  { id: 'mesh', label: 'MESH', subtitle: 'Связь', icon: Radio },
  { id: 'base', label: 'BASE', subtitle: 'База', icon: BookOpen },
  { id: 'archive', label: 'ARCHIVE', subtitle: 'Архив', icon: Archive },
];

const SECONDARY_NAV: Array<{ id: ModuleId; label: string; subtitle: string; icon: typeof User }> = [
  { id: 'profile', label: 'PROFILE', subtitle: 'Профиль', icon: User },
  { id: 'settings', label: 'SETTINGS', subtitle: 'Настройки', icon: Settings },
];

export function AppShell({
  activeModule,
  meshEnabled,
  fullBleed = false,
  onSelectModule,
  onQuickAddPoint,
  children,
}: AppShellProps) {
  const handleNavigate = (id: ModuleId) => {
    onSelectModule(id);
  };

  const visiblePrimaryNav = PRIMARY_NAV.filter((item) => item.id !== 'mesh' || meshEnabled);
  const visibleBottomItems = [
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
    <div className="min-h-screen flex flex-col bg-[#0C0F0E] text-[#E6EBE8]">
      {/* Strict 3-Zone Top Bar */}
      <header className="sticky top-0 z-30 h-12 sm:h-14 bg-[#0C0F0E]/95 backdrop-blur-sm border-b border-[#252E2A] px-4 lg:px-6 flex items-center justify-between gap-8">
        {/* Zone 1: Single-element brand wordmark */}
        <motion.button
          type="button"
          whileTap={{ scale: 0.97 }}
          onClick={() => handleNavigate('map')}
          className="text-base sm:text-lg font-bold tracking-tight text-[#E6EBE8] whitespace-nowrap shrink-0 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72] rounded"
        >
          Urbex-T
        </motion.button>

        {/* Zone 2: Concise single-line text nav links */}
        <nav
          aria-label="Основные разделы PDA"
          className="hidden md:flex items-center gap-6"
        >
          <AnimatePresence initial={false}>
            {visiblePrimaryNav.map((item) => {
              const active = activeModule === item.id;
              return (
                <motion.button
                  key={item.id}
                  initial={item.id === 'mesh' ? { opacity: 0, width: 0 } : false}
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

        {/* Zone 3: 1 Primary Action */}
        <div className="flex items-center shrink-0">
          <motion.button
            type="button"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.95 }}
            onClick={onQuickAddPoint}
            className="min-h-[38px] sm:min-h-[40px] px-3.5 py-1.5 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs sm:text-sm font-semibold hover:bg-[#6DB386] transition-colors flex items-center gap-1.5 whitespace-nowrap shrink-0"
          >
            <Plus className="w-4 h-4" />
            <span>Точка</span>
          </motion.button>
        </div>
      </header>

      {/* Main PDA Workspace */}
      <div
        className={`flex-1 flex w-full ${
          fullBleed ? 'max-w-none' : 'max-w-[1440px] mx-auto'
        }`}
      >
        {/* Desktop Left PDA Module Rail */}
        <aside
          aria-label="Модули терминала"
          className="hidden lg:flex w-52 shrink-0 flex-col justify-between border-r border-[#252E2A] p-3 bg-[#0C0F0E]"
        >
          <div className="space-y-1">
            <AnimatePresence initial={false}>
              {visiblePrimaryNav.map((item) => {
                const Icon = item.icon;
                const active = activeModule === item.id;
                return (
                  <motion.button
                    key={item.id}
                    initial={item.id === 'mesh' ? { opacity: 0, height: 0, marginBottom: 0 } : false}
                    animate={{ opacity: 1, height: 'auto', marginBottom: 4 }}
                    exit={{ opacity: 0, height: 0, marginBottom: 0 }}
                    transition={{ duration: 0.18, ease: 'easeOut' }}
                    type="button"
                    onClick={() => handleNavigate(item.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`relative w-full min-h-[44px] px-3 py-2 rounded-lg flex items-center justify-between text-left overflow-hidden transition-colors ${
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
                    <span className="relative z-10 flex items-center gap-2.5">
                      <Icon
                        className={`w-4 h-4 ${active ? 'text-[#5B9D72]' : 'text-[#6B7771]'}`}
                      />
                      <span className="text-xs font-semibold tracking-wide">
                        {item.label}
                      </span>
                    </span>
                    <span className="relative z-10 text-xs text-[#6B7771]">{item.subtitle}</span>
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
                  className={`relative w-full min-h-[44px] px-3 py-2 rounded-lg flex items-center justify-between text-left overflow-hidden transition-colors ${
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
                  <span className="relative z-10 flex items-center gap-2.5">
                    <Icon
                      className={`w-4 h-4 ${active ? 'text-[#D39239]' : 'text-[#6B7771]'}`}
                    />
                    <span className="text-xs font-semibold tracking-wide">
                      {item.label}
                    </span>
                  </span>
                  <span className="relative z-10 text-xs text-[#6B7771]">{item.subtitle}</span>
                </button>
              );
            })}
          </div>
        </aside>

        {/* Active Module Viewport */}
        <main
          className={`flex-1 min-w-0 flex flex-col ${
            fullBleed ? 'p-0' : 'p-4 lg:p-6 pb-24 lg:pb-6'
          }`}
        >
          {menuSubTitle && (
            <div className="lg:hidden mb-4 flex items-center justify-between gap-2 pb-3 border-b border-[#252E2A]">
              <motion.button
                whileTap={{ scale: 0.96 }}
                type="button"
                onClick={() => handleNavigate('menu')}
                className="min-h-[40px] px-3 py-1.5 rounded-lg bg-[#131816] border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1.5"
              >
                <ArrowLeft className="w-4 h-4 text-[#DF9E43]" />
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

      {/* Floating Cloud Mobile Bottom Bar (< 1024px) */}
      <div className="lg:hidden fixed bottom-3 left-3 right-3 z-40 flex justify-center pointer-events-none">
        <nav
          aria-label="Нижняя навигация"
          className="pointer-events-auto w-full max-w-md h-[60px] px-1.5 rounded-2xl bg-[#131816]/90 backdrop-blur-xl border border-[#2C3732] shadow-[0_12px_32px_rgba(0,0,0,0.65)] flex items-stretch overflow-hidden"
        >
          <AnimatePresence initial={false}>
            {visibleBottomItems.map((item) => {
              const Icon = item.icon;
              const active = activeModule === item.id;
              return (
                <motion.button
                  key={item.id}
                  initial={
                    item.id === 'mesh'
                      ? { opacity: 0, scale: 0.8, flexGrow: 0.001 }
                      : false
                  }
                  animate={{ opacity: 1, scale: 1, flexGrow: 1 }}
                  exit={{ opacity: 0, scale: 0.8, flexGrow: 0.001 }}
                  transition={{ duration: 0.18, ease: 'easeOut' }}
                  whileTap={{ scale: 0.92 }}
                  type="button"
                  onClick={() => handleNavigate(item.id)}
                  aria-current={active ? 'page' : undefined}
                  className={`relative min-h-[44px] min-w-0 flex-1 h-full my-auto rounded-xl flex flex-col items-center justify-center gap-0.5 overflow-hidden transition-colors ${
                    active ? 'text-[#6DB386]' : 'text-[#98A49E]'
                  }`}
                >
                  {active && (
                    <motion.span
                      layoutId="bottom-nav-pill"
                      transition={{ type: 'spring', stiffness: 460, damping: 34 }}
                      className="absolute inset-1 rounded-xl bg-[#1D2823] border border-[#5B9D72]/35 -z-0"
                    />
                  )}
                  <Icon className="relative z-10 w-5 h-5 shrink-0" />
                  <span className="relative z-10 text-[10px] font-medium leading-none truncate">
                    {item.label}
                  </span>
                </motion.button>
              );
            })}
          </AnimatePresence>

          <motion.button
            whileTap={{ scale: 0.92 }}
            type="button"
            onClick={() => handleNavigate('menu')}
            aria-current={isMenuSectionActive ? 'page' : undefined}
            aria-label="Дополнительные разделы"
            className={`relative min-h-[44px] min-w-0 flex-1 h-full my-auto rounded-xl flex flex-col items-center justify-center gap-0.5 transition-colors ${
              isMenuSectionActive ? 'text-[#DF9E43]' : 'text-[#98A49E]'
            }`}
          >
            {isMenuSectionActive && (
              <motion.span
                layoutId="bottom-nav-pill"
                transition={{ type: 'spring', stiffness: 460, damping: 34 }}
                className="absolute inset-1 rounded-xl bg-[#2A2218] border border-[#D39239]/35 -z-0"
              />
            )}
            <Menu className="relative z-10 w-5 h-5 shrink-0" />
            <span className="relative z-10 text-[10px] font-medium leading-none">МЕНЮ</span>
          </motion.button>
        </nav>
      </div>
    </div>
  );
}
