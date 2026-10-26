import { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';

/**
 * True while the page is being printed. Charts read it to switch from
 * "fill the screen width" to a fixed width that fits an A4 page — the
 * screen-width SVG would otherwise be cut off on paper. flushSync makes the
 * re-render happen before the browser lays out the printed page.
 */
export default function usePrintMode() {
  const [printing, setPrinting] = useState(false);
  useEffect(() => {
    const before = () => flushSync(() => setPrinting(true));
    const after = () => setPrinting(false);
    window.addEventListener('beforeprint', before);
    window.addEventListener('afterprint', after);
    return () => {
      window.removeEventListener('beforeprint', before);
      window.removeEventListener('afterprint', after);
    };
  }, []);
  return printing;
}

/** Printable width of A4 with 12mm margins is ~700px; two-column print cards get ~320px each. */
export const PRINT_WIDTH = { full: 680, half: 300 };
