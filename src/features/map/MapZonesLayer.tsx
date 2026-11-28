import { memo, useEffect, useRef } from 'react';
import { Pentagon } from 'lucide-react';
import {
  haversineDistanceM,
  latLonToPixel,
  shouldShowMapObjectLabelsByZoomIdx,
  translateZone,
  type ViewportProjection,
} from '../../core/geo';
import {
  STATUS_META,
  getEffectiveObjectIcon,
  getEffectiveZoneColor,
  getEffectiveZonePattern,
  isOutlineOnlyZoneStyle,
  normalizeZoneColor,
  normalizeZonePattern,
  normalizeZoneStyle,
} from '../../core/labels';
import type {
  GeoPoint,
  ObjectCategory,
  ObjectIconId,
  ReliabilityStatus,
  UrbexObject,
  ZonePattern,
  ZoneStyle,
} from '../../core/types';
import { ObjectGlyph } from '../../components/ObjectGlyph';
import {
  computeCanvasBufferMetrics,
  resolveVisibleSavedZones,
} from './zoneRenderUtils';

const PATTERN_TILE_CACHE = new Map<string, HTMLCanvasElement>();

export function syncCanvas2dContext(
  canvas: HTMLCanvasElement,
  logicalWidth: number,
  logicalHeight: number,
  clearExisting = true
): CanvasRenderingContext2D | null {
  const dpr = Math.min(typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1, 2);
  const { targetWidth, targetHeight, needsResize } = computeCanvasBufferMetrics(
    canvas.width,
    canvas.height,
    logicalWidth,
    logicalHeight,
    dpr
  );
  if (needsResize) {
    canvas.width = targetWidth;
    canvas.height = targetHeight;
  }
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  if (clearExisting && !needsResize) {
    ctx.clearRect(0, 0, logicalWidth, logicalHeight);
  }
  return ctx;
}

export function createZonePatternFill(
  ctx: CanvasRenderingContext2D,
  colorHex: string,
  pattern: ZonePattern
): CanvasPattern | null {
  if (pattern === 'none' || typeof document === 'undefined') return null;
  const cacheKey = `${pattern}:${colorHex}`;
  let tile = PATTERN_TILE_CACHE.get(cacheKey);
  if (!tile) {
    tile = document.createElement('canvas');
    const size =
      pattern === 'hazard_stripes'
        ? 16
        : pattern === 'dots' || pattern === 'grid'
          ? 12
          : 10;
    tile.width = size;
    tile.height = size;
    const tctx = tile.getContext('2d');
    if (!tctx) return null;
    tctx.strokeStyle = colorHex;
    tctx.fillStyle = colorHex;

    if (pattern === 'hatch') {
      tctx.lineWidth = 1.6;
      tctx.beginPath();
      tctx.moveTo(-2, 2);
      tctx.lineTo(2, -2);
      tctx.moveTo(-2, size + 2);
      tctx.lineTo(size + 2, -2);
      tctx.moveTo(size - 2, size + 2);
      tctx.lineTo(size + 2, size - 2);
      tctx.stroke();
    } else if (pattern === 'cross_hatch') {
      tctx.lineWidth = 1.4;
      tctx.beginPath();
      tctx.moveTo(-2, 2);
      tctx.lineTo(2, -2);
      tctx.moveTo(-2, size + 2);
      tctx.lineTo(size + 2, -2);
      tctx.moveTo(size - 2, size + 2);
      tctx.lineTo(size + 2, size - 2);
      tctx.moveTo(-2, size - 2);
      tctx.lineTo(2, size + 2);
      tctx.moveTo(-2, -2);
      tctx.lineTo(size + 2, size + 2);
      tctx.moveTo(size - 2, -2);
      tctx.lineTo(size + 2, 2);
      tctx.stroke();
    } else if (pattern === 'grid') {
      tctx.lineWidth = 1.25;
      tctx.beginPath();
      tctx.moveTo(0.5, 0);
      tctx.lineTo(0.5, size);
      tctx.moveTo(0, 0.5);
      tctx.lineTo(size, 0.5);
      tctx.stroke();
    } else if (pattern === 'dots') {
      tctx.beginPath();
      tctx.arc(3, 3, 1.6, 0, Math.PI * 2);
      tctx.arc(9, 9, 1.6, 0, Math.PI * 2);
      tctx.fill();
    } else if (pattern === 'vertical') {
      tctx.lineWidth = 1.6;
      tctx.beginPath();
      tctx.moveTo(2.5, 0);
      tctx.lineTo(2.5, size);
      tctx.moveTo(7.5, 0);
      tctx.lineTo(7.5, size);
      tctx.stroke();
    } else if (pattern === 'horizontal') {
      tctx.lineWidth = 1.6;
      tctx.beginPath();
      tctx.moveTo(0, 2.5);
      tctx.lineTo(size, 2.5);
      tctx.moveTo(0, 7.5);
      tctx.lineTo(size, 7.5);
      tctx.stroke();
    } else if (pattern === 'hazard_stripes') {
      tctx.lineWidth = 4.6;
      tctx.beginPath();
      tctx.moveTo(-4, 4);
      tctx.lineTo(4, -4);
      tctx.moveTo(-4, size + 4);
      tctx.lineTo(size + 4, -4);
      tctx.moveTo(size - 4, size + 4);
      tctx.lineTo(size + 4, size - 4);
      tctx.stroke();
    }

    if (PATTERN_TILE_CACHE.size >= 96) {
      const oldest = PATTERN_TILE_CACHE.keys().next().value;
      if (oldest) PATTERN_TILE_CACHE.delete(oldest);
    }
    PATTERN_TILE_CACHE.set(cacheKey, tile);
  }
  return ctx.createPattern(tile, 'repeat');
}

export function drawStyledZonePolygon(
  ctx: CanvasRenderingContext2D,
  points: Array<{ x: number; y: number }>,
  opts: {
    colorHex: string;
    style: ZoneStyle;
    pattern: ZonePattern;
    fillAlpha: number;
    strokeAlpha: number;
    lineWidth: number;
    closed: boolean;
  }
) {
  if (points.length === 0) return;
  ctx.save();
  ctx.beginPath();
  points.forEach((px, idx) => {
    if (idx === 0) ctx.moveTo(px.x, px.y);
    else ctx.lineTo(px.x, px.y);
  });
  if (opts.closed && points.length >= 3) {
    ctx.closePath();
    if (!isOutlineOnlyZoneStyle(opts.style)) {
      const tintAlpha =
        opts.style === 'bold' ? Math.min(0.45, opts.fillAlpha * 1.35) : opts.fillAlpha;
      ctx.globalAlpha = tintAlpha;
      ctx.fillStyle = opts.colorHex;
      ctx.fill();
    }
    if (opts.pattern !== 'none') {
      const patternFill = createZonePatternFill(ctx, opts.colorHex, opts.pattern);
      if (patternFill) {
        ctx.globalAlpha = Math.min(
          0.72,
          opts.fillAlpha + (opts.pattern === 'hazard_stripes' ? 0.3 : 0.36)
        );
        ctx.fillStyle = patternFill;
        ctx.fill();
      }
    }
  }

  ctx.lineJoin = 'round';
  if (opts.style === 'glow') {
    ctx.setLineDash([]);
    ctx.globalAlpha = opts.strokeAlpha * 0.34;
    ctx.strokeStyle = opts.colorHex;
    ctx.lineWidth = opts.lineWidth * 3.4;
    ctx.stroke();

    ctx.globalAlpha = opts.strokeAlpha;
    ctx.lineWidth = opts.lineWidth * 1.25;
    ctx.stroke();
  } else if (opts.style === 'double_line') {
    ctx.setLineDash([]);
    ctx.globalAlpha = opts.strokeAlpha;
    ctx.strokeStyle = opts.colorHex;
    ctx.lineWidth = opts.lineWidth * 2.2;
    ctx.stroke();

    ctx.globalAlpha = 0.92;
    ctx.strokeStyle = '#0C0F0E';
    ctx.lineWidth = Math.max(1.2, opts.lineWidth * 0.85);
    ctx.stroke();
  } else {
    ctx.globalAlpha = opts.strokeAlpha;
    ctx.strokeStyle = opts.colorHex;
    ctx.lineWidth = opts.style === 'bold' ? opts.lineWidth * 1.85 : opts.lineWidth;
    if (opts.style === 'dashed' || opts.style === 'dashed_outline') {
      ctx.setLineDash([9, 5]);
    } else if (opts.style === 'dotted') {
      ctx.setLineDash([2.5, 4.5]);
      ctx.lineCap = 'round';
    } else if (opts.style === 'dash_dot') {
      ctx.setLineDash([12, 4, 2.5, 4]);
      ctx.lineCap = 'round';
    } else if (opts.style === 'long_dash') {
      ctx.setLineDash([18, 7]);
    } else {
      ctx.setLineDash([]);
    }
    ctx.stroke();
  }
  ctx.restore();
}

export interface MapZonesLayerProps {
  objects: UrbexObject[];
  selectedId: string | null;
  hiddenObjectId: string | null;
  showObjectZones: boolean;
  projection: ViewportProjection;
}

export const MapZonesLayer = memo(function MapZonesLayer({
  objects,
  selectedId,
  hiddenObjectId,
  showObjectZones,
  projection,
}: MapZonesLayerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = syncCanvas2dContext(canvas, projection.width, projection.height, true);
    if (!ctx) return;

    const visibleZones = resolveVisibleSavedZones(objects, {
      hiddenObjectId,
      showObjectZones,
    });
    for (const obj of visibleZones) {
      if (!obj.zone) continue;
      const zoneHex = getEffectiveZoneColor(obj);
      const zoneStyle = normalizeZoneStyle(obj.zoneStyle);
      const zonePattern = getEffectiveZonePattern(obj);
      const isSelected = obj.id === selectedId;
      const pxPoints = obj.zone.map((pt) => latLonToPixel(pt.lat, pt.lon, projection));
      drawStyledZonePolygon(ctx, pxPoints, {
        colorHex: zoneHex,
        style: zoneStyle,
        pattern: zonePattern,
        fillAlpha: isSelected ? 0.24 : 0.14,
        strokeAlpha: isSelected ? 0.96 : 0.78,
        lineWidth: isSelected ? 2.5 : 1.75,
        closed: true,
      });
    }
  }, [objects, selectedId, hiddenObjectId, showObjectZones, projection]);

  return (
    <canvas
      ref={canvasRef}
      data-testid="map-zones-layer"
      aria-hidden="true"
      className="absolute inset-0 w-full h-full pointer-events-none"
    />
  );
});

export interface MapDraftZonePointMovePreview {
  lat: number;
  lon: number;
  status: ReliabilityStatus;
  zone?: GeoPoint[];
  zoneColor?: string;
  zoneStyle?: ZoneStyle;
  zonePattern?: ZonePattern;
  zoneHatched?: boolean;
}

export interface MapDraftZoneLayerProps {
  isEditingZone: boolean;
  draftZone: GeoPoint[];
  draftZoneColorHex: string;
  draftZoneStyle: ZoneStyle;
  draftZonePattern: ZonePattern;
  isMovingPoint: boolean;
  pointMoveTarget: MapDraftZonePointMovePreview | null;
  draftPoint: GeoPoint | null;
  shiftZoneWithPoint: boolean;
  projection: ViewportProjection;
}

export const MapDraftZoneLayer = memo(function MapDraftZoneLayer({
  isEditingZone,
  draftZone,
  draftZoneColorHex,
  draftZoneStyle,
  draftZonePattern,
  isMovingPoint,
  pointMoveTarget,
  draftPoint,
  shiftZoneWithPoint,
  projection,
}: MapDraftZoneLayerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = syncCanvas2dContext(canvas, projection.width, projection.height, true);
    if (!ctx) return;

    // Active Point Move Preview (Zone Shift Preview + Tether Line from Original Anchor)
    if (isMovingPoint && pointMoveTarget && draftPoint) {
      const targetColor =
        normalizeZoneColor(pointMoveTarget.zoneColor) ??
        STATUS_META[pointMoveTarget.status].colorHex;
      if (pointMoveTarget.zone && pointMoveTarget.zone.length >= 3) {
        const dLat = shiftZoneWithPoint ? draftPoint.lat - pointMoveTarget.lat : 0;
        const dLon = shiftZoneWithPoint ? draftPoint.lon - pointMoveTarget.lon : 0;
        const previewZone =
          translateZone(pointMoveTarget.zone, dLat, dLon) ?? pointMoveTarget.zone;
        const pxPoints = previewZone.map((pt) => latLonToPixel(pt.lat, pt.lon, projection));
        drawStyledZonePolygon(ctx, pxPoints, {
          colorHex: targetColor,
          style: normalizeZoneStyle(pointMoveTarget.zoneStyle),
          pattern: normalizeZonePattern(
            pointMoveTarget.zonePattern,
            Boolean(pointMoveTarget.zoneHatched)
          ),
          fillAlpha: 0.22,
          strokeAlpha: 0.92,
          lineWidth: 2.25,
          closed: true,
        });
      }

      const origPx = latLonToPixel(pointMoveTarget.lat, pointMoveTarget.lon, projection);
      const nextPx = latLonToPixel(draftPoint.lat, draftPoint.lon, projection);
      const shiftDistM = haversineDistanceM(
        pointMoveTarget.lat,
        pointMoveTarget.lon,
        draftPoint.lat,
        draftPoint.lon
      );
      if (shiftDistM > 0.5) {
        ctx.save();
        ctx.setLineDash([5, 4]);
        ctx.strokeStyle = '#DF9E43';
        ctx.lineWidth = 1.75;
        ctx.beginPath();
        ctx.moveTo(origPx.x, origPx.y);
        ctx.lineTo(nextPx.x, nextPx.y);
        ctx.stroke();

        ctx.setLineDash([]);
        ctx.strokeStyle = 'rgba(223, 158, 67, 0.7)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(origPx.x, origPx.y, 6, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }

      ctx.save();
      ctx.fillStyle = 'rgba(91, 157, 114, 0.24)';
      ctx.strokeStyle = 'rgba(109, 179, 134, 0.88)';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(nextPx.x, nextPx.y, 26, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      ctx.restore();
    }

    // Active Draft Zone Overlay (Zone Editing Mode)
    if (isEditingZone && draftZone.length > 0) {
      const pxPoints = draftZone.map((pt) => latLonToPixel(pt.lat, pt.lon, projection));
      drawStyledZonePolygon(ctx, pxPoints, {
        colorHex: draftZoneColorHex,
        style: draftZoneStyle,
        pattern: draftZonePattern,
        fillAlpha: 0.24,
        strokeAlpha: 0.95,
        lineWidth: 2.5,
        closed: draftZone.length >= 3,
      });

      ctx.save();
      pxPoints.forEach((px, idx) => {
        ctx.beginPath();
        ctx.arc(px.x, px.y, idx === 0 ? 5.5 : 4, 0, Math.PI * 2);
        ctx.fillStyle = idx === 0 ? '#DF9E43' : draftZoneColorHex;
        ctx.fill();
        ctx.lineWidth = 1.5;
        ctx.strokeStyle = '#0C0F0E';
        ctx.stroke();
      });
      ctx.restore();
    }
  }, [
    isEditingZone,
    draftZone,
    draftZoneColorHex,
    draftZoneStyle,
    draftZonePattern,
    isMovingPoint,
    pointMoveTarget,
    draftPoint,
    shiftZoneWithPoint,
    projection,
  ]);

  return (
    <canvas
      ref={canvasRef}
      data-testid="map-draft-zone-layer"
      aria-hidden="true"
      className="absolute inset-0 w-full h-full pointer-events-none"
    />
  );
});

export interface MapGpsLayerProps {
  gpsLat: number;
  gpsLon: number;
  gpsAccuracyM: number | null;
  signalColorHex: string;
  signalFillRgba: string;
  signalStrokeRgba: string;
  projection: ViewportProjection;
}

export const MapGpsLayer = memo(function MapGpsLayer({
  gpsLat,
  gpsLon,
  gpsAccuracyM,
  signalColorHex,
  signalFillRgba,
  signalStrokeRgba,
  projection,
}: MapGpsLayerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = syncCanvas2dContext(canvas, projection.width, projection.height, true);
    if (!ctx) return;

    const gpsPx = latLonToPixel(gpsLat, gpsLon, projection);
    const effectiveAccuracyM =
      gpsAccuracyM !== null && gpsAccuracyM > 0 ? gpsAccuracyM : 65;
    const rawAccPx = effectiveAccuracyM / projection.metersPerPixel;
    const maxCirclePx = Math.max(
      48,
      Math.min(projection.width, projection.height) * 0.42
    );
    const accPx = Math.max(18, Math.min(maxCirclePx, rawAccPx));

    ctx.fillStyle = signalFillRgba;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, accPx, 0, Math.PI * 2);
    ctx.fill();

    ctx.save();
    ctx.setLineDash([5, 4]);
    ctx.strokeStyle = signalStrokeRgba;
    ctx.lineWidth = 1.75;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, accPx, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();

    ctx.strokeStyle = signalColorHex;
    ctx.lineWidth = 2.25;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 7.5, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = '#E6EBE8';
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 2.8, 0, Math.PI * 2);
    ctx.fill();

    if (
      gpsPx.x > -40 &&
      gpsPx.x < projection.width + 40 &&
      gpsPx.y > -40 &&
      gpsPx.y < projection.height + 40
    ) {
      const accText =
        gpsAccuracyM !== null ? `±${Math.round(gpsAccuracyM)} м` : 'нет фикса';
      ctx.save();
      ctx.font = '600 10px ui-monospace, SFMono-Regular, Menlo, monospace';
      const textWidth = ctx.measureText(accText).width;
      const pillW = textWidth + 10;
      const pillH = 16;
      const pillX = gpsPx.x - pillW / 2;
      const pillY = gpsPx.y - accPx - pillH - 4;
      ctx.fillStyle = 'rgba(12, 15, 14, 0.85)';
      ctx.strokeStyle = signalStrokeRgba;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(pillX, pillY, pillW, pillH, 4);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = signalColorHex;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(accText, gpsPx.x, pillY + pillH / 2 + 0.5);
      ctx.restore();
    }
  }, [
    gpsLat,
    gpsLon,
    gpsAccuracyM,
    signalColorHex,
    signalFillRgba,
    signalStrokeRgba,
    projection,
  ]);

  return (
    <canvas
      ref={canvasRef}
      data-testid="map-gps-layer"
      aria-hidden="true"
      className="absolute inset-0 w-full h-full pointer-events-none"
    />
  );
});

export interface MapObjectMarkersLayerProps {
  objects: UrbexObject[];
  selectedId: string | null;
  focusPulseId: string | null;
  showObjectLabelsSetting: boolean | undefined;
  zoomIdx: number;
  projection: ViewportProjection;
  hiddenMovingObjectId: string | null;
  interactionDisabled: boolean;
  onSelectObject: (objectId: string) => void;
}

export const MapObjectMarkersLayer = memo(function MapObjectMarkersLayer({
  objects,
  selectedId,
  focusPulseId,
  showObjectLabelsSetting,
  zoomIdx,
  projection,
  hiddenMovingObjectId,
  interactionDisabled,
  onSelectObject,
}: MapObjectMarkersLayerProps) {
  const showLabels = shouldShowMapObjectLabelsByZoomIdx(
    showObjectLabelsSetting,
    zoomIdx
  );

  return (
    <>
      {objects.map((obj) => {
        if (hiddenMovingObjectId && hiddenMovingObjectId === obj.id) {
          return null;
        }
        const pos = latLonToPixel(obj.lat, obj.lon, projection);
        if (
          pos.x < -24 ||
          pos.y < -24 ||
          pos.x > projection.width + 24 ||
          pos.y > projection.height + 24
        ) {
          return null;
        }
        const meta = STATUS_META[obj.status];
        const iconId = getEffectiveObjectIcon(obj as { category: ObjectCategory; icon?: ObjectIconId });
        const isSelected = obj.id === selectedId;
        const isFocusPulsing = obj.id === focusPulseId;
        const hasZone = Boolean(obj.zone && obj.zone.length >= 3);
        return (
          <button
            key={obj.id}
            type="button"
            onClick={() => {
              if (interactionDisabled) return;
              onSelectObject(obj.id);
            }}
            aria-label={`Объект: ${obj.title} (${meta.label})`}
            style={{
              transform: `translate3d(${Math.round(pos.x)}px, ${Math.round(pos.y)}px, 0) translate(-50%, -50%)`,
            }}
            className={`absolute top-0 left-0 min-h-[44px] min-w-[44px] flex items-center justify-center group focus-visible:outline-none z-10 ${
              interactionDisabled ? 'pointer-events-none opacity-75' : ''
            }`}
          >
            {isFocusPulsing && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-0 -m-2 rounded-full border-2 border-[#6DB386] bg-[#5B9D72]/20 animate-ping"
              />
            )}
            {showLabels ? (
              <span
                className={`px-2 py-1 rounded-md border text-xs font-medium whitespace-nowrap transition-all duration-150 shadow-sm flex items-center gap-1.5 ${
                  isSelected
                    ? 'bg-[#1A201E] border-[#E6EBE8] text-[#E6EBE8] scale-105 ring-2 ring-[#5B9D72]/40'
                    : 'bg-[#131816]/90 backdrop-blur-sm border-[#252E2A] text-[#E6EBE8] group-hover:border-[#6DB386] group-hover:scale-[1.03]'
                }`}
              >
                <ObjectGlyph
                  icon={iconId}
                  color={meta.colorHex}
                  className="w-3.5 h-3.5 shrink-0"
                />
                <span className="max-w-[96px] sm:max-w-[140px] truncate inline-block">
                  {obj.title}
                </span>
                {hasZone && (
                  <Pentagon className="inline-block w-3 h-3 text-[#6DB386] shrink-0" />
                )}
              </span>
            ) : (
              <span
                className={`w-6 h-6 rounded-full border-2 flex items-center justify-center transition-transform shadow-sm ${
                  isSelected
                    ? 'scale-125 border-[#E6EBE8]'
                    : 'border-[#0C0F0E] group-hover:scale-110'
                }`}
                style={{ backgroundColor: meta.colorHex }}
              >
                <ObjectGlyph
                  icon={iconId}
                  className="w-3.5 h-3.5 text-[#0C0F0E]"
                  strokeWidth={2.2}
                />
              </span>
            )}
          </button>
        );
      })}
    </>
  );
});
