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
  Plus,
  Radar,
  X,
} from 'lucide-react';
import {
  bearingDeg,
  formatCoordinates,
  formatDistance,
  haversineDistanceM,
  latLonToPixel,
  latToTileY,
  lonToTileX,
  pixelToLatLon,
  tileXToLon,
  tileYToLat,
} from '../../core/geo';
import { CATEGORY_LABELS, POWER_PROFILE_META, STATUS_META } from '../../core/labels';
import type {
  AppSettings,
  GpsFix,
  MapLayerMode,
  OfflineMapSector,
  UrbexObject,
} from '../../core/types';
import { ObjectImage } from '../../components/ObjectImage';

interface MapViewProps {
  objects: UrbexObject[];
  gpsFix: GpsFix;
  isPollingGps: boolean;
  gpsError: string | null;
  settings: AppSettings;
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

export default function MapView({
  objects,
  gpsFix,
  isPollingGps,
  gpsError,
  settings,
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
  const [center, setCenter] = useState({ lat: gpsFix.lat, lon: gpsFix.lon });
  const [zoomIdx, setZoomIdx] = useState(3); // ~4.8 m/px (Z15 satellite)
  const [showContours, setShowContours] = useState(true);
  const [showSectors, setShowSectors] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [tileTick, setTileTick] = useState(0);
  const [cursorCoords, setCursorCoords] = useState<{ lat: number; lon: number }>({
    lat: gpsFix.lat,
    lon: gpsFix.lon,
  });
  const [sectorCachedToast, setSectorCachedToast] = useState<string | null>(null);

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

  const metersPerPixel = ZOOM_METERS_PER_PX[zoomIdx] ?? 4.8;
  const isSatellite = settings.mapLayer !== 'offline_vector';

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

    // Current GPS / Operator Fix reticle
    const gpsPx = latLonToPixel(gpsFix.lat, gpsFix.lon, projection);
    const accPx = Math.max(10, Math.min(80, (gpsFix.accuracyM ?? 15) / metersPerPixel));
    ctx.fillStyle = 'rgba(91, 157, 114, 0.16)';
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, accPx, 0, Math.PI * 2);
    ctx.fill();

    ctx.strokeStyle = '#6DB386';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 7, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = '#E6EBE8';
    ctx.beginPath();
    ctx.arc(gpsPx.x, gpsPx.y, 2.5, 0, Math.PI * 2);
    ctx.fill();
  }, [
    viewport,
    center,
    metersPerPixel,
    projection,
    showContours,
    showSectors,
    settings.offlineSectors,
    gpsFix,
    isSatellite,
    tileTick,
  ]);

  const selectedObject = useMemo(
    () => objects.find((o) => o.id === selectedId) ?? null,
    [objects, selectedId]
  );

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
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
    const wasMoved = dragStateRef.current.moved;
    dragStateRef.current.active = false;
    if (!wasMoved) {
      const rect = e.currentTarget.getBoundingClientRect();
      const clicked = pixelToLatLon(e.clientX - rect.left, e.clientY - rect.top, projection);
      setCursorCoords(clicked);
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
      className="relative flex-1 w-full h-full min-h-[calc(100dvh-3rem)] sm:min-h-[calc(100dvh-3.5rem)] bg-[#0A0D0C] overflow-hidden select-none"
    >
      {/* Full-bleed Canvas Viewport */}
      <div ref={containerRef} className="absolute inset-0">
        <canvas
          ref={canvasRef}
          data-testid="offline-map-canvas"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onWheel={handleWheel}
          onPointerLeave={() => {
            dragStateRef.current.active = false;
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
          return (
            <button
              key={obj.id}
              type="button"
              onClick={() => setSelectedId(obj.id)}
              aria-label={`Объект: ${obj.title} (${meta.label})`}
              style={{
                transform: `translate3d(${Math.round(pos.x)}px, ${Math.round(pos.y)}px, 0) translate(-50%, -50%)`,
              }}
              className="absolute top-0 left-0 min-h-[44px] min-w-[44px] flex items-center justify-center group focus-visible:outline-none z-10"
            >
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
              </span>
            </button>
          );
        })}
      </div>

      {/* Floating Top-Left HUD: Telemetry & GPS Fix Actions */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2, ease: 'easeOut' }}
        className="absolute top-3 left-3 right-16 z-20 flex flex-wrap items-center gap-2 pointer-events-none"
      >
        <div className="pointer-events-auto flex flex-wrap items-center gap-2 px-3 py-1.5 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs">
          <span className="font-mono tabular-nums text-[#E6EBE8] whitespace-nowrap">
            {formatCoordinates(cursorCoords.lat, cursorCoords.lon, settings.coordinateFormat)}
          </span>
          <span className="text-[#6B7771]" aria-hidden="true">·</span>
          <span className="text-[#98A49E] whitespace-nowrap">
            {scaleBarMeters} м
          </span>
          <span className="hidden sm:inline text-[#6B7771]" aria-hidden="true">·</span>
          <span className="hidden sm:inline text-[#98A49E] whitespace-nowrap">
            GPS: {POWER_PROFILE_META[settings.powerProfile].label}
            {gpsFix.accuracyM !== null ? ` ±${gpsFix.accuracyM} м` : ''}
          </span>
          {gpsError && (
            <span className="text-[#DF9E43] whitespace-nowrap">{gpsError}</span>
          )}
        </div>

        <div className="pointer-events-auto flex items-center gap-1.5">
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={onRequestSingleFix}
            className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors whitespace-nowrap"
          >
            {isPollingGps ? 'Опрос...' : 'Запросить GPS'}
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={() => onSetManualFix(cursorCoords.lat, cursorCoords.lon)}
            className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1.5 whitespace-nowrap"
          >
            <Navigation className="w-3.5 h-3.5 text-[#D39239] shrink-0" />
            <span className="hidden sm:inline">Фикс к курсору</span>
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.94 }}
            type="button"
            onClick={() => onChangeMapLayer(isSatellite ? 'offline_vector' : 'satellite')}
            aria-label="Переключить слой карты"
            className="min-h-[36px] px-2.5 py-1 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:border-[#5B9D72] transition-colors flex items-center gap-1.5 whitespace-nowrap"
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
        className="absolute right-3 top-3 z-20 flex flex-col gap-1.5"
      >
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setZoomIdx((z) => Math.min(ZOOM_METERS_PER_PX.length - 1, z + 1))}
          aria-label="Приблизить карту"
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
        >
          <Plus className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setZoomIdx((z) => Math.max(0, z - 1))}
          aria-label="Отдалить карту"
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#E6EBE8] hover:bg-[#1A201E] transition-colors"
        >
          <Minus className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => {
            onRequestSingleFix();
            setCenter({ lat: gpsFix.lat, lon: gpsFix.lon });
          }}
          aria-label="Центрировать по GPS"
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-[#6DB386] hover:bg-[#1A201E] transition-colors"
        >
          <LocateFixed className="w-4 h-4" />
        </motion.button>
        <motion.button
          whileTap={{ scale: 0.92 }}
          type="button"
          onClick={() => setShowContours((v) => !v)}
          aria-label="Переключить векторные изолинии"
          aria-pressed={showContours}
          className={`min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg backdrop-blur-md border transition-colors ${
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
          className={`min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg backdrop-blur-md border transition-colors ${
            showSectors
              ? 'bg-[#203328]/95 border-[#5B9D72] text-[#E6EBE8]'
              : 'bg-[#131816]/90 border-[#252E2A] text-[#98A49E]'
          }`}
        >
          <Radar className="w-4 h-4" />
        </motion.button>
      </motion.div>

      {/* Bottom Action Strip (Left, cleared above floating cloud dock on mobile) */}
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2, ease: 'easeOut' }}
        className="absolute left-3 bottom-[84px] lg:bottom-3 z-20 flex flex-wrap items-center gap-2 pointer-events-none"
      >
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.95 }}
          type="button"
          onClick={() => onCreateAtCoords(cursorCoords.lat, cursorCoords.lon)}
          className="pointer-events-auto min-h-[44px] px-4 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors flex items-center gap-1.5 whitespace-nowrap shadow-md"
        >
          <Crosshair className="w-4 h-4" />
          <span>Отметить точку</span>
        </motion.button>
        <motion.button
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.95 }}
          type="button"
          onClick={handleCacheCurrentSector}
          className="pointer-events-auto min-h-[44px] px-3 py-2 rounded-lg bg-[#131816]/90 backdrop-blur-md border border-[#252E2A] text-xs font-medium text-[#E6EBE8] hover:bg-[#1A201E] transition-colors flex items-center gap-1.5 whitespace-nowrap shadow-md"
        >
          <Download className="w-4 h-4 text-[#D39239]" />
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

      {/* Selected Object Floating Card (Centered window on mobile, bottom-right on desktop) */}
      <AnimatePresence>
        {selectedObject && (
          <motion.aside
            key={selectedObject.id}
            initial={{ opacity: 0, scale: 0.97, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.97, y: 6 }}
            transition={{ duration: 0.16, ease: 'easeOut' }}
            aria-label="Выбранный объект на карте"
            className="absolute inset-x-4 top-1/2 -translate-y-1/2 sm:inset-x-auto sm:top-auto sm:translate-y-0 sm:right-3 sm:bottom-3 sm:w-80 z-30 bg-[#131816]/95 backdrop-blur-md border border-[#252E2A] rounded-xl p-4 space-y-3 shadow-2xl"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2 text-xs">
                  <span className={STATUS_META[selectedObject.status].textClass}>
                    {STATUS_META[selectedObject.status].glyph}{' '}
                    {STATUS_META[selectedObject.status].label}
                  </span>
                  <span className="text-[#6B7771]" aria-hidden="true">·</span>
                  <span className="text-[#98A49E] truncate">
                    {CATEGORY_LABELS[selectedObject.category]}
                  </span>
                </div>
                <h3 className="text-sm font-semibold text-[#E6EBE8] leading-snug">
                  {selectedObject.title}
                </h3>
              </div>

              <button
                type="button"
                onClick={() => setSelectedId(null)}
                aria-label="Скрыть карточку объекта"
                className="min-h-[36px] min-w-[36px] flex items-center justify-center rounded-lg text-[#98A49E] hover:text-[#E6EBE8] hover:bg-[#1A201E] shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <ObjectImage
              src={selectedObject.photoUrl}
              alt={selectedObject.title}
              className="w-full h-32 rounded-lg border border-[#252E2A]"
            />

            <div className="grid grid-cols-2 gap-2 text-xs font-mono tabular-nums text-[#98A49E]">
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
            </div>

            <p className="text-xs text-[#98A49E] line-clamp-2 leading-relaxed">
              {selectedObject.description}
            </p>

            <button
              type="button"
              onClick={() => onOpenObjectInDatabase(selectedObject.id)}
              className="min-h-[44px] w-full px-4 py-2 rounded-lg bg-[#5B9D72] text-[#0C0F0E] text-xs font-semibold hover:bg-[#6DB386] transition-colors"
            >
              Открыть на всю площадь
            </button>
          </motion.aside>
        )}
      </AnimatePresence>
    </section>
  );
}
