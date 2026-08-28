package com.diabecarekids.app.navigation

import com.diabecarekids.app.domain.RegistroComida

/**
 * App routes. Sealed-route state in [App] drives navigation (design DECISION:
 * no nav-compose dependency). [History], [AddPastRecord] and [EditRecord] back
 * the Advanced View / historical record management screens (CAP-005).
 */
sealed interface Route {
    /** T0 — new meal entry form. */
    data object T0 : Route

    /** T2 — postprandial follow-up for the just-saved [RegistroComida]. */
    data class T2(val registro: RegistroComida) : Route

    /** SOS — hold-to-activate emergency alert (CAP-001). */
    data object Sos : Route

    /** Advanced View history timeline (CAP-005, R2). */
    data object History : Route

    /** Historical past-record form (CAP-005, R1/R4). */
    data object AddPastRecord : Route

    /** Edit an existing historical record (CAP-005, R3). */
    data class EditRecord(val registro: RegistroComida) : Route
}
