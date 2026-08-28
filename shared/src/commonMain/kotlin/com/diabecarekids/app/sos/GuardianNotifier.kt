package com.diabecarekids.app.sos

import com.diabecarekids.app.domain.Emergencia
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dispatches a high-priority alert to all of the patient's guardians
 * (REQ-SOS-004). The real Firestore/FCM transport is deferred to a later
 * change; [InMemoryGuardianNotifier] is the production placeholder AND the
 * offline test double (mirrors the InMemory-as-prod precedent).
 */
interface GuardianNotifier {
    suspend fun notifyAllGuardians(emergencia: Emergencia)
}

/** In-memory [GuardianNotifier] recording every dispatched alert for assertions. */
class InMemoryGuardianNotifier : GuardianNotifier {
    private val mutex = Mutex()
    private val sent = mutableListOf<Emergencia>()

    override suspend fun notifyAllGuardians(emergencia: Emergencia) {
        mutex.withLock { sent.add(emergencia) }
    }

    /** Snapshot of every [Emergencia] dispatched so far. */
    suspend fun dispatched(): List<Emergencia> = mutex.withLock { sent.toList() }

    /** Clears the dispatch log. Call between tests for isolation. */
    suspend fun clear() {
        mutex.withLock { sent.clear() }
    }
}
