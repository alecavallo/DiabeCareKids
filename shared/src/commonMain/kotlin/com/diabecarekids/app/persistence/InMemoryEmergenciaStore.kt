package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.Emergencia
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory [EmergenciaStore] backed by a [Mutex]-guarded [Map]
 * (REQ-SOS-002). Mirrors the meal-logging [InMemoryPersistenceStore] pattern.
 */
class InMemoryEmergenciaStore : EmergenciaStore {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, Emergencia>()

    override suspend fun save(emergencia: Emergencia) {
        mutex.withLock { records[emergencia.id] = emergencia }
    }

    override suspend fun get(id: String): Emergencia? =
        mutex.withLock { records[id] }

    override suspend fun update(emergencia: Emergencia) {
        mutex.withLock {
            check(records.containsKey(emergencia.id)) { "No emergency with id ${emergencia.id}" }
            records[emergencia.id] = emergencia
        }
    }
}
