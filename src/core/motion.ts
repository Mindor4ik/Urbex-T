export const EASE_OUT_TACTICAL = [0.22, 1, 0.36, 1] as const;

export const TILE_ENTER_CASCADE_LIMIT = 10;
export const TILE_ENTER_STEP_SEC = 0.026;
export const TILE_ENTER_DURATION_SEC = 0.24;

export function computeTileDelaySec(
  index: number,
  stepSec: number = TILE_ENTER_STEP_SEC,
  cascadeLimit: number = TILE_ENTER_CASCADE_LIMIT
): number {
  if (!Number.isFinite(index) || index <= 0) return 0;
  const safeIndex = Math.floor(index);
  if (safeIndex >= cascadeLimit) return 0;
  return Number((safeIndex * stepSec).toFixed(4));
}

export function tileEnterMotion(index: number = 0, offsetY: number = 12) {
  const safeIndex = Number.isFinite(index) ? Math.max(0, Math.floor(index)) : 0;
  const isCascade = safeIndex < TILE_ENTER_CASCADE_LIMIT;
  return {
    initial: {
      opacity: 0,
      y: isCascade ? offsetY : Math.round(offsetY * 0.5),
      scale: 0.978,
    },
    animate: {
      opacity: 1,
      y: 0,
      scale: 1,
    },
    transition: {
      duration: isCascade ? TILE_ENTER_DURATION_SEC : 0.16,
      delay: computeTileDelaySec(safeIndex),
      ease: EASE_OUT_TACTICAL,
    },
  };
}
