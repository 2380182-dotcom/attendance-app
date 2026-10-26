import { useMemo, useState } from 'react';
import dayjs from 'dayjs';

const REFRESH_KEY = 'cockpit.autoRefreshMinutes';

function readRefresh() {
  try {
    const v = Number(window.localStorage.getItem(REFRESH_KEY));
    return [0, 5, 15].includes(v) && window.localStorage.getItem(REFRESH_KEY) !== null ? v : 15;
  } catch {
    return 15;
  }
}

/** Pakistan "today" in the browser, independent of the viewer's own timezone. */
export function pakistanToday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Karachi' }).format(new Date());
  return parts; // en-CA gives YYYY-MM-DD
}

export const PRESETS = [
  { key: 'today', label: 'Today', days: 1 },
  { key: '7d', label: 'Last 7 days', days: 7 },
  { key: '30d', label: 'Last 30 days', days: 30 },
  { key: '90d', label: 'Last 90 days', days: 90 },
];

export function useCockpitFilters() {
  const today = pakistanToday();
  const [preset, setPreset] = useState('30d');
  const [custom, setCustom] = useState({ startDate: dayjs(today).subtract(29, 'day').format('YYYY-MM-DD'), endDate: today });
  const [segment, setSegment] = useState('ALL');
  const [areaId, setAreaId] = useState('');
  const [agentId, setAgentId] = useState('');
  const [autoRefresh, setAutoRefreshState] = useState(readRefresh);

  const setAutoRefresh = (v) => {
    setAutoRefreshState(v);
    try {
      window.localStorage.setItem(REFRESH_KEY, String(v));
    } catch {
      /* private mode: keep it for this visit only */
    }
  };

  const filters = useMemo(() => {
    let startDate = custom.startDate;
    let endDate = custom.endDate;
    const p = PRESETS.find((x) => x.key === preset);
    if (p) {
      endDate = today;
      startDate = dayjs(today).subtract(p.days - 1, 'day').format('YYYY-MM-DD');
    }
    return { startDate, endDate, segment, areaId, agentId };
  }, [preset, custom, segment, areaId, agentId, today]);

  return {
    filters, preset, setPreset, custom, setCustom, segment, setSegment,
    areaId, setAreaId, agentId, setAgentId, autoRefresh, setAutoRefresh,
  };
}
