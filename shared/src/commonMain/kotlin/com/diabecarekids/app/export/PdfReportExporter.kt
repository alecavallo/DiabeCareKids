package com.diabecarekids.app.export

/**
 * Outcome of a [PdfReportExporter.export] call. [Success] carries the
 * FileProvider content-URI string of the written PDF and the row count;
 * [Failure] carries a human-readable message. The exporter NEVER shares on its
 * own — the ViewModel inspects the outcome and only invokes the
 * [ReportShareLauncher] on success (safe-behavior contract, CAP-004).
 */
sealed interface PdfExportOutcome {
    data class Success(val uri: String, val rowCount: Int) : PdfExportOutcome
    data class Failure(val message: String) : PdfExportOutcome
}

/**
 * Platform seam: renders a fully-prepared [PdfReportData] to a PDF file and
 * returns its FileProvider content-URI. Implemented in composeApp androidMain
 * using native `android.graphics.pdf.PdfDocument` (INV-004 On-Device Generation
 * / Constraint Enforcement — default fonts only, zero new dependencies).
 */
interface PdfReportExporter {
    suspend fun export(data: PdfReportData): PdfExportOutcome
}

/**
 * Platform seam: fires a system share intent (`ACTION_SEND`, `application/pdf`,
 * read grant on the URI) with a chooser so the guardian picks a target. The
 * PDF stays in app-private cache; PHI leaves the device only through a
 * user-chosen target. Implemented in androidMain.
 */
interface ReportShareLauncher {
    fun sharePdf(uri: String)
}
