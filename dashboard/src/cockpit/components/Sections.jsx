import React, { useEffect, useRef, useState } from 'react';
import { Box } from '@mui/material';
import { APPBAR, INK, MEANING } from '../tokens';

/**
 * Sticky section menu. It only JUMPS (smooth scroll) and highlights the
 * section on screen — every section always stays on the page.
 */
export function SectionNav({ sections }) {
  const [active, setActive] = useState(sections[0]?.id);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      // A section is "current" when it crosses the band just under the sticky menu.
      { rootMargin: '-140px 0px -55% 0px', threshold: 0 },
    );
    sections.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) observer.observe(el);
    });
    // Near the top (tall banner above the first section) nothing crosses the band, so pin the first item.
    const onScroll = () => { if (window.scrollY < 120) setActive(sections[0]?.id); };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', onScroll);
    };
  }, [sections]);

  const jump = (id) => {
    const el = document.getElementById(id);
    if (!el) return;
    setActive(id);
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
  };

  return (
    <Box
      component="nav"
      aria-label="Cockpit sections"
      className="cockpit-no-print"
      sx={{
        position: 'sticky',
        top: { xs: APPBAR.xs, sm: APPBAR.sm },
        zIndex: 5,
        mx: { xs: -2, sm: -3 },
        px: { xs: 2, sm: 3 },
        py: 1,
        mb: 2,
        bgcolor: 'rgba(243, 245, 249, 0.86)',
        backdropFilter: 'saturate(160%) blur(10px)',
        borderBottom: `1px solid ${INK.border}`,
        overflowX: 'auto',
        scrollbarWidth: 'none',
        '&::-webkit-scrollbar': { display: 'none' },
      }}
    >
      <Box sx={{ display: 'flex', gap: 0.5, minWidth: 'max-content' }}>
        {sections.map((s) => {
          const on = active === s.id;
          return (
            <Box
              key={s.id}
              component="button"
              type="button"
              onClick={() => jump(s.id)}
              aria-current={on ? 'true' : undefined}
              sx={{
                border: 0, cursor: 'pointer', font: 'inherit', fontSize: 13.5, fontWeight: on ? 700 : 600,
                px: 1.5, py: 0.9, borderRadius: '999px', whiteSpace: 'nowrap',
                color: on ? '#fff' : INK.secondary,
                bgcolor: on ? '#0E2A63' : 'transparent',
                transition: 'background-color 160ms ease, color 160ms ease',
                '&:hover': { bgcolor: on ? '#0E2A63' : 'rgba(14, 42, 99, 0.07)', color: on ? '#fff' : INK.primary },
                '&:focus-visible': { outline: `2px solid ${MEANING.sales}`, outlineOffset: 2 },
              }}
            >
              {s.label}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

/** Section wrapper: title on the left, a short grey explanation on the right. */
export function Section({ id, title, hint, children }) {
  return (
    <Box component="section" id={id} sx={{ mb: 4 }}>
      <Box sx={{
        display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', justifyContent: 'space-between',
        gap: 1, mb: 1.5, pb: 1, borderBottom: `1px solid ${INK.border}`,
      }}>
        <Box component="h2" sx={{ m: 0, fontSize: 19, fontWeight: 800, letterSpacing: '-0.01em', color: INK.primary }}>
          {title}
        </Box>
        {hint && <Box sx={{ fontSize: 13, color: INK.muted }}>{hint}</Box>}
      </Box>
      {children}
    </Box>
  );
}

/**
 * Renders children(visible). `visible` turns true once the section comes
 * within ~600px of the screen (and stays true), or when the page is told
 * to load everything (before printing). Sections use it as their query's
 * `enabled` flag, so lower sections only load when scrolled near.
 */
export function LazySection({ children, eager = false }) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(eager);

  useEffect(() => {
    if (visible) return undefined;
    const loadAll = () => setVisible(true);
    window.addEventListener('cockpit:load-all', loadAll);
    window.addEventListener('beforeprint', loadAll);
    const observer = new IntersectionObserver(
      (entries) => { if (entries.some((e) => e.isIntersecting)) setVisible(true); },
      { rootMargin: '600px 0px' },
    );
    if (ref.current) observer.observe(ref.current);
    return () => {
      observer.disconnect();
      window.removeEventListener('cockpit:load-all', loadAll);
      window.removeEventListener('beforeprint', loadAll);
    };
  }, [visible]);

  return <div ref={ref}>{children(visible)}</div>;
}

/** Responsive grid: 1 column on phones, `cols` side-by-side from `from` up. */
export function Grid({ cols = 2, from = 'lg', gap = 2, children, className }) {
  return (
    <Box className={className} sx={{
      display: 'grid', gap,
      gridTemplateColumns: { xs: '1fr', [from]: `repeat(${cols}, minmax(0, 1fr))` },
    }}>
      {children}
    </Box>
  );
}
