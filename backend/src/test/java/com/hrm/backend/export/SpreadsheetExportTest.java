package com.hrm.backend.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export engine: the CSV a spreadsheet reads without surprises, and the XLSX
 * package Excel has to accept (it validates every part, so a missing relationship
 * or a stray control character makes the file "unreadable").
 */
class SpreadsheetExportTest {

    private static final String NEWLINE = "\n";

    private SheetBuilder sample() {
        return SheetBuilder.of("Daily reports", "Employee", "Date", "Sourced", "Revenue", "Scheduled")
                .row("Asha, Menon", LocalDate.of(2026, 11, 22), 12, new BigDecimal("25000.00"), true)
                .row("Vikram \"V\" Rao", LocalDate.of(2026, 11, 21), null, new BigDecimal("1500.50"), false);
    }

    // ---------- format ----------

    @Test
    void formatDefaultsToCsvAndAcceptsExcelNames() {

        assertEquals(ExportFormat.CSV, ExportFormat.from(null));
        assertEquals(ExportFormat.CSV, ExportFormat.from(""));
        assertEquals(ExportFormat.CSV, ExportFormat.from("CSV"));
        assertEquals(ExportFormat.XLSX, ExportFormat.from("xlsx"));
        assertEquals(ExportFormat.XLSX, ExportFormat.from(" Excel "));
        assertEquals(ExportFormat.XLSX, ExportFormat.from("xls"));

        assertThrows(IllegalArgumentException.class, () -> ExportFormat.from("pdf"));
    }

    // ---------- CSV ----------

    @Test
    void csvStartsWithABomSoExcelReadsUtf8() {

        String csv = new String(CsvWriter.write(sample()), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"), "a BOM is what makes Excel pick UTF-8");
        assertTrue(csv.contains("Employee,Date,Sourced,Revenue,Scheduled"));
    }

    @Test
    void csvQuotesCommasQuotesAndKeepsTypesReadable() {

        String csv = new String(CsvWriter.write(sample()), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Asha, Menon\""), "a comma inside a value must be quoted");
        assertTrue(csv.contains("\"Vikram \"\"V\"\" Rao\""), "inner quotes are doubled");
        assertTrue(csv.contains("2026-11-22"), "dates are written as ISO, month first");
        assertTrue(csv.contains(",25000,"), "25000.00 is written without the trailing zeros");
        assertTrue(csv.contains(",1500.5,"), "decimals survive without padding");
        assertTrue(csv.contains(",Yes" + NEWLINE), "a boolean is Yes/No, not true/false");
        assertTrue(csv.contains(",No" + NEWLINE), "false is No");
    }

    @Test
    void csvStopsFormulaInjectionFromCellValues() {

        SheetBuilder sheet = SheetBuilder.of("Notes", "Title")
                .row("=HYPERLINK(\"http://evil\",\"click\")")
                .row("+1 555")
                .row("@sum");

        String csv = new String(CsvWriter.write(sheet), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"'=HYPERLINK"), "the leading = is defused with a quote");
        assertTrue(csv.contains("'+1 555"));
        assertTrue(csv.contains("'@sum"));
    }

    @Test
    void csvOfAnEmptyTableIsJustTheHeader() {

        SheetBuilder sheet = SheetBuilder.of("Empty", "A", "B");
        String csv = new String(CsvWriter.write(sheet), StandardCharsets.UTF_8);

        assertEquals("\uFEFFA,B" + NEWLINE, csv, "a header-only export is still a valid file");
        assertEquals(0, sheet.rowCount());
    }

    // ---------- XLSX ----------

    @Test
    void xlsxIsAZipWithThePartsExcelNeeds() throws Exception {

        Map<String, String> parts = unzip(XlsxWriter.write(sample()));

        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("_rels/.rels"));
        assertTrue(parts.containsKey("xl/workbook.xml"));
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"));
        assertTrue(parts.containsKey("xl/styles.xml"));
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"));
        assertTrue(parts.get("[Content_Types].xml").contains("spreadsheetml.sheet.main+xml"));
    }

    @Test
    void xlsxSheetHasTheHeaderTheNumbersAndTheEscapedText() throws Exception {

        Map<String, String> parts = unzip(XlsxWriter.write(sample()));
        String sheet = parts.get("xl/worksheets/sheet1.xml");

        assertTrue(sheet.contains("<t xml:space=\"preserve\">Employee</t>"), "header text is inline");
        assertTrue(sheet.contains("s=\"1\""), "the header row uses the bold style");
        assertTrue(sheet.contains("<c r=\"C2\"><v>12</v></c>"), "numbers stay numbers, so sums work");
        assertTrue(sheet.contains("<c r=\"D2\"><v>25000</v></c>"));
        assertTrue(sheet.contains("Asha, Menon"));
        assertTrue(sheet.contains("Vikram &quot;V&quot; Rao"), "quotes are escaped for XML");
        assertTrue(sheet.contains("<dimension ref=\"A1:E3\"/>"), "the used range is declared");
        assertTrue(sheet.contains("<autoFilter ref=\"A1:E3\"/>"), "the header gets a filter in Excel");
        assertTrue(sheet.contains("state=\"frozen\""), "the header row stays visible when scrolling");
        assertTrue(parts.get("xl/workbook.xml").contains("name=\"Daily reports\""));
    }

    @Test
    void xlsxEscapesMarkupAndDropsCharactersXmlForbids() throws Exception {

        SheetBuilder sheet = SheetBuilder.of("Report", "Text", "Owner")
                .row("a & b < c > d", "bell\u0007char");

        String sheetXml = unzip(XlsxWriter.write(sheet)).get("xl/worksheets/sheet1.xml");

        assertTrue(sheetXml.contains("a &amp; b &lt; c &gt; d"));
        assertTrue(sheetXml.contains("bellchar"), "control characters are removed so Excel opens the file");
    }

    @Test
    void xlsxSanitisesSheetNamesAndColumnLetters() {

        assertEquals("Sheet1", XlsxWriter.sheetName(null));
        assertEquals("Sheet1", XlsxWriter.sheetName("  "));
        assertEquals("a b c", XlsxWriter.sheetName("a/b:c"));
        assertEquals(31, XlsxWriter.sheetName("x".repeat(40)).length());

        assertEquals("A", XlsxWriter.columnName(1));
        assertEquals("Z", XlsxWriter.columnName(26));
        assertEquals("AA", XlsxWriter.columnName(27));
        assertEquals("AZ", XlsxWriter.columnName(52));
    }

    @Test
    void aRowWithTooManyCellsIsAProgrammingErrorNotEmptyColumns() {

        SheetBuilder sheet = SheetBuilder.of("T", "A", "B");

        assertThrows(IllegalArgumentException.class, () -> sheet.row("1", "2", "3"));
    }

    // ---------- download ----------

    @Test
    void downloadCarriesTheFormatTheFilenameAndNoCaching() {

        ExportFile csv = ExportFile.of(sample(), "csv", "daily-reports");

        assertEquals("daily-reports-" + LocalDate.now() + ".csv", csv.filename());
        assertEquals("text/csv;charset=UTF-8", csv.toResponse().getHeaders().getContentType().toString());
        assertTrue(csv.toResponse().getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));

        ExportFile xlsx = ExportFile.of(sample(), "xlsx", "candidates");

        assertEquals("candidates-" + LocalDate.now() + ".xlsx", xlsx.filename());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx.toResponse().getHeaders().getContentType().toString());
        assertEquals("no-store", xlsx.toResponse().getHeaders().getCacheControl());
        assertNotNull(xlsx.body());
        assertTrue(xlsx.size() > 500, "an xlsx has several XML parts, so it is never a few dozen bytes");
    }

    private Map<String, String> unzip(byte[] archive) throws Exception {

        Map<String, String> parts = new LinkedHashMap<>();

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        assertFalse(parts.isEmpty(), "the archive must contain the workbook parts");
        return parts;
    }
}
