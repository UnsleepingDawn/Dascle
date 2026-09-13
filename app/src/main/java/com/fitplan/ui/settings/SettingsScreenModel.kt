package com.fitplan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.reminder.ReminderScheduler
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
) : ViewModel() {

    val reminderEnabled: StateFlow<Boolean> = reminderScheduler.enabled.stateIn(viewModelScope)

    val reminderHour: StateFlow<Int> = reminderScheduler.hour.stateIn(viewModelScope)

    val reminderMinute: StateFlow<Int> = reminderScheduler.minute.stateIn(viewModelScope)

    fun setReminderEnabled(enabled: Boolean) {
        reminderScheduler.enabled.set(enabled)
        reminderScheduler.sync()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        reminderScheduler.hour.set(hour)
        reminderScheduler.minute.set(minute)
        reminderScheduler.sync()
    }

    fun canPostNotifications(): Boolean = reminderScheduler.canPostNotifications()

    fun canScheduleExactAlarms(): Boolean = reminderScheduler.canScheduleExactAlarms()
}
