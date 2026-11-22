import type { ModuleId, SettingsCategory } from './types';

export type CommsSubTab = 'packets' | 'radio';

const VALID_MODULES: ReadonlySet<ModuleId> = new Set([
  'map',
  'objects',
  'mesh',
  'base',
  'archive',
  'profile',
  'settings',
  'menu',
  'apps',
]);

const VALID_SETTINGS_CATEGORIES: ReadonlySet<SettingsCategory> = new Set([
  'databases',
  'map_gps',
  'radio_mesh',
  'pda_ui',
]);

export function isValidModuleId(raw: string | null | undefined): raw is ModuleId {
  return typeof raw === 'string' && VALID_MODULES.has(raw as ModuleId);
}

export function isValidSettingsCategory(
  raw: string | null | undefined
): raw is SettingsCategory {
  return (
    typeof raw === 'string' &&
    VALID_SETTINGS_CATEGORIES.has(raw as SettingsCategory)
  );
}

export function pushTabHistory<T>(
  history: readonly T[],
  next: T,
  maxDepth = 24
): T[] {
  if (history.length > 0 && history[history.length - 1] === next) {
    return [...history];
  }
  const appended = [...history, next];
  if (appended.length <= maxDepth) return appended;
  return appended.slice(appended.length - maxDepth);
}

export function popTabHistory<T>(history: readonly T[]): {
  previous: T | null;
  remaining: T[];
} {
  if (history.length <= 1) {
    return { previous: null, remaining: [...history] };
  }
  const remaining = history.slice(0, history.length - 1);
  return {
    previous: remaining[remaining.length - 1] ?? null,
    remaining,
  };
}

export function isCommsModuleVisible(
  meshEnabled: boolean,
  radioChannelsTabEnabled: boolean
): boolean {
  return meshEnabled || radioChannelsTabEnabled;
}

export function resolveCommsSubTab(
  requested: CommsSubTab,
  meshEnabled: boolean,
  radioChannelsTabEnabled: boolean
): CommsSubTab {
  if (requested === 'packets' && !meshEnabled && radioChannelsTabEnabled) {
    return 'radio';
  }
  if (requested === 'radio' && !radioChannelsTabEnabled && meshEnabled) {
    return 'packets';
  }
  return requested;
}

export function sanitizeActiveModule(
  mod: ModuleId,
  opts: {
    pdaModeEnabled: boolean;
    meshEnabled: boolean;
    radioChannelsTabEnabled: boolean;
  }
): ModuleId {
  if (mod === 'apps' && !opts.pdaModeEnabled) return 'map';
  if (
    mod === 'mesh' &&
    !isCommsModuleVisible(opts.meshEnabled, opts.radioChannelsTabEnabled)
  ) {
    return 'map';
  }
  return mod;
}
