import { STATUS_META, normalizeZoneColor } from '../../core/labels';
import type { ReliabilityStatus, UrbexObject } from '../../core/types';

export interface VisibleZoneFilterOptions {
  hiddenObjectId: string | null;
  showObjectZones: boolean;
}

export function resolveVisibleSavedZones(
  objects: UrbexObject[],
  options: VisibleZoneFilterOptions
): UrbexObject[] {
  if (!options.showObjectZones) return [];
  return objects.filter((obj) => {
    if (options.hiddenObjectId && obj.id === options.hiddenObjectId) return false;
    return Boolean(obj.zone && obj.zone.length >= 3);
  });
}

export function resolveDraftZoneColorHex(
  draftZoneColor: string | undefined,
  fallbackStatus: ReliabilityStatus
): string {
  return normalizeZoneColor(draftZoneColor) ?? STATUS_META[fallbackStatus].colorHex;
}

export interface CanvasBufferMetrics {
  targetWidth: number;
  targetHeight: number;
  needsResize: boolean;
}

export function computeCanvasBufferMetrics(
  currentWidth: number,
  currentHeight: number,
  logicalWidth: number,
  logicalHeight: number,
  dpr: number
): CanvasBufferMetrics {
  const safeDpr = Number.isFinite(dpr) && dpr > 0 ? Math.min(dpr, 2) : 1;
  const targetWidth = Math.max(1, Math.round(logicalWidth * safeDpr));
  const targetHeight = Math.max(1, Math.round(logicalHeight * safeDpr));
  return {
    targetWidth,
    targetHeight,
    needsResize: currentWidth !== targetWidth || currentHeight !== targetHeight,
  };
}
