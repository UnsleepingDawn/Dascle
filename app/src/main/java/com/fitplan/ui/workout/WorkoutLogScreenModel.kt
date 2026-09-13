package com.fitplan.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
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
) {
    val completedSets: Int get() = sets.count { it.completed }
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
    val volume: Double,
    val durationSeconds: Long,
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
    private val widgetManager: WidgetManager,
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

    /** 未开始阶段真正点「开始训练」时写进 `workout_session.routine_id`。 */
    private var routineId: Long? = null

    private var sessionId: Long? = null

    private var restJob: Job? = null

    /** [sessionId] 用于继续或回看一次训练；否则按 [routineId] 展示计划预览。 */
    fun load(sessionId: Long? = null, routineId: Long? = null) {
        this.routineId = routineId
        viewModelScope.launch {
            if (sessionId != null) {
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
    }

    fun updateReps(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) { it.copy(reps = value.filter(Char::isDigit)) }
    }

    fun updateSeconds(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) { it.copy(seconds = value.filter(Char::isDigit)) }
    }

    /** 切换计时 / 计数；同一动作已经有完成的组时不允许切换，避免两种记录混在一起。 */
    fun toggleTimed(exerciseId: Long) {
        mutate(exerciseId) { exercise ->
            if (exercise.sets.any { it.completed }) {
                exercise
            } else {
                exercise.copy(isTimed = !exercise.isTimed)
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
                val updated = current.sets.mapIndexed { i, item ->
                    if (i == index) item.copy(id = setId, completed = true) else item
                }
                // 勾完最后一组顺手补一个空行，方便接着加组。
                val sets = if (index == current.sets.lastIndex) updated + emptyEntry(current) else updated
                current.copy(sets = sets)
            }

            startRest(exercise.name, exercise.restSeconds)
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
    }

    private fun startRest(exerciseName: String, seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            _rest.value = null
            return
        }

        restJob = viewModelScope.launch {
            for (remaining in seconds downTo 0) {
                _rest.value = RestState(exerciseName, seconds, remaining)
                if (remaining == 0) {
                    _restFinishedTick.value += 1
                } else {
                    delay(1_000)
                }
            }
            // 留一会儿「休息结束」，然后自动收起。
            delay(REST_DONE_MILLIS)
            _rest.value = null
        }
    }

    private suspend fun loadSession(id: Long) {
        val session = workoutRepository.getSession(id) ?: return
        val sets = workoutRepository.getSets(id)
        val plan = session.routineId?.let { routineRepository.getExercises(it) }.orEmpty()
        val planByExercise = plan.associateBy { it.exerciseId }
        val exerciseIds = (plan.map { it.exerciseId } + sets.map { it.exerciseId }).distinct()
        val names = exerciseRepository.getByIds(exerciseIds).associate { it.id to it.name }

        sessionId = session.id
        _sessionName.value = session.name
        _startedAt.value = session.startedAt
        _finishedAt.value = session.finishedAt
        _phase.value = if (session.isFinished) WorkoutPhase.FINISHED else WorkoutPhase.IN_PROGRESS
        _summary.value = session.finishedAt?.let { finishedAt ->
            WorkoutSummary(
                completedSets = sets.count { it.completed },
                volume = sets.filter { it.completed }.sumOf { it.volume },
                durationSeconds = (finishedAt - session.startedAt).inWholeSeconds,
            )
        }
        _exercises.value = exerciseIds.map { exerciseId ->
            val routineExercise = planByExercise[exerciseId]
            val targetReps = routineExercise?.targetReps ?: DEFAULT_TARGET_REPS
            val ownSets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }
            val isTimed = ownSets.any { it.durationSeconds != null }
            LogExercise(
                exerciseId = exerciseId,
                name = routineExercise?.exerciseName ?: names[exerciseId].orEmpty(),
                targetSets = routineExercise?.targetSets ?: DEFAULT_TARGET_SETS,
                targetReps = targetReps,
                restSeconds = routineExercise?.restSeconds ?: DEFAULT_REST_SECONDS,
                isExtra = routineExercise == null,
                isTimed = isTimed,
                sets = ownSets
                    .map { set ->
                        SetEntry(
                            id = set.id,
                            weight = set.weight.toInputText(),
                            reps = set.reps?.toString().orEmpty(),
                            seconds = set.durationSeconds?.toString().orEmpty(),
                            completed = set.completed,
                        )
                    }
                    .withTailRow(
                        targetSets = routineExercise?.targetSets ?: DEFAULT_TARGET_SETS,
                        targetReps = targetReps,
                        isTimed = isTimed,
                        editable = !session.isFinished,
                    ),
            )
        }
    }

    private fun mutate(exerciseId: Long, transform: (LogExercise) -> LogExercise) {
        _exercises.value = _exercises.value.map { if (it.exerciseId == exerciseId) transform(it) else it }
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
        const val REST_DONE_MILLIS = 2_500L
    }
}

private fun RoutineExercise.toLogExercise(): LogExercise = LogExercise(
    exerciseId = exerciseId,
    name = exerciseName,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
    isExtra = false,
    sets = List(targetSets) { SetEntry(reps = targetReps.toString()) },
)

private fun emptyEntry(exercise: LogExercise): SetEntry =
    SetEntry(reps = if (exercise.isTimed) "" else exercise.targetReps.toString())

/**
 * 把已落库的组补齐成可继续录入的样子：不足目标组数时补空行，
 * 最后一组已经完成时再补一个空行，[editable] 为 false（已经结束的训练）时原样返回。
 */
private fun List<SetEntry>.withTailRow(
    targetSets: Int,
    targetReps: Int,
    isTimed: Boolean,
    editable: Boolean,
): List<SetEntry> {
    if (!editable) return this
    val empty = SetEntry(reps = if (isTimed) "" else targetReps.toString())
    val padded = if (size < targetSets) this + List(targetSets - size) { empty } else this
    return if (padded.isEmpty() || padded.last().completed) padded + empty else padded
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

private fun Double?.toInputText(): String = when {
    this == null -> ""
    this == toLong().toDouble() -> toLong().toString()
    else -> toString()
}
