package com.diabecarekids.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.diabecarekids.app.alarm.WorkManagerAlarmScheduler
import com.diabecarekids.app.navigation.AppGraph
import com.diabecarekids.app.nutrition.ApiConfig
import com.diabecarekids.app.nutrition.CarbResolutionEngineImpl
import com.diabecarekids.app.nutrition.GeminiApiClient
import com.diabecarekids.app.nutrition.NutritionRepositoryImpl
import com.diabecarekids.app.nutrition.UsdaApiClient
import com.diabecarekids.app.persistence.InMemoryPersistenceStore
import com.diabecarekids.app.photocapture.TakePicturePhotoCapture
import com.diabecarekids.app.platform.httpClientEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

        return AppGraph(
            repository = repository,
            store = store,
            photoCapture = photoCapture,
            alarmScheduler = scheduler,
            scope = scope,
        )
    }
}
