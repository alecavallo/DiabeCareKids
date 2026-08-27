package com.diabecarekids.app.photocapture

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.diabecarekids.app.platform.PhotoCapture
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/**
 * [PhotoCapture] implementation using the system camera via
 * [ActivityResultContracts.TakePicture] plus a FileProvider-backed content URI.
 *
 * This follows the design DECISION (TakePicture + FileProvider, zero new
 * dependency) rather than CameraX. The "CameraXPhotoCapture" name in the design
 * file-changes table is stale — the decision table is authoritative.
 *
 * Returns the captured image's content-URI string, or null when the user cancels
 * or no photo is taken (INV-005 / REQ-MEAL-004).
 */
class TakePicturePhotoCapture(
    private val context: Context,
) : PhotoCapture {

    private var launcher: ActivityResultLauncher<Uri>? = null
    private var pendingUri: Uri? = null
    private var pendingResult: CompletableDeferred<String?>? = null

    /**
     * Registers the activity-result launcher. Call once from the owning
     * [ComponentActivity] (e.g. MainActivity) before RESUMED.
     */
    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success -> onTakePictureResult(success) }
    }

    override suspend fun takePhoto(): String? {
        val launcher = launcher ?: return null
        if (pendingResult != null) return null // a capture is already in progress

        val uri = createImageUri()
        pendingUri = uri
        val deferred = CompletableDeferred<String?>()
        pendingResult = deferred
        launcher.launch(uri)

        return try {
            // Suspends until onTakePictureResult completes the deferred.
            deferred.await()
        } finally {
            pendingResult = null
            pendingUri = null
        }
    }

    private fun onTakePictureResult(success: Boolean) {
        val deferred = pendingResult ?: return
        val uri = pendingUri
        deferred.complete(if (success && uri != null) uri.toString() else null)
    }

    private fun createImageUri(): Uri {
        val dir = File(context.cacheDir, MEAL_PHOTOS_DIR).apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    private companion object {
        const val MEAL_PHOTOS_DIR = "meal_photos"
    }
}
