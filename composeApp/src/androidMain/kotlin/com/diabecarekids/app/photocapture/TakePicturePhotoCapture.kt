package com.diabecarekids.app.photocapture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.diabecarekids.app.platform.PhotoCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * The manifest declares CAMERA, but on minSdk 24 launching the camera contract
 * without the runtime permission throws [SecurityException]. So [takePhoto]
 * requests the CAMERA runtime permission (via a [ActivityResultContracts.RequestPermission]
 * launcher) before launching TakePicture and only launches when granted; a denial
 * surfaces through [cameraDenied] instead of crashing.
 */
class TakePicturePhotoCapture(
    private val context: Context,
) : PhotoCapture {

    private var launcher: ActivityResultLauncher<Uri>? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingUri: Uri? = null
    private var pendingResult: CompletableDeferred<String?>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    private val _cameraDenied = MutableStateFlow(false)
    override val cameraDenied: StateFlow<Boolean> = _cameraDenied.asStateFlow()

    override fun consumeCameraDenied(): Boolean {
        val denied = _cameraDenied.value
        _cameraDenied.value = false
        return denied
    }

    /**
     * Registers the activity-result launchers. Call once from the owning
     * [ComponentActivity] (e.g. MainActivity) before RESUMED.
     */
    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success -> onTakePictureResult(success) }
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> pendingPermission?.complete(granted) }
    }

    override suspend fun takePhoto(): String? {
        val launcher = launcher ?: return null
        if (pendingResult != null) return null // a capture is already in progress

        // Never launch the camera contract without the runtime permission (ID-CAM).
        if (!hasCameraPermission() && !awaitCameraPermission()) {
            _cameraDenied.value = true
            return null
        }

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

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private suspend fun awaitCameraPermission(): Boolean {
        val launcher = permissionLauncher ?: return false
        // A permission request may already be in flight (a concurrent takePhoto).
        // Reuse it rather than replacing the deferred, which would leak the first
        // awaiter's suspended coroutine (ID-CAM-CONCURRENT).
        pendingPermission?.let { existing ->
            return existing.await()
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingPermission = deferred
        launcher.launch(Manifest.permission.CAMERA)
        return try {
            deferred.await()
        } finally {
            if (pendingPermission === deferred) pendingPermission = null
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
