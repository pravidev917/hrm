# Exports — CSV and Excel

Every administrator table that holds business data can be downloaded, in two
formats, from the same page it is displayed on:

| Page | Route | Endpoint |
|---|---|---|
| Candidate tracking | `/candidate-tracking` | `GET /api/admin/feedback/export` |
| Daily reports | `/reports` | `GET /api/admin/reports/export` |
| Weekly reports | `/weekly-reports` | `GET /api/admin/weekly-reports/export` |
| Clients | `/clients` | `GET /api/admin/clients/export` |
| Attendance monitor | `/attendance` | `GET /api/admin/attendance/export` |

The toolbar shows two buttons — **CSV** and **Excel** — that download the table
exactly as it is filtered on screen. The export is rendered **on the server**, so
it is always scoped to the caller's company and never limited by what the browser
happened to load (pagination, previous searches).

## Query parameters

| Name | Meaning |
|---|---|
| `format` | `csv` (default) or `xlsx` |
| `q` | the same free-text search the table applies (case-insensitive "contains") |
| `clientId`, `date` | daily reports only |
| `status` | candidates (record status) and clients (`ACTIVE` / `INACTIVE`) |
| `from`, `to` | attendance monitor only |

Any other `format` value returns `400` with a clear message instead of quietly
sending a CSV the user did not ask for.

## What the files contain

- **CSV** — UTF-8 with a BOM, so Excel opens it without mangling accents; values
  containing a comma, a quote or a line break are quoted; a leading `=`, `+`, `-`
  or `@` is prefixed with `'` so a cell can never be executed as a formula.
  Dates are `YYYY-MM-DD`, booleans are `Yes`/`No`, money keeps two decimals.
- **XLSX** — a real workbook written without any third-party library
  (`export/XlsxWriter.java`): bold frozen header row, auto-filter over the table,
  sensible column widths, numbers stored as numbers so Excel can sum them, and
  the same formula-injection protection. Every part of the package is generated
  from scratch and validated by tests that unzip it and read the sheet XML.

File names carry the date, e.g. `daily-reports-2026-11-22.xlsx`.

## How it is built

```
export/
  SheetBuilder.java   the table: headers + typed rows (text, number, date, boolean)
  TextFilter.java     the same "contains" search the tables use
  CsvWriter.java      UTF-8 CSV with quoting and formula guard
  XlsxWriter.java     minimal OOXML writer (zip + five XML parts)
  ExportFormat.java   csv | xlsx, media type and extension
  ExportFile.java     bytes + file name + download headers
```

A controller endpoint is one line:

```java
return ExportFile
        .of(clientService.exportClients(adminId, query, status), format, "clients")
        .toResponse();
```

and each service owns the column list for its own table, so the export never
drifts from the screen.

## Tests

`backend/src/test/java/com/hrm/backend/export/SpreadsheetExportTest.java` covers
the quoting and formula rules, the BOM, the zip layout, XML escaping (including
characters Excel refuses), sheet-name limits, column letters and the download
headers.
