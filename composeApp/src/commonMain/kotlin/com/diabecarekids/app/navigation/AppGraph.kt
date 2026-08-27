package com.diabecarekids.app.navigation

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.NutritionRepository
import com.diabecarekids.app.viewmodel.FollowUpViewModel
import com.diabecarekids.app.viewmodel.MealFormViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Manual composition root (design DECISION: manual constructor injection, no DI
 * lib). Android-only platform adapters ([PhotoCapture], [PostprandialAlarmScheduler])
 * are injected from the platform entry point (MainActivity); [NutritionRepository]
 * and [PersistenceStore] come from the shared module.
 *
 * [scope] drives the ViewModels' internal coroutines. The Android entry point
 * supplies a lifecycle-scoped scope.
 */
class AppGraph(
    val repository: NutritionRepository,
    val store: PersistenceStore,
    val photoCapture: PhotoCapture,
    val alarmScheduler: PostprandialAlarmScheduler,
    val scope: CoroutineScope,
) {
    /** T0 ViewModel is created once and reused across recompositions. */
    val mealFormViewModel: MealFormViewModel by lazy {
        MealFormViewModel(
            repository = repository,
            store = store,
            photoCapture = photoCapture,
            alarmScheduler = alarmScheduler,
            scope = scope,
        )
    }

    /** T2 ViewModel is scoped to a specific [RegistroComida]; create per navigation. */
    fun followUpViewModel(registro: RegistroComida): FollowUpViewModel =
        FollowUpViewModel(
            store = store,
            photoCapture = photoCapture,
            scope = scope,
            registro = registro,
        )
}
