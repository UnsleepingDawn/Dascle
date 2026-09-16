package com.fitplan.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        // 必须在 super.onCreate() 之前调用：它会把 Activity 主题从 Theme.FitPlan.SplashScreen
        // 切成 postSplashScreenTheme（Theme.FitPlan，无 ActionBar）。不调用的话，API 31+ 上
        // Theme.SplashScreen 的父主题是 android:Theme.DeviceDefault.DayNight，会在内容区上方
        // 留下一条显示应用名的系统 ActionBar。
        installSplashScreen()

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
