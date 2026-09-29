/**
 * Small in-memory TTL cache + in-flight de-dupe for read-mostly API calls
 * that the salesman/LMT flow re-fetches often within one shift (product
 * catalog, per-shop prices/discounts, the nearby-shops list) but that
 * rarely change server-side in that window. A local salesman/LMT opens the
 * product list fresh on EVERY shop visit today — dozens of times a day —
 * so caching turns most of those into an instant read instead of a full
 * network round trip.
 *
 * Memory-only: cleared on app restart, never persisted, and never used for
 * the GPS/geofence-verified submit calls themselves — only for read/preview
 * data shown before submitting. The server independently recomputes and
 * verifies everything that matters (price, discount, geofence) at submit
 * time regardless of what this cache is showing, so staleness here is a
 * display nuisance at worst, never a correctness or money risk.
 */
const store = new Map(); // key -> { data, expiresAt, inFlight }

export function cachedFetch(key, fetcher, ttlMs) {
  const now = Date.now();
  const entry = store.get(key);
  if (entry) {
    if (entry.data !== undefined && entry.expiresAt > now) {
      return Promise.resolve(entry.data);
    }
    if (entry.inFlight) {
      return entry.inFlight;
    }
  }
  const promise = fetcher()
    .then((data) => {
      store.set(key, { data, expiresAt: Date.now() + ttlMs });
      return data;
    })
    .catch((err) => {
      store.delete(key);
      throw err;
    });
  store.set(key, { ...(entry || {}), inFlight: promise });
  return promise;
}

/** Drops one entry (exact key) or every entry whose key starts with a prefix. Call after any action that could make a cached value wrong. */
export function clearCached(keyOrPrefix, { prefix = false } = {}) {
  if (!prefix) {
    store.delete(keyOrPrefix);
    return;
  }
  for (const key of store.keys()) {
    if (key.startsWith(keyOrPrefix)) store.delete(key);
  }
}
