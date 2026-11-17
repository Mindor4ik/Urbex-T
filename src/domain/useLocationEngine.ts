import { useCallback, useEffect, useRef, useState } from 'react';
import { DEFAULT_CENTER } from '../data/seed';
import type { GpsFix, PowerProfile } from '../core/types';

export interface LocationEngineState {
  fix: GpsFix;
  isPolling: boolean;
  gpsAvailable: boolean;
  errorMsg: string | null;
  requestSingleFix: () => void;
  setManualFix: (lat: number, lon: number) => void;
}

export function useLocationEngine(
  powerProfile: PowerProfile,
  intervalMs: number,
  activeModuleRequiresGps: boolean
): LocationEngineState {
  const [fix, setFix] = useState<GpsFix>(() => ({
    lat: DEFAULT_CENTER.lat,
    lon: DEFAULT_CENTER.lon,
    altitudeM: 142,
    accuracyM: 12,
    headingDeg: null,
    speedMs: null,
    timestamp: Date.now(),
    source: 'cached',
  }));
  const [isPolling, setIsPolling] = useState(false);
  const [gpsAvailable] = useState(() =>
    typeof navigator !== 'undefined' && 'geolocation' in navigator
  );
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const isTabVisibleRef = useRef(true);

  const requestSingleFix = useCallback(() => {
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
      setErrorMsg('Модуль геолокации недоступен в браузере — активен локальный опорный фикс.');
      return;
    }
    setIsPolling(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setFix({
          lat: Number(pos.coords.latitude.toFixed(6)),
          lon: Number(pos.coords.longitude.toFixed(6)),
          altitudeM: pos.coords.altitude !== null ? Math.round(pos.coords.altitude) : null,
          accuracyM: pos.coords.accuracy !== null ? Math.round(pos.coords.accuracy) : null,
          headingDeg: pos.coords.heading !== null ? Math.round(pos.coords.heading) : null,
          speedMs: pos.coords.speed,
          timestamp: pos.timestamp,
          source: 'gps',
        });
        setErrorMsg(null);
        setIsPolling(false);
      },
      () => {
        // Keep last valid/cached coordinate cleanly without throwing or spamming errors
        setErrorMsg('Сигнал GPS не получен; используется последняя сохранённая позиция.');
        setIsPolling(false);
      },
      {
        enableHighAccuracy: powerProfile === 'precision',
        maximumAge: powerProfile === 'eco' ? 30_000 : 8_000,
        timeout: 6_000,
      }
    );
  }, [powerProfile]);

  const setManualFix = useCallback((lat: number, lon: number) => {
    setFix({
      lat: Number(lat.toFixed(6)),
      lon: Number(lon.toFixed(6)),
      altitudeM: null,
      accuracyM: 1,
      headingDeg: null,
      speedMs: null,
      timestamp: Date.now(),
      source: 'manual',
    });
    setErrorMsg(null);
  }, []);

  useEffect(() => {
    const onVisibility = () => {
      isTabVisibleRef.current = document.visibilityState === 'visible';
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, []);

  // Adaptive power-aware polling: never runs continuously when manual, hidden, or off map/mesh
  useEffect(() => {
    if (powerProfile === 'manual' || intervalMs <= 0 || !activeModuleRequiresGps) {
      return;
    }
    const timer = window.setInterval(() => {
      if (isTabVisibleRef.current) {
        requestSingleFix();
      }
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [powerProfile, intervalMs, activeModuleRequiresGps, requestSingleFix]);

  return {
    fix,
    isPolling,
    gpsAvailable,
    errorMsg,
    requestSingleFix,
    setManualFix,
  };
}
