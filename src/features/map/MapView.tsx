import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  Crosshair,
  Download,
  Globe,
  Layers,
  LocateFixed,
  Map as MapIcon,
  Minus,
  Navigation,
  Pencil,
  Pentagon,
  Plus,
  Radar,
  Undo2,
  X,
} from 'lucide-react';
import {
  bearingDeg,
  buildZoneCircle,
  computePolygonAreaM2,
  computePolygonPerimeterM,
  formatArea,
  formatCoordinates,
  formatDistance,
  getGpsSignalQuality,
  haversineDistanceM,
  isPointInPolygon,
  latLonToPixel,
  latToTileY,
  lonToTileX,
  pixelToLatLon,
  simplifyPolygonPoints,
  tileXToLon,
  tileYToLat,
} from '../../core/geo';
import {
  CATEGORY_LABELS,
  getEffectiveZoneColor,
  getObjectPhotos,
  normalizeZoneColor,
  normalizeZoneStyle,
  POWER_PROFILE_META,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
  ZONE_COLOR_PRESETS,
  ZONE_STYLE_LABELS,
} from '../../core/labels';
import type {
  AppSettings,
  GeoPoint,
  GpsFix,
  MapLayerMode,
  OfflineMapSector,
  UrbexObject,
  ZoneStyle,
} from '../../core/types';
import { ObjectImage } from '../../components/ObjectImage';

export interface MapCameraState {
  lat: number;
  lon: number;
  zoomIdx: number;
}

export interface MapFocusTarget {
  objectId: string;
  lat: number;
  lon: number;
  token: number;
}

export interface ZoneUpdatePayload {
  zone: GeoPoint[];
  zoneColor?: string;
  zoneStyle: ZoneStyle;
  zoneHatched: boolean;
}

interface MapViewProps {
  objects: UrbexObject[];
  gpsFix: GpsFix;
  isPollingGps: boolean;
  gpsError: string | null;
  settings: AppSettings;
  editorEnabled?: boolean;
  savedCamera?: MapCameraState | null;
  onCameraChange?: (cam: MapCameraState) => void;
  focusTarget?: MapFocusTarget | null;
  zoneEditingObject?: UrbexObject | null;
  onEditZoneForObject?: (obj: UrbexObject) => void;
  onSaveObjectZone?: (objectId: string, updates: ZoneUpdatePayload) => Promise<void>;
  onCancelZoneEdit?: () => void;
  onRequestSingleFix: () => void;
  onSetManualFix: (lat: number, lon: number) => void;
  onCreateAtCoords: (lat: number, lon: number) => void;
  onOpenObjectInDatabase: (objectId: string) => void;
  onAddOfflineSector: (sector: OfflineMapSector) => Promise<void>;
  onChangeMapLayer: (layer: MapLayerMode) => Promise<void>;
}

const ZOOM_METERS_PER_PX = [32, 18, 9.5, 4.8, 2.4, 1.2, 0.65];

type CachedTile = HTMLImageElement | 'loading' | 'failed';
const TILE_CACHE = new Map<string, CachedTile>();
const MAX_TILE_CACHE = 128;

function getSatelliteTileUrl(z: number, x: number, y: number): string {
  return `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${z}/${y}/${x}`;
}

function setBoundedTile(key: string, value: CachedTile) {
  if (TILE_CACHE.size >= MAX_TILE_CACHE && !TILE_CACHE.has(key)) {
    const firstKey = TILE_CACHE.keys().next().value;
    if (firstKey) TILE_CACHE.delete(firstKey);
  }
  TILE_CACHE.set(key, value);
}

function computeTileZoom(metersPerPixel: number, centerLat: number): number {
  const cosLat = Math.max(0.2, Math.cos((centerLat * Math.PI) / 180));
  const raw = Math.log2((156543.03392 * cosLat) / metersPerPixel);
  return Math.max(10, Math.min(17, Math.round(raw)));
}

function createHatchPattern(
  ctx: CanvasRenderingContext2D,
  colorHex: string
): CanvasPattern | null {
  if (typeof document === 'undefined') return null;
  const tile = document.createElement('canvas');
  tile.width = 10;
  tile.height = 10;
  const tctx = tile.getContext('2d');
  if (!tctx) return null;
  tctx.strokeStyle = colorHex;
  tctx.lineWidth = 1.6;
  tctx.beginPath();
  tctx.moveTo(-2, 10);
  tctx.lineTo(10, -2);
  tctx.moveTo(0, 12);
  tctx.lineTo(12, 0);
  tctx.stroke();
  return ctx.createPattern(tile, 'repeat');
}

function drawStyledZonePolygon(
  ctx: CanvasRenderingContext2D,
  points: Array<{ x: number; y: number }>,
  opts: {
    colorHex: string;
    style: ZoneStyle;
    hatched: boolean;
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
    if (opts.style !== 'outline') {
      ctx.globalAlpha = opts.fillAlpha;
      ctx.fillStyle = opts.colorHex;
      ctx.fill();
    }
    if (opts.hatched) {
      const pattern = createHatchPattern(ctx, opts.colorHex);
      if (pattern) {
        ctx.globalAlpha = Math.min(0.68, opts.fillAlpha + 0.36);
        ctx.fillStyle = pattern;
        ctx.fill();
      }
    }
  }
  ctx.globalAlpha = opts.strokeAlpha;
  ctx.strokeStyle = opts.colorHex;
  ctx.lineWidth = opts.lineWidth;
  if (opts.style === 'dashed') {
    ctx.setLineDash([9, 5]);
  } else if (opts.style === 'dotted') {
    ctx.setLineDash([2.5, 4.5]);
    ctx.lineCap = 'round';
  } else {
    ctx.setLineDash([]);
  }
  ctx.stroke();
  ctx.restore();
}

export default function MapView({
  objects,
  gpsFix,
  isPollingGps,
  gpsError,
  settings,
  editorEnabled = false,
  savedCamera,
  onCameraChange,
  focusTarget = null,
  zoneEditingObject = null,
  onEditZoneForObject,
  onSaveObjectZone,
  onCancelZoneEdit,
  onRequestSingleFix,
  onSetManualFix,
  onCreateAtCoords,
  onOpenObjectInDatabase,
  onAddOfflineSector,
  onChangeMapLayer,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const [viewport, setViewport] = useState({ width: 1120, height: 720 });
  const [center, setCenter] = useState({
    lat: focusTarget?.lat ?? savedCamera?.lat ?? gpsFix.lat,
    lon: focusTarget?.lon ?? savedCamera?.lon ?? gpsFix.lon,
  });
  const [zoomIdx, setZoomIdx] = useState(
    focusTarget ? Math.max(savedCamera?.zoomIdx ?? 3, 4) : (savedCamera?.zoomIdx ?? 3)
  ); // ~4.8 m/px (Z15 satellite)
  const [showContours, setShowContours] = useState(true);
  const [showSectors, setShowSectors] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(focusTarget?.objectId ?? null);
  const [tileTick, setTileTick] = useState(0);
  const [cursorCoords, setCursorCoords] = useState<{ lat: number; lon: number }>({
    lat: focusTarget?.lat ?? savedCamera?.lat ?? gpsFix.lat,
    lon: focusTarget?.lon ?? savedCamera?.lon ?? gpsFix.lon,
  });
  const [sectorCachedToast, setSectorCachedToast] = useState<string | null>(null);
  const lastAppliedFocusTokenRef = useRef<number | null>(focusTarget?.token ?? null);
  const suppressAutoCenterRef = useRef<boolean>(Boolean(focusTarget));

  // Interactive Zone Drawing State
  const [draftZone, setDraftZone] = useState<GeoPoint[]>([]);
  const [draftZoneColor, setDraftZoneColor] = useState<string>('');
  const [draftZoneStyle, setDraftZoneStyle] = useState<ZoneStyle>('solid');
  const [draftZoneHatched, setDraftZoneHatched] = useState<boolean>(false);
  const [zoneDrawTool, setZoneDrawTool] = useState<'points' | 'freehand'>('points');
  const [zoneSaving, setZoneSaving] = useState(false);
  const freehandRawRef = useRef<GeoPoint[]>([]);
  const isDrawingFreehandRef = useRef(false);

  const dragStateRef = useRef<{
    active: boolean;
    moved: boolean;
    startX: number;
    startY: number;
    startCenterLat: number;
    startCenterLon: number;
  }>({
    active: false,
    moved: false,
    startX: 0,
    startY: 0,
    startCenterLat: gpsFix.lat,
    startCenterLon: gpsFix.lon,
  });

  // Sync persisted camera back to parent
  useEffect(() => {
    onCameraChange?.({ lat: center.lat, lon: center.lon, zoomIdx });
  }, [center.lat, center.lon, zoomIdx, onCameraChange]);

  // Apply external focus-on-object navigation request (e.g. clicking coordinates in ObjectsView)
  useEffect(() => {
    if (!focusTarget || focusTarget.token === lastAppliedFocusTokenRef.current) return;
    lastAppliedFocusTokenRef.current = focusTarget.token;
    suppressAutoCenterRef.current = true;
    setCenter({ lat: focusTarget.lat, lon: focusTarget.lon });
    setCursorCoords({ lat: focusTarget.lat, lon: focusTarget.lon });
    setZoomIdx((prev) => Math.max(prev, 4));
    setSelectedId(focusTarget.objectId);
  }, [focusTarget]);

  // Initialize draft zone & center camera when entering Zone Editing Mode
  useEffect(() => {
    if (!zoneEditingObject || !editorEnabled) {
      setDraftZone([]);
      setDraftZoneColor('');
      setDraftZoneStyle('solid');
      setDraftZoneHatched(false);
      isDrawingFreehandRef.current = false;
      freehandRawRef.current = [];
      return;
    }
    setSelectedId(null);
    setDraftZone(zoneEditingObject.zone ? [...zoneEditingObject.zone] : []);
    setDraftZoneColor(normalizeZoneColor(zoneEditingObject.zoneColor) ?? '');
    setDraftZoneStyle(normalizeZoneStyle(zoneEditingObject.zoneStyle));
    setDraftZoneHatched(Boolean(zoneEditingObject.zoneHatched));
    setCenter({ lat: zoneEditingObject.lat, lon: zoneEditingObject.lon });
  }, [zoneEditingObject, editorEnabled]);

  // Follow GPS fix automatically when settings.autoCenterGps is enabled
  useEffect(() => {
    if (!settings.autoCenterGps || zoneEditingObject) return;
    if (dragStateRef.current.active || suppressAutoCenterRef.current) return;
    setCenter({ lat: gpsFix.lat, lon: gpsFix.lon });
  }, [gpsFix.lat, gpsFix.lon, settings.autoCenterGps, zoneEditingObject]);

  const metersPerPixel = ZOOM_METERS_PER_PX[zoomIdx] ?? 4.8;
  const isSatellite = settings.mapLayer !== 'offline_vector';
  const gpsSignal = useMemo(
    () => getGpsSignalQuality(gpsFix.accuracyM, Boolean(gpsError)),
    [gpsFix.accuracyM, gpsError]
  );

  const projection = useMemo(
    () => ({
      centerLat: center.lat,
      centerLon: center.lon,
      metersPerPixel,
      width: viewport.width,
      height: viewport.height,
    }),
    [center.lat, center.lon, metersPerPixel, viewport.width, viewport.height]
  );

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const updateSize = () => {
      const rect = el.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        setViewport({
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        });
      }
    };
    updateSize();
    const observer = new ResizeObserver(updateSize);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // Render satellite tiles + procedural ortho-terrain fallback + vector overlays
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = viewport.width * dpr;
    canvas.height = viewport.height * dpr;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    // Base fill: satellite dark earth-olive vs dark tactical vector slate
    ctx.fillStyle = isSatellite ? '#121814' : '#0A0D0C';
    ctx.fillRect(0, 0, viewport.width, viewport.height);

    const topLeft = pixelToLatLon(0, 0, projection);
    const bottomRight = pixelToLatLon(viewport.width, viewport.height, projection);

    // Procedural ortho-imagery / land-cover base underneath satellite tiles
    // Guarantees rich satellite appearance even when offline or in restricted sandboxes
    if (isSatellite) {
      const parcelStepLat = 0.0022;
      const parcelStepLon = 0.0034;
      const sLat = Math.floor(bottomRight.lat / parcelStepLat) * parcelStepLat;
      const eLat = Math.ceil(topLeft.lat / parcelStepLat) * parcelStepLat;
      const sLon = Math.floor(topLeft.lon / parcelStepLon) * parcelStepLon;
      const eLon = Math.ceil(bottomRight.lon / parcelStepLon) * parcelStepLon;

      for (let lat = sLat; lat <= eLat; lat += parcelStepLat) {
        for (let lon = sLon; lon <= eLon; lon += parcelStepLon) {
          const hash = Math.abs(Math.sin(lat * 1931 + lon * 3571));
          const p1 = latLonToPixel(lat + parcelStepLat, lon, projection);
          const p2 = latLonToPixel(lat, lon + parcelStepLon, projection);
          const w = Math.ceil(p2.x - p1.x);
          const h = Math.ceil(p2.y - p1.y);
          if (w <= 0 || h <= 0) continue;

          if (hash > 0.72) {
            ctx.fillStyle = '#1B251D'; // industrial / canopy dark green
          } else if (hash > 0.45) {
            ctx.fillStyle = '#161D18'; // mixed ground
          } else if (hash > 0.22) {
            ctx.fillStyle = '#1E211D'; // concrete/soil block
          } else {
            ctx.fillStyle = '#131916';
          }
          ctx.fillRect(p1.x, p1.y, w + 1, h + 1);

          // Subtle roof/structure footprint inside urban parcels
          if (hash > 0.58 && hash < 0.71) {
            ctx.fillStyle = '#252B27';
            ctx.fillRect(p1.x + w * 0.22, p1.y + h * 0.25, w * 0.48, h * 0.36);
          }
        }
      }

      // Draw Water channel (Moskva river corridor)
      ctx.strokeStyle = '#142429';
      ctx.lineWidth = Math.max(12, 72 / metersPerPixel);
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.beginPath();
      const riverPts = [
        [55.741, 37.595],
        [55.746, 37.608],
        [55.75, 37.619],
        [55.753, 37.632],
        [55.759, 37.646],
      ];
      riverPts.forEach(([la, lo], idx) => {
        const p = latLonToPixel(la, lo, projection);
        if (idx === 0) ctx.moveTo(p.x, p.y);
        else ctx.lineTo(p.x, p.y);
      });
      ctx.stroke();

      // Fetch & composite real Esri World Imagery satellite slippy tiles
      const z = computeTileZoom(metersPerPixel, center.lat);
      const maxTile = (1 << z) - 1;
      const minX = Math.max(0, Math.floor(lonToTileX(topLeft.lon, z)));
      const maxX = Math.min(maxTile, Math.floor(lonToTileX(bottomRight.lon, z)));
      const minY = Math.max(0, Math.floor(latToTileY(topLeft.lat, z)));
      const maxY = Math.min(maxTile, Math.floor(latToTileY(bottomRight.lat, z)));

      const tileCount = (maxX - minX + 1) * (maxY - minY + 1);
      if (tileCount > 0 && tileCount <= 64) {
        for (let tx = minX; tx <= maxX; tx++) {
          for (let ty = minY; ty <= maxY; ty++) {
            const key = `${z}/${tx}/${ty}`;
            const cached = TILE_CACHE.get(key);

            if (!cached) {
              setBoundedTile(key, 'loading');
              const img = new Image();
              img.crossOrigin = 'anonymous';
              img.onload = () => {
                setBoundedTile(key, img);
                setTileTick((t) => t + 1);
              };
              img.onerror = () => {
                setBoundedTile(key, 'failed');
              };
              img.src = getSatelliteTileUrl(z, tx, ty);
            } else if (cached instanceof HTMLImageElement && cached.complete) {
              const nwLat = tileYToLat(ty, z);
              const nwLon = tileXToLon(tx, z);
              const seLat = tileYToLat(ty + 1, z);
              const seLon = tileXToLon(tx + 1, z);
              const nw = latLonToPixel(nwLat, nwLon, projection);
              const se = latLonToPixel(seLat, seLon, projection);
              const drawW = se.x - nw.x;
              const drawH = se.y - nw.y;
              if (drawW > 0 && drawH > 0) {
                ctx.save();
                ctx.filter = 'brightness(0.76) contrast(1.08) saturate(0.88)';
                ctx.drawImage(cached, nw.x, nw.y, drawW + 0.6, drawH + 0.6);
                ctx.restore();
              }
            }
          }
        }
      }
    }

    // Metric grid lines (every 500m)
    const stepDegLat = 500 / 111_320;
    const stepDegLon =
      500 / (111_320 * Math.max(0.2, Math.cos((center.lat * Math.PI) / 180)));

    const startLat = Math.floor(bottomRight.lat / stepDegLat) * stepDegLat;
    const endLat = Math.ceil(topLeft.lat / stepDegLat) * stepDegLat;
    const startLon = Math.floor(topLeft.lon / stepDegLon) * stepDegLon;
    const endLon = Math.ceil(bottomRight.lon / stepDegLon) * stepDegLon;

    ctx.lineWidth = 1;
    ctx.strokeStyle = isSatellite
      ? 'rgba(230, 235, 232, 0.10)'
      : 'rgba(37, 46, 42, 0.75)';
    ctx.beginPath();
    for (let lat = startLat; lat <= endLat; lat += stepDegLat) {
      const p = latLonToPixel(lat, center.lon, projection);
      ctx.moveTo(0, Math.round(p.y) + 0.5);
      ctx.lineTo(viewport.width, Math.round(p.y) + 0.5);
    }
    for (let lon = startLon; lon <= endLon; lon += stepDegLon) {
      const p = latLonToPixel(center.lat, lon, projection);
      ctx.moveTo(Math.round(p.x) + 0.5, 0);
      ctx.lineTo(Math.round(p.x) + 0.5, viewport.height);
    }
    ctx.stroke();

    // Optional tactical vector contours & rail overlay
    if (showContours) {
      ctx.strokeStyle = isSatellite
        ? 'rgba(109, 179, 134, 0.24)'
        : 'rgba(91, 157, 114, 0.16)';
      ctx.lineWidth = 1;
      for (let i = -4; i <= 4; i++) {
        const baseLat = 55.752 + i * 0.0045;
        ctx.beginPath();
        for (let s = -12; s <= 12; s++) {
          const lon = 37.618 + s * 0.003;
          const wave = Math.sin(s * 0.45 + i * 0.9) * 0.0014;
          const p = latLonToPixel(baseLat + wave, lon, projection);
          if (s === -12) ctx.moveTo(p.x, p.y);
          else ctx.lineTo(p.x, p.y);
        }
        ctx.stroke();
      }

      if (!isSatellite) {
        ctx.strokeStyle = 'rgba(84, 128, 138, 0.38)';
        ctx.lineWidth = 6;
        ctx.beginPath();
        const riverPts = [
          [55.741, 37.595],
          [55.746, 37.608],
          [55.75, 37.619],
          [55.753, 37.632],
          [55.759, 37.646],
        ];
        riverPts.forEach(([la, lo], idx) => {
          const p = latLonToPixel(la, lo, projection);
          if (idx === 0) ctx.moveTo(p.x, p.y);
          else ctx.lineTo(p.x, p.y);
        });
        ctx.stroke();
      }

      ctx.save();
      ctx.setLineDash([6, 5]);
      ctx.strokeStyle = 'rgba(211, 146, 57, 0.42)';
      ctx.lineWidth = 2;
      ctx.beginPath();
      const railPts = [
        [55.764, 37.604],
        [55.756, 37.617],
        [55.7554, 37.6248],
        [55.747, 37.634],
      ];
      railPts.forEach(([la, lo], idx) => {
        const p = latLonToPixel(la, lo, projection);
        if (idx === 0) ctx.moveTo(p.x, p.y);
        else ctx.lineTo(p.x, p.y);
      });
      ctx.stroke();
      ctx.restore();
    }

    // Cached offline sector rings
    if (showSectors) {
      for (const sec of settings.offlineSectors) {
        const p = latLonToPixel(sec.centerLat, sec.centerLon, projection);
        const radiusPx = (sec.radiusKm * 1000) / metersPerPixel;
        ctx.save();
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = 'rgba(91, 157, 114, 0.55)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.arc(p.x, p.y, radiusPx, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }
    }

    // Object Perimeter Zones (polygons with style, manual color & hatching)
    if (settings.showObjectZones !== false) {
      for (const obj of objects) {
        if (editorEnabled && zoneEditingObject && obj.id === zoneEditingObject.id) continue;
        if (!obj.zone || obj.zone.length < 3) continue;
        const zoneHex = getEffectiveZoneColor(obj);
        const zoneStyle = normalizeZoneStyle(obj.zoneStyle);
        const isSelected = obj.id === selectedId;
        const pxPoints = obj.zone.map((pt) => latLonToPixel(pt.lat, pt.lon, projection));
        drawStyledZonePolygon(ctx, pxPoints, {
          colorHex: zoneHex,
          style: zoneStyle,
          hatched: Boolean(obj.zoneHatched),
          fillAlpha: isSelected ? 0.24 : 0.14,
          strokeAlpha: isSelected ? 0.96 : 0.78,
          lineWidth: isSelected ? 2.5 : 1.75,
          closed: true,
        });
      }
    }

    // Active Draft Zone Overlay (Zone Editing Mode)
    if (editorEnabled && zoneEditingObject && draftZone.length > 0) {
      const effectiveDraftColor =
        normalizeZoneColor(draftZoneColor) ?? STATUS_META[zoneEditingObject.status].colorHex;
      const pxPoints = draftZone.map((pt) => latLonToPixel(pt.lat, pt.lon, projection));
      drawStyledZonePolygon(ctx, pxPoints, {
        colorHex: effectiveDraftColor,
        style: draftZoneStyle,
        hatched: draftZoneHatched,
        fillAlpha: 0.24,
        strokeAlpha: 0.95,
        lineWidth: 2.5,
        closed: draftZone.length >= 3,
      });

      ctx.save();
      // Vertex handles
      pxPoints.forEach((px, idx) => {
        ctx.beginPath();
        ctx.arc(px.x, px.y, idx === 0 ? 5.5 : 4, 0, Math.PI * 2);
        ctx.fillStyle = idx === 0 ? '#DF9E43' : effectiveDraftColor;
        ctx.fill();
        ctx.lineWidth = 1.5;
        ctx.strokeStyle = '#0C0F0E';
        ctx.stroke();
      });
      ctx.restore();
    }

    // Current GPS / Operator Fix reticle & real-time scaled accuracy circle
    const gpsPx = latLonToPixel(gpsFix.lat, gpsFix.lon, projection);
    const effectiveAccuracyM =
      gpsFix.accuracyM !== null && gpsFix.accuracyM > 0 ? gpsFix.accuracyM : 65;
    const rawAccPx = effectiveAccuracyM / metersPerPixel;
    const maxCirclePx = Math.max(48, Math.min(viewport.width, viewport.height) * 0.42);
    const accPx = Math.max(18, Math.min(maxCirclePx, rawAccPx));

    // Translucent accuracy disc
    ctx.fillStyle = gpsSignal.fillRgba;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, accPx, 0, Math.PI * 2);
    ctx.fill();

    // Dashed accuracy perimeter ring
    ctx.save();
    ctx.setLineDash([5, 4]);
    ctx.strokeStyle = gpsSignal.strokeRgba;
    ctx.lineWidth = 1.75;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, accPx, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();

    // Outer position reticle ring in active GPS signal color
    ctx.strokeStyle = gpsSignal.colorHex;
    ctx.lineWidth = 2.25;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 7.5, 0, Math.PI * 2);
    ctx.stroke();

    // Bright center operator dot
    ctx.fillStyle = '#E6EBE8';
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 2.8, 0, Math.PI * 2);
    ctx.fill();

    // Accuracy label anchored cleanly above the accuracy ring
    if (
      gpsPx.x > -40 &&
      gpsPx.x < viewport.width + 40 &&
      gpsPx.y > -40 &&
      gpsPx.y < viewport.height + 40
    ) {
      const accText =
        gpsFix.accuracyM !== null ? `±${Math.round(gpsFix.accuracyM)} м` : 'нет фикса';
      ctx.save();
      ctx.font = '600 10px ui-monospace, SFMono-Regular, Menlo, monospace';
      const textWidth = ctx.measureText(accText).width;
      const pillW = textWidth + 10;
      const pillH = 16;
      const pillX = gpsPx.x - pillW / 2;
      const pillY = gpsPx.y - accPx - pillH - 4;
      ctx.fillStyle = 'rgba(12, 15, 14, 0.85)';
      ctx.strokeStyle = gpsSignal.strokeRgba;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(pillX, pillY, pillW, pillH, 4);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = gpsSignal.colorHex;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(accText, gpsPx.x, pillY + pillH / 2 + 0.5);
      ctx.restore();
    }
  }, [
    viewport,
    center,
    metersPerPixel,
    projection,
    showContours,
    showSectors,
    settings.offlineSectors,
    settings.showObjectZones,
    objects,
    selectedId,
    editorEnabled,
    zoneEditingObject,
    draftZone,
    draftZoneColor,
    draftZoneStyle,
    draftZoneHatched,
    gpsFix,
    gpsSignal,
    isSatellite,
    tileTick,
  ]);

  const selectedObject = useMemo(
    () => objects.find((o) => o.id === selectedId) ?? null,
    [objects, selectedId]
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (zoneEditingObject && zoneDrawTool === 'freehand') {
      e.currentTarget.setPointerCapture(e.pointerId);
      const rect = e.currentTarget.getBoundingClientRect();
      const pt = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
      isDrawingFreehandRef.current = true;
      freehandRawRef.current = [pt];
      setDraftZone([pt]);
      return;
    }

    dragStateRef.current = {
      active: true,
      moved: false,
      startX: e.clientX,
      startY: e.clientY,
      startCenterLat: center.lat,
      startCenterLon: center.lon,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const hovered = pixelToLatLon(x, y, projection);
    setCursorCoords(hovered);

    if (zoneEditingObject && zoneDrawTool === 'freehand' && isDrawingFreehandRef.current) {
      const pts = freehandRawRef.current;
      const last = pts[pts.length - 1];
      if (!last || haversineDistanceM(last.lat, last.lon, hovered.lat, hovered.lon) >= Math.max(4, metersPerPixel * 3)) {
        const next = [...pts, hovered];
        freehandRawRef.current = next;
        setDraftZone(next);
      }
      return;
    }

    if (!dragStateRef.current.active) return;
    const dx = e.clientX - dragStateRef.current.startX;
    const dy = e.clientY - dragStateRef.current.startY;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
      dragStateRef.current.moved = true;
    }

    const metersPerDegLat = 111_320;
    const metersPerDegLon =
      111_320 * Math.max(0.1, Math.cos((dragStateRef.current.startCenterLat * Math.PI) / 180));
    const nextLat =
      dragStateRef.current.startCenterLat + (dy * metersPerPixel) / metersPerDegLat;
    const nextLon =
      dragStateRef.current.startCenterLon - (dx * metersPerPixel) / metersPerDegLon;

    setCenter({
      lat: Number(Math.max(-85, Math.min(85, nextLat)).toFixed(6)),
      lon: Number(Math.max(-179, Math.min(179, nextLon)).toFixed(6)),
    });
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (zoneEditingObject && zoneDrawTool === 'freehand' && isDrawingFreehandRef.current) {
      isDrawingFreehandRef.current = false;
      const simplified = simplifyPolygonPoints(
        freehandRawRef.current,
        Math.max(5, metersPerPixel * 3),
        48
      );
      setDraftZone(simplified);
      return;
    }

    const wasMoved = dragStateRef.current.moved;
    dragStateRef.current.active = false;
    if (!wasMoved) {
      const rect = e.currentTarget.getBoundingClientRect();
      const clicked = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
      setCursorCoords(clicked);

      if (zoneEditingObject && zoneDrawTool === 'points') {
        setDraftZone((prev) => [...prev, clicked]);
        return;
      }

      if (!zoneEditingObject && settings.showObjectZones !== false) {
        const hit = objects.find(
          (o) => o.zone && o.zone.length >= 3 && isPointInPolygon(clicked.lat, clicked.lon, o.zone)
        );
        if (hit) {
          setSelectedId(hit.id);
        }
      }
    }
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    if (e.deltaY < 0) {
      setZoomIdx((z) => Math.min(ZOOM_METERS_PER_PX.length - 1, z + 1));
    } else if (e.deltaY > 0) {
      setZoomIdx((z) => Math.max(0, z - 1));
    }
  };

  const handleCacheCurrentSector = useCallback(async () => {
    const now = Date.now();
    const sector: OfflineMapSector = {
      id: `sec-${now.toString(36)}`,
      name: `Сектор ${center.lat.toFixed(3)}, ${center.lon.toFixed(3)}`,
      centerLat: center.lat,
      centerLon: center.lon,
      radiusKm: 3.5,
      zoomMin: 12,
      zoomMax: 17,
      sizeMb: 7.4,
      cachedAt: now,
    };
    await onAddOfflineSector(sector);
    setSectorCachedToast(`Офлайн-сектор сохранён (${sector.sizeMb} МБ)`);
    window.setTimeout(() => setSectorCachedToast(null), 3200);
  }, [center.lat, center.lon, onAddOfflineSector]);

  const scaleBarMeters = Math.round(metersPerPixel * 96);

  return (
    <section
      aria-label="Спутниковая карта сектора"
      className="relative flex-1 w-full h-full min-h-[calc(100dvh-3rem)] [@media(orientation:landscape)_and_(min-width:560px)_and_(max-width:1023px)]:min-h-[calc(100dvh-2.5rem)] lg:min-h-[calc(100dvh-3.5rem)] bg-[#0A0D0C] overflow-hidden select-none"
    >
      {/* Full-bleed Canvas Viewport */}
      <div ref={containerRef} className="absolute inset-0">
        <canvas
          ref={canvasRef}
          data-testid="offline-map-canvas"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={() => {
            isDrawingFreehandRef.current = false;
            dragStateRef.current.active = false;
          }}
          onWheel={handleWheel}
          onPointerLeave={() => {
            if (!isDrawingFreehandRef.current) {
              dragStateRef.current.active = false;
            }
          }}
          className="w-full h-full block cursor-crosshair touch-none"
        />

        {/* Object Markers Overlay */}
        {objects.map((obj) => {
          const pos = latLonToPixel(obj.lat, obj.lon, projection);
          if (
            pos.x < -24 ||
            pos.y < -24 ||
            pos.x > viewport.width + 24 ||
            pos.y > viewport.height + 24
          ) {
            return null;
          }
          const meta = STATUS_META[obj.status];
          const isSelected = obj.id === selectedId;
          const showLabels = settings.showObjectLabels !== false;
          const hasZone = Boolean(obj.zone && obj.zone.length >= 3);
          return (
            <button
              key={obj.id}
              type="button"
              onClick={() => {
                if (zoneEditingObject) return;
                setSelectedId(obj.id);
              }}
              aria-label={`Объект: ${obj.title} (${meta.label})`}
              style={{
                transform: `translate3d(${Math.round(pos.x)}px, ${Math.round(pos.y)}px, 0) translate(-50%, -50%)`,
              }}
              className={`absolute top-0 left-0 min-h-[44px] min-w-[44px] flex items-center justify-center group focus-visible:outline-none z-10 ${
                zoneEditingObject ? 'pointer-events-none opacity-80' : ''
              }`}
            >
              {showLabels ? (
                <span
                  className={`px-2 py-1 rounded-md border text-xs font-medium whitespace-nowrap transition-all duration-150 shadow-sm ${
                    isSelected
                      ? 'bg-[#1A201E] border-[#E6EBE8] text-[#E6EBE8] scale-105 ring-2 ring-[#5B9D72]/40'
                      : 'bg-[#131816]/90 backdrop-blur-sm border-[#252E2A] text-[#E6EBE8] group-hover:border-[#6DB386] group-hover:scale-[1.03]'
                  }`}
                >
                  <span style={{ color: meta.colorHex }} className="mr-1.5">
                    {meta.glyph}
                  </span>
                  <span className="max-w-[96px] sm:max-w-[140px] truncate inline-block align-bottom">
                    {obj.title}
                  </span>
                  {hasZone && (
                    <Pentagon className="inline-block w-3 h-3 ml-1.5 text-[#6DB386] align-text-bottom" />
                  )}
                </span>
              ) : (
                <span
                  className={`w-4 h-4 rounded-full border-2 flex items-center justify-center transition-transform ${
                    isSelected ? 'scale-125 border-[#E6EBE8]' : 'border-[#0C0F0E] group-hover:scale-110'
                  }`}
                  style={{ backgroundColor: meta.colorHex }}
                />
              )}
            </button>
          );
        })}
      </div>

      {/* Floating Top-Left HUD: Telemetry & GPS Fix Actions */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2, ease: 'easeOut' }}
        className="absolute top-2 left-2 right-[54px] sm:top-3 sm:left-3 sm:right-[68px] z-20 flex flex-wrap items-center gap-1.5 sm:gap-2 pointer-events-none"
      >
        <div className="pointer-events-auto max-w-full min-w-0 flex flex-wrap items-center gap-x-1.5 sm:gap-x-2 gap-y-0.5 px-2.5 py-1 sm:px-3 sm:py-1.5 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[11px] sm:text-xs">
          <span className="font-mono tabular-nums text-[#E6EBE8] whitespace-nowrap">
            {formatCoordinates(cursorCoords.lat, cursorCoords.lon, settings.coordinateFormat)}
          </span>
          <span className="text-[#6B7771]" aria-hidden="true">·</span>
          <span className="text-[#98A49E] whitespace-nowrap">
            {scaleBarMeters} м
          </span>
          <span className="text-[#6B7771]" aria-hidden="true">·</span>
          <span
            data-testid="gps-signal-indicator"
            data-signal-level={gpsSignal.level}
            className="inline-flex items-center gap-1.5 font-medium whitespace-nowrap"
          >
            <span className="relative flex h-2 w-2 sm:h-2.5 sm:w-2.5 shrink-0" aria-hidden="true">
              {isPollingGps && (
                <span
                  style={{ backgroundColor: gpsSignal.colorHex }}
                  className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75"
                />
              )}
              <span
                style={{ backgroundColor: gpsSignal.colorHex }}
                className="relative inline-flex rounded-full h-2 w-2 sm:h-2.5 sm:w-2.5"
              />
            </span>
            <span style={{ color: gpsSignal.colorHex }}>
              GPS: {gpsSignal.label}
              {gpsFix.accuracyM !== null ? ` ±${gpsFix.accuracyM} м` : ''}
            </span>
            <span className="hidden lg:inline text-[#98A49E] font-normal">
              ({POWER_PROFILE_META[settings.powerProfile].label})
            </span>
          </span>
          {gpsError && (
            <span className="text-[#DF9E43] basis-full sm:basis-auto leading-snug">
              {gpsError}
            </span>
          )}
        </div>

        <div className="pointer-events-auto flex items-center gap-1 sm:gap-1.5">
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={onRequestSingleFix}
            className="min-h-[30px] sm:min-h-[34px] lg:min-h-[36px] px-2.5 py-0.5 sm:py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[11px] sm:text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors whitespace-nowrap"
          >
            {isPollingGps ? 'Опрос...' : 'Запросить GPS'}
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={() => onSetManualFix(cursorCoords.lat, cursorCoords.lon)}
            className="min-h-[30px] sm:min-h-[34px] lg:min-h-[36px] px-2 sm:px-2.5 py-0.5 sm:py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[11px] sm:text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1 whitespace-nowrap"
          >
            <Navigation className="w-3.5 h-3.5 text-[#D39239] shrink-0" />
            <span className="hidden [@media(orientation:landscape)_and_(min-width:560px)_and_(max-width:1023px)]:inline">Фикс</span>
            <span className="hidden lg:inline">Фикс к курсору</span>
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={() => onChangeMapLayer(isSatellite ? 'offline_vector' : 'satellite')}
            aria-label="Переключить слой карты"
            className="min-h-[30px] sm:min-h-[34px] lg:min-h-[36px] px-2 sm:px-2.5 py-0.5 sm:py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[11px] sm:text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1 whitespace-nowrap"
          >
            {isSatellite ? (
              <>
                <Globe className="w-3.5 h-3.5 text-[#6DB386] shrink-0" />
                <span>Спутник</span>
              </>
            ) : (
              <>
                <MapIcon className="w-3.5 h-3.5 text-[#98A49E] shrink-0" />
                <span>Вектор</span>
              </>
            )}
          </motion.button>
        </div>
      </motion.div>

      {/* Floating Right Edge Controls */}
      <motion.div
        initial={{ opacity: 0, x: 8 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ duration: 0.2, ease: 'easeOut' }}
        className="absolute right-2 sm:right-3 top-2 sm:top-3 z-20 flex flex-col gap-1 sm:gap-1.5"
      >
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setZoomIdx((z) => Math.min(ZOOM_METERS_PER_PX.length - 1, z + 1))}
          aria-label="Приблизить карту"
          className="min-h-[34px] min-w-[34px] lg:min-h-[42px] lg:min-w-[42px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
        >
          <Plus className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setZoomIdx((z) => Math.max(0, z - 1))}
          aria-label="Отдалить карту"
          className="min-h-[34px] min-w-[34px] lg:min-h-[42px] lg:min-w-[42px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
        >
          <Minus className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => {
            suppressAutoCenterRef.current = false;
            onRequestSingleFix();
            setCenter({ lat: gpsFix.lat, lon: gpsFix.lon });
          }}
          aria-label="Центрировать по GPS"
          className="min-h-[34px] min-w-[34px] lg:min-h-[42px] lg:min-w-[42px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#6DB386] hover:bg-[#1A201E] transition-colors"
        >
          <LocateFixed className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setShowContours((v) => !v)}
          aria-label="Переключить векторные изолинии"
          aria-pressed={showContours}
          className={`min-h-[34px] min-w-[34px] lg:min-h-[42px] lg:min-w-[42px] flex items-center justify-center rounded-lg backdrop-blur-md border transition-colors ${
            showContours
              ? 'bg-[#203328]/95 border-[#5B9D72] text-[#E6EBE8]'
              : 'bg-[#131816]/90 border-[#252E2A] text-[#98A49E]'
          }`}
        >
          <Layers className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setShowSectors((s) => !s)}
          aria-label="Переключить зоны офлайн-секторов"
          aria-pressed={showSectors}
          className={`min-h-[34px] min-w-[34px] lg:min-h-[42px] lg:min-w-[42px] flex items-center justify-center rounded-lg backdrop-blur-md border transition-colors ${
            showSectors
              ? 'bg-[#203328]/95 border-[#5B9D72] text-[#E6EBE8]'
              : 'bg-[#131816]/90 border-[#252E2A] text-[#98A49E]'
          }`}
        >
          <Radar className="w-4 h-4" />
        </motion.button>
      </motion.div>

      {/* Bottom Action Strip OR Interactive Zone Editor HUD */}
      {editorEnabled && zoneEditingObject ? (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
          data-testid="zone-editor-hud"
          className="absolute inset-x-3 bottom-[84px] [@media(orientation:landscape)_and_(min-width:560px)]:bottom-3 lg:bottom-4 [@media(orientation:landscape)_and_(min-width:560px)]:left-3 [@media(orientation:landscape)_and_(min-width:560px)]:right-auto [@media(orientation:landscape)_and_(min-width:560px)]:w-[440px] lg:left-4 lg:right-auto lg:w-[460px] z-30 bg-[#131816]/95 backdrop-blur-md border border-[#5B9D72] rounded-xl p-3 sm:p-4 space-y-2.5 shadow-2xl max-h-[calc(100%-52px)] overflow-y-auto"
        >
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-[#6DB386]">
                <Pentagon className="w-3.5 h-3.5 shrink-0" />
                <span>Разметка зоны объекта</span>
              </div>
              <div className="text-sm font-semibold text-[#E6EBE8] truncate mt-0.5">
                {zoneEditingObject.title}
              </div>
            </div>
            <div className="text-right font-mono tabular-nums text-[11px] text-[#98A49E] shrink-0">
              <div>{draftZone.length} тчк</div>
              {draftZone.length >= 3 && (
                <div className="text-[#E6EBE8]">
                  {formatArea(computePolygonAreaM2(draftZone))} · P={formatDistance(computePolygonPerimeterM(draftZone))}
                </div>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="button"
              onClick={() => setZoneDrawTool('points')}
              className={`min-h-[34px] px-2.5 py-1 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors ${
                zoneDrawTool === 'points'
                  ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
              }`}
            >
              <Crosshair className="w-3.5 h-3.5" />
              <span>По точкам</span>
            </button>
            <button
              type="button"
              onClick={() => setZoneDrawTool('freehand')}
              className={`min-h-[34px] px-2.5 py-1 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors ${
                zoneDrawTool === 'freehand'
                  ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
              }`}
            >
              <Pencil className="w-3.5 h-3.5" />
              <span>Рисовать</span>
            </button>
            <button
              type="button"
              onClick={() =>
                setDraftZone(
                  buildZoneCircle(
                    zoneEditingObject.lat,
                    zoneEditingObject.lon,
                    settings.defaultZoneRadiusM ?? 80
                  )
                )
              }
              className="min-h-[34px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72]"
            >
              Круг R={settings.defaultZoneRadiusM ?? 80} м
            </button>
          </div>

          {/* Live Zone Style, Hatching & Manual Color */}
          <div className="p-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-1.5">
              <div className="flex flex-wrap items-center gap-1">
                {(['solid', 'dashed', 'dotted', 'outline'] as ZoneStyle[]).map((st) => (
                  <button
                    key={st}
                    type="button"
                    onClick={() => setDraftZoneStyle(st)}
                    aria-pressed={draftZoneStyle === st}
                    className={`min-h-[28px] px-2 py-0.5 rounded border text-[11px] font-medium transition-colors ${
                      draftZoneStyle === st
                        ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                        : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                    }`}
                  >
                    {ZONE_STYLE_LABELS[st]}
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setDraftZoneHatched((h) => !h)}
                aria-pressed={draftZoneHatched}
                className={`min-h-[28px] px-2.5 py-0.5 rounded border text-[11px] font-medium transition-colors ${
                  draftZoneHatched
                    ? 'bg-[#203328] border-[#5B9D72] text-[#6DB386]'
                    : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                }`}
              >
                Штриховка: {draftZoneHatched ? 'Вкл' : 'Выкл'}
              </button>
            </div>

            <div className="flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                onClick={() => setDraftZoneColor('')}
                aria-pressed={!draftZoneColor}
                className={`min-h-[26px] px-2 py-0.5 rounded border text-[11px] transition-colors ${
                  !draftZoneColor
                    ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                    : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                }`}
              >
                Авто
              </button>
              {ZONE_COLOR_PRESETS.map((preset) => {
                const active = draftZoneColor.toUpperCase() === preset.hex;
                return (
                  <button
                    key={preset.hex}
                    type="button"
                    onClick={() => setDraftZoneColor(preset.hex)}
                    title={preset.label}
                    aria-label={`Цвет зоны ${preset.label}`}
                    aria-pressed={active}
                    className={`w-6 h-6 rounded-full border-2 transition-transform ${
                      active ? 'border-[#E6EBE8] scale-110' : 'border-[#252E2A] hover:scale-105'
                    }`}
                    style={{ backgroundColor: preset.hex }}
                  />
                );
              })}
              <label
                title="Выбрать произвольный цвет"
                className="flex items-center gap-1 px-1.5 py-0.5 rounded border border-[#252E2A] bg-[#131816] text-[10px] font-mono text-[#98A49E] cursor-pointer hover:border-[#5B9D72]"
              >
                <input
                  type="color"
                  value={
                    normalizeZoneColor(draftZoneColor) ??
                    STATUS_META[zoneEditingObject.status].colorHex
                  }
                  onChange={(e) => setDraftZoneColor(e.target.value.toUpperCase())}
                  aria-label="Свой цвет зоны на карте"
                  className="w-4 h-4 bg-transparent border-0 p-0 cursor-pointer"
                />
                <span>{draftZoneColor || 'HEX'}</span>
              </label>
            </div>
          </div>

          <div className="text-[11px] text-[#98A49E]">
            {zoneDrawTool === 'points'
              ? 'Нажимайте на карту для добавления вершин; перетаскивайте карту для смещения.'
              : 'Зажмите и обведите периметр объекта на карте одной линией.'}
            {draftZone.length > 0 && draftZone.length < 3 && (
              <span className="text-[#DF9E43] ml-1">Нужно минимум 3 точки.</span>
            )}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[#252E2A]">
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                disabled={draftZone.length === 0}
                onClick={() => setDraftZone((prev) => prev.slice(0, -1))}
                aria-label="Удалить последнюю точку"
                className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] disabled:opacity-40 flex items-center gap-1"
              >
                <Undo2 className="w-3.5 h-3.5" />
                <span>Назад</span>
              </button>
              <button
                type="button"
                disabled={draftZone.length === 0}
                onClick={() => setDraftZone([])}
                className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#F1ACA7] disabled:opacity-40"
              >
                Очистить
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onCancelZoneEdit}
                className="min-h-[36px] px-3 py-1 rounded-lg border border-[#252E2A] text-xs text-[#98A49E] hover:text-[#E6EBE8]"
              >
                Отмена
              </button>
              <button
                type="button"
                disabled={zoneSaving || (draftZone.length > 0 && draftZone.length < 3)}
                onClick={async () => {
                  if (!onSaveObjectZone) return;
                  setZoneSaving(true);
                  try {
                    await onSaveObjectZone(zoneEditingObject.id, {
                      zone: draftZone,
                      zoneColor: normalizeZoneColor(draftZoneColor),
                      zoneStyle: draftZoneStyle,
                      zoneHatched: draftZoneHatched,
                    });
                  } finally {
                    setZoneSaving(false);
                  }
                }}
                className="min-h-[36px] px-3.5 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] disabled:opacity-45"
              >
                {zoneSaving ? 'Сохранение...' : 'Сохранить зону'}
              </button>
            </div>
          </div>
        </motion.div>
      ) : (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className="absolute left-2 sm:left-3 bottom-[84px] [@media(orientation:landscape)_and_(min-width:560px)]:bottom-2.5 lg:bottom-3 z-20 flex flex-wrap items-center gap-1.5 sm:gap-2 pointer-events-none"
        >
          {editorEnabled && (
            <motion.button
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.95 }}
              type="button"
              onClick={() => onCreateAtCoords(cursorCoords.lat, cursorCoords.lon)}
              className="pointer-events-auto min-h-[36px] lg:min-h-[42px] px-3 sm:px-4 py-1.5 lg:py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors flex items-center gap-1.5 whitespace-nowrap shadow-md"
            >
              <Crosshair className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              <span>Отметить точку</span>
            </motion.button>
          )}
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.95 }}
            type="button"
            onClick={handleCacheCurrentSector}
            className="pointer-events-auto min-h-[36px] lg:min-h-[42px] px-2.5 sm:px-3 py-1.5 lg:py-2 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:bg-[#1A201E] transition-colors flex items-center gap-1.5 whitespace-nowrap shadow-md"
          >
            <Download className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#D39239]" />
            <span>Кэшировать сектор</span>
          </motion.button>

          <AnimatePresence>
            {sectorCachedToast && (
              <motion.div
                initial={{ opacity: 0, y: 6, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 4, scale: 0.96 }}
                transition={{ duration: 0.18 }}
                className="pointer-events-auto px-3 py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs text-[#E6EBE8]"
              >
                {sectorCachedToast}
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}

      {/* Selected Object Floating Card (Centered window on portrait mobile, compact bottom-right clearing controls on landscape, bottom-right on desktop) */}
      <AnimatePresence>
        {selectedObject && !zoneEditingObject && (
          <motion.aside
            key={selectedObject.id}
            initial={{ opacity: 0, scale: 0.97, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            aria-label="Выбранный объект на карте"
            className="absolute inset-x-4 top-1/2 -translate-y-1/2 [@media(orientation:landscape)_and_(min-width:560px)]:inset-x-auto [@media(orientation:landscape)_and_(min-width:560px)]:top-auto [@media(orientation:landscape)_and_(min-width:560px)]:translate-y-0 [@media(orientation:landscape)_and_(min-width:560px)]:right-14 [@media(orientation:landscape)_and_(min-width:560px)]:bottom-3 [@media(orientation:landscape)_and_(min-width:560px)]:w-[348px] lg:inset-x-auto lg:top-auto lg:translate-y-0 lg:right-3 lg:bottom-3 lg:w-80 z-30 bg-[#131816]/95 backdrop-blur-md border border-[#252E2A] rounded-xl p-3 lg:p-4 space-y-2 lg:space-y-3 shadow-2xl max-h-[calc(100%-24px)] overflow-y-auto"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-start gap-2.5 min-w-0 flex-1 [@media(orientation:landscape)_and_(min-width:560px)]:flex-row lg:flex-col">
                <ObjectImage
                  src={getObjectPhotos(selectedObject)[0]}
                  alt={selectedObject.title}
                  className="hidden [@media(orientation:landscape)_and_(min-width:560px)]:block lg:hidden w-16 h-16 shrink-0 rounded-lg border border-[#252E2A]"
                />
                <div className="min-w-0 space-y-0.5 lg:space-y-1 flex-1">
                  <div className="flex items-center gap-1.5 text-[11px] lg:text-xs">
                    <span className={STATUS_META[selectedObject.status].textClass}>
                      {STATUS_META[selectedObject.status].glyph}{' '}
                      {STATUS_META[selectedObject.status].label}
                    </span>
                    <span className="text-[#6B7771] [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:inline" aria-hidden="true">·</span>
                    <span className="text-[#98A49E] truncate [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:inline">
                      {CATEGORY_LABELS[selectedObject.category]}
                    </span>
                  </div>
                  <h3 className="text-sm font-semibold text-[#E6EBE8] leading-snug line-clamp-1 lg:line-clamp-none">
                    {selectedObject.title}
                  </h3>
                  <div className="hidden [@media(orientation:landscape)_and_(min-width:560px)]:flex lg:hidden items-center gap-2.5 text-[11px] font-mono tabular-nums text-[#98A49E]">
                    <span className="text-[#E6EBE8]">
                      {formatDistance(
                        haversineDistanceM(
                          gpsFix.lat,
                          gpsFix.lon,
                          selectedObject.lat,
                          selectedObject.lon
                        )
                      )}
                    </span>
                    <span className="text-[#DF9E43]">
                      {Math.round(
                        bearingDeg(
                          gpsFix.lat,
                          gpsFix.lon,
                          selectedObject.lat,
                          selectedObject.lon
                        )
                      )}
                      °
                    </span>
                    {selectedObject.zone && selectedObject.zone.length >= 3 && (
                      <span className="text-[#6DB386] truncate">
                        {formatArea(computePolygonAreaM2(selectedObject.zone))}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setSelectedId(null)}
                aria-label="Скрыть карточку объекта"
                className="min-h-[32px] min-w-[32px] lg:min-h-[36px] lg:min-w-[36px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="relative [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:block">
              <ObjectImage
                src={getObjectPhotos(selectedObject)[0]}
                alt={selectedObject.title}
                className="w-full h-32 rounded-lg border border-[#252E2A]"
              />
              {getObjectPhotos(selectedObject).length > 1 && (
                <span className="pointer-events-none absolute bottom-2 right-2 px-1.5 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[10px] font-mono text-[#E6EBE8]">
                  {getObjectPhotos(selectedObject).length} фото
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs font-mono tabular-nums text-[#98A49E] [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:grid">
              <div>
                Дистанция:{' '}
                <span className="text-[#E6EBE8]">
                  {formatDistance(
                    haversineDistanceM(
                      gpsFix.lat,
                      gpsFix.lon,
                      selectedObject.lat,
                      selectedObject.lon
                    )
                  )}
                </span>
              </div>
              <div>
                Азимут:{' '}
                <span className="text-[#E6EBE8]">
                  {Math.round(
                    bearingDeg(
                      gpsFix.lat,
                      gpsFix.lon,
                      selectedObject.lat,
                      selectedObject.lon
                    )
                  )}
                  °
                </span>
              </div>
              {selectedObject.securityLevel && selectedObject.securityLevel !== 'none' && (
                <div className="col-span-2 font-sans text-[#DF9E43]">
                  Режим: {SECURITY_LEVEL_LABELS[selectedObject.securityLevel]}
                </div>
              )}
              {selectedObject.zone && selectedObject.zone.length >= 3 && (
                <div className="col-span-2 text-[#6DB386]">
                  Зона: {formatArea(computePolygonAreaM2(selectedObject.zone))} · P=
                  {formatDistance(computePolygonPerimeterM(selectedObject.zone))}
                </div>
              )}
            </div>

            <p className="text-xs text-[#98A49E] line-clamp-2 leading-relaxed [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:line-clamp-2">
              {selectedObject.description}
            </p>

            <div className="flex gap-2">
              {editorEnabled && onEditZoneForObject && (
                <button
                  type="button"
                  onClick={() => onEditZoneForObject(selectedObject)}
                  className="min-h-[38px] lg:min-h-[44px] px-3 py-1.5 lg:py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-[#E6EBE8] text-xs font-medium hover:bg-[#274032] transition-colors flex items-center justify-center gap-1.5 shrink-0"
                  title="Задать или изменить зону на карте"
                >
                  <Pentagon className="w-3.5 h-3.5 text-[#6DB386]" />
                  <span>Зона</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => onOpenObjectInDatabase(selectedObject.id)}
                className="min-h-[38px] lg:min-h-[44px] flex-1 px-4 py-1.5 lg:py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors"
              >
                Открыть на всю площадь
              </button>
            </div>
          </motion.aside>
        )}
      </AnimatePresence>
    </section>
  );
}
