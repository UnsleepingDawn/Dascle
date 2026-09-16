package com.fitplan.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.metroGraph
import com.fitplan.presentation.core.screens.LoadingScreen
import com.fitplan.presentation.util.DefaultNavigatorScreenTransition
import com.fitplan.presentation.util.setComposeContent
import com.fitplan.ui.home.HomeScreen
import com.fitplan.ui.onboarding.OnboardingScreen
import com.fitplan.ui.onboarding.OnboardingScreenModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

class MainActivity : ComponentActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity injected with $graph")

        setComposeContent {
            // 第一次打开先走个人信息引导，走完（或跳过）才进主界面。
            val onboarding = metroViewModel<OnboardingScreenModel>()
            val onboarded by onboarding.onboarded.collectAsState()

            when (onboarded) {
                null -> LoadingScreen()

                true -> Navigator(HomeScreen) { navigator ->
                    DefaultNavigatorScreenTransition(navigator = navigator)
                }

                false -> OnboardingScreen(
                    onSave = onboarding::complete,
                    onSkip = onboarding::skip,
                )
            }
        }
    }

    companion object {
        private const val TAG = "FitPlan"
    }
}
