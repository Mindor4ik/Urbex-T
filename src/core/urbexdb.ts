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

  const majorVersion = String(candidate.manifest.version ?? '').split('.')[0];
  if (majorVersion !== '1') {
    return {
      ok: false,
      code: 'UNSUPPORTED_VERSION',
      error: `Неподдерживаемая версия формата: ${String(candidate.manifest.version)}.`,
    };
  }

  const payload = candidate.payload;
  if (
    !Array.isArray(payload.objects) ||
    !Array.isArray(payload.meshMessages) ||
    !Array.isArray(payload.baseArticles) ||
    !Array.isArray(payload.archiveItems) ||
    !payload.profile ||
    !payload.settings
  ) {
    return {
      ok: false,
      code: 'INVALID_SCHEMA',
      error: 'В контейнере .urbexdb отсутствуют обязательные таблицы данных.',
    };
  }

  const expectedChecksum = computePayloadChecksum(payload);
  if (candidate.manifest.checksum !== expectedChecksum) {
    return {
      ok: false,
      code: 'CHECKSUM_MISMATCH',
      error: `Нарушена контрольная сумма целостности (ожидалось ${expectedChecksum}, получено ${candidate.manifest.checksum}).`,
    };
  }

  const warnings: string[] = [];
  if (candidate.manifest.counts?.objects !== payload.objects.length) {
    warnings.push('Счётчик объектов в манифесте скорректирован по факту.');
  }

  return {
    ok: true,
    bundle: candidate as UrbexDbBundle,
    warnings,
  };
}

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
