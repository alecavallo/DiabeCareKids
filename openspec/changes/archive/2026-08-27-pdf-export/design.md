# Design: On-Device Clinical PDF Export (CAP-004)

## Technical Approach

Layered on the advanced-history baseline (CAP-005). Pure, testable report-data builder in `shared` (filter + sort + format); platform renderer in `composeApp` androidMain using native `android.graphics.pdf.PdfDocument`; sharing via the existing FileProvider + `ACTION_SEND`. UI: HistoryScreen TopAppBar action + Material3 DateRangePicker (default last 7 days). Zero new dependencies. Follows the established seam pattern (interface in shared, impl in composeApp androidMain, manual DI via AppGraph) — same as PhotoCapture.

## Architecture Decisions

### Decision: Two-step build (pure) + render (platform)
| Option | Tradeoff | Decision |
|---|---|---|
| Exporter receives raw records; formats in androidMain | Mapping logic untestable in commonTest | ❌ |
| `buildReportData(records, from, to)` pure in shared; `PdfReportExporter.export(data)` renders | All filter/sort/format logic covered by commonTest | ✅ |

### Decision: Exporter returns URI; VM triggers share via platform launcher
`AndroidPdfReportExporter` writes the PDF and returns `PdfExportOutcome.Success(uri, rowCount)`. Separate `ReportShareLauncher` interface (shared) + `AndroidReportShareLauncher(activity)` (androidMain) fires ACTION_SEND. The VM orchestrates. Activity-scoped launching avoids `FLAG_ACTIVITY_NEW_TASK` hacks and keeps DI explicit.

### Decision: Lift formatters to shared (no duplication)
`formatEpochMillis` (Hinnant civil-from-days) and `formatGrams` (1-decimal, ID-ROUND) move to `shared/.../format/Formatters.kt` (public); composeApp `ui/TimeFormat.kt` deleted. Only 2 + 5 call sites. Rejected: duplicating into the export package — two copies of date math and rounding rules in a medical report risk divergence; report values must equal screen values.

### Decision: Manual PdfDocument layout, A4, default fonts
A4 portrait (595×842pt), 40pt margins, columns Date 215 / PreBG 100 / 2hBG 100 / Carbs 100pt, row height 24pt, header repeated per page. Deterministic pagination: before each row, if cursor + rowHeight > page bottom → `finishPage()`, new page, redraw header. `Paint(Typeface.DEFAULT)` only (spec constraint). Rejected: iText/PDFBox — violates INV-004 minimal-dependency posture.

### Decision: Extend HistoryViewModel (no new ExportViewModel)
It already holds the full dataset, created per navigation. Adds `isExporting`/`exportError` + `exportReport(from, to)`. Rejected: separate ExportViewModel — duplicates store wiring for one action.

### Decision: Timestamped filename + dir cleanup
`pdf_exports/reporte_<epochMillis>.pdf`; clear older files in dir before write (cache is transient; prevents growth). Proposal's `reports/` dir superseded by `pdf_exports/` per scope note.

## Data Flow

```
Guardian → HistoryScreen: top-bar action → DateRangePicker (default last 7d; end-of-day +86_399_999ms)
HistoryScreen → HistoryViewModel.exportReport(from, to)
VM → buildReportData(state.records, from, to)     // shared, pure: in-range filter, asc sort, row map
VM → exporter.export(data)                        // androidMain: PdfDocument, withContext(Default)
VM ← PdfExportOutcome.Success(uri, rows)          // content://<pkg>.fileprovider/pdf_exports/...
VM → shareLauncher.sharePdf(uri)                  // ACTION_SEND, application/pdf, grant-read, chooser
System → Guardian: share sheet
Failure path: exportError set; share NOT invoked.
```

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/.../format/Formatters.kt` | Create | Public formatEpochMillis + formatGrams (lifted) |
| `shared/.../export/PdfReportData.kt` | Create | Row/data models + buildReportData |
| `shared/.../export/PdfReportExporter.kt` | Create | PdfExportOutcome + exporter & share-launcher interfaces |
| `shared/.../commonTest/.../export/PdfReportDataBuilderTest.kt` | Create | Builder unit tests |
| `composeApp/.../androidMain/.../export/AndroidPdfReportExporter.kt` | Create | PdfDocument renderer + pagination + cache write |
| `composeApp/.../androidMain/.../export/AndroidReportShareLauncher.kt` | Create | ACTION_SEND + chooser |
| `composeApp/.../androidMain/res/xml/file_paths.xml` | Modify | + `<cache-path name="pdf_exports" path="pdf_exports/" />` |
| `composeApp/.../ui/TimeFormat.kt` | Delete | Lifted to shared |
| `composeApp/.../ui/MealFormScreen.kt` | Modify | Remove formatGrams, import from shared |
| `composeApp/.../ui/{History,EditRecord,FollowUp}Screen.kt` | Modify | Import formatters from shared |
| `composeApp/.../viewmodel/HistoryViewModel.kt` | Modify | exportReport + export state |
| `composeApp/.../ui/HistoryScreen.kt` | Modify | Top-bar action + DateRangePicker + exporting UI |
| `composeApp/.../navigation/AppGraph.kt` | Modify | Inject exporter + share launcher |
| `composeApp/.../androidMain/.../MainActivity.kt` | Modify | Construct androidMain adapters |
| `composeApp/.../commonTest/.../Fakes.kt` | Modify | FakePdfReportExporter + FakeReportShareLauncher |
| `composeApp/.../commonTest/.../HistoryViewModelTest.kt` | Modify | Export flow tests |

## Interfaces / Contracts

```kotlin
data class PdfReportRow(val dateText: String, val preMealBgText: String,
    val twoHourBgText: String, val realCarbsText: String)
data class PdfReportData(val title: String, val rangeLabel: String, val rows: List<PdfReportRow>)
fun buildReportData(records: List<RegistroComida>, fromMillis: Long, toMillis: Long): PdfReportData

sealed interface PdfExportOutcome {
    data class Success(val uri: String, val rowCount: Int) : PdfExportOutcome
    data class Failure(val message: String) : PdfExportOutcome
}
interface PdfReportExporter { suspend fun export(data: PdfReportData): PdfExportOutcome }
interface ReportShareLauncher { fun sharePdf(uri: String) }
```

Nullables render as "—" (matches HistoryRow UI). `HistoryState` += `isExporting`, `exportError`. New UI strings Spanish (matches existing screens). Default range: `from = epochMillisNow() - 6 * 86_400_000`, `to = epochMillisNow()`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit shared | Inclusive range bounds; ascending sort; null→"—"; grams rounding; date format | PdfReportDataBuilderTest (pure, no coroutines) |
| Unit composeApp | Success → sharePdf(uri) called; Failure → exportError set and share NOT called; isExporting toggles | HistoryViewModelTest + new Fakes, runTest |
| Android integration | Real PDF renders; multi-page >30 rows; chooser opens | Manual only (no instrumentation infra) |

## Threat Matrix

N/A — no shell, subprocess, routing change, VCS/PR automation, or documentation-execution boundary; all five matrix rows are N/A for this change. Data-exit note: PHI leaves the device only through a user-chosen ACTION_SEND target; the file stays in app-private cacheDir exposed via FileProvider single-URI read-only grant. Safe behavior: export failure never triggers share; no intent handler → ActivityNotFoundException swallowed, file remains cached.

## Migration / Rollout

No migration. Rollback = revert commit (cache files transient; file_paths.xml additive).

## Delivery Forecast

~540 authored lines: shared format lift 25, shared model/builder/interfaces 90, shared tests 60, androidMain renderer+launcher 180, file_paths 4, VM 40, HistoryScreen 70, AppGraph/MainActivity 25, composeApp tests 45. Fits the session's 800-line review budget → single PR. Pre-cut fallback slices: S1 shared pure logic + tests (~175), S2 android renderer + UI + wiring (~365).

## Open Questions

- [ ] Empty-range export: render header-only PDF or disable the action? Spec silent; default: allow header-only.
- [ ] A4 chosen (AR locale); confirm vs Letter.
- [ ] PDF title/header Spanish-only (matches UI)? Default: yes.
