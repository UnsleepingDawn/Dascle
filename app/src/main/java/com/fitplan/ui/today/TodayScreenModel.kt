package com.fitplan.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.CloseStaleWorkouts
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.interactor.GetTodayWorkoutSession
import com.fitplan.domain.interactor.GetUpcomingTrainingPlan
import com.fitplan.domain.interactor.IsRestDay
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.interactor.UpcomingTrainingPlan
import com.fitplan.domain.interactor.UseUpcomingTrainingPlanForToday
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.WeightUnit
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.ScheduleRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.reminder.RestState
import com.fitplan.reminder.RestTimer
import com.fitplan.ui.exercise.DEFAULT_TARGET_SETS
import com.fitplan.ui.exercise.defaultTargets
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
    /**
     * 这个动作自己设的重量单位；null 表示跟随全局。这里存覆盖值而不是算好的生效单位，
     * 界面用 `LocalWeightUnit.current.resolve(weightUnit)` 现算，设置里改完立刻生效。
     */
    val weightUnit: WeightUnit?,
    val reps: Int?,
    val weight: Double?,
    val seconds: Int?,
)

/**
 * 今天这场训练里各动作的状态，供今日页的计划卡片标注：
 * 做满全部组的动作名前加一颗蓝圈勾；只做了至少一组、还没做满的动作名前换成一颗不断晕开的蓝色水波圆圈，
 * 表示「正在训练」；单独排列的动作被「跳过动作」时打删除线并变灰
 * （动作组里的成员不打删除线——组内该显示哪几个由数量规则决定）。
 *
 * [routineId] 是这次训练对应的计划 id；null 表示计划外训练（今天没照任何计划练），
 * 没有计划卡片可以标注。
 */
data class TodaySessionProgress(
    val routineId: Long?,
    /** 今天各动作已完成的组数；不在表里的动作表示一组都还没练。 */
    val completedSetCounts: Map<Long, Int> = emptyMap(),
    /**
     * 今天各动作的总组行数（该做几组）。判定「还没做满」用，口径与记录页铺行一致：
     * 用户加减过组以 `workout_exercise_state.set_count` 为准，否则取「已完成组数」与目标组数里的大者
     * （计划内动作取编排的 `targetSets`，计划外动作取动作库默认组数）。
     */
    val totalSetCounts: Map<Long, Int> = emptyMap(),
    /** 被「跳过动作」的动作。 */
    val skippedExerciseIds: Set<Long>,
    /**
     * 训练中被左滑「移除」的动作，可能是计划里的单独动作，也可能是某个动作组的成员。
     * 计划卡片据此把它们从渲染里剔掉（组被剔空就整组不显示），免得训练时删了动作、
     * 回到今日页还挂着一条。
     */
    val excludedExerciseIds: Set<Long> = emptySet(),
    /**
     * 今天这场训练是否已经结束。结束时动作组只保留「练过的」成员：整组一个都没练就整组不显示，
     * 练了一部分就只显示练过的那几个，不再拿删除线标出没做的；练过的动作也不再画「正在训练」的水波圆圈。
     */
    val finished: Boolean = false,
    /**
     * 练过、但不在本次计划编排里的动作，也就是训练中临时加的那些，
     * 供计划卡片在计划动作之后补出来并标上「临时」；没练过（一组都没勾）的不算。
     */
    val extraExercises: List<TodaySessionExercise> = emptyList(),
) {
    /** 有已完成组的动作。 */
    val completedExerciseIds: Set<Long> get() = completedSetCounts.keys
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TodayScreenModel(
    private val getScheduledRoutinesForDate: GetScheduledRoutinesForDate,
    private val getTodayWorkoutSession: GetTodayWorkoutSession,
    private val isRestDay: IsRestDay,
    private val getUpcomingTrainingPlan: GetUpcomingTrainingPlan,
    private val useUpcomingTrainingPlanForToday: UseUpcomingTrainingPlanForToday,
    private val closeStaleWorkouts: CloseStaleWorkouts,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val scheduleRepository: ScheduleRepository,
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
     * （今天没照任何计划练的那种）补一张总结卡片；其余情况为空列表。
     */
    private val _todaySessionExercises = MutableStateFlow<List<TodaySessionExercise>>(emptyList())
    val todaySessionExercises: StateFlow<List<TodaySessionExercise>> = _todaySessionExercises.asStateFlow()

    /**
     * 今天这场训练里各动作的状态（练到几组 / 跳过 / 组内挑中 / 被移除），训练中也算，
     * 供今日页计划卡片标注进度；今天还没开练时为 null。
     */
    private val _todaySessionProgress = MutableStateFlow<TodaySessionProgress?>(null)
    val todaySessionProgress: StateFlow<TodaySessionProgress?> = _todaySessionProgress.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * 今天之后最近的一次训练安排，休息日的「使用明天的计划」用它。
     * 之后完全没有排期时为 null，那个入口只能置灰。
     */
    private val _upcomingPlan = MutableStateFlow<UpcomingTrainingPlan?>(null)
    val upcomingPlan: StateFlow<UpcomingTrainingPlan?> = _upcomingPlan.asStateFlow()

    /**
     * 可排进今天的常驻计划，休息日「临时加一场训练」弹出的「选择要排入的计划」用它。
     * 只列常驻计划：临时计划不进列表（用户真要临时计划，弹窗里那个按钮会现编一份）。
     */
    private val _pickerRoutines = MutableStateFlow<List<Routine>>(emptyList())
    val pickerRoutines: StateFlow<List<Routine>> = _pickerRoutines.asStateFlow()

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
        viewModelScope.launch { load() }
    }

    /**
     * 进入今日页时调用：先清掉没用的临时计划，再取一次数。
     *
     * 之所以要连着取数、而且放在同一个协程里：刚在临时计划的编排页里什么都没加就退回来时，
     * 那张空壳计划正是排在今天、马上要被清掉的那张；若「取数」与「清扫」各起一个协程，
     * 取数可能先读到它，今日页就留下一张指向已经删掉的计划的卡片（点「开始训练」进去是空的）。
     *
     * 不把清扫并进 [refresh]：用户可能正停在编排页里给刚建好的空计划加动作，
     * 那种时刻刷新一次就会把它删掉。
     */
    fun clearUnusedTempPlans() {
        viewModelScope.launch {
            routineRepository.deleteUnusedTempPlans()
            load()
        }
    }

    private suspend fun load() {
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
        // （今天没照排期练的计划外训练）就没有计划编排可比；这张卡片也给出计划内动作的目标组数。
        val plannedRoutine = todaySession?.routineId
            ?.let { routineId -> routines.firstOrNull { it.routine.id == routineId } }
        val plannedExerciseIds = plannedRoutine?.exercises?.map { it.exerciseId }?.toSet()

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
        _todaySessionProgress.value = todaySession?.let { loadSessionProgress(it, plannedRoutine, extras) }

        _upcomingPlan.value = getUpcomingTrainingPlan(date)
        _pickerRoutines.value = routineRepository.getPermanent()
        _loaded.value = true
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
                    weightUnit = exercise.weightUnit,
                    reps = representative.reps,
                    weight = representative.weight,
                    seconds = representative.durationSeconds,
                )
            }
    }

    /**
     * 汇总今天这场训练里各动作的状态：各练到几组、哪些被跳过、哪些被移除、总共有几组。
     * 训练中也算，所以中途返回今日页就能看到进度；口径与记录页 `workout_exercise_state` 一致。
     *
     * [plannedRoutine] 是今天照练的那张计划（找不到 / 计划外训练时为 null），用来取计划内动作的目标组数；
     * [extraExercises] 由调用方按「练过但不在计划编排里」算好传进来（判定要拿到当天的排期，
     * 不在这层的职责里）。
     */
    private suspend fun loadSessionProgress(
        session: WorkoutSession,
        plannedRoutine: ScheduledRoutine?,
        extraExercises: List<TodaySessionExercise>,
    ): TodaySessionProgress {
        val sets = workoutRepository.getSets(session.id)
        val allStates = workoutRepository.getExerciseStates(session.id)
        // 已经从本次训练里移除的动作（记录页左滑删除）不算进度：跳过与挑中都不作数，
        // 但要把 id 单独带出去，让今日卡片把它们整个从渲染里剔掉。
        val states = allStates.filterNot { it.excluded }
        val completedSetCounts = sets.filter { it.completed }.groupingBy { it.exerciseId }.eachCount()
        // 用户加减过组的动作以它记录的组行数为准；没动过的按「已完成组数」与目标组数里的大者。
        val userSetCounts = states.mapNotNull { state -> state.setCount?.let { state.exerciseId to it } }.toMap()
        val plannedTargetSets = plannedRoutine?.exercises?.associate { it.exerciseId to it.targetSets }.orEmpty()
        // 计划外动作没有编排可比，目标组数取动作库默认值（与记录页给它们铺行用的口径一致）。
        val externalDefaults = defaultSetCounts(
            completedSetCounts.keys.filterNot { it in plannedTargetSets || it in userSetCounts },
        )
        val totalSetCounts = (completedSetCounts.keys + plannedTargetSets.keys + userSetCounts.keys)
            .associateWith { exerciseId ->
                userSetCounts[exerciseId]
                    ?: maxOf(
                        completedSetCounts[exerciseId] ?: 0,
                        plannedTargetSets[exerciseId] ?: externalDefaults[exerciseId] ?: DEFAULT_TARGET_SETS,
                    )
            }
        return TodaySessionProgress(
            routineId = session.routineId,
            completedSetCounts = completedSetCounts,
            totalSetCounts = totalSetCounts,
            skippedExerciseIds = states.filter { it.skipped }.map { it.exerciseId }.toSet(),
            excludedExerciseIds = allStates.filter { it.excluded }.map { it.exerciseId }.toSet(),
            finished = session.isFinished,
            extraExercises = extraExercises,
        )
    }

    /** 这些动作在动作库里的默认组数，用来给计划外的动作算今天一共该做几组。 */
    private suspend fun defaultSetCounts(exerciseIds: Collection<Long>): Map<Long, Int> {
        if (exerciseIds.isEmpty()) return emptyMap()
        return exerciseRepository.getByIds(exerciseIds.toList())
            .associate { it.id to it.defaultTargets().sets }
    }

    /**
     * 休息日改用之后最近一次训练的安排：把那天的计划挪到今天，之后的排期与休息日整体提前，
     * 然后请界面直接进训练记录页开练。
     */
    fun useUpcomingPlan() {
        val plan = _upcomingPlan.value ?: return
        viewModelScope.launch {
            val routineId = useUpcomingTrainingPlanForToday(plan)
            afterScheduleChange()
            _startRoutineRequest.value = routineId
        }
    }

    fun consumeStartRequest() {
        _startRoutineRequest.value = null
    }

    /**
     * 休息日「临时加一场训练」里挑了一个已有计划：把它排进今天，**不**跳训练页。
     *
     * 排完今日页自然从休息页变成这张计划的卡片，用户在卡片上点「开始训练」再开练；
     * 与「使用未来的计划」那种「改完就开练」的入口刻意区分开。
     */
    fun scheduleRoutineForToday(routineId: Long) {
        viewModelScope.launch {
            scheduleRepository.replacePlanOn(routineId, today())
            afterScheduleChange()
        }
    }

    /**
     * 休息日「临时加一场训练」里选了「临时计划」：现编一份只排今天的临时计划，然后把新计划 id 回调出去
     * 进编排页加动作。口径与日历「添加计划 → 临时计划」（`PlanCalendarScreenModel.createTempPlan`）一致。
     */
    fun createTempPlanForToday(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val routineId = routineRepository.insertTemp(name, Clock.System.now())
            scheduleRepository.replacePlanOn(routineId, today())
            afterScheduleChange()
            onCreated(routineId)
        }
    }

    /** 改完排期后的收尾：刷新今日页、通知其它页面重算、刷新桌面组件。 */
    private suspend fun afterScheduleChange() {
        load()
        // 日历页可能已经取过数，改完排期要让它跟着重算。
        dataRevision.bump()
        widgetManager.updateTodayWidget()
    }

    /**
     * 放弃今天的计划：先删掉今天这次还没结束的训练，再按 [shiftBack] 处理今天的排期。
     *
     * [shiftBack] 为 false（「该日休息」）只撤掉今天的排期，之后的安排原地不动；
     * 为 true（「顺着用未来计划」）把今天从时间线上抽掉，之后的整条时间线（训练日与休息日一起）
     * 整体提前一天。口径与日历编辑模式删除弹窗一致，区别只是这里固定只作用于今天。
     * 今天这次训练还没结束（中途退出、练到一半）时一并删掉——今天都不练了，留着它没有意义。
     */
    fun abandonTodayPlan(shiftBack: Boolean) {
        viewModelScope.launch {
            _todaySession.value
                ?.takeUnless { it.isFinished }
                ?.let { workoutRepository.deleteSession(it.id) }
            val dates = listOf(today())
            if (shiftBack) {
                scheduleRepository.deletePlansAndShiftBack(dates)
            } else {
                scheduleRepository.deletePlansOn(dates)
            }
            afterScheduleChange()
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
