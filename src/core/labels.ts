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
  military: 'Военный объект / ВЧ',
  energy: 'Энергетика / ТЭЦ',
  medical: 'Медицина / Госпиталь',
  transport: 'Транспорт / Депо / ЖД',
  hydro: 'Гидротехника / Дамба',
  mining: 'Шахта / Карьер',
  ruins: 'Заброшка / Недострой',
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
  'military',
  'silo',
  'lab',
  'biohazard',
  'antenna',
  'train',
  'bridge',
  'dam',
  'monument',
  'school',
  'hotel',
  'ruins',
  'shelter',
  'fire',
  'compass',
  'stash',
  'substation',
  'cooling',
  'cistern',
  'elevator',
  'hangar',
  'checkpoint',
  'barracks',
  'range',
  'searchlight',
  'munition',
  'metro',
  'vent',
  'drainage',
  'catacomb',
  'vault',
  'depot',
  'bridge_rail',
  'pier',
  'lighthouse',
  'quarry',
  'mine_cart',
  'oil_rig',
  'pipeline',
  'morgue',
  'sanatorium',
  'church',
  'castle',
  'theater',
  'stadium',
  'mall',
  'generator',
  'evac',
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
  military: { label: 'Военный объект / ВЧ' },
  silo: { label: 'ШПУ / ракетная шахта' },
  lab: { label: 'НИИ / лаборатория' },
  biohazard: { label: 'Биоопасность / химзона' },
  antenna: { label: 'Ретранслятор / радио' },
  train: { label: 'Ж/Д узел / тупик' },
  bridge: { label: 'Мост / эстакада' },
  dam: { label: 'Плотина / ГЭС / шлюз' },
  monument: { label: 'Усадьба / памятник / ДК' },
  school: { label: 'Школа / корпус / ВУЗ' },
  hotel: { label: 'Санаторий / пансионат' },
  ruins: { label: 'Руины / развалины' },
  shelter: { label: 'Убежище / бомбоубежище' },
  fire: { label: 'Пожарная часть / МЧС' },
  compass: { label: 'Ориентир / триангуляция' },
  stash: { label: 'Тайник / схрон / кэш' },
  substation: { label: 'Щитовая / ТП / РУ' },
  cooling: { label: 'Градирня / ТЭЦ-труба' },
  cistern: { label: 'Резервуар / РВС' },
  elevator: { label: 'Элеватор / зернобашня' },
  hangar: { label: 'Авиаангар / капонир' },
  checkpoint: { label: 'Блокпост / шлагбаум' },
  barracks: { label: 'Казарма / штаб ВЧ' },
  range: { label: 'Полигон / стрельбище' },
  searchlight: { label: 'Прожектор / ПВО-пост' },
  munition: { label: 'Арсенал / склад БК' },
  metro: { label: 'Станция / вестибюль метро' },
  vent: { label: 'Вентшахта / ВШ' },
  drainage: { label: 'Штольня / дренаж' },
  catacomb: { label: 'Катакомбы / выработки' },
  vault: { label: 'Спецблок / хранилище' },
  depot: { label: 'Ж/Д депо / веерное' },
  bridge_rail: { label: 'Ж/Д мост / виадук' },
  pier: { label: 'Дебаркадер / пирс' },
  lighthouse: { label: 'Маяк / створный знак' },
  quarry: { label: 'Карьер / разрез' },
  mine_cart: { label: 'Рудник / вагонетка' },
  oil_rig: { label: 'Нефтекачка / скважина' },
  pipeline: { label: 'Трубопровод / эстакада' },
  morgue: { label: 'Морг / прозекторская' },
  sanatorium: { label: 'Профилакторий / пионерлагерь' },
  church: { label: 'Храм / колокольня' },
  castle: { label: 'Усадьба / замок / башня' },
  theater: { label: 'ДК / кинотеатр / клуб' },
  stadium: { label: 'Стадион / спортзал' },
  mall: { label: 'ТЦ / универмаг / рынок' },
  generator: { label: 'ДГУ / резервный дизель' },
  evac: { label: 'Точка эвакуации / выход' },
};

export interface ObjectSubtypePreset {
  label: string;
  icon: ObjectIconId;
}

export const CATEGORY_SUBTYPES: Record<ObjectCategory, ObjectSubtypePreset[]> = {
  industrial: [
    { label: 'Цех / промкорпус', icon: 'factory' },
    { label: 'Элеватор / силос', icon: 'elevator' },
    { label: 'Склад / ангар', icon: 'warehouse' },
    { label: 'Резервуар ГСМ', icon: 'fuel' },
    { label: 'НИИ / лаборатория', icon: 'lab' },
    { label: 'Ремзона / мастерская', icon: 'workshop' },
  ],
  bunker: [
    { label: 'Бомбоубежище ЗС ГО', icon: 'shelter' },
    { label: 'Командный пункт (ЗКП)', icon: 'bunker' },
    { label: 'Спецхранилище', icon: 'vault' },
    { label: 'ДГУ / дизельная', icon: 'generator' },
    { label: 'Точка эвакуации', icon: 'evac' },
  ],
  civilian: [
    { label: 'ДК / кинотеатр', icon: 'theater' },
    { label: 'Школа / ВУЗ / НИИ', icon: 'school' },
    { label: 'Санаторий / пионерлагерь', icon: 'sanatorium' },
    { label: 'Храм / колокольня', icon: 'church' },
    { label: 'Усадьба / памятник', icon: 'castle' },
    { label: 'ТЦ / универмаг', icon: 'mall' },
    { label: 'Стадион / спорткомплекс', icon: 'stadium' },
  ],
  infrastructure: [
    { label: 'Радиовышка / мачта', icon: 'tower' },
    { label: 'Ретранслятор', icon: 'antenna' },
    { label: 'КПП / периметр', icon: 'checkpoint' },
    { label: 'Трубопроводная эстакада', icon: 'pipeline' },
    { label: 'Пожарная часть', icon: 'fire' },
  ],
  underground: [
    { label: 'Тоннель / спецход', icon: 'tunnel' },
    { label: 'Вентшахта (ВШ)', icon: 'vent' },
    { label: 'Дренаж / ливневой коллектор', icon: 'drainage' },
    { label: 'Катакомбы / каменоломня', icon: 'catacomb' },
    { label: 'Вестибюль / метрострой', icon: 'metro' },
  ],
  military: [
    { label: 'Воинская часть / штаб', icon: 'barracks' },
    { label: 'ШПУ / ракетная база', icon: 'silo' },
    { label: 'РЛС / узел связи', icon: 'radar' },
    { label: 'Полигон / стрельбище', icon: 'range' },
    { label: 'Склад БК / арсенал', icon: 'munition' },
    { label: 'Позиция ПВО / капонир', icon: 'searchlight' },
  ],
  energy: [
    { label: 'Подстанция / ОРУ / ТП', icon: 'substation' },
    { label: 'ТЭЦ / котельная', icon: 'boiler' },
    { label: 'Градирня / дымовая труба', icon: 'cooling' },
    { label: 'ЛЭП / высоковольтная опора', icon: 'power' },
    { label: 'ДГУ / резервный генератор', icon: 'generator' },
  ],
  medical: [
    { label: 'Госпиталь / больница', icon: 'hospital' },
    { label: 'Морг / патанатомия', icon: 'morgue' },
    { label: 'Биолаборатория', icon: 'biohazard' },
    { label: 'Профилакторий / здравница', icon: 'hotel' },
  ],
  transport: [
    { label: 'Ж/Д депо / веерное', icon: 'depot' },
    { label: 'Ж/Д мост / виадук', icon: 'bridge_rail' },
    { label: 'Аэродром / ВПП / ангар', icon: 'hangar' },
    { label: 'Причал / дебаркадер', icon: 'pier' },
    { label: 'Эстакада / путепровод', icon: 'bridge' },
  ],
  hydro: [
    { label: 'Плотина / ГЭС / шлюз', icon: 'dam' },
    { label: 'Водозабор / насосная', icon: 'water' },
    { label: 'Резервуар / водонапорка', icon: 'cistern' },
    { label: 'Маяк / гидропост', icon: 'lighthouse' },
  ],
  mining: [
    { label: 'Шахтный ствол / копёр', icon: 'shaft' },
    { label: 'Открытый карьер / разрез', icon: 'quarry' },
    { label: 'Рудник / штольня', icon: 'mine_cart' },
    { label: 'Нефтяная вышка / скважина', icon: 'oil_rig' },
  ],
  ruins: [
    { label: 'Недострой / башенный кран', icon: 'crane' },
    { label: 'Руины / развалины', icon: 'ruins' },
    { label: 'Исторический монумент', icon: 'monument' },
    { label: 'Тайник / схрон', icon: 'stash' },
    { label: 'Опасный / аварийный сектор', icon: 'hazard' },
  ],
};

export const CATEGORY_DEFAULT_ICON: Record<ObjectCategory, ObjectIconId> = {
  industrial: 'factory',
  bunker: 'bunker',
  civilian: 'building',
  infrastructure: 'tower',
  underground: 'tunnel',
  military: 'military',
  energy: 'power',
  medical: 'hospital',
  transport: 'train',
  hydro: 'dam',
  mining: 'shaft',
  ruins: 'ruins',
};

const OBJECT_ICON_SET = new Set<string>(OBJECT_ICONS);
const OBJECT_CATEGORY_SET = new Set<string>(Object.keys(CATEGORY_LABELS));

export function normalizeObjectCategory(raw?: string | null): ObjectCategory {
  if (!raw) return 'industrial';
  const cleaned = raw.trim().toLowerCase();
  if (OBJECT_CATEGORY_SET.has(cleaned)) {
    return cleaned as ObjectCategory;
  }
  return 'industrial';
}

export function normalizeObjectIcon(raw?: string | null): ObjectIconId | undefined {
  if (!raw) return undefined;
  const cleaned = raw.trim().toLowerCase().replace(/-/g, '_');
  if (OBJECT_ICON_SET.has(cleaned)) {
    return cleaned as ObjectIconId;
  }
  return undefined;
}

export function normalizeObjectSubtype(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const cleaned = raw.trim();
  return cleaned.length > 0 ? cleaned : undefined;
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
