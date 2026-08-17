import React from 'react';
import { TableCell, TableSortLabel } from '@mui/material';

/** A TableCell with a click-to-sort label, driven by the {sort, onSort} pair from utils/sorting's useSort. */
export default function SortableHeader({ label, sortKey, sort, onSort, align }) {
  return (
    <TableCell align={align}>
      <TableSortLabel
        active={sort.key === sortKey}
        direction={sort.key === sortKey ? sort.direction : 'asc'}
        onClick={() => onSort(sortKey)}
      >
        {label}
      </TableSortLabel>
    </TableCell>
  );
}
