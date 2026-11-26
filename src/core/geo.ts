import type { CoordinateFormat, GeoPoint } from './types';

const EARTH_RADIUS_M = 6_371_000;

export function isValidCoordinate(lat: number, lon: number): boolean {
  return (
    Number.isFinite(lat) &&
    Number.isFinite(lon) &&
    lat >= -90 &&
    lat <= 90 &&
    lon >= -180 &&
    lon <= 180
  );
}

export function haversineDistanceM(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_RADIUS_M * c;
}

export function bearingDeg(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const toDeg = (rad: number) => (rad * 180) / Math.PI;
  const dLon = toRad(lon2 - lon1);
  const y = Math.sin(dLon) * Math.cos(toRad(lat2));
  const x =
    Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
    Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(dLon);
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

export function formatDistance(meters: number): string {
  if (!Number.isFinite(meters) || meters < 0) return '—';
  if (meters < 1000) {
    return `${Math.round(meters)} м`;
  }
  return `${(meters / 1000).toFixed(2)} км`;
}

function toDmsComponent(absDeg: number): { d: number; m: number; s: string } {
  const d = Math.floor(absDeg);
  const minFloat = (absDeg - d) * 60;
  const m = Math.floor(minFloat);
  const s = ((minFloat - m) * 60).toFixed(1);
  return { d, m, s };
}

function toUtmApproximate(lat: number, lon: number): string {
  const zone = Math.floor((lon + 180) / 6) + 1;
  const bandLetters = 'CDEFGHJKLMNPQRSTUVWX';
  const bandIdx = Math.max(
    0,
    Math.min(bandLetters.length - 1, Math.floor((lat + 80) / 8))
  );
  const band = bandLetters[bandIdx] ?? 'N';
  const centralMeridian = (zone - 1) * 6 - 180 + 3;
  const radLat = (lat * Math.PI) / 180;
  const radLonDelta = ((lon - centralMeridian) * Math.PI) / 180;
  const easting = Math.round(
    500_000 + EARTH_RADIUS_M * Math.cos(radLat) * radLonDelta
  );
  const northingRaw = Math.round(EARTH_RADIUS_M * radLat);
  const northing = lat >= 0 ? northingRaw : 10_000_000 + northingRaw;
  return `${zone}${band} ${String(easting).padStart(6, '0')}E ${String(northing).padStart(7, '0')}N`;
}

export function formatCoordinates(
  lat: number,
  lon: number,
  format: CoordinateFormat = 'dd',
  maskOnScreen = false
): string {
  if (maskOnScreen) {
    return '••.•••••° · ••.•••••° [OPSEC]';
  }
  if (!isValidCoordinate(lat, lon)) {
    return 'Нет координат';
  }
  if (format === 'dms') {
    const latDir = lat >= 0 ? 'N' : 'S';
    const lonDir = lon >= 0 ? 'E' : 'W';
    const latDms = toDmsComponent(Math.abs(lat));
    const lonDms = toDmsComponent(Math.abs(lon));
    return `${latDms.d}°${String(latDms.m).padStart(2, '0')}'${latDms.s}"${latDir} ${lonDms.d}°${String(lonDms.m).padStart(2, '0')}'${lonDms.s}"${lonDir}`;
  }
  if (format === 'utm') {
    return toUtmApproximate(lat, lon);
  }
  const latHem = lat >= 0 ? 'N' : 'S';
  const lonHem = lon >= 0 ? 'E' : 'W';
  return `${Math.abs(lat).toFixed(5)}° ${latHem}, ${Math.abs(lon).toFixed(5)}° ${lonHem}`;
}

export interface ViewportProjection {
  centerLat: number;
  centerLon: number;
  metersPerPixel: number;
  width: number;
  height: number;
}

export function latLonToPixel(
  lat: number,
  lon: number,
  proj: ViewportProjection
): { x: number; y: number } {
  const metersPerDegLat = 111_320;
  const metersPerDegLon =
    111_320 * Math.max(0.1, Math.cos((proj.centerLat * Math.PI) / 180));
  const dxMeters = (lon - proj.centerLon) * metersPerDegLon;
  const dyMeters = (proj.centerLat - lat) * metersPerDegLat;
  return {
    x: proj.width / 2 + dxMeters / proj.metersPerPixel,
    y: proj.height / 2 + dyMeters / proj.metersPerPixel,
  };
}

export function pixelToLatLon(
  x: number,
  y: number,
  proj: ViewportProjection
): { lat: number; lon: number } {
  const metersPerDegLat = 111_320;
  const metersPerDegLon =
    111_320 * Math.max(0.1, Math.cos((proj.centerLat * Math.PI) / 180));
  const dxMeters = (x - proj.width / 2) * proj.metersPerPixel;
  const dyMeters = (y - proj.height / 2) * proj.metersPerPixel;
  const lat = Math.max(-89.9, Math.min(89.9, proj.centerLat - dyMeters / metersPerDegLat));
  const lon = Math.max(-179.9, Math.min(179.9, proj.centerLon + dxMeters / metersPerDegLon));
  return {
    lat: Number(lat.toFixed(6)),
    lon: Number(lon.toFixed(6)),
  };
}

export function lonToTileX(lon: number, zoom: number): number {
  return ((lon + 180) / 360) * Math.pow(2, zoom);
}

export function latToTileY(lat: number, zoom: number): number {
  const rad = (Math.max(-85.0511, Math.min(85.0511, lat)) * Math.PI) / 180;
  return (
    ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) *
    Math.pow(2, zoom)
  );
}

export function tileXToLon(x: number, zoom: number): number {
  return (x / Math.pow(2, zoom)) * 360 - 180;
}

export function tileYToLat(y: number, zoom: number): number {
  const n = Math.PI - (2 * Math.PI * y) / Math.pow(2, zoom);
  return (180 / Math.PI) * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
}

export type GpsSignalLevel = 'good' | 'moderate' | 'poor';

export interface GpsSignalQuality {
  level: GpsSignalLevel;
  label: string;
  colorHex: string;
  fillRgba: string;
  strokeRgba: string;
}

export function getGpsSignalQuality(
  accuracyM: number | null,
  hasError = false
): GpsSignalQuality {
  if (hasError || accuracyM === null || !Number.isFinite(accuracyM) || accuracyM <= 0) {
    return {
      level: 'poor',
      label: 'Слабый / нет',
      colorHex: '#DC6860',
      fillRgba: 'rgba(220, 104, 96, 0.20)',
      strokeRgba: 'rgba(220, 104, 96, 0.82)',
    };
  }
  if (accuracyM <= 15) {
    return {
      level: 'good',
      label: 'Точный',
      colorHex: '#6DB386',
      fillRgba: 'rgba(109, 179, 134, 0.18)',
      strokeRgba: 'rgba(109, 179, 134, 0.80)',
    };
  }
  if (accuracyM <= 45) {
    return {
      level: 'moderate',
      label: 'Средний',
      colorHex: '#DF9E43',
      fillRgba: 'rgba(223, 158, 67, 0.18)',
      strokeRgba: 'rgba(223, 158, 67, 0.80)',
    };
  }
  return {
    level: 'poor',
    label: 'Низкая',
    colorHex: '#DC6860',
    fillRgba: 'rgba(220, 104, 96, 0.20)',
    strokeRgba: 'rgba(220, 104, 96, 0.82)',
  };
}

export function computePolygonPerimeterM(points: GeoPoint[]): number {
  if (points.length < 2) return 0;
  let total = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    if (points.length === 2 && i === 1) break;
    total += haversineDistanceM(a.lat, a.lon, b.lat, b.lon);
  }
  return total;
}

export function computePolygonAreaM2(points: GeoPoint[]): number {
  if (points.length < 3) return 0;
  const avgLat = points.reduce((sum, p) => sum + p.lat, 0) / points.length;
  const metersPerDegLat = 111_320;
  const metersPerDegLon = 111_320 * Math.max(0.1, Math.cos((avgLat * Math.PI) / 180));
  const originLat = points[0].lat;
  const originLon = points[0].lon;

  let area2 = 0;
  for (let i = 0; i < points.length; i++) {
    const p1 = points[i];
    const p2 = points[(i + 1) % points.length];
    const x1 = (p1.lon - originLon) * metersPerDegLon;
    const y1 = (p1.lat - originLat) * metersPerDegLat;
    const x2 = (p2.lon - originLon) * metersPerDegLon;
    const y2 = (p2.lat - originLat) * metersPerDegLat;
    area2 += x1 * y2 - x2 * y1;
  }
  return Math.abs(area2) / 2;
}

export function formatArea(areaM2: number): string {
  if (!Number.isFinite(areaM2) || areaM2 <= 0) return '0 м²';
  if (areaM2 < 10_000) {
    return `${Math.round(areaM2)} м²`;
  }
  return `${(areaM2 / 10_000).toFixed(2)} га`;
}

export function isPointInPolygon(lat: number, lon: number, polygon: GeoPoint[]): boolean {
  if (polygon.length < 3) return false;
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const xi = polygon[i].lon;
    const yi = polygon[i].lat;
    const xj = polygon[j].lon;
    const yj = polygon[j].lat;
    const intersect =
      yi > lat !== yj > lat &&
      lon < ((xj - xi) * (lat - yi)) / (yj - yi + 1e-12) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

export function buildZoneCircle(
  centerLat: number,
  centerLon: number,
  radiusMeters: number,
  steps = 16
): GeoPoint[] {
  const safeR = Math.max(10, Math.min(5_000, radiusMeters));
  const metersPerDegLat = 111_320;
  const metersPerDegLon = 111_320 * Math.max(0.1, Math.cos((centerLat * Math.PI) / 180));
  const dLat = safeR / metersPerDegLat;
  const dLon = safeR / metersPerDegLon;
  const pts: GeoPoint[] = [];
  for (let i = 0; i < steps; i++) {
    const angle = (2 * Math.PI * i) / steps;
    pts.push({
      lat: Number((centerLat + dLat * Math.sin(angle)).toFixed(6)),
      lon: Number((centerLon + dLon * Math.cos(angle)).toFixed(6)),
    });
  }
  return pts;
}

export function simplifyPolygonPoints(
  points: GeoPoint[],
  minSpacingM = 8,
  maxPoints = 48
): GeoPoint[] {
  if (points.length <= 3) return points;
  const filtered: GeoPoint[] = [points[0]];
  for (let i = 1; i < points.length; i++) {
    const prev = filtered[filtered.length - 1];
    if (haversineDistanceM(prev.lat, prev.lon, points[i].lat, points[i].lon) >= minSpacingM) {
      filtered.push(points[i]);
    }
  }
  if (filtered.length <= maxPoints) return filtered;
  const step = Math.ceil(filtered.length / maxPoints);
  return filtered.filter((_, idx) => idx % step === 0).slice(0, maxPoints);
}

export function offsetCoordinate(
  lat: number,
  lon: number,
  northMeters: number,
  eastMeters: number
): GeoPoint {
  const metersPerDegLat = 111_320;
  const metersPerDegLon = 111_320 * Math.max(0.1, Math.cos((lat * Math.PI) / 180));
  const nextLat = Math.max(-89.999999, Math.min(89.999999, lat + northMeters / metersPerDegLat));
  let nextLon = lon + eastMeters / metersPerDegLon;
  if (nextLon > 180) nextLon -= 360;
  if (nextLon < -180) nextLon += 360;
  return {
    lat: Number(nextLat.toFixed(6)),
    lon: Number(nextLon.toFixed(6)),
  };
}

export function translateZone(
  zone: GeoPoint[] | undefined,
  dLat: number,
  dLon: number
): GeoPoint[] | undefined {
  if (!zone || zone.length === 0) return zone;
  return zone.map((pt) => {
    const nextLat = Math.max(-89.999999, Math.min(89.999999, pt.lat + dLat));
    let nextLon = pt.lon + dLon;
    if (nextLon > 180) nextLon -= 360;
    if (nextLon < -180) nextLon += 360;
    return {
      lat: Number(nextLat.toFixed(6)),
      lon: Number(nextLon.toFixed(6)),
    };
  });
}

export const MAP_LABEL_MIN_ZOOM_IDX = 3;
export const MAP_LABEL_MIN_TILE_ZOOM = 13.2;

export function shouldShowMapObjectLabels(
  showObjectLabelsSetting: boolean | undefined,
  zoom: number
): boolean {
  return showObjectLabelsSetting !== false && Number.isFinite(zoom) && zoom >= MAP_LABEL_MIN_TILE_ZOOM;
}

export function shouldShowMapObjectLabelsByZoomIdx(
  showObjectLabelsSetting: boolean | undefined,
  zoomIdx: number
): boolean {
  return showObjectLabelsSetting !== false && Number.isFinite(zoomIdx) && zoomIdx >= MAP_LABEL_MIN_ZOOM_IDX;
}
