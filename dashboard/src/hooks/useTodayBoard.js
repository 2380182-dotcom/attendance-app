import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { attendanceApi, agentApi } from '../services/attendanceApi';
import { karachiToday, karachiTodayDayCode } from '../utils/dateUtils';

/**
 * Today's per-agent attendance status, computed client-side from daily-report
 * + the full active-agent roster. Shared so every view built on "who's
 * IN/LATE/ABSENT/OFF today" (Today's Board, the HR Analytics summary cards,
 * Agent Status) agrees with each other by construction, rather than each
 * computing its own version of "today."
 */
export function useTodayBoard() {
  // Pass Pakistan's calendar date explicitly rather than omitting the param
  // — the backend's own "today" default is LocalDate.now() on its UTC
  // container, which can be a different calendar date than Pakistan's
  // during the ~5-hour window after midnight PKT.
  const todayDate = karachiToday().format('YYYY-MM-DD');
  const dailyReport = useQuery({ queryKey: ['daily-report', todayDate], queryFn: () => attendanceApi.getDailyReport(todayDate) });
  // NOT agentApi.getActive() — despite the name, that's "checked in within
  // the last 7 days," not the roster. GET /agents returns every agent row
  // (all roles, all active states), filtered here to field agents who are
  // actually employed — the same definition DashboardService already uses
  // for the "Total Agents" stat elsewhere on this dashboard.
  const allAgents = useQuery({ queryKey: ['agents', 'all'], queryFn: () => agentApi.getAll() });
  const activeAgents = useMemo(
    () => ({
      data: allAgents.data?.filter((a) => a.role === 'AGENT' && a.isActive !== false),
      isLoading: allAgents.isLoading,
      isError: allAgents.isError,
    }),
    [allAgents.data, allAgents.isLoading, allAgents.isError]
  );

  const rows = useMemo(() => {
    if (!dailyReport.data || !activeAgents.data) return [];

    const reportByAgentId = new Map(dailyReport.data.map((r) => [r.attendance.agentId, r]));
    const todayCode = karachiTodayDayCode();

    return activeAgents.data.map((agent) => {
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
      // No record at all today — absent only if today is actually one of
      // their working days; otherwise they're legitimately off, not absent.
      const isWorkingDay = !agent.workingDays || agent.workingDays.length === 0 || agent.workingDays.includes(todayCode);
      return {
        agentId: agent.id,
        employeeId: agent.agentId,
        name: agent.name,
        department: agent.department,
        status: isWorkingDay ? 'ABSENT' : 'OFF',
        checkInTime: null,
        lateMinutes: null,
        faceVerified: null,
      };
    });
  }, [dailyReport.data, activeAgents.data]);

  const counts = useMemo(() => {
    const c = { IN: 0, LATE: 0, ABSENT: 0, OFF: 0 };
    rows.forEach((r) => { c[r.status] = (c[r.status] || 0) + 1; });
    return c;
  }, [rows]);

  return {
    rows,
    counts,
    todayDate,
    isLoading: dailyReport.isLoading || activeAgents.isLoading,
    isError: dailyReport.isError || activeAgents.isError,
  };
}
