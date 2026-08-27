package com.diabecarekids.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.diabecarekids.app.alarm.MealReminderDependencies
import com.diabecarekids.app.alarm.MealReminderNotifier
import com.diabecarekids.app.alarm.WorkManagerAlarmScheduler
import com.diabecarekids.app.alarm.WorkManagerMealReminderScheduler
import com.diabecarekids.app.domain.LocalTimeOfDay
import com.diabecarekids.app.domain.ReminderScheduleEngine
import com.diabecarekids.app.export.AndroidPdfReportExporter
import com.diabecarekids.app.export.AndroidReportShareLauncher
import com.diabecarekids.app.navigation.AppGraph
import com.diabecarekids.app.nutrition.ApiConfig
import com.diabecarekids.app.nutrition.CarbResolutionEngineImpl
import com.diabecarekids.app.nutrition.GeminiApiClient
import com.diabecarekids.app.nutrition.NutritionRepositoryImpl
import com.diabecarekids.app.nutrition.UsdaApiClient
import com.diabecarekids.app.persistence.InMemoryHorariosStore
import com.diabecarekids.app.persistence.InMemoryPersistenceStore
import com.diabecarekids.app.persistence.PersistenceRecentRecordWindowCheck
import com.diabecarekids.app.photocapture.TakePicturePhotoCapture
import com.diabecarekids.app.platform.epochMillisNow
import com.diabecarekids.app.platform.httpClientEngine
import com.diabecarekids.app.platform.todayAtLocalTimeMillis
import com.diabecarekids.app.reminder.MealReminderOrchestrator
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val photoCapture = TakePicturePhotoCapture(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoCapture.register(this)
        enableEdgeToEdge()
        val graph = buildAppGraph()
        setContent {
            App(graph = graph)
        }
    }

    /** Manual composition root (design DECISION). Keys env-injected, never committed. */
    private fun buildAppGraph(): AppGraph {
        val config = ApiConfig.fromEnvironment { name -> System.getenv(name) }

        // Build the engine/repo with real Ktor clients over the shared OkHttp engine.
        val engine = httpClientEngine()
        val usda = UsdaApiClient(HttpClient(engine), config.usdaKey.orEmpty())
        val gemini = GeminiApiClient(HttpClient(engine), config.geminiKey.orEmpty())
        val repository = NutritionRepositoryImpl(CarbResolutionEngineImpl(config, usda, gemini), usda)

        val store = InMemoryPersistenceStore() // prod store for this change (REQ-MEAL-005)
        val scheduler = WorkManagerAlarmScheduler(applicationContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        wireMealReminders(store, scope)

        return AppGraph(
            repository = repository,
            store = store,
            photoCapture = photoCapture,
            alarmScheduler = scheduler,
            pdfExporter = AndroidPdfReportExporter(applicationContext),
            reportShareLauncher = AndroidReportShareLauncher(this),
            scope = scope,
        )
    }

    /**
     * Composition-root wiring for scheduled meal reminders (CAP-006, design D7).
     *
     * Builds the shared stores/engine, populates the process-singleton
     * [MealReminderDependencies] holder (so the worker can re-load state at
     * execution time), and launches the orchestrator's `refresh()` in [scope].
     * The engine consumes the seeded default schedule (no settings UI this
     * change); reminders are (re)scheduled on app start and the worker
     * re-checks the 2h suppression window at execution time.
     */
    private fun wireMealReminders(store: InMemoryPersistenceStore, scope: CoroutineScope) {
        val horariosStore = InMemoryHorariosStore()
        val recentCheck = PersistenceRecentRecordWindowCheck(store)
        val engine = ReminderScheduleEngine(
            now = { epochMillisNow() },
            todayAt = { time: LocalTimeOfDay -> todayAtLocalTimeMillis(time.hour, time.minute) },
        )
        val mealScheduler = WorkManagerMealReminderScheduler(applicationContext)
        val notifier = MealReminderNotifier(applicationContext)

        MealReminderDependencies.populate(
            horariosStore = horariosStore,
            engine = engine,
            recentCheck = recentCheck,
            notifier = notifier,
        )

        val orchestrator = MealReminderOrchestrator(
            engine = engine,
            horariosStore = horariosStore,
            recentCheck = recentCheck,
            scheduler = mealScheduler,
        )
        scope.launch { orchestrator.refresh() }
    }
}
