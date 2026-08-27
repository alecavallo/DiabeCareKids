package com.diabecarekids.app.navigation

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.export.PdfReportExporter
import com.diabecarekids.app.export.ReportShareLauncher
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.NutritionRepository
import com.diabecarekids.app.viewmodel.AddPastRecordViewModel
import com.diabecarekids.app.viewmodel.EditRecordViewModel
import com.diabecarekids.app.viewmodel.FollowUpViewModel
import com.diabecarekids.app.viewmodel.HistoryViewModel
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
    val pdfExporter: PdfReportExporter,
    val reportShareLauncher: ReportShareLauncher,
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
            alarmScheduler = alarmScheduler,
            scope = scope,
            registro = registro,
        )

    /**
     * Advanced View (CAP-005) factories. All three are created per navigation
     * (History re-reads the store on every entry — refresh via per-navigation
     * creation, design decision; Add/Edit are scoped to the current form/record).
     */
    fun historyViewModel(): HistoryViewModel =
        HistoryViewModel(store = store, exporter = pdfExporter, shareLauncher = reportShareLauncher, scope = scope)

    fun addPastRecordViewModel(): AddPastRecordViewModel =
        AddPastRecordViewModel(repository = repository, store = store, scope = scope)

    fun editRecordViewModel(registro: RegistroComida): EditRecordViewModel =
        EditRecordViewModel(store = store, scope = scope, registro = registro)
}
