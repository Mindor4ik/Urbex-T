import type {
  ArchiveMediaType,
  EntryDifficulty,
  ObjectCategory,
  ObjectIconId,
  OperatorRole,
  PdaOrientation,
  PowerProfile,
  ReliabilityStatus,
  SecurityLevel,
  UrbexObject,
  ZoneStyle,
} from './types';

export const STATUS_META: Record<
  ReliabilityStatus,
  { label: string; glyph: string; textClass: string; colorHex: string }
> = {
  verified: {
    label: 'Подтверждён',
    glyph: '●',
    textClass: 'text-[#6DB386]',
    colorHex: '#5B9D72',
  },
  unverified: {
    label: 'Требует проверки',
    glyph: '▲',
    textClass: 'text-[#DF9E43]',
    colorHex: '#D39239',
  },
  contested: {
    label: 'Спорный',
    glyph: '◆',
    textClass: 'text-[#B9A46B]',
    colorHex: '#B9A46B',
  },
  hazardous: {
    label: 'Опасен',
    glyph: '✖',
    textClass: 'text-[#DC6860]',
    colorHex: '#C7564E',
  },
};

export const CATEGORY_LABELS: Record<ObjectCategory, string> = {
  industrial: 'Промзона',
  bunker: 'Спецобъект / Бункер',
  civilian: 'Гражданский объект',
  infrastructure: 'Инфраструктура',
  underground: 'Подземка / Коллектор',
};

export const OBJECT_ICONS: ObjectIconId[] = [
  'factory',
  'bunker',
  'building',
  'tower',
  'tunnel',
  'warehouse',
  'power',
  'boiler',
  'water',
  'fuel',
  'radar',
  'radiation',
  'hazard',
  'skull',
  'observation',
  'gate',
  'crane',
  'hospital',
  'airfield',
  'port',
  'shaft',
  'workshop',
  'camera',
  'pin',
];

export const OBJECT_ICON_META: Record<ObjectIconId, { label: string }> = {
  factory: { label: 'Завод / цех' },
  bunker: { label: 'Бункер / ЗС ГО' },
  building: { label: 'Здание / корпус' },
  tower: { label: 'Вышка / мачта' },
  tunnel: { label: 'Тоннель / метро' },
  warehouse: { label: 'Ангар / склад' },
  power: { label: 'Подстанция / ЛЭП' },
  boiler: { label: 'Котельная / ТЭЦ' },
  water: { label: 'Водоканал / коллектор' },
  fuel: { label: 'Склад ГСМ / цистерны' },
  radar: { label: 'РЛС / радиоузел' },
  radiation: { label: 'Радиация / спецзона' },
  hazard: { label: 'Аварийный объект' },
  skull: { label: 'Особая опасность' },
  observation: { label: 'Наблюдательный пункт' },
  gate: { label: 'КПП / периметр' },
  crane: { label: 'Недострой / кран' },
  hospital: { label: 'Госпиталь / санчасть' },
  airfield: { label: 'Аэродром / ВПП' },
  port: { label: 'Причал / док' },
  shaft: { label: 'Шахта / карьер' },
  workshop: { label: 'Депо / мастерская' },
  camera: { label: 'Пост наблюдения' },
  pin: { label: 'Опорная точка' },
};

export const CATEGORY_DEFAULT_ICON: Record<ObjectCategory, ObjectIconId> = {
  industrial: 'factory',
  bunker: 'bunker',
  civilian: 'building',
  infrastructure: 'tower',
  underground: 'tunnel',
};

const OBJECT_ICON_SET = new Set<string>(OBJECT_ICONS);

export function normalizeObjectIcon(raw?: string | null): ObjectIconId | undefined {
  if (!raw) return undefined;
  const cleaned = raw.trim().toLowerCase();
  if (OBJECT_ICON_SET.has(cleaned)) {
    return cleaned as ObjectIconId;
  }
  return undefined;
}

export function getEffectiveObjectIcon(
  obj: Pick<UrbexObject, 'category' | 'icon'>
): ObjectIconId {
  return normalizeObjectIcon(obj.icon) ?? CATEGORY_DEFAULT_ICON[obj.category] ?? 'pin';
}

export const SECURITY_LEVEL_LABELS: Record<SecurityLevel, string> = {
  none: 'Нет охраны',
  low: 'Слабая / обход',
  medium: 'Средняя / ЧОП',
  high: 'Строгий режим',
};

export const ENTRY_DIFFICULTY_LABELS: Record<EntryDifficulty, string> = {
  easy: 'Свободный',
  moderate: 'Средняя',
  hard: 'Сложная',
  technical: 'Спецснаряжение',
};

export const ROLE_LABELS: Record<OperatorRole, string> = {
  scout: 'Разведчик',
  cartographer: 'Картограф',
  comms: 'Связист Mesh',
  archivist: 'Архивариус',
};

export const PDA_ORIENTATION_LABELS: Record<PdaOrientation, string> = {
  portrait: 'Портретная',
  landscape: 'Горизонтальная',
  auto: 'Автоповорот',
};

export const POWER_PROFILE_META: Record<
  PowerProfile,
  { label: string; intervalMs: number; summary: string }
> = {
  eco: {
    label: 'Эко (30 сек)',
    intervalMs: 30_000,
    summary: 'Минимальный опрос GPS, пауза в фоне',
  },
  balanced: {
    label: 'Баланс (10 сек)',
    intervalMs: 10_000,
    summary: 'Оптимально для пешего маршрута',
  },
  precision: {
    label: 'Точный (2 сек)',
    intervalMs: 2_000,
    summary: 'Частое обновление при съёмке координат',
  },
  manual: {
    label: 'Ручной запрос',
    intervalMs: 0,
    summary: 'GPS включается только по нажатию',
  },
};

export const ARCHIVE_TYPE_LABELS: Record<ArchiveMediaType, string> = {
  photo: 'Фото',
  video: 'Видео',
  audio: 'Аудио',
  note: 'Заметка',
  document: 'Документ',
};

export const ZONE_STYLE_LABELS: Record<ZoneStyle, string> = {
  solid: 'Сплошная',
  dashed: 'Пунктир',
  dotted: 'Точки',
  outline: 'Контур',
};

export const ZONE_COLOR_PRESETS: Array<{ hex: string; label: string }> = [
  { hex: '#5B9D72', label: 'Зелёный' },
  { hex: '#DF9E43', label: 'Янтарный' },
  { hex: '#DC6860', label: 'Красный' },
  { hex: '#38BDF8', label: 'Голубой' },
  { hex: '#A78BFA', label: 'Фиолетовый' },
  { hex: '#E6EBE8', label: 'Белый' },
];

export function normalizeZoneColor(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const trimmed = raw.trim();
  const prefixed = trimmed.startsWith('#') ? trimmed : `#${trimmed}`;
  if (/^#[0-9a-fA-F]{6}$/.test(prefixed)) {
    return prefixed.toUpperCase();
  }
  return undefined;
}

export function normalizeZoneStyle(raw?: string | null): ZoneStyle {
  const lower = (raw ?? '').trim().toLowerCase();
  if (lower === 'dashed' || lower === 'dotted' || lower === 'outline' || lower === 'solid') {
    return lower;
  }
  return 'solid';
}

export function getEffectiveZoneColor(
  obj: Pick<UrbexObject, 'status' | 'zoneColor'>
): string {
  return normalizeZoneColor(obj.zoneColor) ?? STATUS_META[obj.status].colorHex;
}

export function getObjectPhotos(
  obj: Pick<UrbexObject, 'photoUrl' | 'photos'> | null | undefined
): string[] {
  if (!obj) return [];
  const seen = new Set<string>();
  const result: string[] = [];
  const pushUnique = (raw?: string | null) => {
    const trimmed = typeof raw === 'string' ? raw.trim() : '';
    if (!trimmed || seen.has(trimmed)) return;
    seen.add(trimmed);
    result.push(trimmed);
  };
  pushUnique(obj.photoUrl);
  if (Array.isArray(obj.photos)) {
    for (const item of obj.photos) {
      pushUnique(item);
    }
  }
  return result;
}

export function formatShortTime(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

export function formatShortDate(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' });
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} Б`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} МБ`;
}
