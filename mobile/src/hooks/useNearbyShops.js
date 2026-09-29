import { useCallback, useEffect, useRef, useState } from 'react';
import LocationService from '../services/LocationService';
import { apiService } from '../services/api';
import { cachedFetch, clearCached } from '../services/apiCache';

const GPS_TIMEOUT_MS = 25000; // the QR-scan location fetch has no timeout at all and routinely takes longer than 8s on real devices with a weak fix — match that tolerance instead of giving up early
const SLOW_GPS_MESSAGE_DELAY_MS = 4000;
const NEARBY_CACHE_KEY = 'nearby-shops';
const NEARBY_CACHE_TTL_MS = 60 * 1000; // brief — a salesman is stationary at one spot far longer than this while picking a shop

/**
 * Shared by NearbyShopsScreen (LMT) and LocalNearbyShopsScreen (Local) —
 * same server data, same slowness, same fix, one place to fix it in.
 *
 * Addresses the two suspected causes of "Finding nearby shops..." hanging:
 *  1. GPS: LocationService.getQuickLocation tries a cached last-known fix
 *     first and bounds any fresh fix to GPS_TIMEOUT_MS, instead of an
 *     unbounded Location.getCurrentPositionAsync call.
 *  2. Backend cold start: apiService.lmt.getNearbyShops retries with
 *     growing timeouts instead of failing once at 10s.
 * A brief 60s cache also makes "back, then reopen" instant.
 *
 * Returns `status` as a machine-readable {key, attempt, total} rather than
 * prose — the Local screen shows every message bilingually (English+Urdu)
 * and the LMT screen doesn't, so each screen renders its own wording from
 * this key instead of the hook hardcoding one language.
 */
export function useNearbyShops() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [shops, setShops] = useState([]);
  const [status, setStatus] = useState({ key: 'FINDING' });
  const slowGpsTimerRef = useRef(null);

  const clearSlowGpsTimer = () => {
    if (slowGpsTimerRef.current) {
      clearTimeout(slowGpsTimerRef.current);
      slowGpsTimerRef.current = null;
    }
  };

  const fetchNearbyShops = useCallback(async (forceRefresh = false) => {
    setLoading(true);
    setError(null);
    setStatus({ key: 'FINDING' });
    clearSlowGpsTimer();
    slowGpsTimerRef.current = setTimeout(() => setStatus({ key: 'SLOW_GPS' }), SLOW_GPS_MESSAGE_DELAY_MS);

    if (forceRefresh) {
      clearCached(NEARBY_CACHE_KEY);
    }

    try {
      let permission = await LocationService.getPermissionStatus();
      if (!permission.granted) {
        const requestResult = await LocationService.requestPermissions();
        if (!requestResult.success) {
          setError({ key: 'PERMISSION' });
          return;
        }
      }

      const { latitude, longitude } = await LocationService.getQuickLocation({ timeoutMs: GPS_TIMEOUT_MS });
      clearSlowGpsTimer();

      const nearby = await cachedFetch(
        NEARBY_CACHE_KEY,
        () =>
          apiService.lmt.getNearbyShops(latitude, longitude, {
            onRetry: (attempt, total) => setStatus({ key: 'SERVER_WAKING', attempt: attempt + 1, total }),
          }),
        NEARBY_CACHE_TTL_MS
      );
      setShops(nearby || []);
    } catch (e) {
      console.error(e);
      if (e.message === 'GPS_TIMEOUT') {
        setError({ key: 'GPS_TIMEOUT' });
      } else {
        setError({ key: 'OTHER', detail: e.message || 'Unable to find nearby shops.' });
      }
    } finally {
      clearSlowGpsTimer();
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNearbyShops();
    return clearSlowGpsTimer;
  }, [fetchNearbyShops]);

  return {
    loading,
    error,
    shops,
    status,
    refetch: () => fetchNearbyShops(true),
  };
}
