package com.diabecarekids.app.export

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * [ReportShareLauncher] that fires a system share sheet for a generated PDF
 * (CAP-004 Caching & Sharing). `ACTION_SEND` with `application/pdf`, a
 * read-URI grant on the FileProvider URI, and a chooser so the guardian picks
 * the target. The PDF stays in app-private cache; PHI leaves the device only
 * through a user-chosen target.
 *
 * Safe behavior (spec): if no handler can receive the intent, the
 * [ActivityNotFoundException] is swallowed and the file remains cached — the
 * export never crashes the flow.
 */
class AndroidReportShareLauncher(private val activity: Activity) : ReportShareLauncher {

    override fun sharePdf(uri: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, "Compartir informe"))
        } catch (_: ActivityNotFoundException) {
            // No handler available — file remains cached for a later attempt.
        }
    }
}
