package com.diabecarekids.app.navigation

import com.diabecarekids.app.domain.RegistroComida

/**
 * App routes. Only two screens exist in this change; sealed-route state in
 * [App] drives navigation (design DECISION: no nav-compose dependency).
 */
sealed interface Route {
    /** T0 — new meal entry form. */
    data object T0 : Route

    /** T2 — postprandial follow-up for the just-saved [RegistroComida]. */
    data class T2(val registro: RegistroComida) : Route

    /** SOS — hold-to-activate emergency alert (CAP-001). */
    data object Sos : Route
}
