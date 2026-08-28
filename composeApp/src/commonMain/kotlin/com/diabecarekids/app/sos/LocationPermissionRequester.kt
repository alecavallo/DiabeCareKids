package com.diabecarekids.app.sos

/**
 * Requests the runtime location permission from the host OS. Design decision #6
 * (REQ-SOS-003): the request happens on SOS screen ENTRY, never at trigger time,
 * so that a denial at trigger → null coordinates while the alert still fires.
 *
 * Implemented by an Android adapter in androidMain ([FusedLocationProvider]).
 * The composable screen calls [requestOnEntry] from a LaunchedEffect; it is a
 * fire-and-forget launch of the system permission dialog and never blocks the
 * SOS gesture.
 */
interface LocationPermissionRequester {
    fun requestOnEntry()
}
