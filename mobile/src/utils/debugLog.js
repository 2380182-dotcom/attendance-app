/**
 * The one sanctioned way to log diagnostic output in this app. Internally
 * gated on __DEV__, so a call site never needs its own if (__DEV__) guard —
 * and can never accidentally ship to a production console/log reader by
 * omitting one. See the ESLint no-console rule (.eslintrc.js), which blocks
 * bare console.log so this is the only path left for ad hoc debugging.
 *
 * Usage: debugLog('FaceDiag', 'cropped input pixels', values);
 */
export function debugLog(tag, ...args) {
  if (__DEV__) {
    console.log(`[${tag}]`, ...args);
  }
}
