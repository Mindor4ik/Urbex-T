import { useMemo, useState } from 'react';
import { motion } from 'motion/react';
import {
  Archive,
  BookOpen,
  Building2,
  Compass,
  HardDriveDownload,
  Map as MapIcon,
  Maximize2,
  Minimize2,
  Radio,
  Search,
  Settings,
  Smartphone,
  User,
} from 'lucide-react';
import { PDA_ORIENTATION_LABELS } from '../../core/labels';
import type { AppSettings, ModuleId, PdaOrientation } from '../../core/types';

interface AppsViewProps {
  settings: AppSettings;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  onUpdateSettings: (next: AppSettings) => Promise<void>;
  onSelectModule: (mod: ModuleId) => void;
  stats: {
    objectsCount: number;
    archiveCount: number;
    meshCount: number;
    articlesCount: number;
  };
}

interface PdaToolEntry {
  id: string;
  label: string;
  subtitle: string;
  target: ModuleId;
  icon: typeof MapIcon;
  accent: string;
}

export default function AppsView({
  settings,
  isFullscreen = false,
  onToggleFullscreen,
  onUpdateSettings,
  onSelectModule,
  stats,
}: AppsViewProps) {
  const [query, setQuery] = useState('');
  const currentOrientation: PdaOrientation = settings.pdaOrientation ?? 'portrait';

  const tools = useMemo<PdaToolEntry[]>(
    () => [
      {
        id: 'map',
        label: 'Карта сектора',
        subtitle: `${settings.offlineSectors.length} кэш-секторов`,
        target: 'map',
        icon: MapIcon,
        accent: '#6DB386',
      },
      {
        id: 'objects',
        label: 'Реестр объектов',
        subtitle: `${stats.objectsCount} точек`,
        target: 'objects',
        icon: Building2,
        accent: '#6DB386',
      },
      {
        id: 'archive',
        label: 'Полевой архив',
        subtitle: `${stats.archiveCount} файлов`,
        target: 'archive',
        icon: Archive,
        accent: '#6DB386',
      },
      {
        id: 'base',
        label: 'База знаний',
        subtitle: `${stats.articlesCount} инструкций`,
        target: 'base',
        icon: BookOpen,
        accent: '#6DB386',
      },
      ...(settings.meshEnabled
        ? [
            {
              id: 'mesh',
              label: 'Meshtastic LoRa',
              subtitle: `${stats.meshCount} пакетов`,
              target: 'mesh' as ModuleId,
              icon: Radio,
              accent: '#DF9E43',
            },
          ]
        : []),
      {
        id: 'profile',
        label: 'Профиль оператора',
        subtitle: 'Позывной и Node ID',
        target: 'profile',
        icon: User,
        accent: '#98A49E',
      },
      {
        id: 'backup',
        label: 'Резерв .urbexdb',
        subtitle: 'Экспорт и импорт',
        target: 'settings',
        icon: HardDriveDownload,
        accent: '#DF9E43',
      },
      {
        id: 'settings',
        label: 'Настройки PDA',
        subtitle: 'GPS, карта, экран',
        target: 'settings',
        icon: Settings,
        accent: '#DF9E43',
      },
    ],
    [settings.meshEnabled, settings.offlineSectors.length, stats]
  );

  const filteredTools = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tools;
    return tools.filter(
      (t) => t.label.toLowerCase().includes(q) || t.subtitle.toLowerCase().includes(q)
    );
  }, [query, tools]);

  return (
    <div className="space-y-2.5 sm:space-y-3.5" data-testid="pda-apps-view">
      {/* Terminal PDA Control Header */}
      <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-2.5 sm:p-4 space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Smartphone className="w-4 h-4 text-[#DF9E43]" />
            <h1 className="text-xs sm:text-sm font-semibold tracking-wide text-[#E6EBE8] uppercase">
              Приложения и лаунчер PDA
            </h1>
          </div>
          <span className="text-[11px] font-mono text-[#6DB386]">
            {PDA_ORIENTATION_LABELS[currentOrientation]}
          </span>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div className="flex flex-wrap gap-1.5">
            {(['portrait', 'landscape', 'auto'] as PdaOrientation[]).map((orient) => {
              const active = currentOrientation === orient;
              return (
                <button
                  key={orient}
                  type="button"
                  onClick={() =>
                    onUpdateSettings({
                      ...settings,
                      pdaOrientation: orient,
                      updatedAt: Date.now(),
                    })
                  }
                  className={`min-h-[32px] lg:min-h-[38px] px-2.5 sm:px-3 py-1 rounded-lg border text-xs font-medium transition-colors ${
                    active
                      ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  {PDA_ORIENTATION_LABELS[orient]}
                </button>
              );
            })}
          </div>

          {onToggleFullscreen && (
            <button
              type="button"
              onClick={onToggleFullscreen}
              className="min-h-[32px] lg:min-h-[38px] px-3 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-1.5 shrink-0"
            >
              {isFullscreen ? (
                <>
                  <Minimize2 className="w-3.5 h-3.5 text-[#DF9E43]" />
                  <span>Оконный режим</span>
                </>
              ) : (
                <>
                  <Maximize2 className="w-3.5 h-3.5 text-[#DF9E43]" />
                  <span>Во весь экран</span>
                </>
              )}
            </button>
          )}
        </div>
      </section>

      {/* Search & Launcher Grid */}
      <section className="space-y-2.5">
        <div className="flex flex-col [@media(orientation:landscape)_and_(min-width:560px)]:flex-row [@media(orientation:landscape)_and_(min-width:560px)]:items-center justify-between gap-2">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-[#98A49E] px-1 shrink-0">
            Утилиты терминала ({filteredTools.length})
          </span>
          <div className="relative flex-1 [@media(orientation:landscape)_and_(min-width:560px)]:max-w-sm">
            <Search className="w-4 h-4 text-[#6B7771] absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Поиск приложения или утилиты..."
              aria-label="Поиск приложения или утилиты"
              className="w-full h-9 lg:h-10 pl-8 pr-3 rounded-xl bg-[#131816] border border-[#252E2A] text-xs sm:text-sm text-[#E6EBE8] placeholder:text-[#6B7771] focus:outline-none focus:border-[#5B9D72]"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-4 gap-2 sm:gap-2.5">
          {filteredTools.map((tool) => {
            const Icon = tool.icon;
            return (
              <motion.button
                key={tool.id}
                whileTap={{ scale: 0.96 }}
                type="button"
                onClick={() => onSelectModule(tool.target)}
                className="bg-[#131816] hover:bg-[#1A201E] border border-[#252E2A] hover:border-[#5B9D72] rounded-xl p-2.5 sm:p-3 flex flex-col items-start gap-1.5 text-left transition-colors"
              >
                <div className="w-7 h-7 sm:w-9 sm:h-9 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center">
                  <Icon className="w-3.5 h-3.5 sm:w-4 sm:h-4" style={{ color: tool.accent }} />
                </div>
                <div className="min-w-0 w-full">
                  <div className="text-xs font-semibold text-[#E6EBE8] truncate">
                    {tool.label}
                  </div>
                  <div className="text-[11px] text-[#6B7771] truncate">{tool.subtitle}</div>
                </div>
              </motion.button>
            );
          })}
        </div>

        <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-2.5 sm:p-3.5 flex items-start gap-2 text-[11px] sm:text-xs text-[#98A49E]">
          <Compass className="w-4 h-4 text-[#DF9E43] shrink-0 mt-0.5" />
          <span>
            В Android APK этот экран дополнительно отображает все установленные приложения телефона и системные ярлыки (Wi-Fi, Bluetooth, Настройки ОС, Главный экран).
          </span>
        </div>
      </section>
    </div>
  );
}
