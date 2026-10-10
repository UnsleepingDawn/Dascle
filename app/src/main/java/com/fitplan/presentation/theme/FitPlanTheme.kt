package com.fitplan.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fitplan.domain.ui.model.AppDarkMode
import com.fitplan.domain.ui.model.AppTheme
import com.fitplan.presentation.core.theme.colorscheme.BaseColorScheme
import com.fitplan.presentation.core.theme.colorscheme.FitPlanColorScheme
import com.fitplan.presentation.core.theme.colorscheme.GreenAppleColorScheme
import com.fitplan.presentation.core.theme.colorscheme.MonochromeColorScheme

@Composable
fun FitPlanTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkMode: AppDarkMode = AppDarkMode.FOLLOW_SYSTEM,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    BaseFitPlanTheme(
        appTheme = appTheme,
        darkMode = darkMode,
        isAmoled = isAmoled,
        content = content,
    )
}

@Composable
fun FitPlanPreviewTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkMode: AppDarkMode = AppDarkMode.FOLLOW_SYSTEM,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) = BaseFitPlanTheme(appTheme, darkMode, isAmoled, content)

@Composable
private fun BaseFitPlanTheme(
    appTheme: AppTheme,
    darkMode: AppDarkMode,
    isAmoled: Boolean,
    content: @Composable () -> Unit,
) {
    // 固定浅 / 深色时不理会系统；只有「跟随系统」才去读系统设置。
    val isDark = when (darkMode) {
        AppDarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        AppDarkMode.LIGHT -> false
        AppDarkMode.DARK -> true
    }
    MaterialExpressiveTheme(
        colorScheme = remember(appTheme, isDark, isAmoled) {
            getThemeColorScheme(
                appTheme = appTheme,
                isDark = isDark,
                isAmoled = isAmoled,
            )
        },
        content = content,
    )
}

private fun getThemeColorScheme(
    appTheme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val colorScheme = colorSchemes.getOrDefault(appTheme, FitPlanColorScheme)
    return colorScheme.getColorScheme(
        isDark = isDark,
        isAmoled = isAmoled,
        overrideDarkSurfaceContainers = true,
    )
}

private val colorSchemes: Map<AppTheme, BaseColorScheme> = mapOf(
    AppTheme.DEFAULT to FitPlanColorScheme,
    AppTheme.GREEN_APPLE to GreenAppleColorScheme,
    AppTheme.MONOCHROME to MonochromeColorScheme,
)
