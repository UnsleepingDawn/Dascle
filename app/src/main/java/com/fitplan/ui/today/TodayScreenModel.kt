package com.fitplan.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.interactor.GetTodayWorkoutSession
import com.fitplan.domain.interactor.GetUpcomingTrainingPlan
import com.fitplan.domain.interactor.IsRestDay
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.interactor.UpcomingTrainingPlan
import com.fitplan.domain.interactor.UseUpcomingTrainingPlanForToday
import com.fitplan.domain.model.WorkoutSession
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TodayScreenModel(
    private val getScheduledRoutinesForDate: GetScheduledRoutinesForDate,
    private val getTodayWorkoutSession: GetTodayWorkoutSession,
    private val isRestDay: IsRestDay,
    private val getUpcomingTrainingPlan: GetUpcomingTrainingPlan,
    private val useUpcomingTrainingPlanForToday: UseUpcomingTrainingPlanForToday,
    private val workoutRepository: WorkoutRepository,
    private val widgetManager: WidgetManager,
    private val dataRevision: DataRevision,
) : ViewModel() {

    init {
        // 启动时的漏练弹窗可能在今日页取过数之后才改排期（顺延 / 跳过），收到信号就重新加载。
        viewModelScope.launch {
            dataRevision.revision.drop(1).collect { refresh() }
        }
    }

    private val _date = MutableStateFlow(today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _routines = MutableStateFlow<List<ScheduledRoutine>>(emptyList())
    val routines: StateFlow<List<ScheduledRoutine>> = _routines.asStateFlow()

    /** 今天被编排成了休息日：今日页整页换成休息页。 */
    private val _restDay = MutableStateFlow(false)
    val restDay: StateFlow<Boolean> = _restDay.asStateFlow()

    /** 上一次没练完就退出的训练，用于把「开始训练」换成「继续训练」。 */
    private val _unfinished = MutableStateFlow<WorkoutSession?>(null)
    val unfinished: StateFlow<WorkoutSession?> = _unfinished.asStateFlow()

    /**
     * 今天开始的那次训练（结束与否都算）。
     * [WorkoutSession.isFinished] 为 true 表示今天的训练已经做完：计划卡片的「开始训练」置灰，
     * 下方改给一张「计划外训练」卡片，往里加的动作仍然追加到这一次训练上。
     */
    private val _todaySession = MutableStateFlow<WorkoutSession?>(null)
    val todaySession: StateFlow<WorkoutSession?> = _todaySession.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * 今天之后最近的一次训练安排，休息日的「使用明天的方案」用它。
     * 之后完全没有排期时为 null，那个入口只能置灰。
     */
    private val _upcomingPlan = MutableStateFlow<UpcomingTrainingPlan?>(null)
    val upcomingPlan: StateFlow<UpcomingTrainingPlan?> = _upcomingPlan.asStateFlow()

    /** 改完排期后要直接开练的计划 id；界面消费完调 [consumeStartRequest] 清掉，避免返回时又跳一次。 */
    private val _startRoutineRequest = MutableStateFlow<Long?>(null)
    val startRoutineRequest: StateFlow<Long?> = _startRoutineRequest.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val date = today()
            _date.value = date
            _restDay.value = isRestDay(date)
            _routines.value = getScheduledRoutinesForDate(date)
            _unfinished.value = workoutRepository.getUnfinishedSessions().maxByOrNull { it.startedAt }
            _todaySession.value = getTodayWorkoutSession()
            _upcomingPlan.value = getUpcomingTrainingPlan(date)
            _loaded.value = true
        }
    }

    /**
     * 休息日改用之后最近一次训练的安排：把那天的计划挪到今天，之后的排期与休息日整体提前，
     * 然后请界面直接进训练记录页开练。
     */
    fun useUpcomingPlan() {
        val plan = _upcomingPlan.value ?: return
        viewModelScope.launch {
            val routineId = useUpcomingTrainingPlanForToday(plan)
            refresh()
            // 日历页可能已经取过数，改完排期要让它跟着重算。
            dataRevision.bump()
            widgetManager.updateTodayWidget()
            _startRoutineRequest.value = routineId
        }
    }

    fun consumeStartRequest() {
        _startRoutineRequest.value = null
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
