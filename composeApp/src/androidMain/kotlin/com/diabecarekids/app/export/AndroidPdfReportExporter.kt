package com.diabecarekids.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.diabecarekids.app.platform.epochMillisNow
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PdfReportExporter] built on native `android.graphics.pdf.PdfDocument`
 * (INV-004: entirely on-device, no network; Constraint Enforcement: default
 * [Typeface.DEFAULT] only, no custom fonts). Renders a fully-prepared
 * [PdfReportData] as an A4 portrait document (595 x 842pt), writes it to the
 * `pdf_exports/` app-cache directory (clearing stale files first) and returns
 * the FileProvider content-URI.
 *
 * Layout (design DECISION): 40pt margins, 4-column table — Date 215 /
 * Pre-Meal BG 100 / 2h BG 100 / Real Carbs 100pt — 24pt rows, header repeated
 * on every page. Deterministic pagination: before each row, if the cursor
 * would pass the page bottom, the page is finished and a fresh page with a
 * repeated header is started.
 */
class AndroidPdfReportExporter(private val context: Context) : PdfReportExporter {

    override suspend fun export(data: PdfReportData): PdfExportOutcome = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, PDF_EXPORTS_DIR).apply { mkdirs() }
            // Clear stale reports first — cache is transient (design DECISION), prevents growth.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "reporte_${epochMillisNow()}.pdf")
            render(data, file)
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            PdfExportOutcome.Success(uri.toString(), data.rows.size)
        } catch (e: Exception) {
            PdfExportOutcome.Failure(e.message ?: "Error generando el PDF")
        }
    }

    private fun render(data: PdfReportData, file: File) {
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            val rowHeight = 24f
            val headerBaseline = margin + 42f
            val contentBottom = pageHeight - margin

            // Column X origins (4-col table, fixed widths from the design).
            val dateX = margin
            val preX = dateX + DATE_WIDTH
            val twoX = preX + COL_WIDTH
            val carbsX = twoX + COL_WIDTH

            val titlePaint = Paint().apply {
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
                color = Color.BLACK
            }
            val rangePaint = Paint().apply {
                textSize = 9f
                color = Color.DKGRAY
            }
            val headerPaint = Paint().apply {
                typeface = Typeface.DEFAULT_BOLD
                textSize = 11f
                color = Color.BLACK
            }
            val rowPaint = Paint().apply {
                textSize = 10f
                color = Color.BLACK
            }

            var page: PdfDocument.Page? = null
            var cursorY = 0f

            fun startPage(): PdfDocument.Page {
                val pageNumber = document.pages.size
                val p = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create(),
                )
                val canvas = p.canvas
                // Title block (repeated header per page).
                canvas.drawText(data.title, margin, margin + 16f, titlePaint)
                canvas.drawText(data.rangeLabel, margin, margin + 30f, rangePaint)
                canvas.drawLine(margin, margin + 36f, pageWidth - margin, margin + 36f, headerPaint)
                // Column headers.
                canvas.drawText("Fecha", dateX, headerBaseline, headerPaint)
                canvas.drawText("BG Pre", preX, headerBaseline, headerPaint)
                canvas.drawText("BG 2h", twoX, headerBaseline, headerPaint)
                canvas.drawText("Carbos Reales", carbsX, headerBaseline, headerPaint)
                cursorY = headerBaseline + rowHeight
                return p
            }

            page = startPage()
            for (row in data.rows) {
                // Pagination check: start a new page when the row would overflow the bottom.
                if (cursorY + rowHeight > contentBottom) {
                    document.finishPage(page!!)
                    page = startPage()
                }
                val canvas = page!!.canvas
                canvas.drawText(row.dateText, dateX, cursorY, rowPaint)
                canvas.drawText(row.preMealBgText, preX, cursorY, rowPaint)
                canvas.drawText(row.twoHourBgText, twoX, cursorY, rowPaint)
                canvas.drawText(row.realCarbsText, carbsX, cursorY, rowPaint)
                cursorY += rowHeight
            }
            document.finishPage(page!!)

            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private companion object {
        const val PDF_EXPORTS_DIR = "pdf_exports"
        const val DATE_WIDTH = 215f
        const val COL_WIDTH = 100f
    }
}
