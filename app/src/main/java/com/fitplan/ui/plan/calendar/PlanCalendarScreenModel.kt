package com.fitplan.ui.plan.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.CalendarDay
import com.fitplan.domain.interactor.CloseStaleWorkouts
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
    private val closeStaleWorkouts: CloseStaleWorkouts,
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val widgetManager: WidgetManager,
    private val dataRevision: DataRevision,
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
            // 跨零点时 App 可能一直没退过，这里补一次收尾，隔天遗留的训练才会出现在日历上。
            closeStaleWorkouts()
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
            refresh()
            widgetManager.updateTodayWidget()
        }
    }

    /** 把 [date] 改成休息日：撤掉这一天原有的排期，这天随即没有安排、成为休息日。 */
    fun markRestDay(date: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.deletePlansOn(listOf(date))
            afterScheduleChange()
        }
    }

    /**
     * 编辑模式里「该日休息 / 这几日休息」：只撤掉 [dates] 这些天的排期，之后的安排原地不动。
     */
    fun removePlansOn(dates: Collection<LocalDate>) {
        viewModelScope.launch {
            scheduleRepository.deletePlansOn(dates)
            afterScheduleChange()
        }
    }

    /**
     * 编辑模式里「顺着用未来计划 / 已有计划流入」：清掉 [dates] 这些训练日的排期，
     * 并让它们之后的训练日依次前顶进腾出来的位置，末尾多出休息日。
     */
    fun compactPlansAfterRemoving(dates: Collection<LocalDate>) {
        viewModelScope.launch {
            scheduleRepository.deleteAndCompactPlans(dates)
            afterScheduleChange()
        }
    }

    /**
     * 编辑模式里删除选中的休息日：把这些休息日从时间线上抽掉，它们之后的排期整体提前，
     * 被删的休息日由后面的训练补上、末尾多出休息日。
     */
    fun removeRestDaysAndCompact(dates: Collection<LocalDate>) {
        viewModelScope.launch {
            scheduleRepository.deleteRestDaysAndCompact(dates)
            afterScheduleChange()
        }
    }

    /** 在 [date] 之前插入 [count] 天休息：[date] 及以后的排期整体后移，腾出来的日子成为休息日。 */
    fun insertRestDaysBefore(date: LocalDate, count: Int) {
        viewModelScope.launch {
            scheduleRepository.insertRestDaysBefore(date, count)
            afterScheduleChange()
        }
    }

    /** 在 [date] 之前插入一个训练日：[date] 及以后的排期整体后移一天，[routineId] 落在 [date]。 */
    fun insertPlanBefore(routineId: Long, date: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.insertPlanBefore(routineId, date)
            afterScheduleChange()
        }
    }

    /**
     * 改完排期后的收尾：刷新日历、通知其它页面重算、刷新桌面组件。
     *
     * [DataRevision] 必须通知——今日页的「使用之后的方案 / 今日休息」都依赖未来排期，
     * 它们可能已经取过数了。
     */
    private suspend fun afterScheduleChange() {
        refresh()
        dataRevision.bump()
        widgetManager.updateTodayWidget()
    }

    fun removePlan(entryId: Long) {
        viewModelScope.launch {
            scheduleRepository.deleteById(entryId)
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
