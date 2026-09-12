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
import com.fitplan.app.di.appGraph
import com.fitplan.domain.ui.model.AppTheme
import com.fitplan.presentation.theme.FitPlanTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

inline fun ComponentActivity.setComposeContent(
    parent: CompositionContext? = null,
    crossinline content: @Composable () -> Unit,
) {
    setContent(parent) {
        FitPlanTheme(appTheme = AppTheme.DEFAULT) {
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
