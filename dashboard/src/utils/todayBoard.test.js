import { describe, expect, it } from 'vitest';
import { absentTodayCard, buildTodayRows, countByStatus, statusOptions, STATUS_LABEL } from './todayBoard';

const EVERY_DAY = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const agents = [
  { id: 1, agentId: 'A1', name: 'Came In', department: 'Bakery', workingDays: EVERY_DAY },
  { id: 2, agentId: 'A2', name: 'Came Late', department: 'Bakery', workingDays: EVERY_DAY },
  { id: 3, agentId: 'A3', name: 'Not In', department: 'Bakery', workingDays: EVERY_DAY },
  { id: 4, agentId: 'A4', name: 'Day Off', department: 'Bakery', workingDays: ['TUE'] },
  { id: 5, agentId: 'A5', name: 'No Days Set', department: 'Bakery', workingDays: [] },
];
const report = [
  { attendance: { agentId: 1, status: 'IN', checkInTime: '2026-10-26T04:00:00' }, lateMinutes: 0, faceVerified: true },
  { attendance: { agentId: 2, status: 'LATE', checkInTime: '2026-10-26T05:00:00' }, lateMinutes: 30, faceVerified: true },
];
const byName = (rows) => Object.fromEntries(rows.map((r) => [r.name, r.status]));

describe('Today board with absence tracking ON', () => {
  const rows = buildTodayRows(report, agents, 'MON', true);

  it('marks agents with no check-in on a working day ABSENT', () => {
    expect(byName(rows)).toEqual({
      'Came In': 'IN', 'Came Late': 'LATE', 'Not In': 'ABSENT', 'Day Off': 'OFF', 'No Days Set': 'ABSENT',
    });
  });

  it('counts absences', () => {
    expect(countByStatus(rows)).toEqual({ IN: 1, LATE: 1, ABSENT: 2, NO_CHECK_IN: 0, OFF: 1 });
  });

  it('shows the absent number on HR Analytics', () => {
    expect(absentTodayCard(countByStatus(rows), rows.length, true)).toEqual({ value: '40%', sub: '2 agents', clickable: true });
    expect(statusOptions(true)).toContain('ABSENT');
  });
});

describe('Today board with absence tracking OFF', () => {
  const rows = buildTodayRows(report, agents, 'MON', false);

  it('never calls anyone absent: "Not checked in" instead', () => {
    expect(byName(rows)).toEqual({
      'Came In': 'IN', 'Came Late': 'LATE', 'Not In': 'NO_CHECK_IN', 'Day Off': 'OFF', 'No Days Set': 'NO_CHECK_IN',
    });
    expect(STATUS_LABEL.NO_CHECK_IN).toBe('Not checked in');
  });

  it('has no absent count', () => {
    const counts = countByStatus(rows);
    expect(counts.ABSENT).toBe(0);
    expect(counts.NO_CHECK_IN).toBe(2);
  });

  it('shows no absent number on HR Analytics and no Absent filter on Agent Status', () => {
    expect(absentTodayCard(countByStatus(rows), rows.length, false)).toEqual({ value: 'Off', sub: 'Absence tracking is off', clickable: false });
    expect(statusOptions(false)).not.toContain('ABSENT');
    expect(statusOptions(false)).toContain('NO_CHECK_IN');
  });

  it('keeps NON_WORKING_DAY check-ins as OFF', () => {
    const offDay = [{ attendance: { agentId: 4, status: 'NON_WORKING_DAY', checkInTime: '2026-10-26T04:00:00' }, lateMinutes: 0 }];
    expect(byName(buildTodayRows(offDay, agents, 'MON', false))['Day Off']).toBe('OFF');
  });
});
