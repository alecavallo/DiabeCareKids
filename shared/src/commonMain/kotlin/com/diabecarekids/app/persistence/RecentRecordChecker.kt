package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.TipoComida

/**
 * Detects whether a record for [TipoComida] exists within a window of epoch
 * millis. The engine uses it for Post-Logging Suppression (a reminder is
 * suppressed when the user already logged the meal recently).
 */
fun interface RecentRecordWindowCheck {
    /** True when a record of [tipo] exists with fecha_hora_inicio in `[from, until]` (inclusive). */
    suspend fun hasRecent(tipo: TipoComida, from: Long, until: Long): Boolean
}

/**
 * [RecentRecordWindowCheck] backed by [PersistenceStore.getAll].
 */
class PersistenceRecentRecordWindowCheck(
    private val store: PersistenceStore,
) : RecentRecordWindowCheck {
    override suspend fun hasRecent(tipo: TipoComida, from: Long, until: Long): Boolean =
        store.getAll().any { it.tipo_comida == tipo && it.fecha_hora_inicio in from..until }
}
