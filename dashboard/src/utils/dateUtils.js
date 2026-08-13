import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

// The backend stores every timestamp via LocalDateTime.now() with no
// explicit zone — its container runs on UTC (confirmed against a real
// check-in: 06:14 UTC recorded for an actual 11:14am Pakistan action), and
// the JSON it returns has NO timezone suffix (e.g. "2026-07-17T06:14:24").
// That absence matters more than it looks: an offset-less ISO string handed
// to `new Date(...)` is parsed as LOCAL BROWSER time per the ECMAScript
// spec, not UTC — so naively doing `new Date(record.checkInTime)` doesn't
// just skip a conversion, it silently applies the WRONG one. Every read of
// a server timestamp in this app must go through parseServerUtc below,
// never straight into `new Date(...)`.
const KARACHI = 'Asia/Karachi';

/** Parses a naive backend timestamp (no offset) as the UTC value it actually is. */
function parseServerUtc(isoStringNoOffset) {
  return dayjs.utc(isoStringNoOffset);
}

/** Server timestamp -> formatted Pakistan-local string for display. */
export function formatUtcToKarachi(isoStringNoOffset, pattern = 'MMM D, YYYY h:mm A') {
  if (!isoStringNoOffset) return null;
  return parseServerUtc(isoStringNoOffset).tz(KARACHI).format(pattern);
}

/** Today's date, as Pakistan's calendar sees it right now — NOT the browser's. */
export function karachiToday() {
  return dayjs().tz(KARACHI);
}

export function karachiTodayDayCode() {
  const DAY_CODES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  return DAY_CODES[karachiToday().day()];
}

/**
 * A calendar date (e.g. "2026-07-18", picked in the UI) -> the UTC instant
 * that is midnight in Pakistan on that date, formatted as the naive
 * (no-offset) string the backend's date-range endpoints expect. This is
 * NOT the same as "start of day in the browser's own timezone" — it must
 * go through Asia/Karachi explicitly, or a range query silently shifts by
 * whatever the viewing machine's local offset happens to be.
 */
export function karachiStartOfDayIso(calendarDateString) {
  return dayjs.tz(calendarDateString, KARACHI).startOf('day').utc().format('YYYY-MM-DDTHH:mm:ss');
}

export function karachiEndOfDayIso(calendarDateString) {
  return dayjs.tz(calendarDateString, KARACHI).endOf('day').utc().format('YYYY-MM-DDTHH:mm:ss');
}
