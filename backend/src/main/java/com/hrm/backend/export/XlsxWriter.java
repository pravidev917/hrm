package com.hrm.backend.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a real `.xlsx` workbook - a ZIP of the five small XML parts Excel needs -
 * with no third-party library, so the project keeps building offline.
 *
 * Sheet features: a bold, frozen header row, an auto-filter over the whole table,
 * sensible column widths, numbers stored as numbers (so they can be summed), and
 * everything else as inline text so no shared-string table is required.
 */
final class XlsxWriter {

    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";

    private XlsxWriter() {
    }

    static byte[] write(SheetBuilder sheet) {

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {

            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", packageRels());
            put(zip, "xl/workbook.xml", workbook(sheet.sheetName()));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", worksheet(sheet));

            zip.finish();
            return buffer.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the Excel file", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                <Default Extension="xml" ContentType="application/xml"/>\
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>\
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>\
                </Types>""";
    }

    private static String packageRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="%s">\
                <Relationship Id="rId1" Type="%s/officeDocument" Target="xl/workbook.xml"/>\
                </Relationships>""".formatted(NS_PKG_REL, NS_REL);
    }

    private static String workbook(String rawSheetName) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="%s" xmlns:r="%s">\
                <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>\
                </workbook>""".formatted(NS_MAIN, NS_REL, escapeXml(sheetName(rawSheetName)));
    }

    private static String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="%s">\
                <Relationship Id="rId1" Type="%s/worksheet" Target="worksheets/sheet1.xml"/>\
                <Relationship Id="rId2" Type="%s/styles" Target="styles.xml"/>\
                </Relationships>""".formatted(NS_PKG_REL, NS_REL, NS_REL);
    }

    /** Two cell formats: 0 = ordinary, 1 = bold header. */
    private static String styles() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="%s">\
                <fonts count="2">\
                <font><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
                <font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
                </fonts>\
                <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>\
                <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
                <cellXfs count="2">\
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
                </cellXfs>\
                <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>\
                </styleSheet>""".formatted(NS_MAIN);
    }

    private static String worksheet(SheetBuilder sheet) {

        int columns = Math.max(1, sheet.headers().size());
        int lastRow = sheet.rowCount() + 1;
        String lastCell = columnName(columns) + lastRow;

        StringBuilder body = new StringBuilder();

        // header
        body.append("<row r=\"1\">");
        for (int c = 0; c < sheet.headers().size(); c++) {
            String ref = columnName(c + 1) + 1;
            body.append(textCell(ref, sheet.headers().get(c), 1));
        }
        body.append("</row>");

        // values
        int rowNumber = 2;
        for (List<Object> row : sheet.rows()) {
            body.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < row.size(); c++) {
                String ref = columnName(c + 1) + rowNumber;
                Object value = row.get(c);
                if (SheetBuilder.isNumeric(value)) {
                    body.append("<c r=\"").append(ref).append("\"><v>")
                            .append(SheetBuilder.text(value))
                            .append("</v></c>");
                } else {
                    body.append(textCell(ref, SheetBuilder.text(value), 0));
                }
            }
            body.append("</row>");
            rowNumber++;
        }

        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="%s">\
                <dimension ref="A1:%s"/>\
                <sheetViews><sheetView workbookViewId="0">\
                <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>\
                </sheetView></sheetViews>\
                <sheetFormatPr defaultRowHeight="15"/>\
                %s\
                <sheetData>%s</sheetData>\
                <autoFilter ref="A1:%s"/>\
                </worksheet>""".formatted(
                NS_MAIN,
                lastCell,
                columnWidths(sheet.headers()),
                body,
                lastCell);
    }

    private static String textCell(String ref, String value, int style) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\" s=\"" + style + "\"><is><t xml:space=\"preserve\">"
                + escapeXml(value) + "</t></is></c>";
    }

    private static String columnWidths(List<String> headers) {

        if (headers.isEmpty()) {
            return "";
        }

        StringBuilder cols = new StringBuilder("<cols>");
        for (int c = 0; c < headers.size(); c++) {
            int width = Math.min(45, Math.max(10, headers.get(c).length() + 4));
            cols.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        return cols.append("</cols>").toString();
    }

    /** Excel sheet names: at most 31 characters, and none of `\ / ? * [ ] :`. */
    static String sheetName(String raw) {

        if (raw == null || raw.isBlank()) {
            return "Sheet1";
        }

        String cleaned = raw.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet1";
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** 1 -> A, 26 -> Z, 27 -> AA. */
    static String columnName(int index) {

        StringBuilder name = new StringBuilder();
        int remaining = index;
        while (remaining > 0) {
            int letter = (remaining - 1) % 26;
            name.insert(0, (char) ('A' + letter));
            remaining = (remaining - 1) / 26;
        }
        return name.toString();
    }

    private static String escapeXml(String value) {

        if (value == null) {
            return "";
        }

        StringBuilder safe = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> safe.append("&amp;");
                case '<' -> safe.append("&lt;");
                case '>' -> safe.append("&gt;");
                case '"' -> safe.append("&quot;");
                case '\'' -> safe.append("&apos;");
                default -> {
                    // Control characters are not legal in XML 1.0 and make Excel refuse the file.
                    if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') {
                        continue;
                    }
                    safe.append(c);
                }
            }
        }
        return safe.toString();
    }
}
