package com.fitplan.ui.plan.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.interactor.CalendarDay
import com.fitplan.domain.interactor.GetMonthCalendar
import com.fitplan.domain.model.Routine
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.ScheduleRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.widget.WidgetManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlanCalendarScreenModel(
    private val getMonthCalendar: GetMonthCalendar,
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    /** 当前显示月份的第一天。 */
    private val _month = MutableStateFlow(firstDayOfCurrentMonth())
    val month: StateFlow<LocalDate> = _month.asStateFlow()

    private val _days = MutableStateFlow<List<CalendarDay>>(emptyList())
    val days: StateFlow<List<CalendarDay>> = _days.asStateFlow()

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _days.value = getMonthCalendar(_month.value)
            _routines.value = routineRepository.getAll()
        }
    }

    fun showPreviousMonth() {
        _month.value = _month.value.minus(1, DateTimeUnit.MONTH)
        refresh()
    }

    fun showNextMonth() {
        _month.value = _month.value.plus(1, DateTimeUnit.MONTH)
        refresh()
    }

    fun showCurrentMonth() {
        _month.value = firstDayOfCurrentMonth()
        refresh()
    }

    /** [weekly] 为 true 时排成「每周 [date] 这一天」，否则只在 [date] 当天生效。 */
    fun addPlan(routineId: Long, date: LocalDate, weekly: Boolean) {
        viewModelScope.launch {
            if (weekly) {
                scheduleRepository.insertWeekly(routineId, date.dayOfWeek)
            } else {
                scheduleRepository.insertOnce(routineId, date)
                // 这一天原本标着休息，现在改成训练日，把休息标记撤掉。
                scheduleRepository.deleteRestDay(date)
            }
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    fun removePlan(entryId: Long) {
        viewModelScope.launch {
            scheduleRepository.deleteById(entryId)
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    /** 取消某天的休息日标记。 */
    fun removeRestDay(date: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.deleteRestDay(date)
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    /** 删除一次已经结束的训练：连着它记录的所有组一起删掉，并刷新日历与桌面组件。 */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteSession(sessionId)
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    private fun firstDayOfCurrentMonth(): LocalDate {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return today.minus(today.day - 1, DateTimeUnit.DAY)
    }
}
