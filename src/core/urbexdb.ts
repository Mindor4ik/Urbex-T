import { createSeedPayload } from '../data/seed';
import { DEFAULT_CLOUD_SYNC_CONFIG, sanitizePayloadForSync } from './cloudSync';
import {
  normalizeObjectCategory,
  normalizeObjectIcon,
  normalizeObjectSubtype,
  normalizeZoneColor,
  normalizeZoneStyle,
} from './labels';
import type {
  ArchiveItem,
  BaseArticle,
  ConflictStrategy,
  MeshMessage,
  UrbexDbBundle,
  UrbexDbPayload,
  UrbexObject,
} from './types';

export const URBEXDB_FORMAT = 'urbex-t-db' as const;
export const URBEXDB_VERSION = '1.0.0' as const;

/**
 * Deterministic 64-bit FNV-1a + DJB2 dual-hash checksum in hex.
 * Runs synchronously without SubtleCrypto async restrictions across Node tests & offline browsers.
 */
export function computePayloadChecksum(payload: UrbexDbPayload): string {
  const canonical = JSON.stringify(payload);
  let h1 = 0x811c9dc5;
  let h2 = 5381;
  for (let i = 0; i < canonical.length; i++) {
    const code = canonical.charCodeAt(i);
    h1 ^= code;
    h1 = Math.imul(h1, 0x01000193);
    h2 = (Math.imul(h2, 33) ^ code) >>> 0;
  }
  const part1 = (h1 >>> 0).toString(16).padStart(8, '0');
  const part2 = (h2 >>> 0).toString(16).padStart(8, '0');
  return `uxdb-${part1}${part2}`;
}

export function createUrbexDbBundle(
  payload: UrbexDbPayload,
  deviceNodeId: string,
  exportedAt = Date.now()
): UrbexDbBundle {
  const safePayload = sanitizePayloadForSync(payload);
  const checksum = computePayloadChecksum(safePayload);
  return {
    manifest: {
      format: URBEXDB_FORMAT,
      version: URBEXDB_VERSION,
      exportedAt,
      deviceNodeId,
      checksum,
      counts: {
        objects: safePayload.objects.length,
        meshMessages: safePayload.meshMessages.length,
        baseArticles: safePayload.baseArticles.length,
        archiveItems: safePayload.archiveItems.length,
      },
    },
    payload: safePayload,
  };
}

export function serializeUrbexDb(
  payload: UrbexDbPayload,
  deviceNodeId: string,
  exportedAt = Date.now()
): string {
  const bundle = createUrbexDbBundle(payload, deviceNodeId, exportedAt);
  return JSON.stringify(bundle, null, 2);
}

export type ParseUrbexDbResult =
  | { ok: true; bundle: UrbexDbBundle; warnings: string[] }
  | { ok: false; error: string; code: 'INVALID_JSON' | 'INVALID_SCHEMA' | 'UNSUPPORTED_VERSION' | 'CHECKSUM_MISMATCH' };

type RawObjectCompat = Partial<UrbexObject> & {
  photoUri?: string;
  photoUrls?: string[];
  photoUris?: string[];
};
type RawArchiveCompat = Partial<ArchiveItem> & { contentUri?: string };

function normalizePhotoList(o: RawObjectCompat): { photoUrl?: string; photos?: string[] } {
  const seen = new Set<string>();
  const list: string[] = [];
  const push = (raw?: string | null) => {
    const trimmed = typeof raw === 'string' ? raw.trim() : '';
    if (!trimmed || seen.has(trimmed)) return;
    seen.add(trimmed);
    list.push(trimmed);
  };
  push(o.photoUrl ?? o.photoUri);
  if (Array.isArray(o.photos)) o.photos.forEach(push);
  if (Array.isArray(o.photoUrls)) o.photoUrls.forEach(push);
  if (Array.isArray(o.photoUris)) o.photoUris.forEach(push);
  return {
    photoUrl: list[0],
    photos: list.length > 0 ? list : undefined,
  };
}

function normalizeObjects(rawList: RawObjectCompat[]): UrbexObject[] {
  const now = Date.now();
  return rawList
    .filter((o) => o && typeof o.title === 'string' && typeof o.lat === 'number' && typeof o.lon === 'number')
    .map((o, idx) => {
      const normalizedPhotos = normalizePhotoList(o);
      return {
        id: o.id || `obj-manual-${now.toString(36)}-${idx}`,
        title: o.title!.trim(),
        category: normalizeObjectCategory(o.category),
        subtype: normalizeObjectSubtype(o.subtype),
        icon: normalizeObjectIcon(o.icon),
        status: o.status ?? 'unverified',
        lat: o.lat!,
        lon: o.lon!,
        elevationM: typeof o.elevationM === 'number' ? Math.round(o.elevationM) : 0,
        description: o.description ?? 'Импортированный объект.',
        accessNotes: o.accessNotes ?? 'Подходы уточняются на местности.',
        hazards: Array.isArray(o.hazards) ? o.hazards : [],
        photoUrl: normalizedPhotos.photoUrl,
        photos: normalizedPhotos.photos,
        securityLevel: o.securityLevel ?? 'none',
        entryDifficulty: o.entryDifficulty ?? 'moderate',
        floorsOrDepth: o.floorsOrDepth,
        customParams: Array.isArray(o.customParams) ? o.customParams : [],
        zone: Array.isArray(o.zone) ? o.zone : undefined,
        zoneColor: normalizeZoneColor(o.zoneColor),
        zoneStyle: normalizeZoneStyle(o.zoneStyle),
        zoneHatched: Boolean(o.zoneHatched),
        createdAt: typeof o.createdAt === 'number' ? o.createdAt : now,
        updatedAt: typeof o.updatedAt === 'number' ? o.updatedAt : now,
        authorNodeId: o.authorNodeId ?? '!manual00',
      };
    });
}

function normalizeArchive(rawList: RawArchiveCompat[]): ArchiveItem[] {
  const now = Date.now();
  return rawList
    .filter((a) => a && typeof a.title === 'string')
    .map((a, idx) => ({
      id: a.id || `arch-manual-${now.toString(36)}-${idx}`,
      title: a.title!.trim(),
      type: a.type ?? 'note',
      objectId: a.objectId,
      mimeType: a.mimeType ?? 'text/plain',
      sizeBytes: typeof a.sizeBytes === 'number' ? a.sizeBytes : 0,
      dataUrl: a.dataUrl ?? a.contentUri,
      textContent: a.textContent,
      notes: a.notes ?? '',
      createdAt: typeof a.createdAt === 'number' ? a.createdAt : now,
      updatedAt: typeof a.updatedAt === 'number' ? a.updatedAt : now,
    }));
}

/**
 * Removes // line comments and /* ... *\/ block comments outside JSON string literals,
 * allowing hand-authored .urbexdb templates with inline documentation to parse cleanly.
 */
export function stripJsonComments(raw: string): string {
  const input = raw.charCodeAt(0) === 0xfeff ? raw.slice(1) : raw;
  let out = '';
  let inString = false;
  let escaped = false;
  let i = 0;

  while (i < input.length) {
    const ch = input[i];
    const next = i + 1 < input.length ? input[i + 1] : '';

    if (inString) {
      out += ch;
      if (escaped) {
        escaped = false;
      } else if (ch === '\\') {
        escaped = true;
      } else if (ch === '"') {
        inString = false;
      }
      i++;
      continue;
    }

    if (ch === '"') {
      inString = true;
      out += ch;
      i++;
      continue;
    }

    if (ch === '/' && next === '/') {
      i += 2;
      while (i < input.length && input[i] !== '\n' && input[i] !== '\r') {
        i++;
      }
      continue;
    }

    if (ch === '/' && next === '*') {
      i += 2;
      while (i < input.length) {
        if (input[i] === '*' && i + 1 < input.length && input[i + 1] === '/') {
          i += 2;
          break;
        }
        if (input[i] === '\n') out += '\n';
        i++;
      }
      continue;
    }

    out += ch;
    i++;
  }

  return out;
}

export function parseAndVerifyUrbexDb(raw: string): ParseUrbexDbResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(stripJsonComments(raw));
  } catch {
    return {
      ok: false,
      code: 'INVALID_JSON',
      error: 'Файл повреждён: невалидный формат JSON внутри .urbexdb.',
    };
  }

  if (!parsed || typeof parsed !== 'object') {
    return {
      ok: false,
      code: 'INVALID_SCHEMA',
      error: 'Некорректная структура контейнера .urbexdb.',
    };
  }

  const candidate = parsed as Partial<UrbexDbBundle>;
  if (
    !candidate.manifest ||
    !candidate.payload ||
    candidate.manifest.format !== URBEXDB_FORMAT
  ) {
    return {
      ok: false,
      code: 'INVALID_SCHEMA',
      error: 'Отсутствует заголовок формата urbex-t-db.',
    };
  }

  const majorVersion = String(candidate.manifest.version ?? '1.0.0').split('.')[0];
  if (majorVersion !== '1') {
    return {
      ok: false,
      code: 'UNSUPPORTED_VERSION',
      error: `Неподдерживаемая версия формата: ${String(candidate.manifest.version)}.`,
    };
  }

  const rawPayload = candidate.payload as Partial<UrbexDbPayload>;
  if (!Array.isArray(rawPayload.objects)) {
    return {
      ok: false,
      code: 'INVALID_SCHEMA',
      error: 'В контейнере .urbexdb отсутствует массив payload.objects.',
    };
  }

  const warnings: string[] = [];
  const rawChecksum = String(candidate.manifest.checksum ?? '').trim();
  const isManualChecksum =
    rawChecksum === '' || rawChecksum === 'manual' || rawChecksum === 'auto';

  if (isManualChecksum) {
    warnings.push('Импорт ручного шаблона .urbexdb (контрольная сумма пересчитана автоматически).');
  } else {
    // Check strict payload integrity whenever all standard fields were serialized
    if (
      Array.isArray(rawPayload.meshMessages) &&
      Array.isArray(rawPayload.baseArticles) &&
      Array.isArray(rawPayload.archiveItems) &&
      rawPayload.profile &&
      rawPayload.settings
    ) {
      const expectedChecksum = computePayloadChecksum(rawPayload as UrbexDbPayload);
      if (rawChecksum !== expectedChecksum) {
        return {
          ok: false,
          code: 'CHECKSUM_MISMATCH',
          error: `Нарушена контрольная сумма целостности (ожидалось ${expectedChecksum}, получено ${rawChecksum}).`,
        };
      }
    } else {
      return {
        ok: false,
        code: 'INVALID_SCHEMA',
        error: 'В контейнере .urbexdb отсутствуют обязательные таблицы данных.',
      };
    }
  }

  const fallbackSeed = createSeedPayload();
  const normalizedPayload: UrbexDbPayload = {
    objects: normalizeObjects(rawPayload.objects as RawObjectCompat[]),
    meshMessages: Array.isArray(rawPayload.meshMessages) ? rawPayload.meshMessages : [],
    baseArticles: Array.isArray(rawPayload.baseArticles) ? rawPayload.baseArticles : [],
    archiveItems: Array.isArray(rawPayload.archiveItems)
      ? normalizeArchive(rawPayload.archiveItems as RawArchiveCompat[])
      : [],
    profile: rawPayload.profile ?? fallbackSeed.profile,
    settings: rawPayload.settings
      ? {
          ...fallbackSeed.settings,
          ...rawPayload.settings,
          radioChannelsTabEnabled: rawPayload.settings.radioChannelsTabEnabled ?? true,
          editorEnabled: rawPayload.settings.editorEnabled ?? false,
          radioChannels:
            Array.isArray(rawPayload.settings.radioChannels) &&
            rawPayload.settings.radioChannels.length > 0
              ? rawPayload.settings.radioChannels
              : fallbackSeed.settings.radioChannels,
          cloudSync: rawPayload.settings.cloudSync
            ? { ...DEFAULT_CLOUD_SYNC_CONFIG, ...rawPayload.settings.cloudSync }
            : fallbackSeed.settings.cloudSync,
        }
      : fallbackSeed.settings,
  };

  if (candidate.manifest.counts?.objects !== normalizedPayload.objects.length) {
    warnings.push('Счётчик объектов в манифесте скорректирован по факту.');
  }

  const normalizedBundle = isManualChecksum
    ? createUrbexDbBundle(
        normalizedPayload,
        candidate.manifest.deviceNodeId || normalizedPayload.profile.nodeId,
        candidate.manifest.exportedAt || Date.now()
      )
    : {
        manifest: candidate.manifest as UrbexDbBundle['manifest'],
        payload: normalizedPayload,
      };

  return {
    ok: true,
    bundle: normalizedBundle,
    warnings,
  };
}

export function buildUrbexDbTemplateJson(withHints = false): string {
  const now = 1795000000000;
  const template = {
    manifest: {
      format: URBEXDB_FORMAT,
      version: URBEXDB_VERSION,
      exportedAt: now,
      deviceNodeId: '!a48f20c1',
      checksum: 'manual',
      counts: {
        objects: 1,
        meshMessages: 0,
        baseArticles: 1,
        archiveItems: 0,
      },
    },
    payload: {
      objects: [
        {
          id: 'obj-tyumen-01',
          title: 'ТЭЦ-1 · Заброшенный мазутный корпус',
          category: 'industrial',
          subtype: 'ТЭЦ / котельная',
          icon: 'boiler',
          status: 'verified',
          lat: 57.15224,
          lon: 65.52723,
          elevationM: 76,
          description: 'Кирпичный производственный корпус, сохранились мостовые краны и техническая галерея.',
          accessNotes: 'Подход со стороны старой ж/д ветки, забор частично отсутствует.',
          hazards: ['Открытые люки', 'Ржавые перекрытия'],
          photoUrl: 'https://example.com/photo.jpg',
          photos: [
            'https://example.com/photo.jpg',
            'https://example.com/photo-2.jpg',
          ],
          securityLevel: 'low',
          entryDifficulty: 'moderate',
          floorsOrDepth: '3 этажа + подвал -4 м',
          customParams: [
            { key: 'Год постройки', value: '1962' },
            { key: 'O2', value: '20.8%' },
          ],
          zone: [
            { lat: 57.1525, lon: 65.5268 },
            { lat: 57.1525, lon: 65.5277 },
            { lat: 57.1519, lon: 65.5277 },
            { lat: 57.1519, lon: 65.5268 },
          ],
          zoneColor: '#38BDF8',
          zoneStyle: 'dashed',
          zoneHatched: true,
          createdAt: now,
          updatedAt: now,
          authorNodeId: '!a48f20c1',
        },
      ],
      meshMessages: [],
      baseArticles: [
        {
          id: 'art-manual-01',
          title: 'Секторная сводка по региону',
          section: 'safety',
          summary: 'Краткая памятка по объекту и точкам эвакуации.',
          content: 'Проверять перекрытия, фиксировать аварийные выходы на карте.',
          updatedAt: now,
          pinned: true,
        },
      ],
      archiveItems: [],
    },
  };

  if (!withHints) {
    return JSON.stringify(template, null, 2);
  }

  return `/* ============================================================================
 * ПРАВИЛА СОЗДАНИЯ БАЗЫ ДАННЫХ URBEX-T (.urbexdb v1.0.0)
 * ============================================================================
 * 1. Кодировка файла: UTF-8, расширение: .urbexdb (поддерживается в Web и Android).
 * 2. Синтаксис: стандартный JSON + строчные // и блочные комментарии (удаляются при импорте).
 *    НЕ оставляйте лишних запятых после последнего поля перед "}" или "]".
 * 3. Контрольная сумма: для ручных файлов и генераторов ставьте "checksum": "manual".
 *    При импорте приложение само пересчитает хэш FNV-1a+DJB2 и счётчики counts.
 * 4. Обязательные поля объекта в payload.objects[]:
 *    - "title" (строка) — отображаемое название точки;
 *    - "lat", "lon" (числа WGS-84) — широта [-90..90] и долгота [-180..180].
 *    Остальные поля имеют безопасные значения по умолчанию (id сгенерируется сам).
 * 5. Фотографии: можно указать обложку "photoUrl" / "photoUri" И/ИЛИ массив "photos": [...].
 *    Допускаются HTTPS-ссылки, локальные URI или Data URI (data:image/jpeg;base64,...).
 * 6. Зона на карте ("zone"): массив >= 3 точек {"lat": ..., "lon": ...} по контуру.
 *    Можно задать цвет ("zoneColor": "#RRGGBB"), стиль ("zoneStyle") и штриховку ("zoneHatched").
 * 7. Время ("exportedAt", "createdAt", "updatedAt"): Unix timestamp в миллисекундах (ms).
 * 8. Дополнительные таблицы: "baseArticles", "archiveItems", "meshMessages", "profile",
 *    "settings" необязательны — при отсутствии подставятся безопасные значения по умолчанию.
 * ============================================================================ */
{
  // Метаданные контейнера .urbexdb — обязательный заголовок файла
  "manifest": {
    "format": "urbex-t-db",          // Обязательный идентификатор формата (не менять)
    "version": "1.0.0",              // Версия схемы (поддерживаются все 1.x)
    "exportedAt": ${now},     // Время сборки файла (Unix ms)
    "deviceNodeId": "!a48f20c1",     // Meshtastic HEX ID автора/терминала (формат !<8 hex>)
    "checksum": "manual",            // "manual" отключает строгую сверку и включает авто-хэш при импорте
    "counts": {                      // Сводные счётчики таблиц (при импорте уточняются автоматически)
      "objects": 1,
      "meshMessages": 0,
      "baseArticles": 1,
      "archiveItems": 0
    }
  },

  // Основные таблицы базы данных
  "payload": {
    // Реестр объектов на карте и в каталоге
    "objects": [
      {
        "id": "obj-tyumen-01",       // Уникальный ID объекта (используется при слиянии без дублей)
        "title": "ТЭЦ-1 · Заброшенный мазутный корпус", // Название объекта (ОБЯЗАТЕЛЬНО)
        "category": "industrial",    // Категория: industrial | bunker | civilian | infrastructure | underground | military | energy | medical | transport | hydro | mining | ruins
        "subtype": "ТЭЦ / котельная", // Подтип сооружения (опционально, произвольная строка или пресет категории)
        "icon": "boiler",            // Иконка маркера (72 варианта): factory | bunker | building | tower | tunnel | warehouse | power | boiler | water | fuel | radar | radiation | hazard | skull | observation | gate | crane | hospital | airfield | port | shaft | workshop | camera | pin | military | silo | lab | biohazard | antenna | train | bridge | dam | monument | school | hotel | ruins | shelter | fire | compass | stash | substation | cooling | cistern | elevator | hangar | checkpoint | barracks | range | searchlight | munition | metro | vent | drainage | catacomb | vault | depot | bridge_rail | pier | lighthouse | quarry | mine_cart | oil_rig | pipeline | morgue | sanatorium | church | castle | theater | stadium | mall | generator | evac
        "status": "verified",        // Статус: verified | unverified | contested | hazardous
        "lat": 57.15224,             // Широта WGS-84 (ОБЯЗАТЕЛЬНО, число)
        "lon": 65.52723,             // Долгота WGS-84 (ОБЯЗАТЕЛЬНО, число)
        "elevationM": 76,            // Высота над уровнем моря в метрах (по умолчанию 0)
        "description": "Кирпичный производственный корпус, сохранились мостовые краны и техническая галерея.", // Подробное описание состояния
        "accessNotes": "Подход со стороны старой ж/д ветки, забор частично отсутствует.", // Маршрут подхода и точки входа
        "hazards": [                 // Список опасностей и рисков (массив строк)
          "Открытые люки",
          "Ржавые перекрытия"
        ],
        "photoUrl": "https://example.com/photo.jpg", // Главное фото / обложка (совместимо с Android "photoUri")
        "photos": [                  // Галерея фото объекта (первое фото становится обложкой, остальные в карусели)
          "https://example.com/photo.jpg",
          "https://example.com/photo-2.jpg"
        ],
        "securityLevel": "low",      // Охрана: none | low | medium | high
        "entryDifficulty": "moderate", // Доступ: easy | moderate | hard | technical
        "floorsOrDepth": "3 этажа + подвал -4 м", // Этажность или глубина (опционально)
        "customParams": [            // Произвольные полевые замеры и характеристики {key, value}
          { "key": "Год постройки", "value": "1962" },
          { "key": "O2", "value": "20.8%" }
        ],
        "zone": [                    // Полигон зоны объекта на карте (минимум 3 вершины)
          { "lat": 57.1525, "lon": 65.5268 },
          { "lat": 57.1525, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5277 },
          { "lat": 57.1519, "lon": 65.5268 }
        ],
        "zoneColor": "#38BDF8",      // Цвет зоны в HEX (#RRGGBB). Удалите поле или null — цвет возьмётся по статусу
        "zoneStyle": "dashed",       // Контур и заливка: solid | dashed | dotted | outline
        "zoneHatched": true,         // Диагональная штриховка внутри зоны (true | false)
        "createdAt": ${now},  // Дата создания записи (Unix ms)
        "updatedAt": ${now},  // Дата последнего изменения (Unix ms, влияет на стратегию слияния keep_newer)
        "authorNodeId": "!a48f20c1"  // ID узла, добавившего объект
      }
    ],

    // Журнал сообщений Meshtastic (для ручных баз обычно оставляют [])
    "meshMessages": [],

    // База знаний сектора (инструкции, сводки, памятки)
    "baseArticles": [
      {
        "id": "art-manual-01",       // Уникальный ID статьи
        "title": "Секторная сводка по региону", // Заголовок статьи
        "section": "safety",         // Раздел: safety | navigation | comms | architecture | field_note
        "summary": "Краткая памятка по объекту и точкам эвакуации.", // Короткий анонс для списка
        "content": "Проверять перекрытия, фиксировать аварийные выходы на карте.", // Полный текст заметки
        "updatedAt": ${now},  // Время обновления (Unix ms)
        "pinned": true               // Закрепить статью вверху списка (true | false)
      }
    ],

    // Полевой архив (привязанные фото, заметки, документы: type = photo | video | audio | note | document)
    "archiveItems": []
  }
}`;
}

export interface SchemaFieldDoc {
  section: string;
  fields: Array<{ name: string; type: string; desc: string }>;
}

export const URBEXDB_SCHEMA_DOCS: SchemaFieldDoc[] = [
  {
    section: 'manifest (заголовок файла .urbexdb)',
    fields: [
      { name: 'format', type: '"urbex-t-db"', desc: 'Обязательный идентификатор формата.' },
      { name: 'version', type: '"1.0.0"', desc: 'Мажорная версия схемы (поддерживается v1.x).' },
      { name: 'checksum', type: '"uxdb-..." | "manual"', desc: 'Хэш FNV-1a+DJB2 или "manual" при ручном создании файла.' },
      { name: 'deviceNodeId', type: 'string', desc: 'ID узла-источника, например "!a48f20c1".' },
      { name: 'exportedAt', type: 'number (ms)', desc: 'Unix timestamp в миллисекундах.' },
    ],
  },
  {
    section: 'payload.objects[] (точки объектов)',
    fields: [
      { name: 'id, title', type: 'string', desc: 'Уникальный ID и название объекта (обязательно).' },
      { name: 'lat, lon, elevationM', type: 'number', desc: 'Координаты WGS-84 (-90..90, -180..180) и высота в метрах.' },
      { name: 'category', type: 'enum', desc: 'industrial | bunker | civilian | infrastructure | underground | military | energy | medical | transport | hydro | mining | ruins' },
      { name: 'subtype', type: 'string?', desc: 'Необязательный подтип сооружения (например: "Бомбоубежище ЗС ГО", "Градирня").' },
      { name: 'icon', type: 'enum?', desc: 'Иконка объекта (72 варианта): factory, bunker, building, tower, tunnel, warehouse, power, boiler, water, fuel, radar, radiation, hazard, skull, observation, gate, crane, hospital, airfield, port, shaft, workshop, camera, pin, military, silo, lab, biohazard, antenna, train, bridge, dam, monument, school, hotel, ruins, shelter, fire, compass, stash, substation, cooling, cistern, elevator, hangar, checkpoint, barracks, range, searchlight, munition, metro, vent, drainage, catacomb, vault, depot, bridge_rail, pier, lighthouse, quarry, mine_cart, oil_rig, pipeline, morgue, sanatorium, church, castle, theater, stadium, mall, generator, evac.' },
      { name: 'status', type: 'enum', desc: 'verified | unverified | contested | hazardous' },
      { name: 'securityLevel', type: 'enum', desc: 'none | low | medium | high' },
      { name: 'entryDifficulty', type: 'enum', desc: 'easy | moderate | hard | technical' },
      { name: 'floorsOrDepth', type: 'string?', desc: 'Например: "-14 м / 2 яруса".' },
      { name: 'photoUrl / photoUri', type: 'string?', desc: 'URL обложки или встроенный Data URI (data:image/jpeg;base64,...).' },
      { name: 'photos / photoUrls', type: 'string[]?', desc: 'Массив нескольких фото объекта (URL или Data URI), совместим с photoUrl.' },
      { name: 'hazards', type: 'string[]', desc: 'Список опасностей и факторов риска.' },
      { name: 'customParams', type: '{key, value}[]', desc: 'Доп. замеры (газы, радиация, год и т. д.).' },
      { name: 'zone', type: '{lat, lon}[]', desc: 'Полигон периметра объекта (≥3 точек).' },
      { name: 'zoneColor', type: 'string?', desc: 'Необязательный HEX-цвет зоны (#RRGGBB), иначе по статусу.' },
      { name: 'zoneStyle', type: 'enum?', desc: 'Стиль контура/заливки: solid | dashed | dotted | outline.' },
      { name: 'zoneHatched', type: 'boolean?', desc: 'Диагональная штриховка зоны (true/false, отключаемая).' },
    ],
  },
  {
    section: 'Прочие таблицы payload',
    fields: [
      { name: 'baseArticles[]', type: 'BaseArticle[]', desc: 'Статьи базы знаний (section: safety | navigation | comms | architecture | field_note).' },
      { name: 'archiveItems[]', type: 'ArchiveItem[]', desc: 'Фото, заметки и документы (type: photo | video | audio | note | document).' },
      { name: 'meshMessages[]', type: 'MeshMessage[]', desc: 'Журнал пакетов Meshtastic (можно оставить []).' },
      { name: 'profile, settings', type: 'object?', desc: 'Необязательны для ручных файлов — подставятся значения по умолчанию.' },
    ],
  },
];

function mergeById<T extends { id: string; updatedAt?: number; timestamp?: number }>(
  existing: T[],
  incoming: T[],
  strategy: ConflictStrategy
): { merged: T[]; added: number; updated: number; skipped: number } {
  const map = new Map<string, T>();
  for (const item of existing) {
    map.set(item.id, item);
  }

  let added = 0;
  let updated = 0;
  let skipped = 0;

  for (const inc of incoming) {
    const prev = map.get(inc.id);
    if (!prev) {
      map.set(inc.id, inc);
      added++;
      continue;
    }

    if (strategy === 'skip_existing') {
      skipped++;
    } else if (strategy === 'overwrite') {
      map.set(inc.id, inc);
      updated++;
    } else {
      const prevTs = prev.updatedAt ?? prev.timestamp ?? 0;
      const incTs = inc.updatedAt ?? inc.timestamp ?? 0;
      if (incTs > prevTs) {
        map.set(inc.id, inc);
        updated++;
      } else {
        skipped++;
      }
    }
  }

  return {
    merged: Array.from(map.values()),
    added,
    updated,
    skipped,
  };
}

export interface MergeSummary {
  payload: UrbexDbPayload;
  stats: {
    added: number;
    updated: number;
    skipped: number;
  };
}

export function mergeUrbexDbPayloads(
  current: UrbexDbPayload,
  incoming: UrbexDbPayload,
  strategy: ConflictStrategy
): MergeSummary {
  const objMerge = mergeById<UrbexObject>(current.objects, incoming.objects, strategy);
  const meshMerge = mergeById<MeshMessage>(current.meshMessages, incoming.meshMessages, strategy);
  const baseMerge = mergeById<BaseArticle>(current.baseArticles, incoming.baseArticles, strategy);
  const archMerge = mergeById<ArchiveItem>(current.archiveItems, incoming.archiveItems, strategy);

  const profile =
    strategy === 'overwrite' ||
    (strategy === 'keep_newer' && incoming.profile.updatedAt > current.profile.updatedAt)
      ? incoming.profile
      : current.profile;

  const pickedSettings =
    strategy === 'overwrite' ||
    (strategy === 'keep_newer' && incoming.settings.updatedAt > current.settings.updatedAt)
      ? incoming.settings
      : current.settings;

  const settings = {
    ...pickedSettings,
    // Always retain local device cloud sync secrets & active provider connection
    cloudSync: current.settings.cloudSync ?? pickedSettings.cloudSync,
  };

  return {
    payload: {
      objects: objMerge.merged,
      meshMessages: meshMerge.merged.sort((a, b) => a.timestamp - b.timestamp),
      baseArticles: baseMerge.merged,
      archiveItems: archMerge.merged,
      profile,
      settings,
    },
    stats: {
      added: objMerge.added + meshMerge.added + baseMerge.added + archMerge.added,
      updated: objMerge.updated + meshMerge.updated + baseMerge.updated + archMerge.updated,
      skipped: objMerge.skipped + meshMerge.skipped + baseMerge.skipped + archMerge.skipped,
    },
  };
}
