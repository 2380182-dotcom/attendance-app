import { INK } from '../tokens';

export const reducedMotion = typeof window !== 'undefined'
  && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Shared recharts axis/grid styling: hairline, solid, recessive. */
export const axisProps = {
  tick: { fill: INK.muted, fontSize: 11.5 },
  tickLine: false,
  axisLine: { stroke: INK.grid },
};
export const gridProps = { stroke: INK.grid, strokeDasharray: '0', vertical: false };
