package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.RegistroComida
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory store backed by a [Mutex]-guarded [Map].
 *
 * Production store for this change AND the offline test double (REQ-MEAL-005).
 */
class InMemoryPersistenceStore : PersistenceStore {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, RegistroComida>()

    override suspend fun save(registro: RegistroComida) {
        mutex.withLock { records[registro.id] = registro }
    }

    override suspend fun get(id: String): RegistroComida? =
        mutex.withLock { records[id] }

    override suspend fun update(registro: RegistroComida) {
        mutex.withLock {
            check(records.containsKey(registro.id)) { "No record with id ${registro.id}" }
            records[registro.id] = registro
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock { records.remove(id) }
    }
}
