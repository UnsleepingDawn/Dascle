@file:Suppress("NOTHING_TO_INLINE")

package com.fitplan.presentation.util

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.fitplan.app.di.appGraph
import com.fitplan.presentation.core.util.collectAsState
import com.fitplan.presentation.theme.FitPlanTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

inline fun ComponentActivity.setComposeContent(
    parent: CompositionContext? = null,
    crossinline content: @Composable () -> Unit,
) {
    setContent(parent) {
        // 主题偏好从设置页写入，这里订阅它，改完立刻生效、无需重启。
        val themePreferences = appGraph.themePreferences
        val appTheme by themePreferences.appTheme.collectAsState()
        val darkMode by themePreferences.darkMode.collectAsState()
        val amoled by themePreferences.amoled.collectAsState()
        FitPlanTheme(appTheme = appTheme, darkMode = darkMode, isAmoled = amoled) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                LocalMetroViewModelFactory provides appGraph.viewModelFactory,
            ) {
                content()
            }
        }
    }
}
