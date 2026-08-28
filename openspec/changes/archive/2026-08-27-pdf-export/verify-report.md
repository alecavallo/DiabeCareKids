```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:3eeab85ac2f8ea496aae0137b3b38be77611c270000000000000000000000000
verdict: pass
blockers: 0
critical_findings: 0
requirements: 6/6
scenarios: 6/6
test_command: make test
test_exit_code: 0
test_output_hash: sha256:15dff39f4c6c1848773fd2459313b9c844b1b6e6890eaa23c68921481271cbb7
build_command: make build
build_exit_code: 0
build_output_hash: sha256:5e10b3866d638df473a00edab6fcc19e6337173b40dd2b581df54c0acf4e2f33
```

# Verification Report: On-Device Clinical PDF Export (CAP-004)

## Summary
- **Verdict**: PASS
- **Change**: `pdf-export` (CAP-004)
- **Requirements Verified**: 6 / 6
- **Scenarios Verified**: 6 / 6
- **Automated Tests**: Green (`make test` — 64 shared tests pass, 33 composeApp tests pass, 0 failures)
- **Build / Assembly**: Green (`make build` — assembleDebug BUILD SUCCESSFUL)

## Requirements & Scenarios Matrix

| Requirement | Scenario | Status | Evidence |
|---|---|---|---|
| **On-Device Generation (INV-004)** | Generate valid PDF report offline | PASS | `AndroidPdfReportExporter.kt` uses native `android.graphics.pdf.PdfDocument` and local `FileOutputStream` writing to `context.cacheDir/pdf_exports/`. No network/cloud requests are made. |
| **Table Content Format** | Records map to required table columns | PASS | `PdfReportRow` maps Date, Pre-Meal BG (`glicemia_inicial`), 2h BG (`glicemia_postprandial_2h`), and Real Carbs (`carbohidratos_reales`). Verified by `PdfReportDataBuilderTest` (`mapsNullOptionalFieldsToDash`, `roundsGramsToOneDecimal`, `formatsDateAsYyyyMmDdHhMm`). Photo thumbnails are omitted per spec. |
| **Date-Range Filtering** | Filter out out-of-range records | PASS | `buildReportData` strictly filters `fecha_hora_inicio in fromMillis..toMillis` (inclusive). `HistoryScreen` provides Material3 `DateRangePicker` defaulting to last 7 days. Verified by `PdfReportDataBuilderTest.filtersOnlyInRangeInclusive`. |
| **Chronological Ordering** | Records sorted ascending by date | PASS | `buildReportData` sorts records via `.sortedBy { it.fecha_hora_inicio }` (oldest to newest). Verified by `PdfReportDataBuilderTest.sortsAscendingByFechaHoraInicio`. |
| **Caching and Sharing Intent** | Export triggers share intent | PASS | `AndroidPdfReportExporter` saves to `cacheDir/pdf_exports/reporte_<epoch>.pdf` exposed via `file_paths.xml` (`pdf_exports`), returning FileProvider URI. `AndroidReportShareLauncher` launches `ACTION_SEND` with `application/pdf`, read URI grant, and system chooser. Verified by `HistoryViewModelTest.exportSuccessSharesReturnedUri`. |
| **Constraint Enforcement** | Render with constraints applied | PASS | `AndroidPdfReportExporter` uses default system fonts (`Typeface.DEFAULT`, `Typeface.DEFAULT_BOLD`) via `Paint()`. No third-party PDF libraries or external services used. |

## Implementation Tasks Check

All 11 tasks across Slice 1 and Slice 2 are verified implemented:
- **Slice 1 — shared foundation (pure logic + tests)**:
  - **S1.1**: Lifted `formatEpochMillis` and `formatGrams` to `shared/.../format/Formatters.kt` (public). Deleted `TimeFormat.kt` and updated all UI call sites (`MealFormScreen`, `HistoryScreen`, `EditRecordScreen`, `FollowUpScreen`, `MealFormViewModelTest`).
  - **S1.2**: Created `shared/.../export/PdfReportData.kt` with `PdfReportRow`, `PdfReportData`, and pure `buildReportData(records, fromMillis, toMillis)`.
  - **S1.3**: Created `shared/.../export/PdfReportExporter.kt` with `PdfExportOutcome` (`Success`, `Failure`), `PdfReportExporter`, and `ReportShareLauncher` interfaces.
  - **S1.4**: Created `shared/.../commonTest/.../export/PdfReportDataBuilderTest.kt` covering inclusive bounds, ascending sort, null mapping, grams rounding, and date formatting.
- **Slice 2 — android renderer + UI + wiring + tests**:
  - **S2.1**: Created `AndroidPdfReportExporter.kt` using native `PdfDocument`, A4 format (595×842pt), 4-column layout, automatic page overflow pagination with repeated headers, and transient cache directory cleanup.
  - **S2.2**: Updated `file_paths.xml` with `<cache-path name="pdf_exports" path="pdf_exports/" />`.
  - **S2.3**: Created `AndroidReportShareLauncher.kt` with `ACTION_SEND`, `application/pdf`, `FLAG_GRANT_READ_URI_PERMISSION`, chooser dialog, and graceful handling of `ActivityNotFoundException`.
  - **S2.4**: Updated `HistoryViewModel.kt` with `exportReport(from, to)` method, `isExporting` and `exportError` state tracking, safe outcome handling, and re-entry protection.
  - **S2.5**: Updated `HistoryScreen.kt` with TopAppBar "Exportar" action, Material3 `DateRangePicker` dialog (default last 7 days), and loading/error UI indicators.
  - **S2.6**: Wired dependency injection in `AppGraph.kt` and initialized `AndroidPdfReportExporter` and `AndroidReportShareLauncher` in `MainActivity.kt`.
  - **S2.7**: Added `FakePdfReportExporter` and `FakeReportShareLauncher` in `Fakes.kt` and added unit test coverage in `HistoryViewModelTest.kt` (success share, error handling, re-entry suppression).

## Non-Goals & Scope Verification
- No server-side or cloud rendering services used.
- No custom fonts embedded (system default fonts only).
- No Firebase or remote network interactions introduced.
- No separate Dashboard or export screens created (clean entry on HistoryScreen TopAppBar).
- Photo thumbnails omitted from PDF report per acceptable deferral.

## Findings & Severity
- **CRITICAL**: None.
- **WARNING**:
  - Android native PDF rendering and system share sheet execution are validated through compile checks, unit test fakes, and architectural seams. Physical/emulator runtime verification is recommended for visual PDF inspection and target application handling.
- **SUGGESTION / ACCEPTABLE**:
  - Date formatting uses pure arithmetic wall-clock-as-UTC (`Formatters.kt`), avoiding external datetime dependencies while ensuring parity between UI and PDF export.
  - PDF headers are in Spanish ("Fecha", "BG Pre", "BG 2h", "Carbos Reales"), consistent with the application's locale.
  - Export on empty date range generates a valid header-only PDF without error, matching design specifications.
