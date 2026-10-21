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
import kotlinx.datetime.atStartOfDayIn
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
            _routines.value = routineRepository.getPermanent()
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

    /**
     * 给 [date] 开一份「临时计划」并排到这一天，然后把新计划 id 回调出去进编排页。
     *
     * 临时计划（`routine.is_temp`）不进常驻计划列表，只是这一天专属的一次性编排；
     * 名字由界面传入（用户可见文案留在 `strings.xml`），存进库后会显示在日历与今日页上。
     * 用户在编排页里加了动作它就留在这天，什么都没加就退出的话，回到日历时会被 [clearUnusedTempPlans] 清掉。
     */
    fun createTempPlan(date: LocalDate, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val routineId = routineRepository.insertTemp(name, Clock.System.now())
            scheduleRepository.replacePlanOn(routineId, date)
            afterScheduleChange()
            onCreated(routineId)
        }
    }

    /**
     * 清扫没用的临时计划：没加过动作的空壳，或者已经不再排在任意一天上的。
     *
     * 只在日历可见时调用，不挂在 [refresh] 上——用户正在编排页里给刚建好的空计划加动作，
     * 那种时刻刷新一次就会把它删掉。
     */
    fun clearUnusedTempPlans() {
        viewModelScope.launch { routineRepository.deleteUnusedTempPlans() }
    }

    /** 把 [date] 改成休息日：撤掉这一天原有的排期，这天随即没有安排、成为休息日。 */
    fun markRestDay(date: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.deletePlansOn(listOf(date))
            afterScheduleChange()
        }
    }

    /**
     * 编辑模式里「该日休息 / 这几日休息」：撤掉 [dates] 这些天的排期，之后的安排原地不动。
     *
     * 这些天已经练过的记录（含中途退出、还没结束的那次）也一并删掉——用户在这里表达的意图是
     * 「这几天不要了」，留下训练记录会让日历、今日页与统计继续显示练过。
     */
    fun removePlansOn(dates: Collection<LocalDate>) {
        viewModelScope.launch {
            deleteSessionsOn(dates)
            scheduleRepository.deletePlansOn(dates)
            afterScheduleChange()
        }
    }

    /**
     * 编辑模式里「顺着用未来计划 / 已有计划流入」：把 [dates] 这些日子从时间线上抽掉，
     * 它们之后的**整条时间线**（训练日与休息日一起）整体提前——被选中的训练日会被后面的计划补上，
     * 练 / 休节奏原样保留，末尾多出休息日。
     *
     * 选中的全是休息日时也走这条路径（也就是「删除休息日」）：把这些休息日抽掉，后面的训练整体前移。
     *
     * 同 [removePlansOn]，这些天已记录的训练一起删掉。
     */
    fun shiftBackAfterRemoving(dates: Collection<LocalDate>) {
        viewModelScope.launch {
            deleteSessionsOn(dates)
            scheduleRepository.deletePlansAndShiftBack(dates)
            afterScheduleChange()
        }
    }

    /**
     * 删掉 [dates] 这些天已记录的训练（含中途退出、还没结束的那次），组由外键级联清理。
     *
     * 训练记录不属于 [ScheduleRepository]，所以按这些天的本地区间查出来、再按开始时间的本地日期
     * 归到具体某天逐个删——归属口径与日历 `GetMonthCalendar` 把它算在哪一天一致。
     */
    private suspend fun deleteSessionsOn(dates: Collection<LocalDate>) {
        val earliest = dates.minOrNull() ?: return
        val latest = dates.maxOrNull() ?: return
        val zone = TimeZone.currentSystemDefault()
        val wanted = dates.toSet()
        workoutRepository
            .getSessionsBetween(
                start = earliest.atStartOfDayIn(zone),
                end = latest.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
            )
            .filter { it.startedAt.toLocalDateTime(zone).date in wanted }
            .forEach { workoutRepository.deleteSession(it.id) }
    }

    /** 在 [date] 之前插入 [count] 天休息：[date] 及以后的排期整体后移，腾出来的日子成为休息日。 */
    fun insertRestDaysBefore(date: LocalDate, count: Int) {
        viewModelScope.launch {
            scheduleRepository.insertRestDaysBefore(date, count)
            afterScheduleChange()
        }
    }

    /**
     * 交换 [first] 与 [second] 两天各自排的计划。
     *
     * 某一天原本没排计划时，另一天的计划会挪过去；已经记录的训练不跟着移动，仍留在原来的日期上
     * （界面在确认弹窗里事先说明这一点）。
     */
    fun swapPlans(first: LocalDate, second: LocalDate) {
        viewModelScope.launch {
            scheduleRepository.swapPlansOn(first, second)
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
     * [DataRevision] 必须通知——今日页的「使用之后的计划 / 今日休息」都依赖未来排期，
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
