package com.dawnbread.attendance.service;

/**
 * Minimal CSV writing helpers. Pure string formatting only — holds no data itself,
 * so it's safe to share between the HR and Sales export services without weakening
 * the data-boundary between them.
 */
final class CsvUtil {

    private CsvUtil() {}

    static String escape(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
