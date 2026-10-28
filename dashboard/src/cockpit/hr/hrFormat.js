import { MEANING } from '../tokens';

/**
 * HR states: one colour + label each, used the same way on the board, the
 * charts and the agent panel. Not due / off are deliberately grey: they are
 * not a problem, so they never compete with late and absent.
 */
export const STATE = {
  ON_TIME: { label: 'On time', color: MEANING.ok },
  PRESENT: { label: 'Present', color: MEANING.ok },
  LATE: { label: 'Late', color: MEANING.late },
  ABSENT: { label: 'Absent', color: MEANING.alert },
  NOT_DUE: { label: 'Not due yet', color: '#94A3B8' },
  OFF: { label: 'Off', color: '#CBD5E1' },
  HOLIDAY: { label: 'Holiday', color: '#CBD5E1' },
  NOT_JOINED: { label: 'Not joined yet', color: '#E2E8F0' },
  // Instead of ABSENT while the company's absence tracking is off: a fact, not a judgement.
  NO_CHECK_IN: { label: 'Not checked in', color: '#64748B' },
};

/** Seconds since midnight (Pakistan time) -> "9:42 AM". */
export function clock(seconds) {
  if (seconds == null) return '—';
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600) % 24;
  const m = Math.floor((s % 3600) / 60);
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, '0')} ${h >= 12 ? 'PM' : 'AM'}`;
}

/** "14:05" (Pakistan) -> "2:05 PM". */
export function hhmm(value) {
  if (!value) return '—';
  const [h, m] = value.split(':').map(Number);
  return clock(h * 3600 + m * 60);
}
