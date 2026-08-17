/** Builds a CSV string from rows of plain objects, given an ordered list of {key, label} columns. */
export function toCsv(rows, columns) {
  const escape = (value) => {
    const s = value === null || value === undefined ? '' : String(value);
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const header = columns.map((c) => escape(c.label)).join(',');
  const lines = rows.map((row) => columns.map((c) => escape(row[c.key])).join(','));
  // `sep=,` as the literal first line forces Excel to use comma as the
  // delimiter on double-click-open regardless of the OS regional "list
  // separator" setting — without it, Excel silently guesses the wrong
  // delimiter on many locales and dumps every row into column A. Google
  // Sheets doesn't recognize the directive, so it just shows up there as a
  // harmless extra first row; every row below still splits into columns fine.
  return ['sep=,', header, ...lines].join('\r\n');
}

export function downloadCsv(filename, csvString) {
  // UTF-8 BOM so Excel detects the encoding correctly instead of mangling
  // non-ASCII characters (e.g. PKR figures, product names with special chars).
  const BOM = String.fromCharCode(0xfeff);
  const blob = new Blob([BOM + csvString], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
