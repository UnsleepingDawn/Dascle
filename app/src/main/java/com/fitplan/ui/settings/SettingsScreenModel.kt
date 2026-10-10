package com.fitplan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.ui.model.AppDarkMode
import com.fitplan.domain.ui.model.AppTheme
import com.fitplan.presentation.theme.ThemePreferences
import com.fitplan.reminder.ReminderScheduler
import com.fitplan.updater.UpdateChecker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SettingsScreenModel(
    private val reminderScheduler: ReminderScheduler,
    private val updateChecker: UpdateChecker,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val reminderEnabled: StateFlow<Boolean> = reminderScheduler.enabled.stateIn(viewModelScope)

    val reminderHour: StateFlow<Int> = reminderScheduler.hour.stateIn(viewModelScope)

    val reminderMinute: StateFlow<Int> = reminderScheduler.minute.stateIn(viewModelScope)

    /** 启动时自动检查更新。 */
    val updateAutoCheck: StateFlow<Boolean> = updateChecker.autoCheck.stateIn(viewModelScope)

    /** 检查更新用的镜像前缀，留空表示只直连 GitHub。 */
    val updateMirrorPrefix: StateFlow<String> = updateChecker.mirrorPrefix.stateIn(viewModelScope)

    /** 主题配色。 */
    val appTheme: StateFlow<AppTheme> = themePreferences.appTheme.stateIn(viewModelScope)

    /** 深色模式：跟随系统 / 浅色 / 深色。 */
    val darkMode: StateFlow<AppDarkMode> = themePreferences.darkMode.stateIn(viewModelScope)

    /** 深色模式下是否使用纯黑背景。 */
    val amoled: StateFlow<Boolean> = themePreferences.amoled.stateIn(viewModelScope)

    fun setReminderEnabled(enabled: Boolean) {
        reminderScheduler.enabled.set(enabled)
        reminderScheduler.sync()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        reminderScheduler.hour.set(hour)
        reminderScheduler.minute.set(minute)
        reminderScheduler.sync()
    }

    fun setUpdateAutoCheck(enabled: Boolean) {
        updateChecker.autoCheck.set(enabled)
    }

    fun setUpdateMirrorPrefix(prefix: String) {
        updateChecker.mirrorPrefix.set(prefix.trim())
    }

    fun setAppTheme(theme: AppTheme) {
        themePreferences.appTheme.set(theme)
    }

    fun setDarkMode(mode: AppDarkMode) {
        themePreferences.darkMode.set(mode)
    }

    fun setAmoled(enabled: Boolean) {
        themePreferences.amoled.set(enabled)
    }

    fun canPostNotifications(): Boolean = reminderScheduler.canPostNotifications()

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExactAlarms()
}
