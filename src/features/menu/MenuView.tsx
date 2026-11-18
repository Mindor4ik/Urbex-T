import { motion } from 'motion/react';
import {
  Archive,
  BookOpen,
  Building2,
  ChevronRight,
  Map as MapIcon,
  Maximize2,
  Minimize2,
  Radio,
  Settings,
  Shield,
  Smartphone,
  User,
} from 'lucide-react';
import { PDA_ORIENTATION_LABELS, POWER_PROFILE_META, ROLE_LABELS } from '../../core/labels';
import type { AppSettings, ModuleId, PdaOrientation, UserProfile } from '../../core/types';

interface MenuViewProps {
  profile: UserProfile;
  settings: AppSettings;
  isFullscreen?: boolean;
  onToggleFullscreen?: () => void;
  onUpdateSettings?: (next: AppSettings) => Promise<void>;
  stats: {
    objectsCount: number;
    archiveCount: number;
    meshCount: number;
    articlesCount: number;
  };
  onSelectModule: (mod: ModuleId) => void;
}

export default function MenuView({
  profile,
  settings,
  isFullscreen = false,
  onToggleFullscreen,
  onUpdateSettings,
  stats,
  onSelectModule,
}: MenuViewProps) {
  const layerLabel = settings.mapLayer === 'satellite' ? 'Спутник' : 'Офлайн-вектор';
  const gpsLabel = POWER_PROFILE_META[settings.powerProfile].label;
  const currentOrientation: PdaOrientation = settings.pdaOrientation ?? 'portrait';

  const primaryCards: Array<{
    id: ModuleId;
    title: string;
    code: string;
    description: string;
    meta: string;
    icon: typeof Settings;
    accentColor: string;
  }> = [
    {
      id: 'settings',
      title: 'Настройки',
      code: 'SETTINGS',
      description: 'Профиль GPS, слой карты, Meshtastic, кэш и резервное копирование .urbexdb.',
      meta: `GPS: ${gpsLabel} · ${layerLabel} · Mesh ${settings.meshEnabled ? 'ВКЛ' : 'ВЫКЛ'}`,
      icon: Settings,
      accentColor: '#DF9E43',
    },
    {
      id: 'profile',
      title: 'Профиль оператора',
      code: 'PROFILE',
      description: 'Позывной, HEX-идентификатор узла, роль в группе и локальная сводка.',
      meta: `${profile.callsign} · ${profile.nodeId}`,
      icon: User,
      accentColor: '#6DB386',
    },
    {
      id: 'base',
      title: 'База знаний',
      code: 'BASE',
      description: 'Автономные полевые инструкции, ориентирование, безопасность и заметки.',
      meta: `${stats.articlesCount} материалов офлайн`,
      icon: BookOpen,
      accentColor: '#6DB386',
    },
  ];

  const workspaceCards: Array<{
    id: ModuleId;
    title: string;
    code: string;
    meta: string;
    icon: typeof MapIcon;
  }> = [
    {
      id: 'map',
      title: 'Спутниковая карта',
      code: 'MAP',
      meta: `${settings.offlineSectors.length} кэш-секторов`,
      icon: MapIcon,
    },
    {
      id: 'objects',
      title: 'Реестр объектов',
      code: 'OBJECTS',
      meta: `${stats.objectsCount} точек`,
      icon: Building2,
    },
    {
      id: 'archive',
      title: 'Полевой архив',
      code: 'ARCHIVE',
      meta: `${stats.archiveCount} файлов`,
      icon: Archive,
    },
    ...(settings.meshEnabled
      ? [
          {
            id: 'mesh' as ModuleId,
            title: 'Связь Meshtastic',
            code: 'MESH',
            meta: `${stats.meshCount} пакетов`,
            icon: Radio,
          },
        ]
      : []),
  ];

  return (
    <div className="space-y-5" data-testid="menu-fullscreen-view">
      {/* Operator Banner */}
      <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2 text-xs text-[#98A49E]">
            <Shield className="w-4 h-4 text-[#6DB386]" />
            <span>Автономный терминал PDA</span>
            <span className="text-[#6B7771]" aria-hidden="true">·</span>
            <span className="font-mono text-[#DF9E43]">{profile.nodeId}</span>
          </div>
          <h1 className="text-xl font-bold tracking-tight text-[#E6EBE8]">
            {profile.callsign}
          </h1>
          <p className="text-xs text-[#98A49E]">
            Роль: {ROLE_LABELS[profile.role]}
          </p>
        </div>

        <div className="grid grid-cols-3 gap-2 sm:gap-3">
          <div className="px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[11px] text-[#98A49E]">Объекты</div>
            <div className="text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.objectsCount}
            </div>
          </div>
          <div className="px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[11px] text-[#98A49E]">Архив</div>
            <div className="text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.archiveCount}
            </div>
          </div>
          <div className="px-3 py-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[11px] text-[#98A49E]">База</div>
            <div className="text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.articlesCount}
            </div>
          </div>
        </div>
      </section>

      {settings.pdaModeEnabled && (
        <section className="space-y-3" aria-label="Терминал PDA">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-xs font-semibold uppercase tracking-wider text-[#DF9E43] flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5" />
              <span>Терминал PDA</span>
            </h2>
            <span className="text-[11px] font-mono text-[#6DB386]">
              {PDA_ORIENTATION_LABELS[currentOrientation]}
            </span>
          </div>
          <div className="bg-[#131816] border border-[#252E2A] rounded-xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex flex-wrap gap-2">
              {onUpdateSettings &&
                (['portrait', 'landscape', 'auto'] as PdaOrientation[]).map((orient) => {
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
                      className={`min-h-[38px] px-3 py-1.5 rounded-lg border text-xs font-medium transition-colors ${
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
                className="min-h-[38px] px-3.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#DF9E43] text-xs font-medium text-[#E6EBE8] flex items-center justify-center gap-2 shrink-0"
              >
                {isFullscreen ? (
                  <>
                    <Minimize2 className="w-4 h-4 text-[#DF9E43]" />
                    <span>Оконный режим</span>
                  </>
                ) : (
                  <>
                    <Maximize2 className="w-4 h-4 text-[#DF9E43]" />
                    <span>Во весь экран</span>
                  </>
                )}
              </button>
            )}
          </div>
        </section>
      )}

      {/* Main Menu Modules: Settings, Profile, Knowledge Base */}
      <section className="space-y-3" aria-label="Системные разделы">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-[#98A49E] px-1">
          Система и профиль
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {primaryCards.map((item) => {
            const Icon = item.icon;
            return (
              <motion.button
                key={item.id}
                whileTap={{ scale: 0.985 }}
                type="button"
                onClick={() => onSelectModule(item.id)}
                className="w-full text-left bg-[#131816] hover:bg-[#171E1B] border border-[#252E2A] hover:border-[#5B9D72] rounded-xl p-4 flex items-start justify-between gap-3 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72]"
              >
                <div className="flex items-start gap-3.5 min-w-0">
                  <div className="w-11 h-11 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center shrink-0">
                    <Icon className="w-5 h-5" style={{ color: item.accentColor }} />
                  </div>
                  <div className="min-w-0 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-base font-semibold text-[#E6EBE8]">
                        {item.title}
                      </span>
                      <span className="text-[10px] font-mono text-[#6B7771]">
                        {item.code}
                      </span>
                    </div>
                    <p className="text-xs text-[#98A49E] leading-relaxed">
                      {item.description}
                    </p>
                    <div className="text-[11px] font-mono text-[#6DB386] pt-0.5 truncate">
                      {item.meta}
                    </div>
                  </div>
                </div>
                <ChevronRight className="w-5 h-5 text-[#6B7771] shrink-0 mt-1" />
              </motion.button>
            );
          })}
        </div>
      </section>

      {/* Workspace Quick Navigation */}
      <section className="space-y-3" aria-label="Рабочие модули">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-[#98A49E] px-1">
          Рабочие разделы
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {workspaceCards.map((item) => {
            const Icon = item.icon;
            return (
              <motion.button
                key={item.id}
                whileTap={{ scale: 0.985 }}
                type="button"
                onClick={() => onSelectModule(item.id)}
                className="w-full text-left bg-[#131816] hover:bg-[#171E1B] border border-[#252E2A] hover:border-[#323E39] rounded-xl px-4 py-3.5 flex items-center justify-between gap-3 transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-9 h-9 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4 text-[#98A49E]" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-[#E6EBE8] truncate">
                      {item.title}
                    </div>
                    <div className="text-xs text-[#6B7771] truncate">{item.meta}</div>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-[#6B7771] shrink-0" />
              </motion.button>
            );
          })}
        </div>
      </section>
    </div>
  );
}
