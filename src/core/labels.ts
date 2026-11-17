import type {
  ArchiveMediaType,
  ObjectCategory,
  OperatorRole,
  PowerProfile,
  ReliabilityStatus,
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

export const ROLE_LABELS: Record<OperatorRole, string> = {
  scout: 'Разведчик',
  cartographer: 'Картограф',
  comms: 'Связист Mesh',
  archivist: 'Архивариус',
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
