package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.FoodItem
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.UbicacionGps
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.Haptics
import com.diabecarekids.app.platform.LocationProvider
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.CarbResolution
import com.diabecarekids.app.nutrition.NutritionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Nutrition facade stub for ViewModel tests. */
class FakeNutritionRepository(
    var resolution: CarbResolution = CarbResolution.ManualRequired,
    var suggestions: List<FoodItem> = emptyList(),
) : NutritionRepository {
    override suspend fun resolveCarbs(query: String): CarbResolution = resolution
    override suspend fun suggestFoods(query: String): List<FoodItem> = suggestions
}

/** In-memory [PersistenceStore] double that records save/update operations. */
class FakePersistenceStore : PersistenceStore {
    val saved = mutableListOf<RegistroComida>()
    val updated = mutableListOf<RegistroComida>()

    override suspend fun save(registro: RegistroComida) { saved += registro }
    override suspend fun get(id: String): RegistroComida? =
        saved.firstOrNull { it.id == id } ?: updated.firstOrNull { it.id == id }
    override suspend fun update(registro: RegistroComida) {
        val idx = saved.indexOfFirst { it.id == registro.id }
        if (idx >= 0) saved[idx] = registro else updated += registro
    }
    override suspend fun delete(id: String) {
        saved.removeAll { it.id == id }
        updated.removeAll { it.id == id }
    }
}

/** [PhotoCapture] stub. [uri] controls what [takePhoto] returns (null = cancel).
 *  When [results] is non-empty, each [takePhoto] call consumes the next entry
 *  (e.g. to simulate a capture followed by a cancelled capture). */
class FakePhotoCapture(private val uri: String? = "file://captured.jpg") : PhotoCapture {
    var calls = 0
    val results = mutableListOf<String?>()

    private val _cameraDenied = MutableStateFlow(false)
    override val cameraDenied: StateFlow<Boolean> = _cameraDenied.asStateFlow()

    override fun consumeCameraDenied(): Boolean {
        val denied = _cameraDenied.value
        _cameraDenied.value = false
        return denied
    }

    /** Simulates the user denying the CAMERA runtime permission on the next call. */
    fun markCameraDenied() {
        _cameraDenied.value = true
    }

    override suspend fun takePhoto(): String? {
        calls++
        return if (results.isNotEmpty()) results.removeAt(0) else uri
    }
}

/** [PostprandialAlarmScheduler] recording stub. */
class FakeAlarmScheduler : PostprandialAlarmScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun schedule(mealId: String) { scheduled += mealId }
    override fun cancel(mealId: String) { cancelled += mealId }
}

/** [LocationProvider] stub. [result] controls what [currentLocation] returns (null = denied/unavailable). */
class FakeLocationProvider(var result: UbicacionGps? = null) : LocationProvider {
    var calls = 0
    override suspend fun currentLocation(): UbicacionGps? {
        calls++
        return result
    }
}

/** [Haptics] recording stub. */
class FakeHaptics : Haptics {
    var vibrated = false
    var calls = 0
    override fun vibrateSosTriggered() {
        vibrated = true
        calls++
    }
}

