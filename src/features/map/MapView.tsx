import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Check,
  ChevronDown,
  ChevronUp,
  Crosshair,
  Download,
  Globe,
  Grid,
  Layers,
  LocateFixed,
  Map as MapIcon,
  Minus,
  Move,
  Navigation,
  Pencil,
  Pentagon,
  Plus,
  Radar,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Trash2,
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
  offsetCoordinate,
  pixelToLatLon,
  simplifyPolygonPoints,
  tileXToLon,
  tileYToLat,
  translateZone,
} from '../../core/geo';
import { EASE_OUT_TACTICAL } from '../../core/motion';
import {
  CATEGORY_LABELS,
  getEffectiveObjectIcon,
  getEffectiveZonePattern,
  getObjectPhotos,
  normalizeZoneColor,
  normalizeZoneStyle,
  POWER_PROFILE_META,
  SECURITY_LEVEL_LABELS,
  STATUS_META,
  ZONE_COLOR_PRESETS,
  ZONE_PATTERN_LABELS,
  ZONE_PATTERN_ORDER,
  ZONE_STYLE_LABELS,
  ZONE_STYLE_ORDER,
} from '../../core/labels';
import type {
  AppSettings,
  GeoPoint,
  GpsFix,
  MapLayerMode,
  ObjectCategory,
  ObjectIconId,
  OfflineMapSector,
  ReliabilityStatus,
  UrbexObject,
  ZonePattern,
  ZoneStyle,
} from '../../core/types';
import { ObjectGlyph } from '../../components/ObjectGlyph';
import { ObjectImage } from '../../components/ObjectImage';
import { ZoneLinePreview, ZonePatternPreview } from '../../components/ZoneStylePreview';
import {
  MapDraftZoneLayer,
  MapGpsLayer,
  MapObjectMarkersLayer,
  MapZonesLayer,
  syncCanvas2dContext,
} from './MapZonesLayer';
import { resolveDraftZoneColorHex } from './zoneRenderUtils';
import { MapSearchOverlay } from './MapSearchOverlay';
import type { MapSearchResult } from '../../core/mapSearch';

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
  zonePattern: ZonePattern;
  zoneHatched: boolean;
}

export interface PointMoveTarget {
  objectId?: string;
  title: string;
  category: ObjectCategory;
  icon?: ObjectIconId;
  status: ReliabilityStatus;
  lat: number;
  lon: number;
  zone?: GeoPoint[];
  zoneColor?: string;
  zoneStyle?: ZoneStyle;
  zonePattern?: ZonePattern;
  zoneHatched?: boolean;
  isModalDraft?: boolean;
  token: number;
}

export interface PointMoveResult {
  lat: number;
  lon: number;
  zone?: GeoPoint[];
  shiftedZone: boolean;
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
  onCursorChange?: (coords: { lat: number; lon: number }) => void;
  hideHud?: boolean;
  focusTarget?: MapFocusTarget | null;
  zoneEditingObject?: UrbexObject | null;
  onEditZoneForObject?: (obj: UrbexObject) => void;
  onSaveObjectZone?: (objectId: string, updates: ZoneUpdatePayload) => Promise<void>;
  onCancelZoneEdit?: () => void;
  pointMoveTarget?: PointMoveTarget | null;
  onMovePointForObject?: (obj: UrbexObject) => void;
  onSavePointMove?: (result: PointMoveResult) => Promise<void> | void;
  onCancelPointMove?: () => void;
  onRequestSingleFix: () => void;
  onSetManualFix: (lat: number, lon: number) => void;
  onOpenObjectInDatabase: (objectId: string) => void;
  onAddOfflineSector: (sector: OfflineMapSector) => Promise<void>;
  onChangeMapLayer: (layer: MapLayerMode) => Promise<void>;
  onToggleMapGrid?: () => Promise<void> | void;
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

export default function MapView({
  objects,
  gpsFix,
  isPollingGps,
  gpsError,
  settings,
  editorEnabled = false,
  savedCamera,
  onCameraChange,
  onCursorChange,
  hideHud = false,
  focusTarget = null,
  zoneEditingObject = null,
  onEditZoneForObject,
  onSaveObjectZone,
  onCancelZoneEdit,
  pointMoveTarget = null,
  onMovePointForObject,
  onSavePointMove,
  onCancelPointMove,
  onRequestSingleFix,
  onSetManualFix,
  onOpenObjectInDatabase,
  onAddOfflineSector,
  onChangeMapLayer,
  onToggleMapGrid,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const [viewport, setViewport] = useState({ width: 1120, height: 720 });
  const [zoomIdx, setZoomIdx] = useState(
    pointMoveTarget || focusTarget ? Math.max(savedCamera?.zoomIdx ?? 3, 4) : (savedCamera?.zoomIdx ?? 3)
  ); // ~4.8 m/px (Z15 satellite)
  const [center, setCenter] = useState(() => {
    const baseLat = pointMoveTarget?.lat ?? focusTarget?.lat ?? savedCamera?.lat ?? gpsFix.lat;
    const baseLon = pointMoveTarget?.lon ?? focusTarget?.lon ?? savedCamera?.lon ?? gpsFix.lon;
    if (pointMoveTarget && typeof window !== 'undefined' && window.innerWidth < 640 && window.innerHeight > window.innerWidth) {
      const mPerPx = ZOOM_METERS_PER_PX[Math.max(savedCamera?.zoomIdx ?? 3, 4)] ?? 2.4;
      return offsetCoordinate(baseLat, baseLon, -Math.round(window.innerHeight * 0.16 * mPerPx), 0);
    }
    return { lat: baseLat, lon: baseLon };
  });
  const [showContours, setShowContours] = useState(true);
  const [showSectors, setShowSectors] = useState(true);
  const [toolsOpen, setToolsOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchTargetPin, setSearchTargetPin] = useState<{
    title: string;
    subtitle: string;
    lat: number;
    lon: number;
  } | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(focusTarget?.objectId ?? null);
  const [tileTick, setTileTick] = useState(0);
  const [cursorCoords, setCursorCoords] = useState<{ lat: number; lon: number }>({
    lat: pointMoveTarget?.lat ?? focusTarget?.lat ?? savedCamera?.lat ?? gpsFix.lat,
    lon: pointMoveTarget?.lon ?? focusTarget?.lon ?? savedCamera?.lon ?? gpsFix.lon,
  });
  const [sectorCachedToast, setSectorCachedToast] = useState<string | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const [sensorHeadingDeg, setSensorHeadingDeg] = useState<number | null>(null);
  const [focusPulseId, setFocusPulseId] = useState<string | null>(focusTarget?.objectId ?? null);
  const lastAppliedFocusTokenRef = useRef<number | null>(null);
  const lastAppliedPointTokenRef = useRef<number | null>(null);
  const suppressAutoCenterRef = useRef<boolean>(Boolean(focusTarget || pointMoveTarget));
  const focusAnimRafRef = useRef<number | null>(null);
  const focusPulseTimerRef = useRef<number | null>(null);
  const centerRef = useRef(center);
  centerRef.current = center;

  useEffect(() => {
    if (settings.showCompassHud === false || typeof window === 'undefined') {
      setSensorHeadingDeg(null);
      return;
    }
    const handleOrientation = (ev: DeviceOrientationEvent) => {
      const webkitHeading = (ev as DeviceOrientationEvent & { webkitCompassHeading?: number }).webkitCompassHeading;
      if (typeof webkitHeading === 'number' && Number.isFinite(webkitHeading)) {
        setSensorHeadingDeg(((Math.round(webkitHeading) % 360) + 360) % 360);
      } else if (typeof ev.alpha === 'number' && Number.isFinite(ev.alpha)) {
        setSensorHeadingDeg(((Math.round(360 - ev.alpha) % 360) + 360) % 360);
      }
    };
    window.addEventListener('deviceorientation', handleOrientation);
    return () => window.removeEventListener('deviceorientation', handleOrientation);
  }, [settings.showCompassHud]);

  // Interactive Zone Drawing State (synchronized on selection key change before commit to avoid 1-frame flicker)
  const activeZoneEditKey = editorEnabled && zoneEditingObject ? zoneEditingObject.id : null;
  const [syncedZoneEditKey, setSyncedZoneEditKey] = useState<string | null>(activeZoneEditKey);
  const [draftZone, setDraftZone] = useState<GeoPoint[]>(() =>
    editorEnabled && zoneEditingObject?.zone ? [...zoneEditingObject.zone] : []
  );
  const [draftZoneColor, setDraftZoneColor] = useState<string>(() =>
    editorEnabled && zoneEditingObject ? (normalizeZoneColor(zoneEditingObject.zoneColor) ?? '') : ''
  );
  const [draftZoneStyle, setDraftZoneStyle] = useState<ZoneStyle>(() =>
    editorEnabled && zoneEditingObject ? normalizeZoneStyle(zoneEditingObject.zoneStyle) : 'solid'
  );
  const [draftZonePattern, setDraftZonePattern] = useState<ZonePattern>(() =>
    editorEnabled && zoneEditingObject ? getEffectiveZonePattern(zoneEditingObject) : 'none'
  );
  const [zoneDrawTool, setZoneDrawTool] = useState<'points' | 'freehand'>('points');
  const [zoneHudCollapsed, setZoneHudCollapsed] = useState(false);
  const [isAutoCollapsedForDraw, setIsAutoCollapsedForDraw] = useState(false);
  const [zoneStyleExpanded, setZoneStyleExpanded] = useState(false);
  const [zoneSaving, setZoneSaving] = useState(false);
  const freehandRawRef = useRef<GeoPoint[]>([]);
  const isDrawingFreehandRef = useRef(false);
  const zoneEditingObjectRef = useRef(zoneEditingObject);
  zoneEditingObjectRef.current = zoneEditingObject;
  const zoomIdxRef = useRef(zoomIdx);
  zoomIdxRef.current = zoomIdx;

  if (activeZoneEditKey !== syncedZoneEditKey) {
    setSyncedZoneEditKey(activeZoneEditKey);
    setZoneHudCollapsed(false);
    setIsAutoCollapsedForDraw(false);
    setZoneStyleExpanded(false);
    if (activeZoneEditKey && zoneEditingObject) {
      setSelectedId(null);
      setDraftZone(zoneEditingObject.zone ? [...zoneEditingObject.zone] : []);
      setDraftZoneColor(normalizeZoneColor(zoneEditingObject.zoneColor) ?? '');
      setDraftZoneStyle(normalizeZoneStyle(zoneEditingObject.zoneStyle));
      setDraftZonePattern(getEffectiveZonePattern(zoneEditingObject));
    } else {
      setDraftZone([]);
      setDraftZoneColor('');
      setDraftZoneStyle('solid');
      setDraftZonePattern('none');
    }
  }

  // Interactive Point Move State
  const [draftPoint, setDraftPoint] = useState<GeoPoint | null>(
    pointMoveTarget ? { lat: pointMoveTarget.lat, lon: pointMoveTarget.lon } : null
  );
  const [shiftZoneWithPoint, setShiftZoneWithPoint] = useState<boolean>(false);
  const [nudgeStepM, setNudgeStepM] = useState<1 | 5 | 25>(5);
  const [pointSaving, setPointSaving] = useState(false);
  const isDraggingPinRef = useRef(false);

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

  const isEditingZone = Boolean(editorEnabled && zoneEditingObject);
  const isMovingPoint = Boolean(editorEnabled && pointMoveTarget && !isEditingZone);
  const showNormalHud = !isEditingZone && !isMovingPoint && !hideHud;

  // Close tools & search drawers when entering zone editing or hiding HUD
  useEffect(() => {
    if (!showNormalHud) {
      setToolsOpen(false);
      setSearchOpen(false);
    }
  }, [showNormalHud]);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current !== null) {
        window.clearTimeout(toastTimerRef.current);
      }
    };
  }, []);

  // Sync persisted camera & cursor coordinates back to parent
  useEffect(() => {
    onCameraChange?.({ lat: center.lat, lon: center.lon, zoomIdx });
  }, [center.lat, center.lon, zoomIdx, onCameraChange]);

  useEffect(() => {
    onCursorChange?.(cursorCoords);
  }, [cursorCoords, onCursorChange]);

  // Apply external focus-on-object navigation request with smooth camera pan & target pulse
  useEffect(() => {
    if (!focusTarget || focusTarget.token === lastAppliedFocusTokenRef.current) return;
    lastAppliedFocusTokenRef.current = focusTarget.token;
    suppressAutoCenterRef.current = true;
    setSelectedId(focusTarget.objectId);
    setCursorCoords({ lat: focusTarget.lat, lon: focusTarget.lon });
    setZoomIdx((prev) => Math.max(prev, 4));
    setFocusPulseId(focusTarget.objectId);

    if (focusPulseTimerRef.current !== null) {
      window.clearTimeout(focusPulseTimerRef.current);
    }
    focusPulseTimerRef.current = window.setTimeout(() => {
      focusPulseTimerRef.current = null;
      setFocusPulseId(null);
    }, 1550);

    if (focusAnimRafRef.current !== null) {
      window.cancelAnimationFrame(focusAnimRafRef.current);
      focusAnimRafRef.current = null;
    }

    const startLat = centerRef.current.lat;
    const startLon = centerRef.current.lon;
    const dLat = focusTarget.lat - startLat;
    const dLon = focusTarget.lon - startLon;
    const prefersReducedMotion =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (prefersReducedMotion || (Math.abs(dLat) < 0.00001 && Math.abs(dLon) < 0.00001)) {
      setCenter({ lat: focusTarget.lat, lon: focusTarget.lon });
      return;
    }

    const durationMs = 280;
    const startTime = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - startTime) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3);
      setCenter({
        lat: startLat + dLat * eased,
        lon: startLon + dLon * eased,
      });
      if (t < 1) {
        focusAnimRafRef.current = window.requestAnimationFrame(step);
      } else {
        focusAnimRafRef.current = null;
      }
    };
    focusAnimRafRef.current = window.requestAnimationFrame(step);
  }, [focusTarget]);

  useEffect(() => {
    return () => {
      if (focusAnimRafRef.current !== null) {
        window.cancelAnimationFrame(focusAnimRafRef.current);
      }
      if (focusPulseTimerRef.current !== null) {
        window.clearTimeout(focusPulseTimerRef.current);
      }
    };
  }, []);

  const autoCollapseTimerRef = useRef<number | null>(null);

  const collapseZoneHudForGesture = useCallback(() => {
    if (autoCollapseTimerRef.current !== null) {
      window.clearTimeout(autoCollapseTimerRef.current);
      autoCollapseTimerRef.current = null;
    }
    setIsAutoCollapsedForDraw(true);
  }, []);

  const scheduleZoneHudRestore = useCallback(() => {
    if (autoCollapseTimerRef.current !== null) {
      window.clearTimeout(autoCollapseTimerRef.current);
    }
    autoCollapseTimerRef.current = window.setTimeout(() => {
      autoCollapseTimerRef.current = null;
      setIsAutoCollapsedForDraw(false);
    }, 110);
  }, []);

  useEffect(() => {
    return () => {
      if (autoCollapseTimerRef.current !== null) {
        window.clearTimeout(autoCollapseTimerRef.current);
      }
    };
  }, []);

  // Center camera & reset gesture refs when entering or leaving Zone Editing Mode for a selection key
  useEffect(() => {
    if (autoCollapseTimerRef.current !== null) {
      window.clearTimeout(autoCollapseTimerRef.current);
      autoCollapseTimerRef.current = null;
    }
    isDrawingFreehandRef.current = false;
    freehandRawRef.current = [];
    const targetObj = zoneEditingObjectRef.current;
    if (!activeZoneEditKey || !targetObj) {
      return;
    }
    const effW =
      containerRef.current?.clientWidth ||
      (typeof window !== 'undefined' ? window.innerWidth : viewport.width);
    const effH =
      containerRef.current?.clientHeight ||
      (typeof window !== 'undefined' ? window.innerHeight : viewport.height);
    const mPerPx = ZOOM_METERS_PER_PX[zoomIdxRef.current] ?? 4.8;
    const isNarrowPortrait = effW < 640 && effH > effW;
    setCenter(
      isNarrowPortrait
        ? offsetCoordinate(
            targetObj.lat,
            targetObj.lon,
            -Math.round(effH * 0.16 * mPerPx),
            0
          )
        : { lat: targetObj.lat, lon: targetObj.lon }
    );
  }, [activeZoneEditKey]);

  // Initialize draft point & center camera when entering Point Move Mode
  useEffect(() => {
    if (!pointMoveTarget || !editorEnabled) {
      lastAppliedPointTokenRef.current = null;
      setDraftPoint(null);
      isDraggingPinRef.current = false;
      return;
    }
    if (pointMoveTarget.token === lastAppliedPointTokenRef.current) return;
    lastAppliedPointTokenRef.current = pointMoveTarget.token;
    suppressAutoCenterRef.current = true;
    setSelectedId(null);
    setDraftPoint({ lat: pointMoveTarget.lat, lon: pointMoveTarget.lon });
    setShiftZoneWithPoint(false);
    const targetZoomIdx = Math.max(zoomIdx, 4);
    const mPerPx = ZOOM_METERS_PER_PX[targetZoomIdx] ?? 2.4;
    const effW = containerRef.current?.clientWidth || (typeof window !== 'undefined' ? window.innerWidth : viewport.width);
    const effH = containerRef.current?.clientHeight || (typeof window !== 'undefined' ? window.innerHeight : viewport.height);
    const isNarrowPortrait = effW < 640 && effH > effW;
    const initialCam = isNarrowPortrait
      ? offsetCoordinate(pointMoveTarget.lat, pointMoveTarget.lon, -Math.round(effH * 0.16 * mPerPx), 0)
      : { lat: pointMoveTarget.lat, lon: pointMoveTarget.lon };
    setCenter(initialCam);
    setCursorCoords({ lat: pointMoveTarget.lat, lon: pointMoveTarget.lon });
    setZoomIdx((prev) => Math.max(prev, 4));
  }, [pointMoveTarget, editorEnabled, zoomIdx, viewport.width, viewport.height]);

  // Follow GPS fix automatically when settings.autoCenterGps is enabled
  useEffect(() => {
    if (!settings.autoCenterGps || zoneEditingObject || pointMoveTarget) return;
    if (dragStateRef.current.active || suppressAutoCenterRef.current) return;
    setCenter({ lat: gpsFix.lat, lon: gpsFix.lon });
  }, [gpsFix.lat, gpsFix.lon, settings.autoCenterGps, zoneEditingObject, pointMoveTarget]);

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
        const nextW = Math.round(rect.width);
        const nextH = Math.round(rect.height);
        setViewport((prev) =>
          prev.width === nextW && prev.height === nextH
            ? prev
            : { width: nextW, height: nextH }
        );
      }
    };
    updateSize();
    const observer = new ResizeObserver(updateSize);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // Render satellite tiles + procedural ortho-terrain fallback + tactical grid/contours
  // Decoupled from object zones, draft zones, marker selection, and GPS ticks to avoid full-canvas flicker.
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = syncCanvas2dContext(canvas, viewport.width, viewport.height, false);
    if (!ctx) return;

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

    // Metric grid lines (every 500m) when showMapGrid !== false
    if (settings.showMapGrid !== false) {
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
    }

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
  }, [
    viewport,
    center.lat,
    center.lon,
    metersPerPixel,
    projection,
    showContours,
    showSectors,
    settings.offlineSectors,
    settings.showMapGrid,
    isSatellite,
    tileTick,
  ]);

  const hiddenZoneObjectId = editorEnabled
    ? (zoneEditingObject?.id ?? pointMoveTarget?.objectId ?? null)
    : null;
  const hiddenMovingObjectId = isMovingPoint ? (pointMoveTarget?.objectId ?? null) : null;
  const draftZoneColorHex = useMemo(
    () =>
      zoneEditingObject
        ? resolveDraftZoneColorHex(draftZoneColor, zoneEditingObject.status)
        : '#5B9D72',
    [draftZoneColor, zoneEditingObject]
  );
  const handleSelectObjectMarker = useCallback((objectId: string) => {
    setSelectedId(objectId);
  }, []);

  const selectedObject = useMemo(
    () => objects.find((o) => o.id === selectedId) ?? null,
    [objects, selectedId]
  );

  const selectedObjectMetrics = useMemo(() => {
    if (!selectedObject) return null;
    const photos = getObjectPhotos(selectedObject);
    const distM = haversineDistanceM(gpsFix.lat, gpsFix.lon, selectedObject.lat, selectedObject.lon);
    const azDeg = Math.round(bearingDeg(gpsFix.lat, gpsFix.lon, selectedObject.lat, selectedObject.lon));
    const hasZone = Boolean(selectedObject.zone && selectedObject.zone.length >= 3);
    const zoneAreaM2 = hasZone && selectedObject.zone ? computePolygonAreaM2(selectedObject.zone) : 0;
    const zonePerimM = hasZone && selectedObject.zone ? computePolygonPerimeterM(selectedObject.zone) : 0;
    return {
      photos,
      distLabel: formatDistance(distM),
      azDeg,
      hasZone,
      zoneAreaLabel: hasZone ? formatArea(zoneAreaM2) : null,
      zonePerimLabel: hasZone ? formatDistance(zonePerimM) : null,
    };
  }, [selectedObject, gpsFix.lat, gpsFix.lon]);

  const nearbyHazardAlert = useMemo(() => {
    const threshold = settings.hazardProximityAlertM ?? 150;
    if (threshold <= 0) return null;
    let closest: { obj: UrbexObject; distM: number } | null = null;
    for (const obj of objects) {
      if (obj.status !== 'hazardous') continue;
      const distM = Math.round(haversineDistanceM(gpsFix.lat, gpsFix.lon, obj.lat, obj.lon));
      if (distM <= threshold && (!closest || distM < closest.distM)) {
        closest = { obj, distM };
      }
    }
    return closest;
  }, [objects, gpsFix.lat, gpsFix.lon, settings.hazardProximityAlertM]);

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    setSearchOpen(false);
    setToolsOpen(false);
    if (zoneEditingObject && zoneDrawTool === 'freehand') {
      e.currentTarget.setPointerCapture(e.pointerId);
      const rect = e.currentTarget.getBoundingClientRect();
      const pt = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
      isDrawingFreehandRef.current = true;
      setZoneStyleExpanded(false);
      collapseZoneHudForGesture();
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
      if (zoneEditingObject) {
        collapseZoneHudForGesture();
      }
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
      scheduleZoneHudRestore();
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
    if (wasMoved && zoneEditingObject) {
      scheduleZoneHudRestore();
    } else {
      setIsAutoCollapsedForDraw(false);
    }
    if (!wasMoved) {
      const rect = e.currentTarget.getBoundingClientRect();
      const clicked = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
      setCursorCoords(clicked);

      if (zoneEditingObject && zoneDrawTool === 'points') {
        setZoneStyleExpanded(false);
        setDraftZone((prev) => [...prev, clicked]);
        return;
      }

      if (isMovingPoint && pointMoveTarget) {
        setDraftPoint({
          lat: Number(clicked.lat.toFixed(6)),
          lon: Number(clicked.lon.toFixed(6)),
        });
        return;
      }

      if (!zoneEditingObject && !isMovingPoint && settings.showObjectZones !== false) {
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

  const showMapToast = useCallback((msg: string) => {
    if (toastTimerRef.current !== null) {
      window.clearTimeout(toastTimerRef.current);
    }
    setSectorCachedToast(msg);
    toastTimerRef.current = window.setTimeout(() => {
      toastTimerRef.current = null;
      setSectorCachedToast(null);
    }, 3200);
  }, []);

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
    showMapToast(`Офлайн-сектор сохранён (${sector.sizeMb} МБ)`);
  }, [center.lat, center.lon, onAddOfflineSector, showMapToast]);

  const handleSelectSearchResult = useCallback(
    (item: MapSearchResult) => {
      suppressAutoCenterRef.current = true;
      setSearchOpen(false);
      setToolsOpen(false);
      setCursorCoords({ lat: item.lat, lon: item.lon });
      setZoomIdx(item.zoomIdx);

      if (focusAnimRafRef.current !== null) {
        window.cancelAnimationFrame(focusAnimRafRef.current);
        focusAnimRafRef.current = null;
      }

      if (item.kind === 'object' && item.objectId) {
        setSearchTargetPin(null);
        setSelectedId(item.objectId);
        setFocusPulseId(item.objectId);
        if (focusPulseTimerRef.current !== null) {
          window.clearTimeout(focusPulseTimerRef.current);
        }
        focusPulseTimerRef.current = window.setTimeout(() => {
          focusPulseTimerRef.current = null;
          setFocusPulseId(null);
        }, 1550);
      } else {
        setSelectedId(null);
        setSearchTargetPin({
          title: item.title,
          subtitle: item.subtitle,
          lat: item.lat,
          lon: item.lon,
        });
      }

      const startLat = centerRef.current.lat;
      const startLon = centerRef.current.lon;
      const dLat = item.lat - startLat;
      const dLon = item.lon - startLon;
      const distM = haversineDistanceM(startLat, startLon, item.lat, item.lon);
      const prefersReducedMotion =
        typeof window !== 'undefined' &&
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

      if (prefersReducedMotion || distM > 25_000 || (Math.abs(dLat) < 0.00001 && Math.abs(dLon) < 0.00001)) {
        setCenter({ lat: item.lat, lon: item.lon });
      } else {
        const durationMs = 280;
        const startTime = performance.now();
        const step = (now: number) => {
          const t = Math.min(1, (now - startTime) / durationMs);
          const eased = 1 - Math.pow(1 - t, 3);
          setCenter({
            lat: startLat + dLat * eased,
            lon: startLon + dLon * eased,
          });
          if (t < 1) {
            focusAnimRafRef.current = window.requestAnimationFrame(step);
          } else {
            focusAnimRafRef.current = null;
          }
        };
        focusAnimRafRef.current = window.requestAnimationFrame(step);
      }

      showMapToast(`Переход: ${item.title}`);
    },
    [showMapToast]
  );

  const scaleBarMeters = Math.round(metersPerPixel * 96);

  return (
    <section
      aria-label="Спутниковая карта сектора"
      className="relative flex-1 w-full h-full min-h-[calc(100dvh-3rem)] [@media(orientation:landscape)_and_(min-width:560px)_and_(max-width:1023px)]:min-h-[calc(100dvh-2.5rem)] lg:min-h-[calc(100dvh-3.5rem)] bg-[#0A0D0C] overflow-clip select-none"
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
            setIsAutoCollapsedForDraw(false);
          }}
          onWheel={handleWheel}
          onPointerLeave={() => {
            if (!isDrawingFreehandRef.current) {
              dragStateRef.current.active = false;
              setIsAutoCollapsedForDraw(false);
            }
          }}
          className="w-full h-full block cursor-crosshair touch-none"
        />

        {/* Memoized Saved Zones Layer (isolated from editor draft & base map renders) */}
        <MapZonesLayer
          objects={objects}
          selectedId={selectedId}
          hiddenObjectId={hiddenZoneObjectId}
          showObjectZones={settings.showObjectZones !== false}
          projection={projection}
        />

        {/* Memoized Active Zone Draft & Point-Move Preview Layer */}
        <MapDraftZoneLayer
          isEditingZone={isEditingZone}
          draftZone={draftZone}
          draftZoneColorHex={draftZoneColorHex}
          draftZoneStyle={draftZoneStyle}
          draftZonePattern={draftZonePattern}
          isMovingPoint={isMovingPoint}
          pointMoveTarget={pointMoveTarget}
          draftPoint={draftPoint}
          shiftZoneWithPoint={shiftZoneWithPoint}
          projection={projection}
        />

        {/* Memoized GPS Reticle & Accuracy Layer */}
        <MapGpsLayer
          gpsLat={gpsFix.lat}
          gpsLon={gpsFix.lon}
          gpsAccuracyM={gpsFix.accuracyM}
          signalColorHex={gpsSignal.colorHex}
          signalFillRgba={gpsSignal.fillRgba}
          signalStrokeRgba={gpsSignal.strokeRgba}
          projection={projection}
        />

        {/* Memoized Object Markers Overlay */}
        <MapObjectMarkersLayer
          objects={objects}
          selectedId={selectedId}
          focusPulseId={focusPulseId}
          showObjectLabelsSetting={settings.showObjectLabels}
          zoomIdx={zoomIdx}
          projection={projection}
          hiddenMovingObjectId={hiddenMovingObjectId}
          interactionDisabled={Boolean(zoneEditingObject || isMovingPoint)}
          onSelectObject={handleSelectObjectMarker}
        />

        {/* Searched City / Coordinate Pin */}
        <AnimatePresence>
          {showNormalHud && searchTargetPin && (() => {
            const pt = latLonToPixel(searchTargetPin.lat, searchTargetPin.lon, projection);
            if (pt.x < -80 || pt.x > viewport.width + 80 || pt.y < -80 || pt.y > viewport.height + 80) {
              return null;
            }
            return (
              <div
                key={`pin-${searchTargetPin.lat}-${searchTargetPin.lon}`}
                data-testid="map-search-pin"
                style={{
                  transform: `translate3d(${Math.round(pt.x)}px, ${Math.round(pt.y)}px, 0) translate(-50%, -50%)`,
                }}
                className="absolute top-0 left-0 z-10 pointer-events-auto"
              >
                <motion.div
                  initial={{ opacity: 0, scale: 0.72, y: -4 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.72, y: -4 }}
                  transition={{ duration: 0.18, ease: EASE_OUT_TACTICAL }}
                  className="flex flex-col items-center"
                >
                  <div className="w-7 h-7 rounded-full border-2 border-[#6DB386] bg-[#131816]/90 flex items-center justify-center shadow-[0_0_16px_rgba(109,179,134,0.5)]">
                    <Crosshair className="w-3.5 h-3.5 text-[#6DB386]" />
                  </div>
                  <div className="mt-1 flex items-center gap-1 px-2 py-0.5 rounded bg-[#0C0F0E]/92 border border-[#5B9D72]/80 text-[10.5px] font-medium text-[#E6EBE8] whitespace-nowrap shadow">
                    <span>{searchTargetPin.title}</span>
                    <button
                      type="button"
                      onClick={() => setSearchTargetPin(null)}
                      aria-label="Убрать метку поиска"
                      className="text-[#98A49E] hover:text-[#E6EBE8] transition-colors"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                </motion.div>
              </div>
            );
          })()}
        </AnimatePresence>

        {/* Draggable Target Pin Overlay (Point Move Mode) */}
        {isMovingPoint && pointMoveTarget && draftPoint && (() => {
          const pinPos = latLonToPixel(draftPoint.lat, draftPoint.lon, projection);
          const effectiveIcon = getEffectiveObjectIcon(pointMoveTarget);
          const statusColor = STATUS_META[pointMoveTarget.status].colorHex;
          return (
            <div
              data-testid="point-move-draggable-pin"
              role="slider"
              aria-label={`Перетащите точку объекта ${pointMoveTarget.title}`}
              aria-valuetext={`${draftPoint.lat.toFixed(5)}, ${draftPoint.lon.toFixed(5)}`}
              tabIndex={0}
              onPointerDown={(e) => {
                e.stopPropagation();
                e.currentTarget.setPointerCapture(e.pointerId);
                isDraggingPinRef.current = true;
              }}
              onPointerMove={(e) => {
                if (!isDraggingPinRef.current || !containerRef.current) return;
                e.stopPropagation();
                const rect = containerRef.current.getBoundingClientRect();
                const pt = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
                const nextPt = {
                  lat: Number(pt.lat.toFixed(6)),
                  lon: Number(pt.lon.toFixed(6)),
                };
                setDraftPoint(nextPt);
                setCursorCoords(nextPt);
              }}
              onPointerUp={(e) => {
                if (isDraggingPinRef.current) {
                  e.stopPropagation();
                  isDraggingPinRef.current = false;
                }
              }}
              onPointerCancel={() => {
                isDraggingPinRef.current = false;
              }}
              style={{
                transform: `translate3d(${Math.round(pinPos.x)}px, ${Math.round(pinPos.y)}px, 0) translate(-50%, -50%)`,
              }}
              className="absolute top-0 left-0 z-20 w-11 h-11 flex items-center justify-center cursor-grab active:cursor-grabbing touch-none select-none"
            >
              <div
                className="w-11 h-11 rounded-full border-2 flex items-center justify-center bg-[#131816]/95 shadow-[0_0_20px_rgba(91,157,114,0.55)] ring-4 ring-[#5B9D72]/30"
                style={{ borderColor: statusColor }}
              >
                <ObjectGlyph icon={effectiveIcon} color={statusColor} className="w-5 h-5" />
              </div>
              <span className="pointer-events-none absolute top-full left-1/2 -translate-x-1/2 mt-1.5 px-2 py-0.5 rounded bg-[#0C0F0E]/92 border border-[#5B9D72] text-[10px] font-medium text-[#E6EBE8] whitespace-nowrap shadow">
                {pointMoveTarget.title || 'Новая точка'}
              </span>
            </div>
          );
        })()}
      </div>

      {/* Compact Normal Map HUD (hidden while editing object zone or object fields) */}
      <AnimatePresence>
        {showNormalHud && (
          <motion.div
            key="map-top-left-hud"
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            className="absolute top-2 left-2 right-[58px] sm:top-3 sm:left-3 sm:right-[72px] z-20 flex flex-wrap items-center gap-1.5 pointer-events-none"
          >
            <div className="pointer-events-auto max-w-full min-w-0 flex flex-wrap items-center gap-x-1.5 sm:gap-x-2 gap-y-0.5 px-2.5 py-1 rounded-lg bg-[#131816]/88 backdrop-blur-md border border-[#252E2A] text-[11px] sm:text-xs shadow-sm">
              <span className="font-mono tabular-nums text-[#E6EBE8] whitespace-nowrap">
                {settings.maskCoordinatesOnScreen
                  ? '••.•••••° · ••.•••••° [OPSEC]'
                  : formatCoordinates(cursorCoords.lat, cursorCoords.lon, settings.coordinateFormat)}
              </span>
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span className="text-[#98A49E] whitespace-nowrap font-mono tabular-nums">
                {scaleBarMeters} м
              </span>
              {settings.showCompassHud !== false && (
                <>
                  <span className="text-[#6B7771]" aria-hidden="true">·</span>
                  <span className="text-[#DF9E43] whitespace-nowrap font-mono tabular-nums">
                    HDG {sensorHeadingDeg ?? Math.round(gpsFix.headingDeg ?? 0)}°
                  </span>
                </>
              )}
              <span className="text-[#6B7771]" aria-hidden="true">·</span>
              <span
                data-testid="gps-signal-indicator"
                data-signal-level={gpsSignal.level}
                className="inline-flex items-center gap-1.5 font-medium whitespace-nowrap"
              >
                <span className="relative flex h-2 w-2 shrink-0" aria-hidden="true">
                  {isPollingGps && (
                    <span
                      style={{ backgroundColor: gpsSignal.colorHex }}
                      className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-75"
                    />
                  )}
                  <span
                    style={{ backgroundColor: gpsSignal.colorHex }}
                    className="relative inline-flex rounded-full h-2 w-2"
                  />
                </span>
                <span style={{ color: gpsSignal.colorHex }}>
                  {gpsFix.accuracyM !== null ? `±${gpsFix.accuracyM} м` : gpsSignal.label}
                </span>
              </span>
              {gpsError && (
                <span className="text-[#DF9E43] basis-full sm:basis-auto leading-snug text-[10px] sm:text-[11px]">
                  {gpsError}
                </span>
              )}
            </div>
            {settings.showCompassHud !== false && (() => {
              const compassDeg = sensorHeadingDeg ?? (((Math.round(gpsFix.headingDeg ?? 0) % 360) + 360) % 360);
              const dirs = ['С', 'СВ', 'В', 'ЮВ', 'Ю', 'ЮЗ', 'З', 'СЗ'];
              const cardinal = dirs[Math.floor((compassDeg + 22) / 45) % 8];
              return (
                <div
                  className="pointer-events-auto basis-full flex items-center"
                  aria-label="Мини-компас"
                >
                  <div className="inline-flex items-center gap-2 rounded-[18px] bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] shadow-[0_0_14px_rgba(91,157,114,0.18)] p-1.5 pr-2">
                    <div className="w-8 h-8 rounded-full bg-[#0C0F0E] border border-[#252E2A] flex items-center justify-center relative shrink-0">
                      <svg viewBox="0 0 36 36" className="w-7 h-7">
                        <circle cx="18" cy="18" r="14" fill="none" stroke="#252E2A" strokeWidth="1" />
                        <line x1="18" y1="2.5" x2="18" y2="6" stroke="#D96459" strokeWidth="1.8" strokeLinecap="round" />
                        <line x1="33.5" y1="18" x2="30" y2="18" stroke="#6B7771" strokeWidth="1.2" strokeLinecap="round" />
                        <line x1="18" y1="33.5" x2="18" y2="30" stroke="#6B7771" strokeWidth="1.2" strokeLinecap="round" />
                        <line x1="2.5" y1="18" x2="6" y2="18" stroke="#6B7771" strokeWidth="1.2" strokeLinecap="round" />
                        <g transform={`rotate(${compassDeg} 18 18)`}>
                          <polygon points="18,6 20.8,18 15.2,18" fill="#D96459" />
                          <polygon points="18,29 20.2,18 15.8,18" fill="#56635D" />
                          <circle cx="18" cy="18" r="1.8" fill="#E6EBE8" />
                        </g>
                      </svg>
                    </div>
                    <div className="w-[54px] leading-tight">
                      <div className="flex items-center gap-1 font-mono text-[11px] whitespace-nowrap">
                        <span className="font-bold text-[#6DB386]">{cardinal}</span>
                        <span className="font-semibold text-[#E6EBE8] tabular-nums">{compassDeg}°</span>
                      </div>
                      <div className="text-[9px] text-[#6B7771] whitespace-nowrap">С · Компас</div>
                    </div>
                  </div>
                </div>
              );
            })()}
            {nearbyHazardAlert && (
              <button
                type="button"
                onClick={() => setSelectedId(nearbyHazardAlert.obj.id)}
                className="pointer-events-auto px-2.5 py-1 rounded-lg bg-[#381B19]/92 border border-[#C7564E] text-[11px] font-medium text-[#F1ACA7] shadow-md flex items-center gap-1.5"
              >
                <span>✖</span>
                <span className="truncate max-w-[220px]">
                  Опасная зона ({nearbyHazardAlert.distM} м): {nearbyHazardAlert.obj.title}
                </span>
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Compact Top-Right Controls: Unified Zoom/GPS Capsule + Collapsible Map Tools Drawer */}
      <AnimatePresence>
        {showNormalHud && (
          <motion.div
            key="map-top-right-hud"
            initial={{ opacity: 0, x: 6 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            className="absolute right-2 sm:right-3 top-2 sm:top-3 z-20 flex flex-col items-end gap-1.5"
          >
            <div className="flex flex-col rounded-xl bg-[#131816]/88 backdrop-blur-md border border-[#252E2A] overflow-hidden divide-y divide-[#252E2A] shadow-md">
              <motion.button
                whileTap={{ scale: 0.92 }}
                type="button"
                onClick={() => setZoomIdx((z) => Math.min(ZOOM_METERS_PER_PX.length - 1, z + 1))}
                aria-label="Приблизить карту"
                className="min-h-[34px] min-w-[36px] lg:min-h-[38px] lg:min-w-[38px] flex items-center justify-center text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
              >
                <Plus className="w-4 h-4" />
              </motion.button>
              <motion.button
                whileTap={{ scale: 0.92 }}
                type="button"
                onClick={() => setZoomIdx((z) => Math.max(0, z - 1))}
                aria-label="Отдалить карту"
                className="min-h-[34px] min-w-[36px] lg:min-h-[38px] lg:min-w-[38px] flex items-center justify-center text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
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
                className="min-h-[34px] min-w-[36px] lg:min-h-[38px] lg:min-w-[38px] flex items-center justify-center text-[#6DB386] hover:bg-[#1A201E] transition-colors"
              >
                <LocateFixed className="w-4 h-4" />
              </motion.button>
            </div>

            <motion.button
              whileTap={{ scale: 0.93 }}
              type="button"
              data-testid="map-search-btn"
              onClick={() => {
                setToolsOpen(false);
                setSearchOpen((v) => !v);
              }}
              aria-label="Поиск по карте"
              aria-expanded={searchOpen}
              className={`min-h-[34px] min-w-[36px] lg:min-h-[38px] lg:min-w-[38px] px-2 flex items-center justify-center gap-1 rounded-xl backdrop-blur-md border shadow-md transition-colors ${
                searchOpen
                  ? 'bg-[#203328]/95 border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#131816]/88 border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72]'
              }`}
            >
              <Search className="w-4 h-4 text-[#6DB386]" />
            </motion.button>

            <motion.button
              whileTap={{ scale: 0.93 }}
              type="button"
              onClick={() => {
                setSearchOpen(false);
                setToolsOpen((v) => !v);
              }}
              aria-label="Инструменты карты"
              aria-expanded={toolsOpen}
              className={`min-h-[34px] min-w-[36px] lg:min-h-[38px] lg:min-w-[38px] px-2 flex items-center justify-center gap-1 rounded-xl backdrop-blur-md border shadow-md transition-colors ${
                toolsOpen
                  ? 'bg-[#203328]/95 border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#131816]/88 border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72]'
              }`}
            >
              <SlidersHorizontal className="w-4 h-4 text-[#DF9E43]" />
            </motion.button>

            <AnimatePresence>
              {toolsOpen && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.92, y: -4 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.92, y: -4 }}
                  transition={{ duration: 0.17, ease: EASE_OUT_TACTICAL }}
                  style={{ transformOrigin: 'top right' }}
                  data-testid="map-tools-panel"
                  className="w-60 sm:w-64 p-2.5 rounded-xl bg-[#131816]/95 backdrop-blur-md border border-[#252E2A] shadow-2xl space-y-2 text-xs"
                >
                  <div className="flex items-center justify-between gap-2 pb-1.5 border-b border-[#252E2A]">
                    <span className="text-[11px] font-semibold text-[#E6EBE8]">
                      Инструменты карты
                    </span>
                    <span className="text-[10px] font-mono text-[#98A49E]">
                      {POWER_PROFILE_META[settings.powerProfile].label}
                    </span>
                  </div>

                  <div className="space-y-1">
                    <button
                      type="button"
                      onClick={() => onChangeMapLayer(isSatellite ? 'offline_vector' : 'satellite')}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        {isSatellite ? (
                          <Globe className="w-3.5 h-3.5 text-[#6DB386]" />
                        ) : (
                          <MapIcon className="w-3.5 h-3.5 text-[#98A49E]" />
                        )}
                        <span>Слой карты</span>
                      </span>
                      <span className="font-mono text-[11px] text-[#6DB386]">
                        {isSatellite ? 'Спутник' : 'Вектор'}
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setShowContours((v) => !v)}
                      aria-pressed={showContours}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        <Layers className="w-3.5 h-3.5 text-[#6DB386]" />
                        <span>Изолинии рельефа</span>
                      </span>
                      <span className={`font-mono text-[11px] ${showContours ? 'text-[#6DB386]' : 'text-[#6B7771]'}`}>
                        {showContours ? 'Вкл' : 'Выкл'}
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setShowSectors((s) => !s)}
                      aria-pressed={showSectors}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        <Radar className="w-3.5 h-3.5 text-[#6DB386]" />
                        <span>Офлайн-секторы</span>
                      </span>
                      <span className={`font-mono text-[11px] ${showSectors ? 'text-[#6DB386]' : 'text-[#6B7771]'}`}>
                        {showSectors ? 'Вкл' : 'Выкл'}
                      </span>
                    </button>

                    {onToggleMapGrid && (
                      <button
                        type="button"
                        onClick={() => void onToggleMapGrid()}
                        aria-pressed={settings.showMapGrid !== false}
                        className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                      >
                        <span className="flex items-center gap-1.5">
                          <Grid className="w-3.5 h-3.5 text-[#6DB386]" />
                          <span>Сетка координат</span>
                        </span>
                        <span
                          className={`font-mono text-[11px] ${
                            settings.showMapGrid !== false ? 'text-[#6DB386]' : 'text-[#6B7771]'
                          }`}
                        >
                          {settings.showMapGrid !== false ? 'Вкл' : 'Выкл'}
                        </span>
                      </button>
                    )}
                  </div>

                  <div className="pt-1.5 border-t border-[#252E2A] space-y-1">
                    <button
                      type="button"
                      onClick={() => {
                        onRequestSingleFix();
                      }}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        <LocateFixed className="w-3.5 h-3.5 text-[#6DB386]" />
                        <span>{isPollingGps ? 'Опрос GPS...' : 'Запросить GPS'}</span>
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        onSetManualFix(cursorCoords.lat, cursorCoords.lon);
                        setToolsOpen(false);
                      }}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        <Navigation className="w-3.5 h-3.5 text-[#DF9E43]" />
                        <span>Фикс к курсору</span>
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={async () => {
                        await handleCacheCurrentSector();
                        setToolsOpen(false);
                      }}
                      className="w-full min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] flex items-center justify-between gap-2 text-[#E6EBE8]"
                    >
                      <span className="flex items-center gap-1.5">
                        <Download className="w-3.5 h-3.5 text-[#DF9E43]" />
                        <span>Кэшировать сектор</span>
                      </span>
                    </button>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Map City / Place / Coordinate Search Popover */}
      <AnimatePresence>
        {showNormalHud && searchOpen && (
          <MapSearchOverlay
            objects={objects}
            onSelectResult={handleSelectSearchResult}
            onClose={() => setSearchOpen(false)}
          />
        )}
      </AnimatePresence>

      {/* Interactive Zone Editor HUD (Unified Smooth Collapsible & Expandable Card) */}
      <AnimatePresence>
        {isEditingZone && !hideHud && zoneEditingObject ? (
          (() => {
            const previewHex =
              normalizeZoneColor(draftZoneColor) ??
              STATUS_META[zoneEditingObject.status].colorHex;
            const canSaveZone =
              !zoneSaving && (draftZone.length === 0 || draftZone.length >= 3);
            const effectiveZoneCollapsed = zoneHudCollapsed || isAutoCollapsedForDraw;
            const zoneMetricsInline =
              draftZone.length >= 3
                ? `${draftZone.length} тчк · ${formatArea(computePolygonAreaM2(draftZone))} · P=${formatDistance(computePolygonPerimeterM(draftZone))}`
                : `${draftZone.length} тчк`;

            const handleSaveZone = async () => {
              if (!onSaveObjectZone || !canSaveZone) return;
              setZoneSaving(true);
              try {
                await onSaveObjectZone(zoneEditingObject.id, {
                  zone: draftZone,
                  zoneColor: normalizeZoneColor(draftZoneColor),
                  zoneStyle: draftZoneStyle,
                  zonePattern: draftZonePattern,
                  zoneHatched: draftZonePattern !== 'none' && draftZonePattern !== 'no_fill',
                });
              } finally {
                setZoneSaving(false);
              }
            };

            return (
              <motion.div
                key="zone-editor-card"
                initial={{ opacity: 0, y: 14, scale: 0.985 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 14, scale: 0.985 }}
                transition={{ duration: 0.22, ease: EASE_OUT_TACTICAL }}
                data-testid={effectiveZoneCollapsed ? 'zone-editor-collapsed-bar' : 'zone-editor-hud'}
                className="absolute inset-x-3 bottom-[84px] [@media(orientation:landscape)_and_(min-width:560px)]:bottom-3 lg:bottom-4 [@media(orientation:landscape)_and_(min-width:560px)]:left-3 [@media(orientation:landscape)_and_(min-width:560px)]:right-auto [@media(orientation:landscape)_and_(min-width:560px)]:w-[430px] lg:left-4 lg:right-auto lg:w-[450px] z-30 bg-[#131816]/95 backdrop-blur-md border border-[#5B9D72] rounded-xl shadow-2xl px-2.5 sm:px-3 py-2 sm:py-2.5"
              >
                {/* Persistent Header Row (fixed left anchor, typography, and action height so title never jumps) */}
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1 flex items-center gap-2">
                    <span
                      className="w-2.5 h-2.5 rounded-full shrink-0 inline-block"
                      style={{ backgroundColor: previewHex }}
                    />
                    <div className="min-w-0 flex-1">
                      <div
                        data-testid="zone-hud-title"
                        className="text-xs sm:text-[13px] leading-tight font-semibold text-[#E6EBE8] truncate"
                      >
                        {zoneEditingObject.title}
                      </div>
                      <div
                        className={`text-[10.5px] leading-tight font-mono tabular-nums mt-0.5 transition-colors duration-150 ${
                          draftZone.length > 0 && draftZone.length < 3
                            ? 'text-[#DF9E43]'
                            : effectiveZoneCollapsed
                            ? 'text-[#6DB386]'
                            : 'text-[#98A49E]'
                        }`}
                      >
                        {zoneMetricsInline}
                      </div>
                    </div>
                  </div>

                  <div className="relative flex items-center justify-end shrink-0 h-[30px]">
                    <AnimatePresence mode="popLayout" initial={false}>
                      {effectiveZoneCollapsed ? (
                        <motion.div
                          key="header-actions-collapsed"
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.16, ease: EASE_OUT_TACTICAL }}
                          className="flex items-center gap-1.5 shrink-0 h-[30px]"
                        >
                          {draftZone.length > 0 && (
                            <button
                              type="button"
                              onClick={() => setDraftZone((prev) => prev.slice(0, -1))}
                              aria-label="Удалить последнюю точку"
                              className="h-[30px] w-[30px] rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72] flex items-center justify-center shrink-0"
                            >
                              <Undo2 className="w-3.5 h-3.5" />
                            </button>
                          )}
                          {!isAutoCollapsedForDraw && (
                            <>
                              <button
                                type="button"
                                disabled={!canSaveZone}
                                onClick={() => void handleSaveZone()}
                                aria-label="Сохранить зону"
                                className="h-[30px] w-[30px] rounded-lg bg-[#5B9D72] text-[#0C0F0E] hover:bg-[#6DB386] disabled:opacity-45 flex items-center justify-center shrink-0"
                              >
                                <Check className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  if (autoCollapseTimerRef.current !== null) {
                                    window.clearTimeout(autoCollapseTimerRef.current);
                                    autoCollapseTimerRef.current = null;
                                  }
                                  setZoneHudCollapsed(false);
                                  setIsAutoCollapsedForDraw(false);
                                }}
                                aria-label="Развернуть панель зоны"
                                className="h-[30px] px-2 rounded-lg bg-[#1A201E] border border-[#323E39] hover:border-[#5B9D72] text-[11px] font-medium text-[#E6EBE8] flex items-center gap-1 shrink-0"
                              >
                                <ChevronUp className="w-3.5 h-3.5 text-[#6DB386]" />
                                <span>Панель</span>
                              </button>
                            </>
                          )}
                        </motion.div>
                      ) : (
                        <motion.button
                          key="header-actions-expanded"
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.16, ease: EASE_OUT_TACTICAL }}
                          type="button"
                          onClick={() => setZoneHudCollapsed(true)}
                          aria-label="Свернуть панель зоны"
                          className="h-[30px] px-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] hover:border-[#5B9D72] text-[11px] text-[#98A49E] hover:text-[#E6EBE8] flex items-center gap-1 shrink-0"
                        >
                          <ChevronDown className="w-3.5 h-3.5" />
                          <span>Свернуть</span>
                        </motion.button>
                      )}
                    </AnimatePresence>
                  </div>
                </div>

                {/* Smoothly Collapsible & Expandable Body */}
                <AnimatePresence initial={false}>
                  {!effectiveZoneCollapsed && (
                    <motion.div
                      key="zone-hud-body"
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{
                        height: { duration: 0.25, ease: EASE_OUT_TACTICAL },
                        opacity: { duration: 0.17, ease: 'easeOut' },
                      }}
                      className="overflow-hidden"
                    >
                      <div className="pt-2.5 pb-0.5 space-y-2 max-h-[36dvh] sm:max-h-[40dvh] [@media(orientation:landscape)_and_(min-width:560px)]:max-h-[calc(100dvh-110px)] lg:max-h-[calc(100dvh-110px)] overflow-y-auto">

              <div className="flex items-center gap-1 sm:gap-1.5 overflow-x-auto pb-0.5">
                <button
                  type="button"
                  onClick={() => setZoneDrawTool('points')}
                  className={`min-h-[30px] px-2 sm:px-2.5 py-1 rounded-lg border text-[10.5px] sm:text-[11px] font-medium flex items-center gap-1 transition-colors shrink-0 whitespace-nowrap ${
                    zoneDrawTool === 'points'
                      ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
                  }`}
                >
                  <Crosshair className="w-3 h-3" />
                  <span>По точкам</span>
                </button>
                <button
                  type="button"
                  onClick={() => setZoneDrawTool('freehand')}
                  className={`min-h-[30px] px-2 sm:px-2.5 py-1 rounded-lg border text-[10.5px] sm:text-[11px] font-medium flex items-center gap-1 transition-colors shrink-0 whitespace-nowrap ${
                    zoneDrawTool === 'freehand'
                      ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
                  }`}
                >
                  <Pencil className="w-3 h-3" />
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
                  className="min-h-[30px] px-2 sm:px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[10.5px] sm:text-[11px] text-[#DF9E43] hover:border-[#5B9D72] shrink-0 whitespace-nowrap"
                >
                  <span className="sm:hidden">R={settings.defaultZoneRadiusM ?? 80}м</span>
                  <span className="hidden sm:inline">Круг {settings.defaultZoneRadiusM ?? 80}м</span>
                </button>
                <button
                  type="button"
                  onClick={() => setZoneStyleExpanded((v) => !v)}
                  aria-expanded={zoneStyleExpanded}
                  className={`min-h-[30px] px-2 sm:px-2.5 py-1 rounded-lg border text-[10.5px] sm:text-[11px] font-medium flex items-center gap-1.5 transition-colors shrink-0 whitespace-nowrap ml-auto ${
                    zoneStyleExpanded
                      ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#0C0F0E] border-[#252E2A] text-[#E6EBE8] hover:border-[#5B9D72]'
                  }`}
                >
                  <span className="hidden sm:inline-flex items-center gap-1.5">
                    <ZoneLinePreview style={draftZoneStyle} color={previewHex} />
                    <ZonePatternPreview pattern={draftZonePattern} color={previewHex} />
                  </span>
                  <span
                    className="w-2.5 h-2.5 sm:w-2 sm:h-2 rounded-full shrink-0"
                    style={{ backgroundColor: previewHex }}
                  />
                  <span>Стиль</span>
                  {zoneStyleExpanded ? (
                    <ChevronUp className="w-3 h-3 text-[#98A49E]" />
                  ) : (
                    <ChevronDown className="w-3 h-3 text-[#98A49E]" />
                  )}
                </button>
              </div>

              <AnimatePresence>
                {zoneStyleExpanded && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.15 }}
                    className="overflow-hidden"
                  >
                    <div className="p-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] space-y-2">
                      <div className="space-y-1">
                        <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider text-[#98A49E]">
                          <span>Линия границы</span>
                          <span className="text-[#E6EBE8] normal-case">
                            {ZONE_STYLE_LABELS[draftZoneStyle]}
                          </span>
                        </div>
                        <div className="flex items-center gap-1 overflow-x-auto pb-1">
                          {ZONE_STYLE_ORDER.map((st) => {
                            const active = draftZoneStyle === st;
                            return (
                              <button
                                key={st}
                                type="button"
                                onClick={() => setDraftZoneStyle(st)}
                                aria-pressed={active}
                                className={`min-h-[28px] px-2 py-0.5 rounded-lg border text-[11px] font-medium transition-colors flex items-center gap-1.5 shrink-0 ${
                                  active
                                    ? 'bg-[#1A201E] border-[#5B9D72] text-[#E6EBE8]'
                                    : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                                }`}
                              >
                                <ZoneLinePreview style={st} color={active ? previewHex : '#98A49E'} />
                                <span>{ZONE_STYLE_LABELS[st]}</span>
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      <div className="space-y-1">
                        <div className="flex items-center justify-between text-[10px] font-mono uppercase tracking-wider text-[#98A49E]">
                          <span>Стиль заливки зоны</span>
                          <span className="text-[#E6EBE8] normal-case">
                            {ZONE_PATTERN_LABELS[draftZonePattern]}
                          </span>
                        </div>
                        <div className="flex items-center gap-1 overflow-x-auto pb-1">
                          {ZONE_PATTERN_ORDER.map((pat) => {
                            const active = draftZonePattern === pat;
                            return (
                              <button
                                key={pat}
                                type="button"
                                onClick={() => setDraftZonePattern(pat)}
                                aria-pressed={active}
                                className={`min-h-[28px] px-2 py-0.5 rounded-lg border text-[11px] font-medium transition-colors flex items-center gap-1.5 shrink-0 ${
                                  active
                                    ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                                    : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                                }`}
                              >
                                <ZonePatternPreview
                                  pattern={pat}
                                  color={active ? previewHex : '#98A49E'}
                                />
                                <span>{ZONE_PATTERN_LABELS[pat]}</span>
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5 pt-0.5">
                        <button
                          type="button"
                          onClick={() => setDraftZoneColor('')}
                          aria-pressed={!draftZoneColor}
                          className={`min-h-[26px] px-2 py-0.5 rounded-lg border text-[10.5px] transition-colors shrink-0 ${
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
                              className={`w-5 h-5 rounded-full border-2 transition-transform shrink-0 ${
                                active
                                  ? 'border-[#E6EBE8] scale-110'
                                  : 'border-[#252E2A] hover:scale-105'
                              }`}
                              style={{ backgroundColor: preset.hex }}
                            />
                          );
                        })}
                        <label
                          title="Выбрать произвольный цвет"
                          className="flex items-center gap-1 px-1.5 py-0.5 rounded-lg border border-[#252E2A] bg-[#131816] text-[10px] font-mono text-[#98A49E] cursor-pointer hover:border-[#5B9D72] shrink-0"
                        >
                          <input
                            type="color"
                            value={previewHex}
                            onChange={(e) => setDraftZoneColor(e.target.value.toUpperCase())}
                            aria-label="Свой цвет зоны на карте"
                            className="w-4 h-4 bg-transparent border-0 p-0 cursor-pointer"
                          />
                          <span>{draftZoneColor || 'HEX'}</span>
                        </label>
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {draftZone.length > 0 && draftZone.length < 3 && (
                <div className="text-[10.5px] text-[#DF9E43]">
                  Добавьте ещё {3 - draftZone.length} тчк для замкнутой зоны.
                </div>
              )}

              <div className="flex items-center justify-between gap-2 pt-1.5 border-t border-[#252E2A]">
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    disabled={draftZone.length === 0}
                    onClick={() => setDraftZone((prev) => prev.slice(0, -1))}
                    aria-label="Удалить последнюю точку"
                    className="min-h-[32px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[11px] text-[#E6EBE8] disabled:opacity-40 flex items-center gap-1"
                  >
                    <Undo2 className="w-3.5 h-3.5" />
                    <span>Назад</span>
                  </button>
                  <button
                    type="button"
                    disabled={draftZone.length === 0}
                    onClick={() => setDraftZone([])}
                    aria-label="Очистить зону"
                    className="min-h-[32px] w-[32px] rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-[#F1ACA7] disabled:opacity-40 flex items-center justify-center"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={onCancelZoneEdit}
                    className="min-h-[32px] px-2.5 py-1 rounded-lg border border-[#252E2A] text-[11px] text-[#98A49E] hover:text-[#E6EBE8]"
                  >
                    Отмена
                  </button>
                  <button
                    type="button"
                    disabled={!canSaveZone}
                    onClick={() => void handleSaveZone()}
                    className="min-h-[32px] px-3 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-[11px] font-semibold hover:bg-[#6DB386] disabled:opacity-45 flex items-center gap-1"
                  >
                    <Check className="w-3.5 h-3.5" />
                    <span>{zoneSaving ? 'Сохранение...' : 'Сохранить'}</span>
                  </button>
                </div>
              </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.div>
            );
          })()
        ) : null}
      </AnimatePresence>
      {!isEditingZone && isMovingPoint && !hideHud && pointMoveTarget && draftPoint ? (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
          data-testid="point-move-hud"
          className="absolute inset-x-2.5 sm:inset-x-3 bottom-[76px] [@media(orientation:landscape)_and_(min-width:560px)]:bottom-3 lg:bottom-4 [@media(orientation:landscape)_and_(min-width:560px)]:left-3 [@media(orientation:landscape)_and_(min-width:560px)]:right-auto [@media(orientation:landscape)_and_(min-width:560px)]:w-[440px] lg:left-4 lg:right-auto lg:w-[460px] z-30 bg-[#131816]/95 backdrop-blur-md border border-[#5B9D72] rounded-xl p-2.5 sm:p-4 space-y-1.5 sm:space-y-2.5 shadow-2xl max-h-[calc(100%-24px)] overflow-y-auto"
        >
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-[#6DB386]">
                <Move className="w-3.5 h-3.5 shrink-0" />
                <span>Перемещение точки объекта</span>
              </div>
              <div className="flex items-center gap-1.5 text-sm font-semibold text-[#E6EBE8] truncate mt-0.5">
                <ObjectGlyph
                  icon={getEffectiveObjectIcon(pointMoveTarget)}
                  color={STATUS_META[pointMoveTarget.status].colorHex}
                  className="w-4 h-4 shrink-0"
                />
                <span className="truncate">{pointMoveTarget.title || 'Новая точка'}</span>
              </div>
            </div>
            <div className="text-right font-mono tabular-nums text-[11px] shrink-0">
              <div className="text-[#DF9E43]">
                Сдвиг:{' '}
                {formatDistance(
                  Math.round(
                    haversineDistanceM(
                      pointMoveTarget.lat,
                      pointMoveTarget.lon,
                      draftPoint.lat,
                      draftPoint.lon
                    )
                  )
                )}
              </div>
              <div className="text-[#98A49E]">
                {formatCoordinates(
                  draftPoint.lat,
                  draftPoint.lon,
                  settings.coordinateFormat,
                  Boolean(settings.maskCoordinatesOnScreen)
                )}
              </div>
            </div>
          </div>

          {/* Precision D-Pad & Step Size */}
          <div className="p-2 rounded-lg bg-[#0C0F0E] border border-[#252E2A] flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1">
              {([1, 5, 25] as const).map((step) => (
                <button
                  key={step}
                  type="button"
                  onClick={() => setNudgeStepM(step)}
                  aria-pressed={nudgeStepM === step}
                  className={`min-h-[30px] px-2 rounded border text-[11px] font-mono transition-colors ${
                    nudgeStepM === step
                      ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                      : 'bg-[#131816] border-[#252E2A] text-[#98A49E] hover:text-[#E6EBE8]'
                  }`}
                >
                  {step} м
                </button>
              ))}
            </div>

            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setDraftPoint((p) => (p ? offsetCoordinate(p.lat, p.lon, nudgeStepM, 0) : p))}
                aria-label={`Сдвинуть на ${nudgeStepM} м на север`}
                className="min-h-[32px] min-w-[32px] rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
              >
                <ArrowUp className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => setDraftPoint((p) => (p ? offsetCoordinate(p.lat, p.lon, -nudgeStepM, 0) : p))}
                aria-label={`Сдвинуть на ${nudgeStepM} м на юг`}
                className="min-h-[32px] min-w-[32px] rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
              >
                <ArrowDown className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => setDraftPoint((p) => (p ? offsetCoordinate(p.lat, p.lon, 0, -nudgeStepM) : p))}
                aria-label={`Сдвинуть на ${nudgeStepM} м на запад`}
                className="min-h-[32px] min-w-[32px] rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => setDraftPoint((p) => (p ? offsetCoordinate(p.lat, p.lon, 0, nudgeStepM) : p))}
                aria-label={`Сдвинуть на ${nudgeStepM} м на восток`}
                className="min-h-[32px] min-w-[32px] rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[#E6EBE8] flex items-center justify-center"
              >
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() =>
                  setDraftPoint({
                    lat: Number(center.lat.toFixed(6)),
                    lon: Number(center.lon.toFixed(6)),
                  })
                }
                title="Поместить точку в центр экрана"
                className="min-h-[32px] px-2.5 rounded-lg bg-[#131816] border border-[#252E2A] hover:border-[#5B9D72] text-[11px] font-medium text-[#E6EBE8] flex items-center gap-1"
              >
                <Crosshair className="w-3.5 h-3.5 text-[#6DB386]" />
                <span>По центру</span>
              </button>
            </div>
          </div>

          {pointMoveTarget.zone && pointMoveTarget.zone.length >= 3 && (
            <button
              type="button"
              onClick={() => setShiftZoneWithPoint((v) => !v)}
              aria-pressed={shiftZoneWithPoint}
              className={`w-full min-h-[34px] px-2.5 py-1 rounded-lg border text-xs font-medium flex items-center justify-between gap-2 transition-colors ${
                shiftZoneWithPoint
                  ? 'bg-[#203328] border-[#5B9D72] text-[#E6EBE8]'
                  : 'bg-[#0C0F0E] border-[#252E2A] text-[#98A49E]'
              }`}
            >
              <span className="flex items-center gap-1.5">
                <Pentagon className="w-3.5 h-3.5 text-[#6DB386]" />
                <span>Сдвигать зону вместе с точкой</span>
              </span>
              <span className={`font-mono text-[11px] ${shiftZoneWithPoint ? 'text-[#6DB386]' : 'text-[#6B7771]'}`}>
                {shiftZoneWithPoint ? 'Вкл' : 'Выкл'}
              </span>
            </button>
          )}

          <p className="hidden sm:block text-[11px] text-[#98A49E]">
            Перетащите маркер объекта, нажмите на карту или уточните стрелками.
          </p>

          <div className="flex flex-wrap items-center justify-between gap-2 pt-1.5 sm:pt-2 border-t border-[#252E2A]">
            <button
              type="button"
              onClick={() => setDraftPoint({ lat: pointMoveTarget.lat, lon: pointMoveTarget.lon })}
              className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#0C0F0E] border border-[#252E2A] text-xs text-[#E6EBE8] hover:border-[#5B9D72] flex items-center gap-1"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Сброс</span>
            </button>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onCancelPointMove}
                className="min-h-[36px] px-3 py-1 rounded-lg border border-[#252E2A] text-xs text-[#98A49E] hover:text-[#E6EBE8]"
              >
                Отмена
              </button>
              <button
                type="button"
                disabled={pointSaving}
                onClick={async () => {
                  if (!onSavePointMove) return;
                  setPointSaving(true);
                  try {
                    const dLat = draftPoint.lat - pointMoveTarget.lat;
                    const dLon = draftPoint.lon - pointMoveTarget.lon;
                    const nextZone =
                      shiftZoneWithPoint && pointMoveTarget.zone && pointMoveTarget.zone.length >= 3
                        ? translateZone(pointMoveTarget.zone, dLat, dLon)
                        : pointMoveTarget.zone;
                    await onSavePointMove({
                      lat: draftPoint.lat,
                      lon: draftPoint.lon,
                      zone: nextZone,
                      shiftedZone: Boolean(
                        shiftZoneWithPoint && pointMoveTarget.zone && pointMoveTarget.zone.length >= 3
                      ),
                    });
                  } finally {
                    setPointSaving(false);
                  }
                }}
                className="min-h-[36px] px-3.5 py-1 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] disabled:opacity-45"
              >
                {pointSaving
                  ? 'Сохранение...'
                  : pointMoveTarget.isModalDraft
                    ? 'Применить точку'
                    : 'Сохранить точку'}
              </button>
            </div>
          </div>
        </motion.div>
      ) : (
        <AnimatePresence>
          {showNormalHud && sectorCachedToast && (
            <motion.div
              initial={{ opacity: 0, y: 6, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 4, scale: 0.96 }}
              transition={{ duration: 0.18 }}
              className="absolute left-2 sm:left-3 bottom-[84px] [@media(orientation:landscape)_and_(min-width:560px)]:bottom-2.5 lg:bottom-3 z-20 px-3 py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-xs text-[#E6EBE8] shadow-md"
            >
              {sectorCachedToast}
            </motion.div>
          )}
        </AnimatePresence>
      )}

      {/* Selected Object Floating Card (Compact bottom sheet above dock on portrait mobile, bottom-right on landscape/desktop) */}
      <AnimatePresence>
        {showNormalHud && selectedObject && selectedObjectMetrics && (
          <motion.aside
            key={selectedObject.id}
            initial={{ opacity: 0, scale: 0.97, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            aria-label="Выбранный объект на карте"
            className="absolute inset-x-2 bottom-[78px] [@media(orientation:landscape)_and_(min-width:560px)]:inset-x-auto [@media(orientation:landscape)_and_(min-width:560px)]:right-3 [@media(orientation:landscape)_and_(min-width:560px)]:bottom-3 [@media(orientation:landscape)_and_(min-width:560px)]:w-[348px] lg:inset-x-auto lg:right-3 lg:bottom-3 lg:w-80 z-30 bg-[#131816]/95 backdrop-blur-md border border-[#252E2A] rounded-xl p-2.5 lg:p-4 space-y-2 lg:space-y-3 shadow-2xl max-h-[38dvh] [@media(orientation:landscape)_and_(min-width:560px)]:max-h-[calc(100%-24px)] lg:max-h-[calc(100%-24px)] overflow-y-auto"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-start gap-2.5 min-w-0 flex-1 lg:flex-col">
                <div className="relative shrink-0 lg:hidden">
                  <ObjectImage
                    src={selectedObjectMetrics.photos[0]}
                    alt={selectedObject.title}
                    fallbackIcon={getEffectiveObjectIcon(selectedObject)}
                    className={`${selectedObjectMetrics.photos.length > 0 ? 'w-16 h-16' : 'w-11 h-11'} rounded-lg border border-[#252E2A]`}
                  />
                  {selectedObjectMetrics.photos.length > 1 && (
                    <span className="pointer-events-none absolute bottom-1 right-1 px-1 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[9px] font-mono text-[#E6EBE8] leading-none">
                      {selectedObjectMetrics.photos.length}
                    </span>
                  )}
                </div>
                <div className="min-w-0 space-y-0.5 lg:space-y-1 flex-1">
                  <div className="flex items-center gap-1.5 text-[11px] lg:text-xs">
                    <ObjectGlyph
                      icon={getEffectiveObjectIcon(selectedObject)}
                      color={STATUS_META[selectedObject.status].colorHex}
                      className="w-3.5 h-3.5 shrink-0"
                    />
                    <span className={STATUS_META[selectedObject.status].textClass}>
                      {STATUS_META[selectedObject.status].label}
                    </span>
                    <span className="text-[#6B7771]" aria-hidden="true">·</span>
                    <span className="text-[#98A49E] truncate">
                      {CATEGORY_LABELS[selectedObject.category]}
                      {selectedObject.subtype ? ` · ${selectedObject.subtype}` : ''}
                    </span>
                  </div>
                  <h3 className="text-sm font-semibold text-[#E6EBE8] leading-snug line-clamp-1 lg:line-clamp-none">
                    {selectedObject.title}
                  </h3>
                  <div className="flex lg:hidden flex-wrap items-center gap-x-2.5 gap-y-0.5 text-[11px] font-mono tabular-nums text-[#98A49E]">
                    <span className="text-[#E6EBE8]">{selectedObjectMetrics.distLabel}</span>
                    <span className="text-[#DF9E43]">{selectedObjectMetrics.azDeg}°</span>
                    {selectedObject.securityLevel && selectedObject.securityLevel !== 'none' && (
                      <span className="font-sans text-[#DF9E43] truncate">
                        {SECURITY_LEVEL_LABELS[selectedObject.securityLevel]}
                      </span>
                    )}
                    {selectedObjectMetrics.zoneAreaLabel && (
                      <span className="text-[#6DB386] truncate">
                        S={selectedObjectMetrics.zoneAreaLabel} · P={selectedObjectMetrics.zonePerimLabel}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setSelectedId(null)}
                aria-label="Скрыть карточку объекта"
                className="min-h-[30px] min-w-[30px] lg:min-h-[36px] lg:min-w-[36px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="relative hidden lg:block">
              <ObjectImage
                src={selectedObjectMetrics.photos[0]}
                alt={selectedObject.title}
                fallbackIcon={getEffectiveObjectIcon(selectedObject)}
                className="w-full h-32 rounded-lg border border-[#252E2A]"
              />
              {selectedObjectMetrics.photos.length > 1 && (
                <span className="pointer-events-none absolute bottom-2 right-2 px-1.5 py-0.5 rounded bg-[#0C0F0E]/85 border border-[#252E2A] text-[10px] font-mono text-[#E6EBE8]">
                  {selectedObjectMetrics.photos.length} фото
                </span>
              )}
            </div>

            <div className="hidden lg:grid grid-cols-2 gap-2 text-xs font-mono tabular-nums text-[#98A49E]">
              <div>
                Дистанция:{' '}
                <span className="text-[#E6EBE8]">{selectedObjectMetrics.distLabel}</span>
              </div>
              <div>
                Азимут:{' '}
                <span className="text-[#E6EBE8]">{selectedObjectMetrics.azDeg}°</span>
              </div>
              {selectedObject.securityLevel && selectedObject.securityLevel !== 'none' && (
                <div className="col-span-2 font-sans text-[#DF9E43]">
                  Режим: {SECURITY_LEVEL_LABELS[selectedObject.securityLevel]}
                </div>
              )}
              {selectedObjectMetrics.zoneAreaLabel && (
                <div className="col-span-2 text-[#6DB386]">
                  Зона: {selectedObjectMetrics.zoneAreaLabel} · P={selectedObjectMetrics.zonePerimLabel}
                </div>
              )}
            </div>

            <p className="text-[11px] lg:text-xs text-[#98A49E] line-clamp-1 lg:line-clamp-2 leading-snug lg:leading-relaxed [@media(orientation:landscape)_and_(min-width:560px)]:hidden lg:block">
              {selectedObject.description}
            </p>

            <div className="flex flex-wrap gap-1.5 lg:gap-2">
              {editorEnabled && onMovePointForObject && (
                <button
                  type="button"
                  onClick={() => onMovePointForObject(selectedObject)}
                  className="min-h-[34px] lg:min-h-[44px] px-2.5 lg:px-3 py-1 lg:py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-[#E6EBE8] text-xs font-medium hover:bg-[#274032] transition-colors flex items-center justify-center gap-1.5 shrink-0"
                  title="Переместить точку объекта вручную"
                >
                  <Move className="w-3.5 h-3.5 text-[#6DB386]" />
                  <span>Точка</span>
                </button>
              )}
              {editorEnabled && onEditZoneForObject && (
                <button
                  type="button"
                  onClick={() => onEditZoneForObject(selectedObject)}
                  className="min-h-[34px] lg:min-h-[44px] px-2.5 lg:px-3 py-1 lg:py-2 rounded-lg bg-[#203328] border border-[#5B9D72] text-[#E6EBE8] text-xs font-medium hover:bg-[#274032] transition-colors flex items-center justify-center gap-1.5 shrink-0"
                  title="Задать или изменить зону на карте"
                >
                  <Pentagon className="w-3.5 h-3.5 text-[#6DB386]" />
                  <span>Зона</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => onOpenObjectInDatabase(selectedObject.id)}
                className="min-h-[34px] lg:min-h-[44px] flex-1 px-3 lg:px-4 py-1 lg:py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors whitespace-nowrap"
              >
                Открыть полностью
              </button>
            </div>
          </motion.aside>
        )}
      </AnimatePresence>
    </section>
  );
}
