import { haversineDistanceM, isValidCoordinate } from './geo';
import { CATEGORY_LABELS } from './labels';
import type { UrbexObject } from './types';

export type MapSearchKind = 'coord' | 'object' | 'city' | 'place';
export type MapSearchSource = 'local' | 'online';

export interface MapSearchResult {
  id: string;
  title: string;
  subtitle: string;
  lat: number;
  lon: number;
  kind: MapSearchKind;
  source: MapSearchSource;
  zoomIdx: number;
  objectId?: string;
}

interface OfflineCityEntry {
  id: string;
  name: string;
  region: string;
  lat: number;
  lon: number;
  aliases: string[];
  popularRank?: number;
}

const OFFLINE_CITIES: readonly OfflineCityEntry[] = [
  { id: 'moscow', name: 'Москва', region: 'Россия', lat: 55.7558, lon: 37.6173, aliases: ['moscow', 'msk', 'мск', 'moskva'], popularRank: 1 },
  { id: 'spb', name: 'Санкт-Петербург', region: 'Россия', lat: 59.9343, lon: 30.3351, aliases: ['spb', 'спб', 'питер', 'ленинград', 'saint petersburg', 'st petersburg', 'piter'], popularRank: 2 },
  { id: 'kazan', name: 'Казань', region: 'Татарстан, Россия', lat: 55.7961, lon: 49.1064, aliases: ['kazan', 'кзн'], popularRank: 3 },
  { id: 'ekb', name: 'Екатеринбург', region: 'Свердловская обл., Россия', lat: 56.8389, lon: 60.6057, aliases: ['ekb', 'екб', 'yekaterinburg', 'свердловск'], popularRank: 4 },
  { id: 'nsk', name: 'Новосибирск', region: 'Новосибирская обл., Россия', lat: 55.0084, lon: 82.9357, aliases: ['nsk', 'нск', 'novosibirsk'], popularRank: 5 },
  { id: 'nnov', name: 'Нижний Новгород', region: 'Нижегородская обл., Россия', lat: 56.3269, lon: 44.0059, aliases: ['nizhny novgorod', 'nn', 'нн', 'горький'] },
  { id: 'samara', name: 'Самара', region: 'Самарская обл., Россия', lat: 53.1959, lon: 50.1002, aliases: ['samara', 'куйбышев'] },
  { id: 'krasnodar', name: 'Краснодар', region: 'Краснодарский край, Россия', lat: 45.0355, lon: 38.9753, aliases: ['krasnodar', 'крд'] },
  { id: 'sochi', name: 'Сочи', region: 'Краснодарский край, Россия', lat: 43.5855, lon: 39.7231, aliases: ['sochi'] },
  { id: 'rostov', name: 'Ростов-на-Дону', region: 'Ростовская обл., Россия', lat: 47.2357, lon: 39.7015, aliases: ['rostov', 'рнд', 'rostov-on-don'] },
  { id: 'ufa', name: 'Уфа', region: 'Башкортостан, Россия', lat: 54.7388, lon: 55.9721, aliases: ['ufa'] },
  { id: 'krasnoyarsk', name: 'Красноярск', region: 'Красноярский край, Россия', lat: 56.0153, lon: 92.8932, aliases: ['krasnoyarsk'] },
  { id: 'voronezh', name: 'Воронеж', region: 'Воронежская обл., Россия', lat: 51.672, lon: 39.1843, aliases: ['voronezh', 'врн'] },
  { id: 'perm', name: 'Пермь', region: 'Пермский край, Россия', lat: 58.0105, lon: 56.2502, aliases: ['perm'] },
  { id: 'volgograd', name: 'Волгоград', region: 'Волгоградская обл., Россия', lat: 48.708, lon: 44.5133, aliases: ['volgograd', 'сталинград'] },
  { id: 'chelyabinsk', name: 'Челябинск', region: 'Челябинская обл., Россия', lat: 55.1644, lon: 61.4368, aliases: ['chelyabinsk', 'члб'] },
  { id: 'omsk', name: 'Омск', region: 'Омская обл., Россия', lat: 54.9885, lon: 73.3242, aliases: ['omsk'] },
  { id: 'tyumen', name: 'Тюмень', region: 'Тюменская обл., Россия', lat: 57.1553, lon: 65.5619, aliases: ['tyumen'] },
  { id: 'saratov', name: 'Саратов', region: 'Саратовская обл., Россия', lat: 51.5336, lon: 46.0343, aliases: ['saratov'] },
  { id: 'tolyatti', name: 'Тольятти', region: 'Самарская обл., Россия', lat: 53.5078, lon: 49.4204, aliases: ['tolyatti'] },
  { id: 'izhevsk', name: 'Ижевск', region: 'Удмуртия, Россия', lat: 56.8526, lon: 53.2069, aliases: ['izhevsk'] },
  { id: 'barnaul', name: 'Барнаул', region: 'Алтайский край, Россия', lat: 53.3548, lon: 83.7698, aliases: ['barnaul'] },
  { id: 'irkutsk', name: 'Иркутск', region: 'Иркутская обл., Россия', lat: 52.2869, lon: 104.305, aliases: ['irkutsk'] },
  { id: 'khabarovsk', name: 'Хабаровск', region: 'Хабаровский край, Россия', lat: 48.4802, lon: 135.0719, aliases: ['khabarovsk'] },
  { id: 'vladivostok', name: 'Владивосток', region: 'Приморский край, Россия', lat: 43.1155, lon: 131.8855, aliases: ['vladivostok', 'вдк'] },
  { id: 'kaliningrad', name: 'Калининград', region: 'Калининградская обл., Россия', lat: 54.7104, lon: 20.4522, aliases: ['kaliningrad', 'кёнигсберг'] },
  { id: 'murmansk', name: 'Мурманск', region: 'Мурманская обл., Россия', lat: 68.9707, lon: 33.075, aliases: ['murmansk'] },
  { id: 'arkhangelsk', name: 'Архангельск', region: 'Архангельская обл., Россия', lat: 64.5399, lon: 40.5158, aliases: ['arkhangelsk'] },
  { id: 'yaroslavl', name: 'Ярославль', region: 'Ярославская обл., Россия', lat: 57.6261, lon: 39.8845, aliases: ['yaroslavl'] },
  { id: 'tula', name: 'Тула', region: 'Тульская обл., Россия', lat: 54.1931, lon: 37.6173, aliases: ['tula'] },
  { id: 'tver', name: 'Тверь', region: 'Тверская обл., Россия', lat: 56.8587, lon: 35.9176, aliases: ['tver'] },
  { id: 'ryazan', name: 'Рязань', region: 'Рязанская обл., Россия', lat: 54.6292, lon: 39.7364, aliases: ['ryazan'] },
  { id: 'kaluga', name: 'Калуга', region: 'Калужская обл., Россия', lat: 54.5138, lon: 36.2612, aliases: ['kaluga'] },
  { id: 'vladimir', name: 'Владимир', region: 'Владимирская обл., Россия', lat: 56.1291, lon: 40.4066, aliases: ['vladimir'] },
  { id: 'smolensk', name: 'Смоленск', region: 'Смоленская обл., Россия', lat: 54.7826, lon: 32.0453, aliases: ['smolensk'] },
  { id: 'pskov', name: 'Псков', region: 'Псковская обл., Россия', lat: 57.8193, lon: 28.3324, aliases: ['pskov'] },
  { id: 'veliky-novgorod', name: 'Великий Новгород', region: 'Новгородская обл., Россия', lat: 58.5228, lon: 31.2698, aliases: ['veliky novgorod'] },
  { id: 'petrozavodsk', name: 'Петрозаводск', region: 'Карелия, Россия', lat: 61.7873, lon: 34.3543, aliases: ['petrozavodsk'] },
  { id: 'grozny', name: 'Грозный', region: 'Чечня, Россия', lat: 43.3184, lon: 45.6981, aliases: ['grozny'] },
  { id: 'makhachkala', name: 'Махачкала', region: 'Дагестан, Россия', lat: 42.9831, lon: 47.5047, aliases: ['makhachkala'] },
  { id: 'sevastopol', name: 'Севастополь', region: 'Крым', lat: 44.6167, lon: 33.5254, aliases: ['sevastopol'] },
  { id: 'simferopol', name: 'Симферополь', region: 'Крым', lat: 44.9521, lon: 34.1024, aliases: ['simferopol'] },
  { id: 'minsk', name: 'Минск', region: 'Беларусь', lat: 53.9006, lon: 27.559, aliases: ['minsk'] },
  { id: 'brest', name: 'Брест', region: 'Беларусь', lat: 52.0976, lon: 23.7341, aliases: ['brest'] },
  { id: 'gomel', name: 'Гомель', region: 'Беларусь', lat: 52.4345, lon: 30.9754, aliases: ['gomel'] },
  { id: 'almaty', name: 'Алматы', region: 'Казахстан', lat: 43.2389, lon: 76.8897, aliases: ['almaty', 'алма-ата'] },
  { id: 'astana', name: 'Астана', region: 'Казахстан', lat: 51.1605, lon: 71.4704, aliases: ['astana', 'нур-султан'] },
  { id: 'kyiv', name: 'Киев', region: 'Украина', lat: 50.4501, lon: 30.5234, aliases: ['kyiv', 'kiev'] },
  { id: 'odesa', name: 'Одесса', region: 'Украина', lat: 46.4825, lon: 30.7233, aliases: ['odesa', 'odessa'] },
  { id: 'lviv', name: 'Львов', region: 'Украина', lat: 49.8397, lon: 24.0297, aliases: ['lviv'] },
  { id: 'tbilisi', name: 'Тбилиси', region: 'Грузия', lat: 41.7151, lon: 44.8271, aliases: ['tbilisi'] },
  { id: 'yerevan', name: 'Ереван', region: 'Армения', lat: 40.1872, lon: 44.5152, aliases: ['yerevan'] },
  { id: 'baku', name: 'Баку', region: 'Азербайджан', lat: 40.4093, lon: 49.8671, aliases: ['baku'] },
  { id: 'tashkent', name: 'Ташкент', region: 'Узбекистан', lat: 41.2995, lon: 69.2401, aliases: ['tashkent'] },
  { id: 'bishkek', name: 'Бишкек', region: 'Кыргызстан', lat: 42.8746, lon: 74.5698, aliases: ['bishkek'] },
  { id: 'tallinn', name: 'Таллин', region: 'Эстония', lat: 59.437, lon: 24.7536, aliases: ['tallinn'] },
  { id: 'riga', name: 'Рига', region: 'Латвия', lat: 56.9496, lon: 24.1052, aliases: ['riga'] },
  { id: 'vilnius', name: 'Вильнюс', region: 'Литва', lat: 54.6872, lon: 25.2797, aliases: ['vilnius'] },
  { id: 'warsaw', name: 'Варшава', region: 'Польша', lat: 52.2297, lon: 21.0122, aliases: ['warsaw', 'warszawa'] },
  { id: 'berlin', name: 'Берлин', region: 'Германия', lat: 52.52, lon: 13.405, aliases: ['berlin'] },
  { id: 'prague', name: 'Прага', region: 'Чехия', lat: 50.0755, lon: 14.4378, aliases: ['prague', 'praha'] },
  { id: 'vienna', name: 'Вена', region: 'Австрия', lat: 48.2082, lon: 16.3738, aliases: ['vienna', 'wien'] },
  { id: 'paris', name: 'Париж', region: 'Франция', lat: 48.8566, lon: 2.3522, aliases: ['paris'] },
  { id: 'london', name: 'Лондон', region: 'Великобритания', lat: 51.5072, lon: -0.1276, aliases: ['london'] },
  { id: 'istanbul', name: 'Стамбул', region: 'Турция', lat: 41.0082, lon: 28.9784, aliases: ['istanbul'] },
  { id: 'tokyo', name: 'Токио', region: 'Япония', lat: 35.6762, lon: 139.6503, aliases: ['tokyo'] },
  { id: 'new-york', name: 'Нью-Йорк', region: 'США', lat: 40.7128, lon: -74.006, aliases: ['new york', 'nyc'] },
];

function normalizeSearchToken(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/\s+/g, ' ');
}

export function parseCoordinateSearch(query: string): { lat: number; lon: number } | null {
  const trimmed = query.trim();
  if (!trimmed) return null;

  const coordRegex =
    /^\s*([+-]?\d+(?:[.,]\d+)?)\s*°?\s*([NSСЮ]?)\s*[,;\s]+\s*([+-]?\d+(?:[.,]\d+)?)\s*°?\s*([EWВЗ]?)\s*$/i;
  const match = coordRegex.exec(trimmed);
  if (!match) return null;

  // Replace decimal comma only if it's clearly decimal (e.g. when semicolon/space separates lat and lon)
  const rawLat = Number(match[1].replace(',', '.'));
  const latHem = match[2].toUpperCase();
  const rawLon = Number(match[3].replace(',', '.'));
  const lonHem = match[4].toUpperCase();

  if (!Number.isFinite(rawLat) || !Number.isFinite(rawLon)) return null;

  let lat = rawLat;
  let lon = rawLon;
  if (latHem === 'S' || latHem === 'Ю') lat = -Math.abs(lat);
  if (lonHem === 'W' || lonHem === 'З') lon = -Math.abs(lon);

  if (!isValidCoordinate(lat, lon)) return null;
  return {
    lat: Number(lat.toFixed(6)),
    lon: Number(lon.toFixed(6)),
  };
}

export function searchOfflineMapLocations(
  query: string,
  objects: readonly UrbexObject[],
  limit = 8
): MapSearchResult[] {
  const normalized = normalizeSearchToken(query);
  const results: MapSearchResult[] = [];

  if (!normalized) {
    return OFFLINE_CITIES
      .filter((c) => c.popularRank !== undefined)
      .sort((a, b) => (a.popularRank ?? 99) - (b.popularRank ?? 99))
      .slice(0, Math.min(limit, 5))
      .map((city) => ({
        id: `city-${city.id}`,
        title: city.name,
        subtitle: city.region,
        lat: city.lat,
        lon: city.lon,
        kind: 'city' as const,
        source: 'local' as const,
        zoomIdx: 1,
      }));
  }

  const parsedCoord = parseCoordinateSearch(query);
  if (parsedCoord) {
    results.push({
      id: `coord-${parsedCoord.lat}-${parsedCoord.lon}`,
      title: `${parsedCoord.lat.toFixed(5)}, ${parsedCoord.lon.toFixed(5)}`,
      subtitle: 'Перейти к координатам',
      lat: parsedCoord.lat,
      lon: parsedCoord.lon,
      kind: 'coord',
      source: 'local',
      zoomIdx: 3,
    });
  }

  const scoredObjects: Array<{ score: number; item: MapSearchResult }> = [];
  for (const obj of objects) {
    const titleNorm = normalizeSearchToken(obj.title);
    const subNorm = normalizeSearchToken(obj.subtype ?? '');
    const catNorm = normalizeSearchToken(CATEGORY_LABELS[obj.category] ?? '');
    let score = 0;
    if (titleNorm === normalized) score = 120;
    else if (titleNorm.startsWith(normalized)) score = 100;
    else if (titleNorm.includes(normalized)) score = 80;
    else if (subNorm.includes(normalized) || catNorm.includes(normalized)) score = 60;
    if (score > 0) {
      scoredObjects.push({
        score,
        item: {
          id: `obj-${obj.id}`,
          title: obj.title,
          subtitle: obj.subtype
            ? `${CATEGORY_LABELS[obj.category]} · ${obj.subtype}`
            : CATEGORY_LABELS[obj.category],
          lat: obj.lat,
          lon: obj.lon,
          kind: 'object',
          source: 'local',
          zoomIdx: 4,
          objectId: obj.id,
        },
      });
    }
  }
  scoredObjects.sort((a, b) => b.score - a.score);

  const scoredCities: Array<{ score: number; item: MapSearchResult }> = [];
  for (const city of OFFLINE_CITIES) {
    const nameNorm = normalizeSearchToken(city.name);
    const regionNorm = normalizeSearchToken(city.region);
    const aliasesNorm = city.aliases.map(normalizeSearchToken);

    let score = 0;
    if (nameNorm === normalized || aliasesNorm.includes(normalized)) {
      score = 130;
    } else if (
      nameNorm.startsWith(normalized) ||
      aliasesNorm.some((a) => a.startsWith(normalized))
    ) {
      score = 105;
    } else if (
      nameNorm.includes(normalized) ||
      aliasesNorm.some((a) => a.includes(normalized))
    ) {
      score = 82;
    } else if (regionNorm.includes(normalized)) {
      score = 50;
    }

    if (score > 0) {
      scoredCities.push({
        score: score + (city.popularRank ? 6 - city.popularRank : 0),
        item: {
          id: `city-${city.id}`,
          title: city.name,
          subtitle: city.region,
          lat: city.lat,
          lon: city.lon,
          kind: 'city',
          source: 'local',
          zoomIdx: 1,
        },
      });
    }
  }
  scoredCities.sort((a, b) => b.score - a.score);

  for (const entry of scoredObjects.slice(0, 4)) {
    results.push(entry.item);
  }
  for (const entry of scoredCities) {
    if (results.length >= limit) break;
    results.push(entry.item);
  }
  return results.slice(0, limit);
}

const CITY_ADDRESS_TYPES = new Set([
  'city',
  'town',
  'village',
  'hamlet',
  'municipality',
  'state',
  'region',
  'administrative',
  'county',
]);

export function parseNominatimResults(payload: unknown): MapSearchResult[] {
  if (!Array.isArray(payload)) return [];
  const parsed: MapSearchResult[] = [];

  for (const raw of payload) {
    if (!raw || typeof raw !== 'object') continue;
    const item = raw as Record<string, unknown>;
    const lat = Number(item.lat);
    const lon = Number(item.lon);
    if (!isValidCoordinate(lat, lon)) continue;

    const displayName =
      typeof item.display_name === 'string' ? item.display_name.trim() : '';
    const explicitName = typeof item.name === 'string' ? item.name.trim() : '';
    const parts = displayName
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);

    const title = explicitName || parts[0] || `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
    const subtitleParts = parts.filter((part, index) => {
      if (index === 0 && normalizeSearchToken(part) === normalizeSearchToken(title)) {
        return false;
      }
      return true;
    });
    const subtitle = subtitleParts.slice(0, 3).join(', ') || 'OpenStreetMap';

    const addrType = typeof item.addresstype === 'string' ? item.addresstype : '';
    const objType = typeof item.type === 'string' ? item.type : '';
    const isCityLike =
      CITY_ADDRESS_TYPES.has(addrType) || CITY_ADDRESS_TYPES.has(objType);

    const placeId = item.place_id !== undefined ? String(item.place_id) : `${lat}-${lon}`;
    parsed.push({
      id: `osm-${placeId}`,
      title,
      subtitle,
      lat: Number(lat.toFixed(6)),
      lon: Number(lon.toFixed(6)),
      kind: isCityLike ? 'city' : 'place',
      source: 'online',
      zoomIdx: isCityLike ? 1 : 3,
    });
  }

  return parsed;
}

export function mergeMapSearchResults(
  localResults: readonly MapSearchResult[],
  onlineResults: readonly MapSearchResult[],
  limit = 10
): MapSearchResult[] {
  const merged: MapSearchResult[] = [...localResults];

  for (const remote of onlineResults) {
    const remoteTitle = normalizeSearchToken(remote.title);
    const duplicate = merged.some((existing) => {
      const sameName = normalizeSearchToken(existing.title) === remoteTitle;
      const distM = haversineDistanceM(existing.lat, existing.lon, remote.lat, remote.lon);
      return sameName && distM < 15_000;
    });
    if (!duplicate) {
      merged.push(remote);
    }
    if (merged.length >= limit) break;
  }

  return merged.slice(0, limit);
}

export async function searchMapLocationsOnline(
  query: string,
  signal?: AbortSignal
): Promise<MapSearchResult[]> {
  const trimmed = query.trim();
  if (trimmed.length < 2) return [];

  const url =
    `https://nominatim.openstreetmap.org/search?format=jsonv2&accept-language=ru&limit=6&q=${encodeURIComponent(trimmed)}`;

  const timeoutCtrl = new AbortController();
  const timer = globalThis.setTimeout(() => timeoutCtrl.abort(), 4500);
  const onParentAbort = () => timeoutCtrl.abort();
  signal?.addEventListener('abort', onParentAbort, { once: true });

  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      signal: timeoutCtrl.signal,
    });
    if (!res.ok) return [];
    const json: unknown = await res.json();
    return parseNominatimResults(json);
  } catch {
    return [];
  } finally {
    globalThis.clearTimeout(timer);
    signal?.removeEventListener('abort', onParentAbort);
  }
}
