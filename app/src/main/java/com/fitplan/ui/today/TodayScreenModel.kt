package com.fitplan.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.CloseStaleWorkouts
import com.fitplan.domain.interactor.GetNextRestDay
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.interactor.GetTodayWorkoutSession
import com.fitplan.domain.interactor.GetUpcomingTrainingPlan
import com.fitplan.domain.interactor.IsRestDay
import com.fitplan.domain.interactor.RestTodayMode
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.interactor.TakeRestToday
import com.fitplan.domain.interactor.UpcomingTrainingPlan
import com.fitplan.domain.interactor.UseUpcomingTrainingPlanForToday
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.pickedExerciseIds
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.reminder.RestState
import com.fitplan.reminder.RestTimer
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
import kotlinx.datetime.atStartOfDayIn
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

/**
 * 今天这场训练里各动作的状态，供今日页的计划卡片标注：
 * 练过的动作整行反色底，跳过与动作组里没挑中的动作打删除线并变灰。
 *
 * [routineId] 是这次训练对应的计划 id；null 表示计划外训练（休息日「临时加一个方案」），
 * 没有计划卡片可以标注。
 */
data class TodaySessionProgress(
    val routineId: Long?,
    /** 有已完成组的动作。 */
    val completedExerciseIds: Set<Long>,
    /** 被「跳过动作」的动作。 */
    val skippedExerciseIds: Set<Long>,
    /** 动作组里挑中要练的动作；单独排列的动作不在这个集合里，判定时不用管。 */
    val pickedExerciseIds: Set<Long>,
    /**
     * 练过、但不在本次计划编排里的动作，也就是训练中临时加的那些，
     * 供计划卡片在计划动作之后补出来并标上「临时」；没练过（一组都没勾）的不算。
     */
    val extraExercises: List<TodaySessionExercise> = emptyList(),
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
    private val getNextRestDay: GetNextRestDay,
    private val takeRestToday: TakeRestToday,
    private val closeStaleWorkouts: CloseStaleWorkouts,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val widgetManager: WidgetManager,
    private val dataRevision: DataRevision,
    private val restTimer: RestTimer,
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

    /**
     * 上一次没练完就退出的训练，用于把「开始训练」换成「继续训练」。
     *
     * 只认**当天**开始的训练：隔天的旧账由 [CloseStaleWorkouts] 收尾，不再给续练入口（与桌面组件、
     * 训练日历口径一致）；今天这次已经结束时也一律不给，免得与「还想练？」重复。
     */
    private val _unfinished = MutableStateFlow<WorkoutSession?>(null)
    val unfinished: StateFlow<WorkoutSession?> = _unfinished.asStateFlow()

    /**
     * 今天开始的那次训练（结束与否都算）；同一天既有已结束又有未结束的（重复开始留下的孤儿）时
     * 取已结束的那次，由 `GetTodayWorkoutSession` 保证。
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

    /**
     * 今天这场训练里各动作的状态（练过 / 跳过 / 组内挑中），训练中也算，
     * 供今日页计划卡片标注进度；今天还没开练时为 null。
     */
    private val _todaySessionProgress = MutableStateFlow<TodaySessionProgress?>(null)
    val todaySessionProgress: StateFlow<TodaySessionProgress?> = _todaySessionProgress.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * 今天之后最近的一次训练安排，休息日的「使用明天的方案」用它。
     * 之后完全没有排期时为 null，那个入口只能置灰。
     */
    private val _upcomingPlan = MutableStateFlow<UpcomingTrainingPlan?>(null)
    val upcomingPlan: StateFlow<UpcomingTrainingPlan?> = _upcomingPlan.asStateFlow()

    /**
     * 今天之后最近的一个休息日，训练卡片的「今日休息」用它判断能否「顺延直到占用下一个休息日」；
     * 之后不再休息时为 null，那个选项只能置灰。
     */
    private val _nextRestDay = MutableStateFlow<LocalDate?>(null)
    val nextRestDay: StateFlow<LocalDate?> = _nextRestDay.asStateFlow()

    /** 改完排期后要直接开练的计划 id；界面消费完调 [consumeStartRequest] 清掉，避免返回时又跳一次。 */
    private val _startRoutineRequest = MutableStateFlow<Long?>(null)
    val startRoutineRequest: StateFlow<Long?> = _startRoutineRequest.asStateFlow()

    /**
     * 组间休息倒计时；非空表示正在休息。由 app 级的 [RestTimer] 共享，
     * 所以从记录页退出来、切到别的 Tab 之后也照常走，今日页跟着显示进度。
     */
    val rest: StateFlow<RestState?> = restTimer.rest

    /** 每自增一次表示休息刚刚结束，界面据此让闹钟摇摆提醒。 */
    val restFinishedTick: StateFlow<Int> = restTimer.finishedTick

    fun refresh() {
        viewModelScope.launch {
            // 先把隔天遗留的旧账收尾（有已勾组就补记成已结束），后面的取数才是一致的。
            closeStaleWorkouts()
            val date = today()
            _date.value = date
            _restDay.value = isRestDay(date)
            val routines = getScheduledRoutinesForDate(date)
            _routines.value = routines
            val todaySession = getTodayWorkoutSession()
            _todaySession.value = todaySession
            // 「继续训练」只照顾当天没练完的那次：隔天的旧账已被 CloseStaleWorkouts 收尾，
            // 当天这次也已经结束时更不该再给续练入口（否则会和「再来！」一起亮）。
            _unfinished.value = workoutRepository.getUnfinishedSessions()
                .filter { it.startedAt >= date.atStartOfDayIn(TimeZone.currentSystemDefault()) }
                .maxByOrNull { it.startedAt }
                .takeIf { todaySession?.isFinished != true }

            // 今天照哪张计划练：找得到当天的排期卡片才谈得上「计划内 / 临时」，找不到
            // （休息日「临时加一个方案」这类计划外训练）就没有计划编排可比。
            val plannedExerciseIds = todaySession?.routineId
                ?.let { routineId -> routines.firstOrNull { it.routine.id == routineId } }
                ?.exercises
                ?.map { it.exerciseId }
                ?.toSet()

            // 今天练过的动作汇总（只含已完成组）；训练中与已结束都算，只查一次给两处共用。
            val loggedExercises = todaySession?.let { loadSessionExercises(it.id) }.orEmpty()

            // 没有计划卡片时，练了什么全靠总结卡片列出来（口径与界面的 standaloneFinished 一致，
            // 只有已结束的训练才给卡片）；有计划卡片时改由计划卡片自己展示，这里不再重复。
            _todaySessionExercises.value = when {
                plannedExerciseIds != null -> emptyList()
                else -> loggedExercises.takeIf { todaySession?.isFinished == true }.orEmpty()
            }

            // 有计划卡片时，把练过、计划编排里却没有的动作标出来：它们就是训练中临时加的。
            val extras = loggedExercises.filterNot { it.exerciseId in plannedExerciseIds.orEmpty() }
            _todaySessionProgress.value = todaySession?.let { loadSessionProgress(it, extras) }

            _upcomingPlan.value = getUpcomingTrainingPlan(date)
            _nextRestDay.value = getNextRestDay(date)
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
     * 汇总今天这场训练里各动作的状态：哪些练过（有已完成组）、哪些被跳过、动作组里挑中了谁。
     * 训练中也算，所以中途返回今日页就能看到进度；口径与记录页 `workout_exercise_state` 一致。
     *
     * [extraExercises] 由调用方按「练过但不在计划编排里」算好传进来（判定要拿到当天的排期，
     * 不在这层的职责里）。
     */
    private suspend fun loadSessionProgress(
        session: WorkoutSession,
        extraExercises: List<TodaySessionExercise>,
    ): TodaySessionProgress {
        val sets = workoutRepository.getSets(session.id)
        val states = workoutRepository.getExerciseStates(session.id)
        return TodaySessionProgress(
            routineId = session.routineId,
            completedExerciseIds = sets.filter { it.completed }.map { it.exerciseId }.toSet(),
            skippedExerciseIds = states.filter { it.skipped }.map { it.exerciseId }.toSet(),
            pickedExerciseIds = pickedExerciseIds(sets.map { it.exerciseId }.distinct(), states),
            extraExercises = extraExercises,
        )
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

    /**
     * 今日休息：把今天改成休息日并按 [mode] 顺延之后的排期。
     *
     * 今天这次训练还没结束（中途退出、练到一半）时先把它作废——今天都不练了，留着这次未完成的
     * 训练没有意义；已记录的组连着这次训练一起删掉，由数据层级联处理。
     */
    fun restToday(mode: RestTodayMode) {
        viewModelScope.launch {
            _todaySession.value
                ?.takeUnless { it.isFinished }
                ?.let { workoutRepository.deleteSession(it.id) }
            takeRestToday(mode)
            refresh()
            // 日历页可能已经取过数，改完排期要让它跟着重算。
            dataRevision.bump()
            widgetManager.updateTodayWidget()
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
