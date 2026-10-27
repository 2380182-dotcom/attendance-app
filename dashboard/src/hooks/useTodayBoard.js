import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { attendanceApi, agentApi } from '../services/attendanceApi';
import { karachiToday, karachiTodayDayCode } from '../utils/dateUtils';
import { companySettingsApi } from '../services/holidayApi';
import { buildTodayRows, countByStatus } from '../utils/todayBoard';

/**
 * Today's per-agent attendance status, computed client-side from daily-report
 * + the full active-agent roster (rules in utils/todayBoard.js; while the
 * company's absence tracking is off, nobody is ABSENT — NO_CHECK_IN instead). Shared so every view built on "who's
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

  // The company's absence tracking switch (Admin > Holidays). Unknown or
  // unreadable = treated as off, so no absence numbers appear by mistake.
  const settings = useQuery({ queryKey: ['company-settings'], queryFn: companySettingsApi.get });
  const absenceTracking = settings.data?.absenceTracking === true;

  const rows = useMemo(() => {
    if (!dailyReport.data || !activeAgents.data) return [];
    return buildTodayRows(dailyReport.data, activeAgents.data, karachiTodayDayCode(), absenceTracking);
  }, [dailyReport.data, activeAgents.data, absenceTracking]);

  const counts = useMemo(() => countByStatus(rows), [rows]);

  return {
    rows,
    counts,
    todayDate,
    absenceTracking,
    isLoading: dailyReport.isLoading || activeAgents.isLoading || settings.isLoading,
    isError: dailyReport.isError || activeAgents.isError,
  };
}
