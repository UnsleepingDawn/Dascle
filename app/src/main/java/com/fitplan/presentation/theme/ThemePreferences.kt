package com.fitplan.presentation.theme

import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.core.common.preference.getEnum
import com.fitplan.domain.ui.model.AppDarkMode
import com.fitplan.domain.ui.model.AppTheme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * 主题与个性化偏好：配色、深色模式、AMOLED 纯黑。
 *
 * 只负责读写偏好；[FitPlanTheme] 在根组合里订阅它们，改动立刻生效、无需重启。
 */
@Inject
@SingleIn(AppScope::class)
class ThemePreferences(preferenceStore: PreferenceStore) {

    /** 配色方案，见 [AppTheme]。 */
    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(KEY_THEME, AppTheme.DEFAULT)

    /** 深色模式：跟随系统 / 固定浅色 / 固定深色。 */
    val darkMode: Preference<AppDarkMode> =
        preferenceStore.getEnum(KEY_DARK_MODE, AppDarkMode.FOLLOW_SYSTEM)

    /** 深色模式下用纯黑背景，OLED 屏更省电。 */
    val amoled: Preference<Boolean> = preferenceStore.getBoolean(KEY_AMOLED, false)

    private companion object {
        const val KEY_THEME = "theme_app_theme"
        const val KEY_DARK_MODE = "theme_dark_mode"
        const val KEY_AMOLED = "theme_amoled"
    }
}
