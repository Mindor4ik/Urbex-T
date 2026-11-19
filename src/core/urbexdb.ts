import { createSeedPayload } from '../data/seed';
import { normalizeZoneColor, normalizeZoneStyle } from './labels';
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
  const checksum = computePayloadChecksum(payload);
  return {
    manifest: {
      format: URBEXDB_FORMAT,
      version: URBEXDB_VERSION,
      exportedAt,
      deviceNodeId,
      checksum,
      counts: {
        objects: payload.objects.length,
        meshMessages: payload.meshMessages.length,
        baseArticles: payload.baseArticles.length,
        archiveItems: payload.archiveItems.length,
      },
    },
    payload,
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
        category: o.category ?? 'industrial',
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

export function parseAndVerifyUrbexDb(raw: string): ParseUrbexDbResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
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
          editorEnabled: rawPayload.settings.editorEnabled ?? false,
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

export function buildUrbexDbTemplateJson(): string {
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
  return JSON.stringify(template, null, 2);
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
      { name: 'category', type: 'enum', desc: 'industrial | bunker | civilian | infrastructure | underground' },
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

  const settings =
    strategy === 'overwrite' ||
    (strategy === 'keep_newer' && incoming.settings.updatedAt > current.settings.updatedAt)
      ? incoming.settings
      : current.settings;

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
