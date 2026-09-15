package com.fitplan.ui.plan.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.CalendarDay
import com.fitplan.domain.interactor.GetMonthCalendar
import com.fitplan.domain.interactor.RecordPastWorkout
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
import kotlinx.coroutines.flow.drop
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
    private val recordPastWorkout: RecordPastWorkout,
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val widgetManager: WidgetManager,
    dataRevision: DataRevision,
) : ViewModel() {

    init {
        // 启动时的漏练弹窗可能在日历取过数之后才改排期（顺延 / 跳过），收到信号就重新加载。
        viewModelScope.launch {
            dataRevision.revision.drop(1).collect { refresh() }
        }
    }

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

    /**
     * 把计划加到 [date] 这一天：今天及以后写排期；已经过去的日子没法再「排」，直接补记成实际训练。
     * 无论哪种，这一天原本若标着休息，都把休息标记撤掉。
     *
     * 排期一天只有一条，写排期时会替换掉这一天原有的计划（界面上只在空白日子给「添加计划」入口）。
     */
    fun addPlan(routineId: Long, date: LocalDate) {
        viewModelScope.launch {
            if (date < today()) {
                recordPastWorkout(routineId, date)
            } else {
                scheduleRepository.replacePlanOn(routineId, date)
            }
            scheduleRepository.deleteRestDay(date)
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    /** 把 [date] 标成休息日；这一天原有的排期一并撤掉（一天不是训练日就是休息日）。 */
    fun markRestDay(date: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.setRestDay(date)
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
        val today = today()
        return today.minus(today.day - 1, DateTimeUnit.DAY)
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
