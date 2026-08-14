import dayjs from 'dayjs';
import { DAY_CODES, karachiDateOf, karachiToday } from './dateUtils';

/** Ranks agents by LATE occurrences within a set of attendance records (any date range). */
export function computeChronicLateComers(records, limit = 10) {
  const counts = new Map();
  records.forEach((r) => {
    if (r.status !== 'LATE') return;
    const entry = counts.get(r.agentId) || { agentId: r.agentId, agentName: r.agentName, lateCount: 0 };
    entry.lateCount += 1;
    counts.set(r.agentId, entry);
  });
  return Array.from(counts.values()).sort((a, b) => b.lateCount - a.lateCount).slice(0, limit);
}

/**
 * Ranks agents by absence count over a date range — no backend endpoint for
 * this, computed the same way as the Today's Board absentee diff, just
 * repeated per day: an active agent has an "absence" on any of their own
 * working days where no attendance record exists at all. Skips days before
 * the agent existed (createdAt) so a recently-hired agent isn't flagged for
 * days before they could possibly have checked in.
 */
export function computeMostAbsences(records, activeAgents, startDate, endDate, limit = 10) {
  const recordedDayKeys = new Set(records.map((r) => `${r.agentId}|${karachiDateOf(r.checkInTime)}`));
  const today = karachiToday();
  const lastDay = endDate.isAfter(today, 'day') ? today : endDate;

  const counts = new Map();
  let cursor = startDate;
  while (!cursor.isAfter(lastDay, 'day')) {
    const dayCode = DAY_CODES[cursor.day()];
    const dateStr = cursor.format('YYYY-MM-DD');

    activeAgents.forEach((agent) => {
      const isWorkingDay = !agent.workingDays || agent.workingDays.length === 0 || agent.workingDays.includes(dayCode);
      if (!isWorkingDay) return;
      if (agent.createdAt && dayjs(agent.createdAt).isAfter(cursor, 'day')) return; // didn't exist yet

      const key = `${agent.id}|${dateStr}`;
      if (recordedDayKeys.has(key)) return;

      const entry = counts.get(agent.id) || { agentId: agent.id, agentName: agent.name, absenceCount: 0 };
      entry.absenceCount += 1;
      counts.set(agent.id, entry);
    });

    cursor = cursor.add(1, 'day');
  }
  return Array.from(counts.values()).sort((a, b) => b.absenceCount - a.absenceCount).slice(0, limit);
}

/** Check-ins per day across the range, for the attendance-trend chart. */
export function computeAttendanceTrend(records) {
  const counts = new Map();
  records.forEach((r) => {
    const dateStr = karachiDateOf(r.checkInTime);
    if (!dateStr) return;
    counts.set(dateStr, (counts.get(dateStr) || 0) + 1);
  });
  return Array.from(counts.entries())
    .map(([date, count]) => ({ date, count }))
    .sort((a, b) => a.date.localeCompare(b.date));
}
