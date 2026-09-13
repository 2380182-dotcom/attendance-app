import { useMemo, useState } from 'react';

/** Client-side pagination for small admin-managed lists (hundreds of rows, not thousands). */
export function usePagination(rows, initialRowsPerPage = 10) {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(initialRowsPerPage);

  const paged = useMemo(
    () => rows.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage),
    [rows, page, rowsPerPage]
  );

  const handleChangePage = (event, newPage) => setPage(newPage);
  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return { page, rowsPerPage, paged, handleChangePage, handleChangeRowsPerPage, resetPage: () => setPage(0) };
}
