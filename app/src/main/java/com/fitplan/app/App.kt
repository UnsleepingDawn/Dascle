package com.fitplan.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.GraphProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory

class App : Application(), GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = BuildConfig.DEBUG)
    }

    @Inject lateinit var appContext: Context

    override fun onCreate() {
        super<Application>.onCreate()

        graph.inject(this)

        Log.d(TAG, "AppGraph initialized: $graph, appContext injected: ${::appContext.isInitialized}")
    }

    override fun onTerminate() {
        Log.d(TAG, "App terminated")
        super.onTerminate()
    }

    companion object {
        private const val TAG = "FitPlan"
    }
}
