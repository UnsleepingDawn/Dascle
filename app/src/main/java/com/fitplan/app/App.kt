package com.fitplan.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.GraphProvider
import com.fitplan.domain.repository.ExerciseSeedRepository
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class App : Application(), GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = BuildConfig.DEBUG)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var appContext: Context

    @Inject lateinit var exerciseSeedRepository: ExerciseSeedRepository

    override fun onCreate() {
        super<Application>.onCreate()

        graph.inject(this)

        Log.d(TAG, "AppGraph initialized: $graph, appContext injected: ${::appContext.isInitialized}")

        applicationScope.launch {
            val inserted = exerciseSeedRepository.importSeedExercises()
            Log.d(TAG, "Seed exercises inserted: $inserted")
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        Log.d(TAG, "App terminated")
        super.onTerminate()
    }

    companion object {
        private const val TAG = "FitPlan"
    }
}
