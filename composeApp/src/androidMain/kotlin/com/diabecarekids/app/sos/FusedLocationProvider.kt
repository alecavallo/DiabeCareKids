package com.diabecarekids.app.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.diabecarekids.app.domain.UbicacionGps
import com.diabecarekids.app.platform.LocationProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android [LocationProvider] backed by Google Play services location
 * (FusedLocationProviderClient), plus [LocationPermissionRequester] for the
 * design decision #6 "request on SOS screen entry" flow.
 *
 * Composition-of/delegate pattern mirroring [com.diabecarekids.app.photocapture.TakePicturePhotoCapture]:
 * the permission [ActivityResultLauncher] is registered once from the owning
 * [ComponentActivity] (MainActivity) via [register]; [requestOnEntry] fires the
 * system permission dialog on screen entry, and [currentLocation] returns null
 * whenever the runtime permission is denied — so the SOS alert never blocks on
 * a missing fix (REQ-SOS-003).
 *
 * Uses [getCurrentLocation] on API 30+ (with a cancellation token) and the
 * legacy [getLastLocation] below it. Coordinates map to the shared
 * [UbicacionGps] value.
 */
class FusedLocationProvider(
    private val context: Context,
) : LocationProvider, LocationPermissionRequester {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    /**
     * Registers the permission-result launcher. Call once from the owning
     * [ComponentActivity] (MainActivity) before RESUMED.
     */
    fun register(activity: ComponentActivity) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> pendingPermission?.complete(granted) }
    }

    /** Fires the runtime location request (design decision #6: screen entry). */
    override fun requestOnEntry() {
        if (!hasLocationPermission()) {
            permissionLauncher?.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override suspend fun currentLocation(): UbicacionGps? {
        if (!hasLocationPermission()) return null
        val location = requestFix() ?: return null
        return UbicacionGps(
            latitud = location.latitude,
            longitud = location.longitude,
            precision_metros = location.accuracy.toDouble(),
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission") // guarded by hasLocationPermission()
    private suspend fun requestFix(): Location? = withContext(Dispatchers.Main.immediate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cts = CancellationTokenSource()
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .awaitResult()
        } else {
            @Suppress("DEPRECATION")
            fusedLocationClient.lastLocation.awaitResult()
        }
    }

    /** Bridges a Play-services [Task] to a suspend result; null on failure. */
    private suspend fun Task<Location>.awaitResult(): Location? =
        suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
            }
        }
}
