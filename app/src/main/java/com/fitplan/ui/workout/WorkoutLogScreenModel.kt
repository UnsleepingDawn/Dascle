package com.fitplan.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.interactor.UpdateExerciseWeight
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseProgressHint
import com.fitplan.domain.model.RoutineExercise
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
    val isTimed: Boolean = false,
    val skipped: Boolean = false,
    val sets: List<SetEntry> = emptyList(),
    /** 计划里设定的默认重量（kg）；null 表示自重或不预填。 */
    val targetWeight: Double? = null,
    /** 计划里设定的默认时长（秒）；非 null 表示计划把该动作定为计时类。 */
    val targetSeconds: Int? = null,
    /** 动作库里的默认重量（kg）；计划没设目标重量时用它兜底。 */
    val defaultWeight: Double? = null,
) {
    val completedSets: Int get() = sets.count { it.completed }

    /** 判定达标与提示加重量用的基准：计划目标重量优先，没设就用动作库默认重量。 */
    val weightBaseline: Double? get() = targetWeight ?: defaultWeight
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

/**
 * 渐进重量提示：某个动作已经能拿 [currentWeight] 做满目标组了，问问要不要加重量。
 * 同一个动作在一场训练里只会提示一次，处理过就记进 `handledHintExerciseIds`。
 */
data class WeightIncreaseHint(
    val exerciseId: Long,
    val name: String,
    /** 当前基准重量（kg），用户填的增量在它之上累加。 */
    val currentWeight: Double,
    /** 本次达标的组数，弹窗文案用。 */
    val completedSets: Int,
)

/**
 * 训练记录页：把 `workout_session` / `workout_set` 当作唯一事实来源——勾选一组就立刻落库，
 * 没勾选的行只存在于内存里，所以中途退出甚至杀进程都不会丢已经完成的记录。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class WorkoutLogScreenModel(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val hintRepository: ExerciseProgressHintRepository,
    private val updateExerciseWeight: UpdateExerciseWeight,
    private val widgetManager: WidgetManager,
    private val restNotifier: RestNotifier,
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

    /** 非空表示正在问「要不要加重量」；[WeightIncreaseHint] 里的动作就是被问的那个。 */
    private val _weightHint = MutableStateFlow<WeightIncreaseHint?>(null)
    val weightHint: StateFlow<WeightIncreaseHint?> = _weightHint.asStateFlow()

    /** 非空表示用户点了「好的！」，正在等他填要加多少 kg。 */
    private val _weightIncreaseInput = MutableStateFlow<WeightIncreaseHint?>(null)
    val weightIncreaseInput: StateFlow<WeightIncreaseHint?> = _weightIncreaseInput.asStateFlow()

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
     * [reopen] 为 true 时先把已结束的训练重新置为进行中（从今日页的「计划外训练」进入），
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
        _weightHint.value = null
        _weightIncreaseInput.value = null
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
                _exercises.value = routineId
                    ?.let { routineRepository.getExercises(it) }
                    .orEmpty()
                    .map { it.toLogExercise() }
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

    /** 切换计时 / 计数；同一动作已经有完成的组时不允许切换，避免两种记录混在一起。 */
    fun toggleTimed(exerciseId: Long) {
        mutate(exerciseId) { exercise ->
            if (exercise.sets.any { it.completed }) {
                exercise
            } else {
                val timed = !exercise.isTimed
                // 切换后把空着的录入框按计划默认值补齐，避免新模式下输入框全是空的。
                exercise.copy(
                    isTimed = timed,
                    sets = exercise.sets.map { entry ->
                        if (timed) {
                            entry.copy(seconds = entry.seconds.ifBlank { exercise.targetSeconds?.toString().orEmpty() })
                        } else {
                            entry.copy(
                                weight = entry.weight.ifBlank { exercise.targetWeight.toWeightText() },
                                reps = entry.reps.ifBlank { exercise.targetReps.toString() },
                            )
                        }
                    },
                )
            }
        }
    }

    fun toggleSkipped(exerciseId: Long) {
        mutate(exerciseId) { it.copy(skipped = !it.skipped) }
    }

    fun addSetRow(exerciseId: Long) {
        mutate(exerciseId) { exercise ->
            exercise.copy(sets = exercise.sets + emptyEntry(exercise))
        }
    }

    /** 去掉最后一组；已经落库的那一组同时删掉。 */
    fun removeSetRow(exerciseId: Long) {
        val last = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets?.lastOrNull() ?: return
        mutate(exerciseId) { exercise -> exercise.copy(sets = exercise.sets.dropLast(1)) }
        last.id?.let { setId -> viewModelScope.launch { workoutRepository.deleteSet(setId) } }
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
                weight = if (exercise.isTimed) null else entry.weight.toDoubleOrNull(),
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

            startRest(exercise.name, exercise.restSeconds)
            maybeShowWeightHint(exerciseId)
        }
    }

    /** 点「不用，下次再提醒我」或点掉遮罩：这一场不再打扰，该动作的暂缓档位保持不变。 */
    fun dismissWeightHint() {
        _weightHint.value?.let { handledHintExerciseIds += it.exerciseId }
        _weightHint.value = null
    }

    /** 点「好的！」：收起提示，改让用户填要加多少 kg。 */
    fun promptWeightIncrease() {
        val hint = _weightHint.value ?: return
        handledHintExerciseIds += hint.exerciseId
        _weightHint.value = null
        _weightIncreaseInput.value = hint
    }

    /** 关掉输入增量的弹窗：什么都不改，这一场也不再问这个动作。 */
    fun dismissWeightIncreaseInput() {
        _weightIncreaseInput.value = null
    }

    /**
     * 点「暂时别提醒我加重量」：推迟 3 / 5 / 7 / 9 天，点满后该动作进入休眠，
     * 不再按时间提醒，等用户主动加重量时才重新激活。
     */
    fun snoozeWeightHint() {
        val hint = _weightHint.value ?: return
        handledHintExerciseIds += hint.exerciseId
        _weightHint.value = null
        viewModelScope.launch {
            val current = hintRepository.get(hint.exerciseId)
            val base = current ?: ExerciseProgressHint(exerciseId = hint.exerciseId)
            hintRepository.upsert(base.snoozed(Clock.System.now()))
        }
    }

    /**
     * 输入要加多少 kg 并确认：动作库的默认重量与所有计划编排的目标重量一起换成新值，
     * 今天还没勾选的行也跟着改，已经完成的记录保留当时的实际重量。
     */
    fun applyWeightIncrease(delta: Double) {
        val hint = _weightIncreaseInput.value ?: return
        _weightIncreaseInput.value = null
        val newWeight = hint.currentWeight + delta
        viewModelScope.launch {
            updateExerciseWeight.applyFromHint(hint.exerciseId, newWeight)
            mutate(hint.exerciseId) { exercise ->
                exercise.copy(
                    targetWeight = newWeight,
                    sets = exercise.sets.map { entry ->
                        if (entry.isPrefilledWith(hint.currentWeight)) {
                            entry.copy(weight = newWeight.toWeightText())
                        } else {
                            entry
                        }
                    },
                )
            }
        }
    }

    /**
     * 一个动作的目标组全部用「基准重量及以上、目标次数及以上」做完时，问一句要不要加重量。
     * 计时动作没有重量、暂缓期内与已休眠的动作、这一场已经处理过的动作都不打扰。
     */
    private fun maybeShowWeightHint(exerciseId: Long) {
        if (_phase.value != WorkoutPhase.IN_PROGRESS || editing) return
        if (exerciseId in handledHintExerciseIds) return
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        if (exercise.isTimed || exercise.skipped) return
        val baseline = exercise.weightBaseline ?: return

        val completed = exercise.sets.filter { it.completed }
        if (completed.size < exercise.targetSets) return
        val qualified = completed.all { entry ->
            val weight = entry.weight.toDoubleOrNull()
            val reps = entry.reps.toIntOrNull()
            weight != null && reps != null && weight >= baseline && reps >= exercise.targetReps
        }
        if (!qualified) return

        viewModelScope.launch {
            val hint = hintRepository.get(exerciseId)
            // 查库期间用户可能已经练到下一个动作、或者处理过这条提示，落状态前再确认一次。
            if (exerciseId in handledHintExerciseIds) return@launch
            if (hint != null && !hint.canRemindAt(Clock.System.now())) return@launch
            _weightHint.value = WeightIncreaseHint(
                exerciseId = exerciseId,
                name = exercise.name,
                currentWeight = baseline,
                completedSets = completed.size,
            )
        }
    }

    /** 把动作库里的动作临时加进本次训练，目标值取默认的 3 组 × 10 次、休息 90 秒。 */
    fun addExtraExercise(exerciseId: Long) {
        if (_exercises.value.any { it.exerciseId == exerciseId }) return
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId) ?: return@launch
            val extra = LogExercise(
                exerciseId = exercise.id,
                name = exercise.name,
                targetSets = DEFAULT_TARGET_SETS,
                targetReps = DEFAULT_TARGET_REPS,
                restSeconds = DEFAULT_REST_SECONDS,
                isExtra = true,
                defaultWeight = exercise.defaultWeight,
            )
            _exercises.value = _exercises.value + extra.copy(sets = List(extra.targetSets) { emptyEntry(extra) })
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
        val plan = session.routineId?.let { routineRepository.getExercises(it) }.orEmpty()
        val planByExercise = plan.associateBy { it.exerciseId }
        val exerciseIds = (plan.map { it.exerciseId } + sets.map { it.exerciseId }).distinct()
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
        _exercises.value = exerciseIds.map { exerciseId ->
            val routineExercise = planByExercise[exerciseId]
            val ownSets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }
            // 已经落库的组以库里的记录为准；还没开练时，计划设了默认时长就按计时展示。
            val isTimed = if (ownSets.isNotEmpty()) {
                ownSets.any { it.durationSeconds != null }
            } else {
                routineExercise?.targetSeconds != null
            }
            val exercise = LogExercise(
                exerciseId = exerciseId,
                name = routineExercise?.exerciseName ?: exercisesById[exerciseId]?.name.orEmpty(),
                targetSets = routineExercise?.targetSets ?: DEFAULT_TARGET_SETS,
                targetReps = routineExercise?.targetReps ?: DEFAULT_TARGET_REPS,
                restSeconds = routineExercise?.restSeconds ?: DEFAULT_REST_SECONDS,
                isExtra = routineExercise == null,
                isTimed = isTimed,
                targetWeight = routineExercise?.targetWeight,
                targetSeconds = routineExercise?.targetSeconds,
                defaultWeight = exercisesById[exerciseId]?.defaultWeight,
            )
            exercise.copy(
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
                    .paddedToTargetSets(exercise = exercise, editable = !session.isFinished),
            )
        }
    }

    private fun mutate(exerciseId: Long, transform: (LogExercise) -> LogExercise) {
        _exercises.value = _exercises.value.map { if (it.exerciseId == exerciseId) transform(it) else it }
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

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_TARGET_REPS = 10
        const val DEFAULT_REST_SECONDS = 90
        const val REST_TICK_MILLIS = 1_000L
        const val REST_DONE_MILLIS = 2_500L
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun RoutineExercise.toLogExercise(): LogExercise {
    val exercise = LogExercise(
        exerciseId = exerciseId,
        name = exerciseName,
        targetSets = targetSets,
        targetReps = targetReps,
        restSeconds = restSeconds,
        isExtra = false,
        isTimed = isTimed,
        targetWeight = targetWeight,
        targetSeconds = targetSeconds,
    )
    return exercise.copy(sets = List(targetSets) { emptyEntry(exercise) })
}

/** 一行的初始录入值：计划里设了默认重量/时长就先填进去，让输入框不是空的。 */
private fun emptyEntry(exercise: LogExercise): SetEntry = if (exercise.isTimed) {
    SetEntry(seconds = exercise.targetSeconds?.toString().orEmpty())
} else {
    SetEntry(weight = exercise.targetWeight.toWeightText(), reps = exercise.targetReps.toString())
}

/**
 * 这一行还是「按基准重量预填、且没勾选」的状态：默认重量一变，这些行要跟着一起换；
 * 已经完成的记录保留当时实际用的重量，用户手动改过重量的行也不动。
 */
private fun SetEntry.isPrefilledWith(baseline: Double): Boolean {
    if (completed) return false
    val weight = weight.toDoubleOrNull()
    return weight == null || weight == baseline
}

/**
 * 把已落库的组补齐成可继续录入的样子：不足目标组数时补空行，让计划目标一眼可见；
 * 已经完成的行不会被追加新行——想多练一组得自己点「加一组」。
 * [editable] 为 false（已经结束的训练）时原样返回。
 */
private fun List<SetEntry>.paddedToTargetSets(exercise: LogExercise, editable: Boolean): List<SetEntry> {
    if (!editable) return this
    val missing = exercise.targetSets - size
    return if (missing <= 0) this else this + List(missing) { emptyEntry(exercise) }
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
