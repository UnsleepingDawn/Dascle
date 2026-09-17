package com.fitplan.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.ClearRestDay
import com.fitplan.domain.interactor.RestoreRestDay
import com.fitplan.domain.interactor.UpdateExerciseProgression
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.ExerciseProgressHint
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.repository.ExerciseProgressHintRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.reminder.RestNotifier
import com.fitplan.widget.WidgetManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** 记录页的三个阶段，由 `workout_session.finished_at` 决定。 */
enum class WorkoutPhase { NOT_STARTED, IN_PROGRESS, FINISHED }

/** 一组的录入状态；[id] 为 null 表示这一组还没写进 `workout_set`。 */
data class SetEntry(
    val id: Long? = null,
    val weight: String = "",
    val reps: String = "",
    val seconds: String = "",
    val completed: Boolean = false,
)

/** 记录页里的一个动作：计划内动作带目标值，计划外动作用默认值。 */
data class LogExercise(
    val exerciseId: Long,
    val name: String,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val isExtra: Boolean,
    /** 计量方式（次数 / 时长），跟随动作库，记录页不允许切换。 */
    val metric: ExerciseMetric = ExerciseMetric.DEFAULT,
    /** 负重方式（外部负重 / 自重 / 辅助），决定要不要填重量。 */
    val loadMode: ExerciseLoadMode = ExerciseLoadMode.DEFAULT,
    val skipped: Boolean = false,
    val sets: List<SetEntry> = emptyList(),
    /** 计划里设定的默认重量（kg）；辅助类动作表示助力。null 表示自重或不预填。 */
    val targetWeight: Double? = null,
    /** 计划里设定的默认时长（秒）；只在计时动作下有意义。 */
    val targetSeconds: Int? = null,
    /** 动作库里的默认重量（kg）；计划没设目标重量时用它兜底。 */
    val defaultWeight: Double? = null,
    /** 动作库里的默认次数；计划目标次数缺失时用它兜底。 */
    val defaultReps: Int? = null,
    /** 动作库里的默认时长（秒）；计划目标时长缺失时用它兜底。 */
    val defaultDurationSeconds: Int? = null,
    /** 动作库里的训练提示（动作要领）；空串表示这个动作没有提示。 */
    val description: String = "",
) {
    val completedSets: Int get() = sets.count { it.completed }

    /** 按次数还是按时长录入。 */
    val isTimed: Boolean get() = metric == ExerciseMetric.DURATION

    /** 要不要给重量输入框；纯自重动作不给。 */
    val showsWeight: Boolean get() = loadMode != ExerciseLoadMode.BODYWEIGHT

    /** 重量是不是「助力」（辅助引体向上等）：越大越轻松。 */
    val weightIsAssistance: Boolean get() = loadMode == ExerciseLoadMode.ASSISTED

    /** 达标与提示用的重量基准：计划目标重量优先，没设就用动作库默认重量。 */
    val weightBaseline: Double? get() = targetWeight ?: defaultWeight

    /** 达标与提示用的次数基准；都缺失时退回 [DEFAULT_TARGET_REPS]。 */
    val repsBaseline: Int get() = targetReps.takeIf { it > 0 } ?: defaultReps ?: DEFAULT_TARGET_REPS

    /** 达标与提示用的时长基准；计划与动作库都没标时为空，此时不提示加时间。 */
    val secondsBaseline: Int? get() = targetSeconds ?: defaultDurationSeconds
}

/**
 * 记录页列表的一项：单独排的动作，或者一个动作组。
 *
 * 动作组只负责「挑了哪几个动作来练」，动作自身的录入状态仍然统一放在 [LogExercise] 里，
 * 因此组内动作拥有和单独动作完全一致的记录能力。
 */
sealed interface LogItem {

    /** 列表 key；动作与动作组会重 id，必须带前缀。 */
    val key: String

    data class Exercise(val exerciseId: Long) : LogItem {
        override val key: String get() = "e$exerciseId"
    }

    data class Group(
        val groupId: Long,
        /** 「做其中 x 个」，即最多能挑几个。 */
        val maxPicks: Int,
        val memberIds: List<Long>,
        val pickedIds: List<Long>,
    ) : LogItem {
        override val key: String get() = "g$groupId"

        val canPickMore: Boolean get() = pickedIds.size < maxPicks
    }
}

/** 组间休息倒计时；[remainingSeconds] 为 0 表示刚刚结束。 */
data class RestState(
    val exerciseName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
)

/** 一次已结束训练的汇总。 */
data class WorkoutSummary(
    val completedSets: Int,
    val durationSeconds: Long,
)

/** 渐进提示要往上调的目标：[WEIGHT] 重量、[REPS] 次数、[SECONDS] 时长。 */
enum class ProgressKind { WEIGHT, REPS, SECONDS }

/**
 * 渐进提示：某个动作已经按当前基准做满目标组了，问问要不要把目标抬高一点。
 *
 * 同一个动作在一场训练里只会提示一次，处理过就记进 `handledHintExerciseIds`。
 * 可选的方向由 [metric] 与 [loadMode] 决定：自重只能加次数 / 加时间，负重可以加重量，
 * 辅助类动作则是「减少辅助重量」。
 */
data class ProgressHint(
    val exerciseId: Long,
    val name: String,
    /** 本次达标的组数，弹窗文案用。 */
    val completedSets: Int,
    val metric: ExerciseMetric,
    val loadMode: ExerciseLoadMode,
    /** 当前基准重量（kg）；辅助类动作是助力值。null 表示没有重量基准。 */
    val weightBaseline: Double?,
    /** 当前基准次数；只在 [ExerciseMetric.REPS] 下有意义。 */
    val repsBaseline: Int?,
    /** 当前基准时长（秒）；只在 [ExerciseMetric.DURATION] 下有意义。 */
    val secondsBaseline: Int?,
) {
    /** 弹窗里能给出哪些方向：越靠前越主推。 */
    val kinds: List<ProgressKind>
        get() = buildList {
            if (weightBaseline != null) add(ProgressKind.WEIGHT)
            when (metric) {
                ExerciseMetric.REPS -> add(ProgressKind.REPS)
                ExerciseMetric.DURATION -> if (secondsBaseline != null) add(ProgressKind.SECONDS)
            }
        }
}

/** 用户已经选了方向、正在填要把目标定到多少。 */
data class ProgressTargetInput(
    val hint: ProgressHint,
    val kind: ProgressKind,
)

/**
 * 用户填好了目标值、正在做最后确认；这时还没写库。
 *
 * [target] 是绝对值（kg / 次 / 秒），辅助类动作的重量表示助力值，比基准**小**才算更难。
 */
data class ProgressTargetConfirm(
    val hint: ProgressHint,
    val kind: ProgressKind,
    val target: Double,
)

/** [target] 是不是比当前基准更难：外重要更重、辅助助力要更轻、次数 / 时长要更多。 */
fun ProgressHint.isHarderTarget(kind: ProgressKind, target: Double?): Boolean {
    if (target == null) return false
    return when (kind) {
        ProgressKind.WEIGHT -> {
            val baseline = weightBaseline ?: return false
            if (loadMode == ExerciseLoadMode.ASSISTED) target < baseline else target > baseline
        }

        ProgressKind.REPS -> repsBaseline?.let { target.toInt() > it } ?: false
        ProgressKind.SECONDS -> secondsBaseline?.let { target.toInt() > it } ?: false
    }
}

/**
 * 训练记录页：把 `workout_session` / `workout_set` 当作唯一事实来源——勾选一组就立刻落库；
 * 「跳过动作」「增删组后的组行数」「动作组里挑中了谁」这类没勾选的临时状态写进
 * `workout_exercise_state`，所以中途退出甚至杀进程后重进，这些状态与已完成的记录都还在。
 * 只有输入框里还没勾选的数值留在内存里。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class WorkoutLogScreenModel(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val hintRepository: ExerciseProgressHintRepository,
    private val updateProgress: UpdateExerciseProgression,
    private val clearRestDay: ClearRestDay,
    private val restoreRestDay: RestoreRestDay,
    private val widgetManager: WidgetManager,
    private val restNotifier: RestNotifier,
    private val dataRevision: DataRevision,
) : ViewModel() {

    private val _phase = MutableStateFlow(WorkoutPhase.NOT_STARTED)
    val phase: StateFlow<WorkoutPhase> = _phase.asStateFlow()

    private val _sessionName = MutableStateFlow("")
    val sessionName: StateFlow<String> = _sessionName.asStateFlow()

    private val _startedAt = MutableStateFlow<Instant?>(null)
    val startedAt: StateFlow<Instant?> = _startedAt.asStateFlow()

    private val _finishedAt = MutableStateFlow<Instant?>(null)
    val finishedAt: StateFlow<Instant?> = _finishedAt.asStateFlow()

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)
    val summary: StateFlow<WorkoutSummary?> = _summary.asStateFlow()

    private val _exercises = MutableStateFlow<List<LogExercise>>(emptyList())
    val exercises: StateFlow<List<LogExercise>> = _exercises.asStateFlow()

    /** 列表结构：单独动作与动作组（组内含已挑中的子动作）交错排列。 */
    private val _items = MutableStateFlow<List<LogItem>>(emptyList())
    val items: StateFlow<List<LogItem>> = _items.asStateFlow()

    private val _rest = MutableStateFlow<RestState?>(null)
    val rest: StateFlow<RestState?> = _rest.asStateFlow()

    /** 每自增一次表示倒计时归零，界面据此震动一次。 */
    private val _restFinishedTick = MutableStateFlow(0)
    val restFinishedTick: StateFlow<Int> = _restFinishedTick.asStateFlow()

    /** 每自增一次表示训练已经放弃，界面据此退出记录页。 */
    private val _exitTick = MutableStateFlow(0)
    val exitTick: StateFlow<Int> = _exitTick.asStateFlow()

    /** 计划外动作选择用的全量动作库。 */
    private val _allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val allExercises: StateFlow<List<Exercise>> = _allExercises.asStateFlow()

    /** 手动收起的动作卡；只活在内存里，重新打开这次训练默认都是展开的。 */
    private val _collapsedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedExerciseIds: StateFlow<Set<Long>> = _collapsedExerciseIds.asStateFlow()

    /** 非空表示正在问「要不要把目标提高一点」；[ProgressHint] 里的动作就是被问的那个。 */
    private val _progressHint = MutableStateFlow<ProgressHint?>(null)
    val progressHint: StateFlow<ProgressHint?> = _progressHint.asStateFlow()

    /** 非空表示用户选了方向，正在等他填「增加到多少」。 */
    private val _progressTargetInput = MutableStateFlow<ProgressTargetInput?>(null)
    val progressTargetInput: StateFlow<ProgressTargetInput?> = _progressTargetInput.asStateFlow()

    /** 非空表示目标值已填好，正在等他最后确认一次；这一步还没写库。 */
    private val _progressTargetConfirm = MutableStateFlow<ProgressTargetConfirm?>(null)
    val progressTargetConfirm: StateFlow<ProgressTargetConfirm?> = _progressTargetConfirm.asStateFlow()

    /**
     * 这一场里已经处理过（点过任意一个按钮、或者点掉遮罩）的动作，处理过就不再重复弹；
     * 只活在内存里，重新打开这次训练会重新开始判定。
     */
    private val handledHintExerciseIds = mutableSetOf<Long>()

    /** 未开始阶段真正点「开始训练」时写进 `workout_session.routine_id`。 */
    private var routineId: Long? = null

    private var sessionId: Long? = null

    /** true 表示正在编辑一次已经结束的训练：输入框解锁，改动即时落库。 */
    private var editing = false

    private var restJob: Job? = null

    /**
     * [sessionId] 用于继续或回看一次训练；否则按 [routineId] 展示计划预览。
     * [editing] 为 true 时把已结束的训练当作可编辑状态打开（从训练日历的「编辑」进入）。
     * [reopen] 为 true 时先把已结束的训练重新置为进行中（从今日页的「还想练？」进入），
     * 之后加的动作与组都追加到同一次训练上。
     */
    fun load(
        sessionId: Long? = null,
        routineId: Long? = null,
        editing: Boolean = false,
        reopen: Boolean = false,
    ) {
        this.routineId = routineId
        this.editing = editing
        // 换了一场训练就重新开始判定，上一场处理过的动作不影响这一场。
        handledHintExerciseIds.clear()
        _collapsedExerciseIds.value = emptySet()
        _progressHint.value = null
        _progressTargetInput.value = null
        _progressTargetConfirm.value = null
        viewModelScope.launch {
            if (sessionId != null) {
                if (reopen && workoutRepository.getSession(sessionId)?.isFinished == true) {
                    workoutRepository.reopenSession(sessionId)
                    widgetManager.updateTodayWidget()
                }
                loadSession(sessionId)
            } else {
                this@WorkoutLogScreenModel.sessionId = null
                _phase.value = WorkoutPhase.NOT_STARTED
                _startedAt.value = null
                _finishedAt.value = null
                _summary.value = null
                _sessionName.value = routineId?.let { routineRepository.getById(it)?.name }.orEmpty()
                val items = routineId?.let { routineRepository.getItems(it) }.orEmpty()
                _items.value = items.map { it.toLogItem(pickedIds = emptySet()) }
                _exercises.value = items.flatMap { it.exercises }.map { it.toLogExercise() }
            }
        }
    }

    /** 首次打开计划外动作选择器时才去读动作库。 */
    fun loadExercises() {
        if (_allExercises.value.isNotEmpty()) return
        viewModelScope.launch {
            _allExercises.value = exerciseRepository.getAll()
                .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))
        }
    }

    /** [fallbackName] 为计划名为空时使用的兜底名字（由界面传入字符串资源）。 */
    fun startWorkout(fallbackName: String) {
        viewModelScope.launch {
            val id = workoutRepository.startSession(
                routineId = routineId,
                name = _sessionName.value.ifBlank { fallbackName },
                startedAt = Clock.System.now(),
            )
            // 今天开练了，今天就不再是休息日（休息日的「临时方案」走到这里把标记撤掉；
            // 有排期的日子本来就不该有休息标记，这里删的是空集）。
            clearRestDay(today())
            dataRevision.bump()
            loadSession(id)
            widgetManager.updateTodayWidget()
        }
    }

    fun updateWeight(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) { it.copy(weight = value.filter { char -> char.isDigit() || char == '.' }) }
        persistEditedSet(exerciseId, index)
    }

    fun updateReps(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) { it.copy(reps = value.filter(Char::isDigit)) }
        persistEditedSet(exerciseId, index)
    }

    fun updateSeconds(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) { it.copy(seconds = value.filter(Char::isDigit)) }
        persistEditedSet(exerciseId, index)
    }

    /** 跳过 / 恢复动作；这个状态会在本次训练里一直保留，退出重进也还在。 */
    fun toggleSkipped(exerciseId: Long) {
        val skipped = !(_exercises.value.firstOrNull { it.exerciseId == exerciseId }?.skipped ?: return)
        mutate(exerciseId) { it.copy(skipped = skipped) }
        // 跳过的卡只剩标题与「恢复动作」，顺手把收起标记清掉，恢复时直接看到完整的组行。
        if (skipped) {
            _collapsedExerciseIds.value = _collapsedExerciseIds.value - exerciseId
        }
        persistState { sessionId -> workoutRepository.setExerciseSkipped(sessionId, exerciseId, skipped) }
    }

    /** 手动收起 / 展开一张动作卡。 */
    fun toggleExerciseCollapsed(exerciseId: Long) {
        val collapsed = _collapsedExerciseIds.value
        _collapsedExerciseIds.value = if (exerciseId in collapsed) collapsed - exerciseId else collapsed + exerciseId
    }

    /**
     * 从动作组里挑一个动作来练；已经挑满「做其中 x 个」时不再接受新的。
     * 挑中后组卡片内会出现这个动作的子卡，能像单独动作一样录入。
     */
    fun pickGroupExercise(groupId: Long, exerciseId: Long) {
        val group = _items.value.firstOrNull { it is LogItem.Group && it.groupId == groupId } as? LogItem.Group
        if (group == null || exerciseId in group.pickedIds || !group.canPickMore) return
        _items.value = _items.value.map { item ->
            if (item is LogItem.Group && item.groupId == groupId) {
                item.copy(pickedIds = item.pickedIds + exerciseId)
            } else {
                item
            }
        }
        persistState { sessionId -> workoutRepository.setExercisePicked(sessionId, exerciseId, true) }
    }

    /**
     * 取消挑中的动作，只收起子卡、不删记录；
     * 已经练过的动作不给取消，免得用户以为记录也跟着没了。
     */
    fun unpickGroupExercise(groupId: Long, exerciseId: Long) {
        val completed = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.completedSets ?: 0
        if (completed > 0) return
        _items.value = _items.value.map { item ->
            if (item is LogItem.Group && item.groupId == groupId) {
                item.copy(pickedIds = item.pickedIds - exerciseId)
            } else {
                item
            }
        }
        persistState { sessionId -> workoutRepository.setExercisePicked(sessionId, exerciseId, false) }
    }

    fun addSetRow(exerciseId: Long) {
        mutate(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets + emptyEntry(exercise))
        }
        persistSetCount(exerciseId)
    }

    /**
     * 减一组：只减还没做的那一组（从最后一行没做的开始减），已经完成的组不动；
     * 一组都没做过时不做任何事。界面负责在「只剩最后一组且没做」时改走跳过确认。
     */
    fun removeSetRow(exerciseId: Long) {
        val sets = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets ?: return
        val index = sets.indexOfLast { !it.completed }
        if (index < 0) return
        val removed = sets[index]
        mutate(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets.filterIndexed { i, _ -> i != index })
        }
        removed.id?.let { setId -> viewModelScope.launch { workoutRepository.deleteSet(setId) } }
        persistSetCount(exerciseId)
    }

    /** 勾选 / 撤销一组：勾选时立刻落库，并启动组间休息倒计时。 */
    fun toggleCompleted(exerciseId: Long, index: Int) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val entry = exercise.sets.getOrNull(index) ?: return
        val currentSessionId = sessionId ?: return

        viewModelScope.launch {
            if (entry.completed) {
                entry.id?.let { setId ->
                    workoutRepository.updateSet(
                        entry.toWorkoutSet(setId, currentSessionId, exerciseId, index, completed = false),
                    )
                }
                mutateSet(exerciseId, index) { it.copy(completed = false) }
                return@launch
            }

            val reps = entry.reps.toIntOrNull()
            val seconds = entry.seconds.toIntOrNull()
            val valid = if (exercise.isTimed) seconds != null && seconds > 0 else reps != null && reps > 0
            if (!valid) return@launch

            val setId = entry.id ?: workoutRepository.addSet(
                sessionId = currentSessionId,
                exerciseId = exerciseId,
                setIndex = index + 1,
                // 纯自重动作不给重量输入框，落库时也不要凭空写一个 0。
                weight = if (exercise.showsWeight) entry.weight.toDoubleOrNull() else null,
                reps = if (exercise.isTimed) null else reps,
                durationSeconds = if (exercise.isTimed) seconds else null,
            )

            // addSet 写进去的是「未完成」的占位行，勾选状态得再更新一次才落库。
            workoutRepository.updateSet(
                entry.toWorkoutSet(setId, currentSessionId, exerciseId, index, completed = true),
            )

            mutate(exerciseId) { current ->
                current.copy(
                    sets = current.sets.mapIndexed { i, item ->
                        if (i == index) item.copy(id = setId, completed = true) else item
                    },
                )
            }

            collapseWhenAllSetsDone(exerciseId)
            startRest(exercise.name, exercise.restSeconds)
            maybeShowProgressHint(exerciseId)
        }
    }

    /**
     * 刚勾完一组：如果这个动作的组已经全部做完，就把卡片自动收起，把列表腾出来；
     * 想改自己点「展开」。只在训练进行中生效，编辑已结束的训练时不动卡片。
     */
    private fun collapseWhenAllSetsDone(exerciseId: Long) {
        if (_phase.value != WorkoutPhase.IN_PROGRESS || editing) return
        val sets = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets ?: return
        if (sets.isNotEmpty() && sets.all { it.completed }) {
            _collapsedExerciseIds.value = _collapsedExerciseIds.value + exerciseId
        }
    }

    /** 点「不用，下次再提醒我」或点掉遮罩：这一场不再打扰，该动作的暂缓档位保持不变。 */
    fun dismissProgressHint() {
        _progressHint.value?.let { handledHintExerciseIds += it.exerciseId }
        _progressHint.value = null
    }

    /** 选了某个方向（加重 / 加次数 / 加时间）：收起提示，改让用户填「增加到多少」。 */
    fun promptProgressIncrease(kind: ProgressKind) {
        val hint = _progressHint.value ?: return
        handledHintExerciseIds += hint.exerciseId
        _progressHint.value = null
        _progressTargetInput.value = ProgressTargetInput(hint, kind)
    }

    /** 关掉输入目标值的弹窗：什么都不改，这一场也不再问这个动作。 */
    fun dismissProgressTargetInput() {
        _progressTargetInput.value = null
    }

    /**
     * 填好目标值后先校验一次：确实比当前基准更难才收起输入框，把值交给确认弹窗。
     *
     * 这一步**不写库**，用户还能在确认弹窗里点「取消」回到输入。
     */
    fun confirmProgressTarget(target: Double) {
        val input = _progressTargetInput.value ?: return
        if (!input.hint.isHarderTarget(input.kind, target)) return
        _progressTargetInput.value = null
        _progressTargetConfirm.value = ProgressTargetConfirm(input.hint, input.kind, target)
    }

    /** 确认弹窗点「取消」：回到填目标值的弹窗，让用户改数值。 */
    fun dismissProgressTargetConfirm() {
        val confirm = _progressTargetConfirm.value ?: return
        _progressTargetConfirm.value = null
        _progressTargetInput.value = ProgressTargetInput(confirm.hint, confirm.kind)
    }

    /**
     * 点「暂时别提醒我提高目标」：推迟 3 / 5 / 7 / 9 天，点满后该动作进入休眠，
     * 不再按时间提醒，等用户主动提高目标时才重新激活。
     */
    fun snoozeProgressHint() {
        val hint = _progressHint.value ?: return
        handledHintExerciseIds += hint.exerciseId
        _progressHint.value = null
        viewModelScope.launch {
            val current = hintRepository.get(hint.exerciseId)
            val base = current ?: ExerciseProgressHint(exerciseId = hint.exerciseId)
            hintRepository.upsert(base.snoozed(Clock.System.now()))
        }
    }

    /**
     * 确认弹窗点「确定」：按 [ProgressTargetConfirm.kind] 把动作库的默认值与该动作在**所有**计划里的
     * 目标值一起换成 [ProgressTargetConfirm.target]，今天还没勾选的行也跟着改，
     * 已经完成的记录保留当时的实际数值。
     *
     * 辅助类动作（器械辅助引体向上）的重量是助力，目标值比基准小才算更难。
     */
    fun applyProgressTarget() {
        val confirm = _progressTargetConfirm.value ?: return
        _progressTargetConfirm.value = null
        val hint = confirm.hint
        val target = confirm.target
        viewModelScope.launch {
            when (confirm.kind) {
                ProgressKind.WEIGHT -> {
                    val baseline = hint.weightBaseline ?: return@launch
                    val newWeight = if (hint.loadMode == ExerciseLoadMode.ASSISTED) {
                        target.coerceAtLeast(0.0)
                    } else {
                        target
                    }
                    if (!hint.isHarderTarget(ProgressKind.WEIGHT, newWeight)) return@launch
                    updateProgress.applyWeight(hint.exerciseId, newWeight)
                    mutate(hint.exerciseId) { exercise ->
                        exercise.copy(
                            targetWeight = newWeight,
                            sets = exercise.sets.map { entry ->
                                if (entry.isPrefilledWeight(baseline)) {
                                    entry.copy(weight = newWeight.toWeightText())
                                } else {
                                    entry
                                }
                            },
                        )
                    }
                }

                ProgressKind.REPS -> {
                    val baseline = hint.repsBaseline ?: return@launch
                    val newReps = target.toInt()
                    if (newReps <= baseline) return@launch
                    updateProgress.applyReps(hint.exerciseId, newReps)
                    mutate(hint.exerciseId) { exercise ->
                        exercise.copy(
                            targetReps = newReps,
                            sets = exercise.sets.map { entry ->
                                if (entry.isPrefilledReps(baseline)) {
                                    entry.copy(reps = newReps.toString())
                                } else {
                                    entry
                                }
                            },
                        )
                    }
                }

                ProgressKind.SECONDS -> {
                    val baseline = hint.secondsBaseline ?: return@launch
                    val newSeconds = target.toInt()
                    if (newSeconds <= baseline) return@launch
                    updateProgress.applySeconds(hint.exerciseId, newSeconds)
                    mutate(hint.exerciseId) { exercise ->
                        exercise.copy(
                            targetSeconds = newSeconds,
                            sets = exercise.sets.map { entry ->
                                if (entry.isPrefilledSeconds(baseline)) {
                                    entry.copy(seconds = newSeconds.toString())
                                } else {
                                    entry
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * 一个动作的目标组全部按当前基准做完时，问一句要不要把目标提高一点。
     *
     * 可给的方向由动作类型决定：自重动作只能加次数 / 加时间；外部负重可以加重量；
     * 辅助类动作给的是「减少辅助重量」。暂缓期内、已休眠、已跳过、这一场处理过的动作都不打扰。
     */
    private fun maybeShowProgressHint(exerciseId: Long) {
        if (_phase.value != WorkoutPhase.IN_PROGRESS || editing) return
        if (exerciseId in handledHintExerciseIds) return
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        if (exercise.skipped) return

        val completed = exercise.sets.filter { it.completed }
        if (completed.size < exercise.targetSets) return

        // 重量基准：计划 / 动作库都没标时，退回「本次每组都一样的那个重量」（单杠悬挂这类可选负重动作）。
        val weightBaseline = if (exercise.showsWeight) {
            exercise.weightBaseline ?: completed
                .mapNotNull { it.weight.toDoubleOrNull() }
                .distinct()
                .singleOrNull()
        } else {
            null
        }
        val repsBaseline = exercise.repsBaseline.takeIf { !exercise.isTimed }
        val secondsBaseline = exercise.secondsBaseline.takeIf { exercise.isTimed }
        if (exercise.isTimed && secondsBaseline == null) return

        val qualified = completed.all { entry ->
            val weightOk = when (exercise.loadMode) {
                ExerciseLoadMode.BODYWEIGHT -> true
                ExerciseLoadMode.EXTERNAL ->
                    weightBaseline == null ||
                        (entry.weight.toDoubleOrNull()?.let { it >= weightBaseline } == true)
                ExerciseLoadMode.ASSISTED ->
                    weightBaseline == null ||
                        (entry.weight.toDoubleOrNull()?.let { it <= weightBaseline } == true)
            }
            if (!weightOk) return@all false
            if (exercise.isTimed) {
                (entry.seconds.toIntOrNull() ?: 0) >= (secondsBaseline ?: return@all false)
            } else {
                (entry.reps.toIntOrNull() ?: 0) >= (repsBaseline ?: return@all false)
            }
        }
        if (!qualified) return

        val hint = ProgressHint(
            exerciseId = exerciseId,
            name = exercise.name,
            completedSets = completed.size,
            metric = exercise.metric,
            loadMode = exercise.loadMode,
            weightBaseline = weightBaseline,
            repsBaseline = repsBaseline,
            secondsBaseline = secondsBaseline,
        )
        if (hint.kinds.isEmpty()) return

        viewModelScope.launch {
            val saved = hintRepository.get(exerciseId)
            // 查库期间用户可能已经练到下一个动作、或者处理过这条提示，落状态前再确认一次。
            if (exerciseId in handledHintExerciseIds) return@launch
            if (saved != null && !saved.canRemindAt(Clock.System.now())) return@launch
            _progressHint.value = hint
        }
    }

    /** 把动作库里的动作临时加进本次训练，目标值取动作库默认值（次数缺省 10 次）、休息 90 秒。 */
    fun addExtraExercise(exerciseId: Long) {
        if (_exercises.value.any { it.exerciseId == exerciseId }) return
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId) ?: return@launch
            val extra = LogExercise(
                exerciseId = exercise.id,
                name = exercise.name,
                targetSets = DEFAULT_TARGET_SETS,
                targetReps = exercise.repsOrDefault,
                restSeconds = DEFAULT_REST_SECONDS,
                isExtra = true,
                metric = exercise.metric,
                loadMode = exercise.loadMode,
                targetWeight = if (exercise.showsWeight) exercise.defaultWeight else null,
                targetSeconds = if (exercise.isTimed) exercise.defaultDurationSeconds else null,
                defaultWeight = exercise.defaultWeight,
                defaultReps = exercise.defaultReps,
                defaultDurationSeconds = exercise.defaultDurationSeconds,
                description = exercise.description,
            )
            _exercises.value = _exercises.value + extra.copy(sets = List(extra.targetSets) { emptyEntry(extra) })
            _items.value = _items.value + LogItem.Exercise(exerciseId)
        }
    }

    fun finishWorkout(note: String) {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            skipRest()
            if (note.isNotBlank()) {
                workoutRepository.updateSessionNote(currentSessionId, note.trim())
            }
            workoutRepository.finishSession(currentSessionId, Clock.System.now())
            loadSession(currentSessionId)
            widgetManager.updateTodayWidget()
        }
    }

    /** 放弃训练：连同已经记录的组一起删掉，然后由界面退出记录页。 */
    fun abandonWorkout() {
        val currentSessionId = sessionId
        viewModelScope.launch {
            skipRest()
            currentSessionId?.let { workoutRepository.deleteSession(it) }
            // 删完今天可能就什么都没剩了（休息日的「临时方案」放弃），这时把今天还原成休息日；
            // 今天还有排期或还有别的训练时，RestoreRestDay 自己会跳过。
            restoreRestDay(today())
            dataRevision.bump()
            widgetManager.updateTodayWidget()
            _exitTick.value += 1
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        _rest.value = null
        restNotifier.cancel()
    }

    private fun startRest(exerciseName: String, seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            _rest.value = null
            restNotifier.cancel()
            return
        }

        // 以截止时刻为准，而不是累加 delay：进程被系统冻结时回到前台也能算出正确剩余。
        val endAt = Clock.System.now() + seconds.seconds
        // 前台由本协程刷新界面；退到后台后由通知栏的系统倒计时接管。
        restNotifier.start(exerciseName, endAt)
        restJob = viewModelScope.launch {
            while (true) {
                val remaining = remainingSeconds(endAt)
                _rest.value = RestState(exerciseName, seconds, remaining)
                if (remaining <= 0) break
                delay(REST_TICK_MILLIS)
            }
            _restFinishedTick.value += 1
            // 留一会儿「休息结束」，然后自动收起。
            delay(REST_DONE_MILLIS)
            _rest.value = null
        }
    }

    /** 按截止时刻反算剩余秒数（向上取整，避免显示比实际少一秒）。 */
    private fun remainingSeconds(endAt: Instant): Int {
        val millis = (endAt - Clock.System.now()).inWholeMilliseconds
        return if (millis <= 0) 0 else ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    private suspend fun loadSession(id: Long) {
        val session = workoutRepository.getSession(id) ?: return
        val sets = workoutRepository.getSets(id)
        val states = workoutRepository.getExerciseStates(id).associateBy { it.exerciseId }
        val items = session.routineId?.let { routineRepository.getItems(it) }.orEmpty()
        val plan = items.flatMap { it.exercises }
        val planByExercise = plan.associateBy { it.exerciseId }
        val plannedExerciseIds = plan.map { it.exerciseId }
        val recordedExerciseIds = sets.map { it.exerciseId }.distinct()
        val exerciseIds = (plannedExerciseIds + recordedExerciseIds).distinct()
        val exercisesById = exerciseRepository.getByIds(exerciseIds).associateBy { it.id }

        sessionId = session.id
        _sessionName.value = session.name
        _startedAt.value = session.startedAt
        _finishedAt.value = session.finishedAt
        _phase.value = if (session.isFinished) WorkoutPhase.FINISHED else WorkoutPhase.IN_PROGRESS
        _summary.value = session.finishedAt?.let { finishedAt ->
            WorkoutSummary(
                completedSets = sets.count { it.completed },
                durationSeconds = (finishedAt - session.startedAt).inWholeSeconds,
            )
        }
        // 组里挑过哪些动作：有状态行的动作以 `workout_exercise_state.picked` 为准（取消挑选也能记住），
        // 没有状态行的是升级到 schema 12 之前的老训练，退回「本次已有 workout_set 记录」的旧判定。
        val recordedIds = recordedExerciseIds.toSet()
        val pickedIds = recordedIds - states.keys + states.values.filter { it.picked }.map { it.exerciseId }
        _items.value = buildList {
            items.forEach { add(it.toLogItem(pickedIds = pickedIds)) }
            // 记录里出现、但计划编排里已经找不到的动作（计划外动作，或计划改过之后被移除的动作）。
            recordedExerciseIds.filterNot { it in plannedExerciseIds }.forEach { add(LogItem.Exercise(it)) }
        }
        _exercises.value = exerciseIds.map { exerciseId ->
            val routineExercise = planByExercise[exerciseId]
            val libraryExercise = exercisesById[exerciseId]
            val ownSets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }
            // 计量方式 / 负重方式一律以动作库为准；动作库查不到时退回计划编排上的值。
            val metric = libraryExercise?.metric ?: routineExercise?.metric ?: ExerciseMetric.DEFAULT
            val loadMode = libraryExercise?.loadMode ?: routineExercise?.loadMode ?: ExerciseLoadMode.DEFAULT
            val exercise = LogExercise(
                exerciseId = exerciseId,
                name = routineExercise?.exerciseName ?: libraryExercise?.name.orEmpty(),
                targetSets = routineExercise?.targetSets ?: DEFAULT_TARGET_SETS,
                targetReps = routineExercise?.targetReps ?: libraryExercise?.repsOrDefault ?: DEFAULT_TARGET_REPS,
                restSeconds = routineExercise?.restSeconds ?: DEFAULT_REST_SECONDS,
                isExtra = routineExercise == null,
                metric = metric,
                loadMode = loadMode,
                targetWeight = routineExercise?.targetWeight,
                targetSeconds = routineExercise?.targetSeconds,
                defaultWeight = libraryExercise?.defaultWeight,
                defaultReps = libraryExercise?.defaultReps,
                defaultDurationSeconds = libraryExercise?.defaultDurationSeconds,
                description = libraryExercise?.description.orEmpty(),
            )
            exercise.copy(
                skipped = states[exerciseId]?.skipped == true,
                sets = ownSets
                    .map { set ->
                        SetEntry(
                            id = set.id,
                            weight = set.weight.toWeightText(),
                            reps = set.reps?.toString().orEmpty(),
                            seconds = set.durationSeconds?.toString().orEmpty(),
                            completed = set.completed,
                        )
                    }
                    .sizedTo(
                        exercise = exercise,
                        expectedSets = states[exerciseId]?.setCount,
                        editable = !session.isFinished,
                    ),
            )
        }
    }

    private fun mutate(exerciseId: Long, transform: (LogExercise) -> LogExercise) {
        _exercises.value = _exercises.value.map { if (it.exerciseId == exerciseId) transform(it) else it }
    }

    /**
     * 把记录页的临时状态（跳过 / 组行数 / 组内挑选）写进 `workout_exercise_state`。
     * 还没开始训练时（停在计划预览，[sessionId] 为空）没有可挂靠的训练，直接跳过。
     */
    private fun persistState(write: suspend (Long) -> Unit) {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch { write(currentSessionId) }
    }

    /** 把该动作当前展示的组行数落库，重进后照此铺行；减到 0 也要记下来。 */
    private fun persistSetCount(exerciseId: Long) {
        val count = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets?.size ?: return
        persistState { sessionId -> workoutRepository.setExerciseSetCount(sessionId, exerciseId, count) }
    }

    /**
     * 编辑已结束的训练时，重量/次数/时长一改就写库，退出页面也不会丢；
     * 还没落库的新行（[SetEntry.id] 为空）等勾选时再走 [toggleCompleted] 写入。
     */
    private fun persistEditedSet(exerciseId: Long, index: Int) {
        if (!editing) return
        val entry = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets?.getOrNull(index) ?: return
        val setId = entry.id ?: return
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            workoutRepository.updateSet(
                entry.toWorkoutSet(setId, currentSessionId, exerciseId, index, completed = entry.completed),
            )
        }
    }

    private fun mutateSet(exerciseId: Long, index: Int, transform: (SetEntry) -> SetEntry) {
        mutate(exerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets.mapIndexed { i, entry -> if (i == index) transform(entry) else entry },
            )
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val REST_TICK_MILLIS = 1_000L
        const val REST_DONE_MILLIS = 2_500L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/** 计划外动作的默认目标组数。 */
private const val DEFAULT_TARGET_SETS = 3

/** 计划外动作与各类兜底用的默认目标次数。 */
private const val DEFAULT_TARGET_REPS = 10

/** 计划外动作的默认组间休息（秒）。 */
private const val DEFAULT_REST_SECONDS = 90

private fun RoutineExercise.toLogExercise(): LogExercise {
    val exercise = LogExercise(
        exerciseId = exerciseId,
        name = exerciseName,
        targetSets = targetSets,
        targetReps = targetReps,
        restSeconds = restSeconds,
        isExtra = false,
        metric = metric,
        loadMode = loadMode,
        targetWeight = targetWeight,
        targetSeconds = targetSeconds,
    )
    return exercise.copy(sets = List(targetSets) { emptyEntry(exercise) })
}

/**
 * 计划编排的一项转成记录页列表项；[pickedIds] 是本次已经挑中的动作 id，只有动作组用得上。
 * 组内动作的先后由计划决定，所以挑中的顺序不会打乱组内顺序。
 */
private fun RoutineItem.toLogItem(pickedIds: Set<Long>): LogItem = when (this) {
    is RoutineItem.Exercise -> LogItem.Exercise(value.exerciseId)

    is RoutineItem.Group -> LogItem.Group(
        groupId = value.id,
        maxPicks = value.maxPicks,
        memberIds = value.exercises.map { it.exerciseId },
        pickedIds = value.exercises.map { it.exerciseId }.filter { it in pickedIds },
    )
}

/**
 * 一行的初始录入值：需要重量的动作预填计划目标重量，计时动作预填目标时长，计数动作预填目标次数；
 * 纯自重动作不预填重量（连输入框都没有）。
 */
private fun emptyEntry(exercise: LogExercise): SetEntry = SetEntry(
    weight = if (exercise.showsWeight) exercise.targetWeight.toWeightText() else "",
    reps = if (exercise.isTimed) "" else exercise.targetReps.toString(),
    seconds = if (exercise.isTimed) exercise.targetSeconds?.toString().orEmpty() else "",
)

/**
 * 这一行还是「按基准值预填、且没勾选」的状态：默认值一变，这些行要跟着一起换；
 * 已经完成的记录保留当时实际用的数值，用户手动改过的行也不动。
 */
private fun SetEntry.isPrefilledWeight(baseline: Double): Boolean {
    if (completed) return false
    val value = weight.toDoubleOrNull()
    return value == null || value == baseline
}

private fun SetEntry.isPrefilledReps(baseline: Int): Boolean {
    if (completed) return false
    val value = reps.toIntOrNull()
    return value == null || value == baseline
}

private fun SetEntry.isPrefilledSeconds(baseline: Int): Boolean {
    if (completed) return false
    val value = seconds.toIntOrNull()
    return value == null || value == baseline
}

/**
 * 把已落库的组补齐成可继续录入的样子：按本次训练的「期望组行数」补空行——
 * 用户增删过组就以增删后的数量为准（[expectedSets]），否则按计划目标组数铺；
 * 已经完成的行不会被追加新行——想多练一组得自己点「加一组」。
 * [editable] 为 false（已经结束的训练）时原样返回。
 */
private fun List<SetEntry>.sizedTo(
    exercise: LogExercise,
    expectedSets: Int?,
    editable: Boolean,
): List<SetEntry> {
    if (!editable) return this
    val expected = expectedSets ?: maxOf(size, exercise.targetSets)
    return if (size >= expected) this else this + List(expected - size) { emptyEntry(exercise) }
}

private fun SetEntry.toWorkoutSet(
    id: Long,
    sessionId: Long,
    exerciseId: Long,
    index: Int,
    completed: Boolean,
): WorkoutSet = WorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setIndex = index + 1,
    weight = weight.toDoubleOrNull(),
    reps = reps.toIntOrNull(),
    durationSeconds = seconds.toIntOrNull(),
    completed = completed,
)

/** 重量转成输入框文本：整数不带小数点，null 显示为空（重量输入框、计划默认重量共用）。 */
internal fun Double?.toWeightText(): String = when {
    this == null -> ""
    this == toLong().toDouble() -> toLong().toString()
    else -> toString()
}
