package com.hrm.backend.export;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One table on its way to a file: a header row plus data rows.
 *
 * Cells are kept as plain values (text, number, date, boolean, null) so the same
 * table can be written as CSV or as an Excel sheet with numbers as numbers.
 */
public final class SheetBuilder {

    private final String sheetName;
    private final List<String> headers;
    private final List<List<Object>> rows = new ArrayList<>();

    private SheetBuilder(String sheetName, List<String> headers) {
        this.sheetName = sheetName;
        this.headers = List.copyOf(headers);
    }

    public static SheetBuilder of(String sheetName, String... headers) {
        return new SheetBuilder(sheetName, List.of(headers));
    }

    public static SheetBuilder of(String sheetName, List<String> headers) {
        return new SheetBuilder(sheetName, headers);
    }

    /** Adds a row; a missing trailing cell is padded with null so one bad record cannot break the file. */
    public SheetBuilder row(Object... cells) {

        if (cells.length > headers.size()) {
            throw new IllegalArgumentException(
                    "A row of " + cells.length + " cells does not fit " + headers.size() + " columns");
        }

        List<Object> row = new ArrayList<>(headers.size());
        for (Object cell : cells) {
            row.add(cell);
        }
        while (row.size() < headers.size()) {
            row.add(null);
        }
        rows.add(row);
        return this;
    }

    public String sheetName() {
        return sheetName;
    }

    public List<String> headers() {
        return headers;
    }

    public List<List<Object>> rows() {
        return List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * A cell as the reader should see it in a text file: numbers without trailing
     * zeros, dates as `YYYY-MM-DD`, booleans as Yes/No, blanks as empty.
     */
    public static String text(Object cell) {

        if (cell == null) {
            return "";
        }

        if (cell instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (cell instanceof Double || cell instanceof Float) {
            double value = ((Number) cell).doubleValue();
            return value == Math.rint(value) && !Double.isInfinite(value)
                    ? String.format(Locale.ROOT, "%.0f", value)
                    : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
        if (cell instanceof Number) {
            return cell.toString();
        }
        if (cell instanceof Boolean flag) {
            return flag ? "Yes" : "No";
        }
        if (cell instanceof LocalDate date) {
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (cell instanceof LocalDateTime dateTime) {
            return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (cell instanceof ZonedDateTime zoned) {
            return zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (cell instanceof java.time.Instant instant) {
            return instant.toString();
        }
        if (cell instanceof Enum<?> constant) {
            return constant.name();
        }

        return cell.toString();
    }

    /** True when Excel should treat the cell as a number rather than text. */
    public static boolean isNumeric(Object cell) {
        return cell instanceof Number || cell instanceof BigDecimal || cell instanceof BigInteger;
    }
}
