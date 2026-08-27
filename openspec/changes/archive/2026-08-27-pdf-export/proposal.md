# Proposal: On-Device Clinical PDF Export (CAP-004)

## Intent

Guardians need to generate and share clinical meal-log reports with pediatric endocrinologists directly from their devices. In compliance with INV-004, PDF generation must be 100% on-device, offline, and private, eliminating third-party cloud dependencies for Protected Health Information (PHI).

## Scope

### In Scope
- `PdfReportExporter` interface and pure domain report row models (`PdfReportRow`, `PdfReportData`) in `commonMain`.
- Date range filtering (epoch millis) and medical report formatting (Date, Pre-Meal BG, 2h BG, Real Carbs) in `commonMain`.
- Platform implementation `AndroidPdfReportExporter` in `androidMain` using native `android.graphics.pdf.PdfDocument`.
- PDF generation to application cache directory (`reports/`) and sharing via `ACTION_SEND` Intent with `FileProvider`.
- UI trigger in `HistoryScreen` (TopAppBar action) with a Material3 `DateRangePicker` modal.
- `HistoryViewModel` integration for triggering export flow and handling state.

### Out of Scope
- Third-party PDF rendering libraries (e.g. iText, PDFBox).
- Cloud/server-side PDF generation or network sync.
- Photo thumbnails inside PDF (deferred to future change to keep line budget and memory footprint bounded).
- Standalone Dashboard screen (export lives directly in History screen).
- Custom embedded typography (uses platform default system fonts).

## Capabilities

### New Capabilities
- `pdf-export`: Generates structured clinical PDF reports from historical meal logs within a selected date range and shares them via platform intents.

### Modified Capabilities
None

## Approach

- **Domain/Common Layer**: `PdfReportExporter` contract in `shared/commonMain`. Pure formatting helper aggregates `RegistroComida` list, filters by start/end epoch, and formats values into tabular data.
- **Android Platform Layer**: `AndroidPdfReportExporter` in `composeApp/androidMain` renders structured A4/Letter pages using `android.graphics.pdf.PdfDocument`, `Canvas`, and `Paint`. Manages page breaks and writes output to `cacheDir/reports/`.
- **Sharing**: Exposes cached PDF via `FileProvider` (`res/xml/file_paths.xml`) and invokes `ACTION_SEND` Intent with MIME `application/pdf`.
- **UI/Presentation**: `HistoryScreen` exposes an "Export" action in `TopAppBar`. Selecting a range triggers `HistoryViewModel.exportReport(from, to)`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/src/commonMain/kotlin/.../export/` | New | `PdfReportExporter` interface, `PdfReportData`, pure builders |
| `composeApp/src/androidMain/kotlin/.../export/` | New | `AndroidPdfReportExporter` (`PdfDocument` + `ACTION_SEND`) |
| `composeApp/src/androidMain/res/xml/file_paths.xml` | Modified | Add `reports/` cache path for FileProvider |
| `composeApp/src/commonMain/kotlin/.../viewmodel/HistoryViewModel.kt` | Modified | Add export state & trigger methods |
| `composeApp/src/commonMain/kotlin/.../ui/HistoryScreen.kt` | Modified | Add Export button & `DateRangePicker` dialog |
| `composeApp/src/commonMain/kotlin/.../navigation/AppGraph.kt` | Modified | Wire `PdfReportExporter` dependency |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Multi-page layout overflow in `PdfDocument` | Medium | Calculate row heights deterministically; insert explicit page breaks before table overflow. |
| Large memory consumption during generation | Low | Process records in a single sequential pass without retaining heavy bitmaps (thumbnails deferred). |
| File sharing permission issues on Android | Low | Use standard `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION`. |

## Rollback Plan

Revert the change commit. Since persistence schema is untouched and PDF files reside in transient cache, rollback has zero database migration impact.

## Dependencies

- Android SDK `android.graphics.pdf.PdfDocument` (API 21+)
- Existing `FileProvider` configuration

## Success Criteria

- [ ] Guardians can select a date range and generate an on-device PDF of meal records.
- [ ] Generated PDF includes date/time, Pre-meal BG, 2h BG, and Real Carbs in a clean table layout.
- [ ] System sharing sheet opens with the generated PDF attached.
- [ ] Works completely offline with zero external network calls.
