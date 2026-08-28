package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.Emergencia

/**
 * Persistence abstraction for [Emergencia] records (REQ-SOS-002). A
 * Firestore-backed implementation is deferred to a later change;
 * [InMemoryEmergenciaStore] is the production store for this change AND the
 * offline test double (mirrors the meal-logging [PersistenceStore] pattern).
 *
 * [update] is reserved for a future RESUELTA lifecycle; nothing in this change
 * transitions a record out of ACTIVE.
 */
interface EmergenciaStore {
    suspend fun save(emergencia: Emergencia)
    suspend fun get(id: String): Emergencia?
    suspend fun update(emergencia: Emergencia)
}
