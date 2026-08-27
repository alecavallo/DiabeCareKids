package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.FoodItem
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.CarbResolution
import com.diabecarekids.app.nutrition.NutritionRepository

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

/** [PhotoCapture] stub. [uri] controls what [takePhoto] returns (null = cancel). */
class FakePhotoCapture(private val uri: String? = "file://captured.jpg") : PhotoCapture {
    var calls = 0
    override suspend fun takePhoto(): String? {
        calls++
        return uri
    }
}

/** [PostprandialAlarmScheduler] recording stub. */
class FakeAlarmScheduler : PostprandialAlarmScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun schedule(mealId: String) { scheduled += mealId }
    override fun cancel(mealId: String) { cancelled += mealId }
}
