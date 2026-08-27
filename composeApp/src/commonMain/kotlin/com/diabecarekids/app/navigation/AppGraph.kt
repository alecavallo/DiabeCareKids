package com.diabecarekids.app.navigation

import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.persistence.InMemoryEmergenciaStore
import com.diabecarekids.app.persistence.PersistenceStore
import com.diabecarekids.app.platform.Haptics
import com.diabecarekids.app.platform.LocationProvider
import com.diabecarekids.app.platform.PhotoCapture
import com.diabecarekids.app.platform.PostprandialAlarmScheduler
import com.diabecarekids.app.nutrition.NutritionRepository
import com.diabecarekids.app.sos.GuardianNotifier
import com.diabecarekids.app.sos.InMemoryGuardianNotifier
import com.diabecarekids.app.sos.LocationPermissionRequester
import com.diabecarekids.app.sos.SosController
import com.diabecarekids.app.sos.SosHoldStateMachine
import com.diabecarekids.app.viewmodel.AddPastRecordViewModel
import com.diabecarekids.app.viewmodel.EditRecordViewModel
import com.diabecarekids.app.viewmodel.FollowUpViewModel
import com.diabecarekids.app.viewmodel.HistoryViewModel
import com.diabecarekids.app.viewmodel.MealFormViewModel
import com.diabecarekids.app.viewmodel.SosViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Manual composition root (design DECISION: manual constructor injection, no DI
 * lib). Android-only platform adapters ([PhotoCapture], [PostprandialAlarmScheduler],
 * [LocationProvider], [Haptics]) are injected from the platform entry point
 * (MainActivity); [NutritionRepository] and [PersistenceStore] come from the
 * shared module.
 *
 * [scope] drives the ViewModels' internal coroutines. The Android entry point
 * supplies a lifecycle-scoped scope.
 */
class AppGraph(
    val repository: NutritionRepository,
    val store: PersistenceStore,
    val photoCapture: PhotoCapture,
    val alarmScheduler: PostprandialAlarmScheduler,
    val locationProvider: LocationProvider,
    val haptics: Haptics,
    val locationPermission: LocationPermissionRequester,
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
    */

    /**
     * SOS ViewModel is created once and reused across recompositions. The fire
     * pipeline's [GuardianNotifier] is the shared in-memory placeholder (design
     * decision #4 — real Firestore/FCM transport is deferred to a later change);
     * location + haptics are the Android adapters injected by MainActivity.
     */
    val sosViewModel: SosViewModel by lazy {
        val store = InMemoryEmergenciaStore()
        val notifier = InMemoryGuardianNotifier()
        val controller = SosController(
            machine = SosHoldStateMachine(),
            store = store,
            locationProvider = locationProvider,
            notifier = notifier,
            haptics = haptics,
            scope = scope,
            patientId = SOS_PATIENT_ID,
            patientName = SOS_PATIENT_NAME,
        )
        SosViewModel(controller)
    }

    /**
     * Advanced View (CAP-005) factories. All three are created per navigation
     * (History re-reads the store on every entry — refresh via per-navigation
     * creation, design decision; Add/Edit are scoped to the current form/record).
     */
    fun historyViewModel(): HistoryViewModel =
        HistoryViewModel(store = store, scope = scope)

    fun addPastRecordViewModel(): AddPastRecordViewModel =
        AddPastRecordViewModel(repository = repository, store = store, scope = scope)

    fun editRecordViewModel(registro: RegistroComida): EditRecordViewModel =
        EditRecordViewModel(store = store, scope = scope, registro = registro)

    private companion object {
        // Placeholder identity until a patient profile exists (CAP-001 scope).
        const val SOS_PATIENT_ID = "local"
        const val SOS_PATIENT_NAME = "Paciente"
    }
}
