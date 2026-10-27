/**
 * Today's per-agent status for the HR pages built on useTodayBoard (Today's
 * Board, Agent Status, HR Analytics). Pure, so it is unit-tested.
 *
 * With the company's absence tracking OFF nobody is "ABSENT": an agent with
 * no check-in on a working day is NO_CHECK_IN ("Not checked in"), the same
 * wording as the HR cockpit.
 */
export const STATUS_LABEL = {
  IN: 'In',
  LATE: 'Late',
  ABSENT: 'Absent',
  NO_CHECK_IN: 'Not checked in',
  OFF: 'Off',
};

/** dailyReport: GET /attendance/daily-report rows; agents: active field agents; todayCode: e.g. "MON". */
export function buildTodayRows(dailyReport, agents, todayCode, absenceTracking) {
  const reportByAgentId = new Map(dailyReport.map((r) => [r.attendance.agentId, r]));
  return agents.map((agent) => {
    const record = reportByAgentId.get(agent.id);
    if (record) {
      return {
        agentId: agent.id,
        employeeId: agent.agentId,
        name: agent.name,
        department: agent.department,
        status: record.attendance.status === 'NON_WORKING_DAY' ? 'OFF' : record.attendance.status,
        checkInTime: record.attendance.checkInTime,
        lateMinutes: record.lateMinutes,
        faceVerified: record.faceVerified,
      };
    }
    // No record at all today — only a working day counts; otherwise they're legitimately off.
    const isWorkingDay = !agent.workingDays || agent.workingDays.length === 0 || agent.workingDays.includes(todayCode);
    let status = 'OFF';
    if (isWorkingDay) status = absenceTracking ? 'ABSENT' : 'NO_CHECK_IN';
    return {
      agentId: agent.id,
      employeeId: agent.agentId,
      name: agent.name,
      department: agent.department,
      status,
      checkInTime: null,
      lateMinutes: null,
      faceVerified: null,
    };
  });
}

export function countByStatus(rows) {
  const c = { IN: 0, LATE: 0, ABSENT: 0, NO_CHECK_IN: 0, OFF: 0 };
  rows.forEach((r) => { c[r.status] = (c[r.status] || 0) + 1; });
  return c;
}

/** The status filter options for Agent Status, in order. */
export function statusOptions(absenceTracking) {
  return ['ALL', 'IN', 'LATE', absenceTracking ? 'ABSENT' : 'NO_CHECK_IN', 'OFF'];
}

/** HR Analytics "Absent Today" card: a number only while absence tracking is on. */
export function absentTodayCard(counts, total, absenceTracking) {
  if (!absenceTracking) {
    return { value: 'Off', sub: 'Absence tracking is off', clickable: false };
  }
  const percent = total > 0 ? (counts.ABSENT / total) * 100 : 0;
  return { value: `${percent.toFixed(0)}%`, sub: `${counts.ABSENT} agents`, clickable: true };
}
