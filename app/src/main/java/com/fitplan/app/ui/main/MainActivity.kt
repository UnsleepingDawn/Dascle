package com.fitplan.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import cafe.adriel.voyager.navigator.Navigator
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.metroGraph
import com.fitplan.presentation.util.DefaultNavigatorScreenTransition
import com.fitplan.presentation.util.setComposeContent
import com.fitplan.ui.home.HomeScreen

class MainActivity : ComponentActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity injected with $graph")

        setComposeContent {
            Navigator(HomeScreen) { navigator ->
                DefaultNavigatorScreenTransition(navigator = navigator)
            }
        }
    }

    companion object {
        private const val TAG = "FitPlan"
    }
}
