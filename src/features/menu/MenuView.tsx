import { motion } from 'motion/react';
import {
  Archive,
  BookOpen,
  Building2,
  ChevronRight,
  Database,
  Map as MapIcon,
  Radio,
  Settings,
  Shield,
  User,
} from 'lucide-react';
import { POWER_PROFILE_META, ROLE_LABELS } from '../../core/labels';
import type { AppSettings, ModuleId, UserProfile } from '../../core/types';

interface MenuViewProps {
  profile: UserProfile;
  settings: AppSettings;
  activeDbName?: string;
  mountedCount?: number;
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
  activeDbName,
  mountedCount = 1,
  stats,
  onSelectModule,
}: MenuViewProps) {
  const layerLabel = settings.mapLayer === 'satellite' ? 'Спутник' : 'Офлайн-вектор';
  const gpsLabel = POWER_PROFILE_META[settings.powerProfile].label;

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
      title: 'Настройки и базы',
      code: 'SETTINGS',
      description: 'Менеджер баз данных (регионы), GPS, карта, Meshtastic и формат .urbexdb.',
      meta: `GPS: ${gpsLabel} · ${layerLabel}${activeDbName ? ` · БД: ${activeDbName}` : ''}`,
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
    <div className="space-y-3 sm:space-y-4" data-testid="menu-fullscreen-view">
      {/* Operator Banner */}
      <section className="bg-[#131816] border border-[#252E2A] rounded-xl p-3 sm:p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="space-y-0.5">
          <div className="flex items-center gap-2 text-[11px] sm:text-xs text-[#98A49E]">
            <Shield className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#6DB386]" />
            <span>Автономный терминал PDA</span>
            <span className="text-[#6B7771]" aria-hidden="true">·</span>
            <span className="font-mono text-[#DF9E43]">{profile.nodeId}</span>
          </div>
          <h1 className="text-lg sm:text-xl font-bold tracking-tight text-[#E6EBE8]">
            {profile.callsign}
          </h1>
          <p className="text-xs text-[#98A49E] flex flex-wrap items-center gap-2">
            <span>Роль: {ROLE_LABELS[profile.role]}</span>
            {activeDbName && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[#0C0F0E] border border-[#252E2A] text-[11px] text-[#6DB386]">
                <Database className="w-3 h-3" />
                <span>
                  {activeDbName}
                  {mountedCount > 1 ? ` (+${mountedCount - 1})` : ''}
                </span>
              </span>
            )}
          </p>
        </div>

        <div className="grid grid-cols-3 gap-2">
          <div className="px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[10px] sm:text-[11px] text-[#98A49E]">Объекты</div>
            <div className="text-sm sm:text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.objectsCount}
            </div>
          </div>
          <div className="px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[10px] sm:text-[11px] text-[#98A49E]">Архив</div>
            <div className="text-sm sm:text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.archiveCount}
            </div>
          </div>
          <div className="px-2.5 py-1.5 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-center">
            <div className="text-[10px] sm:text-[11px] text-[#98A49E]">База</div>
            <div className="text-sm sm:text-base font-mono font-semibold text-[#E6EBE8] tabular-nums">
              {stats.articlesCount}
            </div>
          </div>
        </div>
      </section>

      {/* Main Menu Modules: Settings, Profile, Knowledge Base */}
      <section className="space-y-2" aria-label="Системные разделы">
        <h2 className="text-[11px] sm:text-xs font-semibold uppercase tracking-wider text-[#98A49E] px-1">
          Система и профиль
        </h2>
        <div className="grid grid-cols-1 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-3 gap-2.5 sm:gap-3">
          {primaryCards.map((item) => {
            const Icon = item.icon;
            return (
              <motion.button
                key={item.id}
                whileTap={{ scale: 0.985 }}
                type="button"
                onClick={() => onSelectModule(item.id)}
                className="w-full text-left bg-[#131816] hover:bg-[#171E1B] border border-[#252E2A] hover:border-[#5B9D72] rounded-xl p-3 sm:p-4 flex items-start justify-between gap-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B9D72]"
              >
                <div className="flex items-start gap-2.5 sm:gap-3 min-w-0">
                  <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4 sm:w-5 sm:h-5" style={{ color: item.accentColor }} />
                  </div>
                  <div className="min-w-0 space-y-0.5">
                    <div className="flex items-center gap-1.5">
                      <span className="text-sm sm:text-base font-semibold text-[#E6EBE8] truncate">
                        {item.title}
                      </span>
                      <span className="text-[10px] font-mono text-[#6B7771] shrink-0">
                        {item.code}
                      </span>
                    </div>
                    <p className="text-[11px] sm:text-xs text-[#98A49E] line-clamp-2 leading-snug">
                      {item.description}
                    </p>
                    <div className="text-[10px] sm:text-[11px] font-mono text-[#6DB386] pt-0.5 truncate">
                      {item.meta}
                    </div>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-[#6B7771] shrink-0 mt-1" />
              </motion.button>
            );
          })}
        </div>
      </section>

      {/* Workspace Quick Navigation */}
      <section className="space-y-2" aria-label="Рабочие модули">
        <h2 className="text-[11px] sm:text-xs font-semibold uppercase tracking-wider text-[#98A49E] px-1">
          Рабочие разделы
        </h2>
        <div className="grid grid-cols-2 [@media(orientation:landscape)_and_(min-width:560px)]:grid-cols-4 gap-2 sm:gap-3">
          {workspaceCards.map((item) => {
            const Icon = item.icon;
            return (
              <motion.button
                key={item.id}
                whileTap={{ scale: 0.985 }}
                type="button"
                onClick={() => onSelectModule(item.id)}
                className="w-full text-left bg-[#131816] hover:bg-[#171E1B] border border-[#252E2A] hover:border-[#323E39] rounded-xl px-3 py-2.5 sm:px-4 sm:py-3.5 flex items-center justify-between gap-2 transition-colors"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4 text-[#98A49E]" />
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs sm:text-sm font-medium text-[#E6EBE8] truncate">
                      {item.title}
                    </div>
                    <div className="text-[11px] text-[#6B7771] truncate">{item.meta}</div>
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
