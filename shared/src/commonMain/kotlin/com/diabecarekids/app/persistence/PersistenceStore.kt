package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.RegistroComida

/**
 * Persistence abstraction (REQ-MEAL-005). A Firestore-backed implementation is
 * deferred to a later change; [InMemoryPersistenceStore] is the production
 * store for this change AND the offline test double.
 */
interface PersistenceStore {
    suspend fun save(registro: RegistroComida)
    suspend fun get(id: String): RegistroComida?
    suspend fun update(registro: RegistroComida)
    suspend fun delete(id: String)

    /**
     * All stored records. Used by reminder suppression to detect a recent
     * record for a given meal type (Post-Logging Suppression).
     */
    suspend fun getAll(): List<RegistroComida>
}
