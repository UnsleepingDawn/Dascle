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
import com.fitplan.domain.repository.ExerciseRepository
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

/**
 * 今日已结束训练里一个动作的实际训练量，供今日页的训练总结卡片使用。
 *
 * [reps] / [weight] / [seconds] 是这次训练里这个动作的「代表值」，由界面上同一条汇总文案
 * 拼成「3 组 × 10 次 · 60kg」这样的摘要，不逐组罗列。
 */
data class TodaySessionExercise(
    val exerciseId: Long,
    val name: String,
    val completedSets: Int,
    val isTimed: Boolean,
    val showsWeight: Boolean,
    val weightIsAssistance: Boolean,
    val reps: Int?,
    val weight: Double?,
    val seconds: Int?,
)

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
    private val exerciseRepository: ExerciseRepository,
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
     * 下方改给一张「还想练？」卡片，往里加的动作仍然追加到这一次训练上。
     */
    private val _todaySession = MutableStateFlow<WorkoutSession?>(null)
    val todaySession: StateFlow<WorkoutSession?> = _todaySession.asStateFlow()

    /**
     * 今天这次已结束训练练了哪些动作、各自练了多少，供今日页在没有对应计划卡片时
     * （休息日「临时加一个方案」这类计划外训练）补一张总结卡片；其余情况为空列表。
     */
    private val _todaySessionExercises = MutableStateFlow<List<TodaySessionExercise>>(emptyList())
    val todaySessionExercises: StateFlow<List<TodaySessionExercise>> = _todaySessionExercises.asStateFlow()

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
            _todaySessionExercises.value = _todaySession.value
                ?.takeIf { it.isFinished }
                ?.let { loadSessionExercises(it.id) }
                .orEmpty()
            _upcomingPlan.value = getUpcomingTrainingPlan(date)
            _loaded.value = true
        }
    }

    /**
     * 汇总一次已结束训练里各动作的实际训练量：动作名加上「完成了多少组、每组大致练什么」。
     *
     * 只统计已完成组（与日历「实际训练」、训练总结口径一致）；代表值取重量最大的那组
     * （同重量取次数最多），计时动作取时间最长的一组，这样摘要稳定可预测，不受录入顺序影响。
     * 动作按各自最小 `setIndex` 排，跟训练记录里的顺序一致；动作库里已经找不到的动作跳过。
     */
    private suspend fun loadSessionExercises(sessionId: Long): List<TodaySessionExercise> {
        val setsByExercise = workoutRepository.getSets(sessionId)
            .filter { it.completed }
            .groupBy { it.exerciseId }
        if (setsByExercise.isEmpty()) return emptyList()
        val exercisesById = exerciseRepository.getByIds(setsByExercise.keys.toList()).associateBy { it.id }
        return setsByExercise.entries
            .sortedBy { (_, sets) -> sets.minOf { it.setIndex } }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                val representative = if (exercise.isTimed) {
                    sets.maxBy { it.durationSeconds ?: 0 }
                } else {
                    sets.maxWithOrNull(
                        compareBy({ it.weight ?: Double.NEGATIVE_INFINITY }, { it.reps ?: 0 }),
                    ) ?: sets.first()
                }
                TodaySessionExercise(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    completedSets = sets.size,
                    isTimed = exercise.isTimed,
                    showsWeight = exercise.showsWeight,
                    weightIsAssistance = exercise.weightIsAssistance,
                    reps = representative.reps,
                    weight = representative.weight,
                    seconds = representative.durationSeconds,
                )
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
