/**
 * Shared date-range preset math for the HR and Sales per-agent CSV report screens.
 * Pure date computation only — no data access, safe to share across both.
 */

export const DATE_PRESETS = ['TODAY', 'WEEK', 'MONTH', 'YEAR', 'CUSTOM'];

function toDateStr(date) {
  return date.toISOString().split('T')[0];
}

/**
 * @param {'TODAY'|'WEEK'|'MONTH'|'YEAR'|'CUSTOM'} preset
 * @param {Date} [customFrom] - used only when preset === 'CUSTOM'
 * @param {Date} [customTo] - used only when preset === 'CUSTOM'
 * @returns {{ from: string, to: string }} ISO date strings (yyyy-MM-dd)
 */
export function computeDateRange(preset, customFrom, customTo) {
  const today = new Date();
  const todayStr = toDateStr(today);

  switch (preset) {
    case 'TODAY':
      return { from: todayStr, to: todayStr };
    case 'WEEK': {
      const weekAgo = new Date(today);
      weekAgo.setDate(weekAgo.getDate() - 6);
      return { from: toDateStr(weekAgo), to: todayStr };
    }
    case 'MONTH': {
      const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
      return { from: toDateStr(monthStart), to: todayStr };
    }
    case 'YEAR': {
      const yearStart = new Date(today.getFullYear(), 0, 1);
      return { from: toDateStr(yearStart), to: todayStr };
    }
    case 'CUSTOM':
    default:
      return {
        from: toDateStr(customFrom || today),
        to: toDateStr(customTo || today),
      };
  }
}

export const PRESET_LABELS = {
  TODAY: 'Today',
  WEEK: 'Week',
  MONTH: 'Month',
  YEAR: 'Year',
  CUSTOM: 'Custom',
};
