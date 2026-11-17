import type { CoordinateFormat } from './types';

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
  format: CoordinateFormat = 'dd'
): string {
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

