# Tasks: On-Device Clinical PDF Export (CAP-004)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~540 authored (S1 ~175 shared+lift, S2 ~365 android+UI+wiring) |
| 800-line budget risk | Low |
| Chained PRs recommended | No (single PR fits) |
| Suggested split | single PR; pre-cut fallback S1 → S2 |
| Delivery strategy | auto-chain |
| Chain strategy | pending (fallback: stacked-to-main, cached) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units (fallback only — active if diff exceeds 800)

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| S1 | shared pure logic + tests | PR 1 | `make test` | N/A — pure JVM commonTest, no device | revert `shared/.../format/` + `shared/.../export/`; restore `TimeFormat.kt` |
| S2 | android renderer + UI + wiring | PR 2 | `make test` + `make build` | Manual: export >30-row PDF, confirm chooser opens | revert `androidMain/.../export/`, VM/UI/AppGraph/MainActivity edits; `file_paths.xml` additive |

## Slice 1 — shared foundation (pure logic + tests)

| ID | Task | Spec req(s) | Files | Verify |
|----|------|-------------|-------|--------|
| S1.1 [x] | Create `shared/.../format/Formatters.kt`; lift `formatEpochMillis` + `formatGrams` (public). Delete `composeApp/.../ui/TimeFormat.kt`; update import sites (MealFormScreen, HistoryScreen, EditRecordScreen, FollowUpScreen) | Table Content Format; Chronological Ordering | `shared/.../format/Formatters.kt`; `ui/TimeFormat.kt`(del); `ui/{MealFormScreen,HistoryScreen,EditRecordScreen,FollowUpScreen}.kt` | `make test` |
| S1.2 [x] | Create `shared/.../export/PdfReportData.kt`: `PdfReportRow`, `PdfReportData`, pure `buildReportData(records, fromMillis, toMillis)` — filter in-range (inclusive), sort asc, map nulls→"—" | Table Content Format; Date-Range Filtering; Chronological Ordering | `shared/.../export/PdfReportData.kt` | `make test` |
| S1.3 [x] | Create `shared/.../export/PdfReportExporter.kt`: `PdfExportOutcome` (Success/Failure), `PdfReportExporter`, `ReportShareLauncher` interfaces | On-Device Generation; Caching & Sharing | `shared/.../export/PdfReportExporter.kt` | `make test` |
| S1.4 [x] | Create `shared/.../commonTest/.../export/PdfReportDataBuilderTest.kt`: inclusive bounds, asc sort, null→"—", grams rounding, date format | Date-Range Filtering; Chronological Ordering; Table Content Format | `shared/src/commonTest/kotlin/com/diabecarekids/app/export/PdfReportDataBuilderTest.kt` | `make test` |

## Slice 2 — android renderer + UI + wiring + tests

| ID | Task | Spec req(s) | Files | Verify |
|----|------|-------------|-------|--------|
| S2.1 [x] | Create `AndroidPdfReportExporter.kt`: `PdfDocument` A4 (595×842pt), 4-col table (Date 215 / PreBG 100 / 2hBG 100 / Carbs 100), manual pagination, header per page, `Typeface.DEFAULT`, write `pdf_exports/reporte_<epoch>.pdf` (clear dir first), return FileProvider content-uri | On-Device Generation; Constraint Enforcement; Caching | `composeApp/src/androidMain/kotlin/com/diabecarekids/app/export/AndroidPdfReportExporter.kt` | `make build` |
| S2.2 [x] | Modify `file_paths.xml`: add `<cache-path name="pdf_exports" path="pdf_exports/" />` | Caching & Sharing | `composeApp/src/androidMain/res/xml/file_paths.xml` | `make build` |
| S2.3 [x] | Create `AndroidReportShareLauncher.kt`: `ACTION_SEND` `application/pdf` + `FLAG_GRANT_READ_URI_PERMISSION` + chooser; swallow `ActivityNotFoundException` | Caching & Sharing | `composeApp/src/androidMain/kotlin/com/diabecarekids/app/export/AndroidReportShareLauncher.kt` | `make build` |
| S2.4 [x] | Modify `HistoryViewModel.kt`: `exportReport(from, to)` → buildReportData → exporter.export → sharePdf; add `isExporting`/`exportError` to `HistoryState` | Date-Range Filtering; Caching & Sharing | `composeApp/.../viewmodel/HistoryViewModel.kt` | `make test` |
| S2.5 [x] | Modify `HistoryScreen.kt`: TopAppBar "Export Medical Report" action + Material3 DateRangePicker (default last 7 days, `to = now`, `from = now - 6*86_400_000`); exporting/error UI | Date-Range Filtering | `composeApp/.../ui/HistoryScreen.kt` | `make build` |
| S2.6 [x] | Wire DI: `AppGraph.kt` inject `PdfReportExporter` + `ReportShareLauncher`; `MainActivity.kt` construct androidMain adapters | On-Device Generation | `composeApp/.../navigation/AppGraph.kt`; `composeApp/src/androidMain/kotlin/com/diabecarekids/app/MainActivity.kt` | `make build` |
| S2.7 [x] | Add `FakePdfReportExporter` + `FakeReportShareLauncher` to `Fakes.kt`; `HistoryViewModelTest.kt`: Success→sharePdf(uri) called; Failure→exportError set & share NOT called; isExporting toggles | Caching & Sharing | `composeApp/.../commonTest/.../viewmodel/{Fakes,HistoryViewModelTest}.kt` | `make test` |

## Notes

- Threat matrix: all five rows N/A — no RED-test tasks. Safe-behavior tests covered in S2.7 (failure never shares; no handler swallowed).
- Open questions (design): empty-range → header-only PDF (default allow); A4 vs Letter; Spanish-only header (default yes).
- Slice boundary for work-unit commits: commit after S1.4 (`make test` green) before S2.1.
