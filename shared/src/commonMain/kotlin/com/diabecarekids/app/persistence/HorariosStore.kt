package com.diabecarekids.app.persistence

import com.diabecarekids.app.domain.ConfiguracionHorarios
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Read/write access to the scheduled reminder configuration (Eligibility Gate).
 * Seeded with defaults by [InMemoryHorariosStore]; a persistent adapter is
 * deferred to a later change (the engine consumes the seeded default in this
 * slice, per design D7).
 */
interface HorariosStore {
    suspend fun load(): ConfiguracionHorarios
    suspend fun save(config: ConfiguracionHorarios)
}

/** Default reminder configuration seed: 08:00 / 12:30 / 17:00 / 21:00, 15 min, active. */
val defaultConfiguracionHorarios = ConfiguracionHorarios(
    horario_desayuno = "08:00",
    horario_almuerzo = "12:30",
    horario_merienda = "17:00",
    horario_cena = "21:00",
    ventana_anticipacion_minutos = 15,
    recordatorios_activos = true,
)

/**
 * Thread-safe in-memory [HorariosStore] backed by a [kotlinx.coroutines.sync.Mutex].
 * Mirrors the [InMemoryPersistenceStore] concurrency pattern.
 */
class InMemoryHorariosStore(initial: ConfiguracionHorarios = defaultConfiguracionHorarios) : HorariosStore {
    private val mutex = Mutex()
    private var current: ConfiguracionHorarios = initial

    override suspend fun load(): ConfiguracionHorarios = mutex.withLock { current }

    override suspend fun save(config: ConfiguracionHorarios) {
        mutex.withLock { current = config }
    }
}
