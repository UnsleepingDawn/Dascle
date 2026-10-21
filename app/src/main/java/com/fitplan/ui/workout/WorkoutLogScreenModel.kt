package com.fitplan.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.BuildConfig
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.UpdateExerciseProgression
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.ExerciseProgressHint
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WeightUnit
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.model.isWeightAtLeast
import com.fitplan.domain.model.isWeightAtMost
import com.fitplan.domain.model.isWeightGreaterThan
import com.fitplan.domain.model.isWeightLessThan
import com.fitplan.domain.model.pickedExerciseIds
import com.fitplan.domain.model.resolve
import com.fitplan.domain.model.sameWeight
import com.fitplan.domain.repository.ExerciseLowReminderRepository
import com.fitplan.domain.repository.ExerciseProgressHintRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.presentation.unit.WeightUnitPreferences
import com.fitplan.reminder.RestState
import com.fitplan.reminder.RestTimer
import com.fitplan.ui.common.toKilogramsOrNull
import com.fitplan.ui.common.toWeightText
import com.fitplan.ui.exercise.DEFAULT_REST_SECONDS
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
    /**
     * 重量是不是用户自己手改过（而不是照默认值预填、或由上一组带过来的）。
     *
     * 完成一组时会把这一组的重量带给后面还没做的组，只覆盖没手改过的那些，
     * 所以这个标记决定了一行的重量会不会被后面接着改。
     */
    val weightEdited: Boolean = false,
)

/**
 * 某一组低于计划目标、正在问用户要不要重填。
 *
 * [index] 是动作内的组序号；用户确认「就这样」后才按这个位置把那组记录上。
 */
data class LowTargetReminder(
    val exerciseId: Long,
    val index: Int,
)

/**
 * 低量提醒点「重新填一次」后正在填的设置表单。
 *
 * [index] 是这一组在动作内的组序号；用户「保存」后只写回数值、**不**顺手记录这一组。
 */
data class LowTargetRetry(
    val exerciseId: Long,
    val index: Int,
)

/**
 * 动作卡「默认设置」保存完、正在问要不要把新目标用到这次训练剩下的组。
 *
 * [previous] 是保存前的目标值：还没勾选的组行靠它判断「仍是按旧目标预填的」，只有这种行才跟着换；
 * 已经完成的组与用户手改过的数值一律保留。
 */
data class DefaultsApply(
    val exerciseId: Long,
    val previous: ExerciseTargetDraft,
)

/**
 * 重填表单里「计划目标」这一块提交上来的值。
 *
 * 与 [LogExercise] 不同，重量 / 时长用可空表达「没设」：清空重量输入框表示不写这一项。
 * 组数与休息在计划表里是 NOT NULL 带默认值，所以始终有值。
 */
data class ExerciseTargetDraft(
    val sets: Int,
    val reps: Int,
    val weight: Double?,
    val seconds: Int?,
    val restSeconds: Int,
)

/** 记录页里的一个动作：计划内动作带目标值，计划外动作用默认值。 */
data class LogExercise(
    val exerciseId: Long,
    val name: String,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val isExtra: Boolean,
    /**
     * 计划编排里的那条动作记录 id（`routine_exercise.id`）；计划外动作为 null。
     *
     * 重填弹窗据此判断能不能改计划目标：只有计划内的动作才写得回 `routine_exercise`。
     */
    val routineExerciseId: Long? = null,
    /** 计量方式（次数 / 时长），跟随动作库，记录页不允许切换。 */
    val metric: ExerciseMetric = ExerciseMetric.DEFAULT,
    /** 负重方式（外部负重 / 自重 / 辅助），决定要不要填重量。 */
    val loadMode: ExerciseLoadMode = ExerciseLoadMode.DEFAULT,
    /** 实际使用的重量单位：动作自己设了就用动作的，没设就跟随全局。录入与展示都按它换算。 */
    val weightUnit: WeightUnit,
    /** 这个动作自己设定的重量单位；null 表示跟随全局设置。 */
    val weightUnitOverride: WeightUnit? = null,
    /** 这个动作涉及的全部部位（主部位在前）；用来在训练页给出练前热身提示。 */
    val muscleGroups: List<MuscleGroup> = emptyList(),
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

    /**
     * 下一组该做的组的序号（第一组未完成的行）；返回 -1 表示每一组都已完成。
     *
     * 完成只能按顺序来，所以只有这一行上的「做完了」按钮是可点的，
     * 后面的行要么已完成、要么被锁定。
     */
    val nextSetIndex: Int get() = sets.indexOfFirst { !it.completed }

    /**
     * 最后一组已完成的组的序号（最后一条已完成的行）；返回 -1 表示一组都还没完成。
     *
     * 撤销只能从后往前，所以只有这一行上的「撤销」按钮是可点的，
     * 更早的那些已完成行要等后面的组退回来之后才能动。
     */
    val lastCompletedIndex: Int get() = sets.indexOfLast { it.completed }

    /** 这个动作当前的组是否已经全部勾完（一个组行都没有时不算完成）。 */
    val isDone: Boolean get() = sets.isNotEmpty() && sets.all { it.completed }

    /** 按次数还是按时长录入。 */
    val isTimed: Boolean get() = metric == ExerciseMetric.DURATION

    /** 要不要给重量输入框；纯自重动作不给。 */
    val showsWeight: Boolean get() = loadMode != ExerciseLoadMode.BODYWEIGHT

    /** 重量是不是「助力」（辅助引体向上等）：越大越轻松。 */
    val weightIsAssistance: Boolean get() = loadMode == ExerciseLoadMode.ASSISTED

    /** 达标与提示用的重量基准：计划目标重量优先，没设就用动作库默认重量。 */
    val weightBaseline: Double? get() = targetWeight ?: defaultWeight

    /** 达标与提示用的次数基准；都缺失时退回动作库的兜底次数 [Exercise.DEFAULT_REPS]。 */
    val repsBaseline: Int get() = targetReps.takeIf { it > 0 } ?: defaultReps ?: Exercise.DEFAULT_REPS

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
        /** 「建议做 x 个」：鼓励挑几个，挑多了只是提示，不拦人。 */
        val maxPicks: Int,
        /** 组名；为空表示没起过名字，界面回退到默认的「动作组」。 */
        val groupName: String? = null,
        val memberIds: List<Long>,
        val pickedIds: List<Long>,
    ) : LogItem {
        override val key: String get() = "g$groupId"

        /** 挑中的动作数已经超过建议值时，界面把计数标成醒目色。 */
        val overPicked: Boolean get() = pickedIds.size > maxPicks
    }
}

/**
 * 按列表顺序找出「某个部位第一次被练到」的那张卡片：返回「列表项 key -> 在这一项上方要提示的部位」。
 *
 * 从上往下扫一遍顶层列表项：某个部位第一次出现时记在那一项上，之后同部位不再提示，于是每个部位
 * 最多只在最靠前的那张卡上方给一条热身说明。动作组按**全部组内动作**算（不按本次挑中了谁），
 * 所以勾选 / 取消挑动作不会让提示条上下跳。
 *
 * [musclesOf] 取某个动作涉及的部位；查不到时返回空列表即可。
 */
internal fun warmupMusclesByItemKey(
    items: List<LogItem>,
    musclesOf: (Long) -> List<MuscleGroup>,
): Map<String, List<MuscleGroup>> {
    val seen = mutableSetOf<MuscleGroup>()
    val result = mutableMapOf<String, List<MuscleGroup>>()
    items.forEach { item ->
        val exerciseIds = when (item) {
            is LogItem.Exercise -> listOf(item.exerciseId)
            is LogItem.Group -> item.memberIds
        }
        val firstTimeMuscles = exerciseIds
            .flatMap(musclesOf)
            .distinct()
            .filter { seen.add(it) }
        if (firstTimeMuscles.isNotEmpty()) {
            result[item.key] = firstTimeMuscles
        }
    }
    return result
}

/**
 * 去掉本次训练里已经被划走的热身提示。
 *
 * 划走一条就代表这个部位已经热过身，它不该在更靠后的卡片上再冒出来，
 * 所以这里是把整个部位从表里摘掉，而不是只跳过这一条。
 */
internal fun Map<String, List<MuscleGroup>>.withoutDismissedWarmup(
    dismissed: Set<MuscleGroup>,
): Map<String, List<MuscleGroup>> = if (dismissed.isEmpty()) {
    this
} else {
    mapNotNull { (key, muscles) ->
        muscles.filterNot { it in dismissed }.takeIf { it.isNotEmpty() }?.let { key to it }
    }.toMap()
}

/**
 * 删掉 [removingKey] 那一格之后，这一格显示的热身提示里再也不会出现的部位。
 *
 * 这一格被删掉时，留在它上面的提示必须跟着一起收掉，否则收完高度、记录真的删掉那一帧会整块
 * 跳一下；而**后面还会出现**的部位要原地留着——移除这一格之后它会被挪到后面那张卡上，落点
 * 与原来基本重合（相邻两张卡时完全重合），留着反而看不出挪动。
 *
 * 算法就是「这一格现在显示的提示 减去 移除之后整张表还会显示的提示」。
 * [musclesOf] 取某个动作涉及的部位；查不到时返回空列表即可。
 */
internal fun warmupMusclesLeavingWith(
    items: List<LogItem>,
    removingKey: String,
    dismissed: Set<MuscleGroup>,
    musclesOf: (Long) -> List<MuscleGroup>,
): Set<MuscleGroup> {
    val current = warmupMusclesByItemKey(items, musclesOf)
        .withoutDismissedWarmup(dismissed)[removingKey]
        .orEmpty()
    if (current.isEmpty()) return emptySet()
    val stillShown = warmupMusclesByItemKey(items.filterNot { it.key == removingKey }, musclesOf)
        .withoutDismissedWarmup(dismissed)
        .values
        .flatten()
        .toSet()
    return current.filterNot { it in stillShown }.toSet()
}

/**
 * 「计划外动作」里选中了一个今天计划里已经有的动作时的提醒。
 *
 * 同一个动作在同一次训练里重复排进去，记录页会冒出两张同名卡、动作记录被拆成两处，
 * 所以这里只提醒、不真的添加：要么去那张已有的卡片上加组，要么去对应的动作组里挑中它。
 */
sealed interface ExtraExerciseNotice {

    /** 用户选中的动作名。 */
    val name: String

    /** 这个动作在今天的计划里是单独排的一个动作。 */
    data class AlreadyInWorkout(override val name: String) : ExtraExerciseNotice

    /** 这个动作属于今天计划里的某个动作组；[groupName] 为空表示这个组没起过名字。 */
    data class InGroup(override val name: String, val groupName: String?) : ExtraExerciseNotice
}

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
    /** 实际使用的重量单位；弹窗里的基准值与输入都按它换算，比较仍在千克空间。 */
    val weightUnit: WeightUnit,
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
            // 容差内算持平：磅换算成千克后是长小数，精确比较会把「刚填的同一个值」判成没变难。
            if (loadMode == ExerciseLoadMode.ASSISTED) {
                target.isWeightLessThan(baseline)
            } else {
                target.isWeightGreaterThan(baseline)
            }
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
    private val lowReminderRepository: ExerciseLowReminderRepository,
    private val updateProgress: UpdateExerciseProgression,
    private val widgetManager: WidgetManager,
    private val restTimer: RestTimer,
    private val dataRevision: DataRevision,
    private val weightUnitPreferences: WeightUnitPreferences,
) : ViewModel() {

    /**
     * 当前 App 版本号（`versionCode`）。
     *
     * 低量提醒标记按它来判断「本版本是否已提醒过」：版本一变，旧标记自然失效，
     * 相当于每次更新都给所有动作重新开放一次提醒。
     */
    private val versionCode = BuildConfig.VERSION_CODE.toLong()

    /**
     * 当前的全局默认训练重量单位。
     *
     * 构造时同步读一次，保证首帧铺出来的预填值就是对的；之后订阅偏好变化：
     * 设置里改了全局单位，这里把界面上已经铺出来的重量文本整体换算一遍。
     */
    private var globalWeightUnit: WeightUnit = weightUnitPreferences.weightUnit.get()

    init {
        viewModelScope.launch {
            weightUnitPreferences.weightUnit.changes().collect { unit ->
                val previous = globalWeightUnit
                if (previous == unit) return@collect
                globalWeightUnit = unit
                rebaseToGlobalUnit(previous = previous, next = unit)
            }
        }
    }

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

    /**
     * 组间休息的倒计时状态；由 app 级的 [RestTimer] 共享，退出记录页后仍继续走，
     * 「今日」页也读同一份状态。
     */
    val rest: StateFlow<RestState?> = restTimer.rest

    /** 每自增一次表示训练已经结束或放弃，界面据此退出记录页。 */
    private val _exitTick = MutableStateFlow(0)
    val exitTick: StateFlow<Int> = _exitTick.asStateFlow()

    /** 每自增一次表示刚有一个动作第一次全部勾完，界面据此放礼花。 */
    private val _celebrateTick = MutableStateFlow(0)
    val celebrateTick: StateFlow<Int> = _celebrateTick.asStateFlow()

    /** 计划外动作选择用的全量动作库。 */
    private val _allExercises = MutableStateFlow<List<Exercise>>(emptyList())
    val allExercises: StateFlow<List<Exercise>> = _allExercises.asStateFlow()

    /**
     * 手动收起的动作卡；只活在内存里。
     * 重新打开一次进行中的训练时，已经全部做完的动作会按 [collapseFinishedExercises] 预先收起，其余默认展开。
     */
    private val _collapsedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedExerciseIds: StateFlow<Set<Long>> = _collapsedExerciseIds.asStateFlow()

    /**
     * 本次训练里已经被左滑划走的热身提示：划走就代表这个部位已经热过身，本次训练不再显示。
     * 落库保存，所以退出重进、甚至换个会话重开 App 都保持；训练记录被删时跟着一起清掉。
     */
    private val _dismissedWarmupMuscles = MutableStateFlow<Set<MuscleGroup>>(emptySet())
    val dismissedWarmupMuscles: StateFlow<Set<MuscleGroup>> = _dismissedWarmupMuscles.asStateFlow()

    /** 非空表示正在问「要不要把目标提高一点」；[ProgressHint] 里的动作就是被问的那个。 */
    private val _progressHint = MutableStateFlow<ProgressHint?>(null)
    val progressHint: StateFlow<ProgressHint?> = _progressHint.asStateFlow()

    /** 非空表示用户选了方向，正在等他填「增加到多少」。 */
    private val _progressTargetInput = MutableStateFlow<ProgressTargetInput?>(null)
    val progressTargetInput: StateFlow<ProgressTargetInput?> = _progressTargetInput.asStateFlow()

    /** 非空表示目标值已填好，正在等他最后确认一次；这一步还没写库。 */
    private val _progressTargetConfirm = MutableStateFlow<ProgressTargetConfirm?>(null)
    val progressTargetConfirm: StateFlow<ProgressTargetConfirm?> = _progressTargetConfirm.asStateFlow()

    /** 非空表示某一组低于计划目标，正在问要不要重填；这时那组还没记录。 */
    private val _lowTargetReminder = MutableStateFlow<LowTargetReminder?>(null)
    val lowTargetReminder: StateFlow<LowTargetReminder?> = _lowTargetReminder.asStateFlow()

    /** 非空表示用户选了「重新填一次」，正在设置表单里改这一组的数值与计划目标。 */
    private val _lowTargetRetry = MutableStateFlow<LowTargetRetry?>(null)
    val lowTargetRetry: StateFlow<LowTargetRetry?> = _lowTargetRetry.asStateFlow()

    /** 非空表示选了一个今天计划里已经有的计划外动作，正在提醒用户去原处操作。 */
    private val _extraNotice = MutableStateFlow<ExtraExerciseNotice?>(null)
    val extraNotice: StateFlow<ExtraExerciseNotice?> = _extraNotice.asStateFlow()

    /** 非空表示动作卡的「默认设置」已经保存，正在问要不要把新目标用到这次训练剩下的组。 */
    private val _defaultsApply = MutableStateFlow<DefaultsApply?>(null)
    val defaultsApply: StateFlow<DefaultsApply?> = _defaultsApply.asStateFlow()

    /**
     * 这一场里已经处理过（点过任意一个按钮、或者点掉遮罩）的动作，处理过就不再重复弹；
     * 只活在内存里，重新打开这次训练会重新开始判定。
     */
    private val handledHintExerciseIds = mutableSetOf<Long>()

    /**
     * 这一场里已经放过礼花的动作。同一个动作只在**第一次**做满全部组时庆祝一次：
     * 撤销之后重新做完（或者中途退出再进来重新做）都不再重复放，免得同一个动作反复放礼花。
     * 只活在内存里，换一场训练会重新开始判定。
     */
    private val celebratedExerciseIds = mutableSetOf<Long>()

    /**
     * 这一场里被滑删移除的动作。除了落库，还要在内存里记一份：
     * 用户之后又从「计划外动作」把它加回来时，得知道要先把移除标记撤掉，否则重进又会消失。
     */
    private val excludedExerciseIds = mutableSetOf<Long>()

    /** 未开始阶段真正点「开始训练」时写进 `workout_session.routine_id`。 */
    private var routineId: Long? = null

    /** 正在插入这一场训练；用来挡住「开始训练」的连点，避免留下永远不结束的孤儿 session。 */
    private var starting = false

    private var sessionId: Long? = null

    /** true 表示正在编辑一次已经结束的训练：输入框解锁，改动即时落库。 */
    private var editing = false

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
        celebratedExerciseIds.clear()
        excludedExerciseIds.clear()
        _collapsedExerciseIds.value = emptySet()
        _dismissedWarmupMuscles.value = emptySet()
        _progressHint.value = null
        _progressTargetInput.value = null
        _progressTargetConfirm.value = null
        _lowTargetReminder.value = null
        _lowTargetRetry.value = null
        _extraNotice.value = null
        _defaultsApply.value = null
        starting = false
        viewModelScope.launch {
            if (sessionId != null) {
                if (reopen && workoutRepository.getSession(sessionId)?.isFinished == true) {
                    workoutRepository.reopenSession(sessionId)
                    // 这次训练重新变成进行中，今日页与日历得跟着重算，否则还会以为今天已经练完。
                    dataRevision.bump()
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
                _items.value = items.mapNotNull { it.toLogItem(pickedIds = emptySet()) }
                _exercises.value = items.flatMap { it.exercises }.map { it.toLogExercise(globalWeightUnit) }
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
        // 防重：连点「开始训练」会插入两条 workout_session，其中一条永远不结束，变成隔天还挂着的旧账。
        // 标记在挂起之前就置上，第二次点击直接返回；只靠界面禁用按钮挡不住极快的连点。
        if (starting || _phase.value != WorkoutPhase.NOT_STARTED) return
        starting = true
        viewModelScope.launch {
            try {
                val id = workoutRepository.startSession(
                    routineId = routineId,
                    name = _sessionName.value.ifBlank { fallbackName },
                    startedAt = Clock.System.now(),
                )
                dataRevision.bump()
                loadSession(id)
                widgetManager.updateTodayWidget()
            } finally {
                starting = false
            }
        }
    }

    fun updateWeight(exerciseId: Long, index: Int, value: String) {
        mutateSet(exerciseId, index) {
            it.copy(
                weight = value.filter { char -> char.isDigit() || char == '.' },
                weightEdited = true,
            )
        }
        persistEditedSet(exerciseId, index)
    }

    /**
     * 改某个动作的重量单位；[unit] 为 null 表示跟随全局设置。
     *
     * 三类组行分别处理：
     * - 已完成的组：库里的千克值不动，只把显示文本换算到新单位；
     * - 未完成但用户手改过的组：保留输入框里的数字，按新单位重新解释——用户通常是在纠正
     *   「刚才按错单位读了刻度」这件事；
     * - 未完成且仍是预填值的组：按千克基准在新单位下重新预填。
     */
    fun updateWeightUnit(exerciseId: Long, unit: WeightUnit?) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val oldUnit = exercise.weightUnit
        val newUnit = globalWeightUnit.resolve(unit)
        if (exercise.weightUnitOverride == unit && oldUnit == newUnit) return
        val baseline = exercise.weightBaseline
        mutate(exerciseId) { current ->
            current.copy(
                weightUnit = newUnit,
                weightUnitOverride = unit,
                sets = current.sets.map { entry ->
                    when {
                        entry.completed -> entry.weight.toKilogramsOrNull(oldUnit)
                            ?.let { kilograms -> entry.copy(weight = kilograms.toWeightText(newUnit)) }
                            ?: entry

                        entry.weightEdited -> entry

                        // 没有重量基准时预填值本来就是空（或由上一条带过来），原样保留。
                        baseline == null -> entry

                        else -> entry.copy(weight = baseline.toWeightText(newUnit))
                    }
                },
            )
        }
        viewModelScope.launch { exerciseRepository.updateWeightUnit(exerciseId, unit) }
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
     * 左滑划走某个部位的热身提示：本次训练不再显示，退出重进也保持。
     *
     * 划走即视为这个部位已经热过身，所以同部位更靠后的卡片也不会再冒出来。
     * 状态落在 `workout_warmup_dismissed` 上，没有 session（还没点「开始训练」）时只改内存。
     */
    fun dismissWarmupHint(muscle: MuscleGroup) {
        if (muscle in _dismissedWarmupMuscles.value) return
        _dismissedWarmupMuscles.value = _dismissedWarmupMuscles.value + muscle
        val currentSessionId = sessionId ?: return
        viewModelScope.launch { workoutRepository.dismissWarmupHint(currentSessionId, muscle) }
    }

    /**
     * 把动作从本次训练里移除：卡片与它本次已记录的组一起消失，计划编排本身不变，
     * 退出重进仍然保持（移除状态落在 `workout_exercise_state.excluded` 上）。
     */
    fun removeExercise(exerciseId: Long) {
        if (_exercises.value.none { it.exerciseId == exerciseId }) return
        _exercises.value = _exercises.value.filterNot { it.exerciseId == exerciseId }
        _items.value = _items.value.mapNotNull { item ->
            when {
                item is LogItem.Exercise && item.exerciseId == exerciseId -> null
                // 逻辑上滑删只挂在单独动作卡上，这里顺手把组内成员也摘掉，免得留下点不到的空位。
                item is LogItem.Group && exerciseId in item.memberIds -> {
                    val members = item.memberIds - exerciseId
                    if (members.isEmpty()) {
                        null
                    } else {
                        item.copy(memberIds = members, pickedIds = item.pickedIds - exerciseId)
                    }
                }

                else -> item
            }
        }
        _collapsedExerciseIds.value = _collapsedExerciseIds.value - exerciseId
        excludedExerciseIds += exerciseId
        persistState { sessionId ->
            workoutRepository.setExerciseExcluded(sessionId, exerciseId, excluded = true)
        }
    }

    /**
     * 解散整个动作组：组卡消失，组内每个成员都从本次训练里移除，挑中过的动作连同本次记录一起删掉。
     * 全员都被移除后，重进时这一组也不会再出现。
     */
    fun removeGroup(groupId: Long) {
        val group = _items.value.filterIsInstance<LogItem.Group>().firstOrNull { it.groupId == groupId } ?: return
        _items.value = _items.value.filterNot { it is LogItem.Group && it.groupId == groupId }
        _exercises.value = _exercises.value.filterNot { it.exerciseId in group.memberIds }
        _collapsedExerciseIds.value = _collapsedExerciseIds.value - group.memberIds.toSet()
        excludedExerciseIds += group.memberIds
        persistState { sessionId ->
            group.memberIds.forEach { workoutRepository.setExerciseExcluded(sessionId, it, excluded = true) }
        }
    }

    /**
     * 从动作组里挑一个动作来练；「建议做 x 个」只是指标，挑超了也照样接受。
     * 挑中后组卡片内会出现这个动作的子卡，能像单独动作一样录入。
     */
    fun pickGroupExercise(groupId: Long, exerciseId: Long) {
        val group = _items.value.firstOrNull { it is LogItem.Group && it.groupId == groupId } as? LogItem.Group
        if (group == null || exerciseId in group.pickedIds) return
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
            exercise.copy(sets = exercise.sets + exercise.appendedEntry())
        }
        persistSetCount(exerciseId)
    }

    /**
     * 减一组：只减还没做的那一组（从最后一行没做的开始减），已经完成的组不动；
     * 一组都没做过时不做任何事。界面负责在「只剩最后一组且没做」时改走跳过确认。
     *
     * 减掉的正好是这个动作最后一组没做的（剩下的组都已做完）时，等同做满：自动收起并放礼花。
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
        collapseWhenAllSetsDone(exerciseId)
    }

    /**
     * 完成 / 撤销一组：完成时立刻落库，并启动组间休息倒计时。
     *
     * 完成只能按顺序、撤销只能反着来：一个动作里只有排在最前面的那一组还没做完时，
     * 才允许完成它；也只有最后那一组已完成的组，才允许退回未完成。这样记录永远是一段
     * 「前 k 组已完成」的连续前缀，中间不会被跳过或挖空。界面会把当前不可点的那几档
     * 按钮置灰。
     */
    fun toggleCompleted(exerciseId: Long, index: Int) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val entry = exercise.sets.getOrNull(index) ?: return
        val currentSessionId = sessionId ?: return

        viewModelScope.launch {
            if (entry.completed) {
                // 顺序约束：不是「最后那一组已完成的组」就不接受，界面也会把这类按钮置灰。
                if (index != exercise.lastCompletedIndex) return@launch

                entry.id?.let { setId ->
                    workoutRepository.updateSet(
                        entry.toWorkoutSet(
                            id = setId,
                            sessionId = currentSessionId,
                            exerciseId = exerciseId,
                            index = index,
                            completed = false,
                            weightUnit = exercise.weightUnit,
                        ),
                    )
                }
                mutateSet(exerciseId, index) { it.copy(completed = false) }
                return@launch
            }

            // 顺序约束：不是「最靠前的未完成组」就不接受，界面也会把这类按钮置灰。
            if (index != exercise.nextSetIndex) return@launch

            val reps = entry.reps.toIntOrNull()
            val seconds = entry.seconds.toIntOrNull()
            val valid = if (exercise.isTimed) seconds != null && seconds > 0 else reps != null && reps > 0
            if (!valid) return@launch

            // 低于计划目标：这个动作在当前版本还没提醒过时，先弹窗给一次重填机会，暂不记录。
            if (isBelowTarget(exercise, entry) && !lowReminderRepository.isShown(exerciseId, versionCode)) {
                _lowTargetReminder.value = LowTargetReminder(exerciseId, index)
                return@launch
            }

            completeSet(exerciseId, index)
        }
    }

    /**
     * 真正把一组记为完成：落库、更新界面、自动收起、开始休息，并判定渐进提示。
     *
     * 从 [toggleCompleted] 里抽出来，好让「就这样」这条路径在确认后再走一次同样的收尾；
     * 走这里时调用方已经校验过顺序与数值合法性。
     */
    private suspend fun completeSet(exerciseId: Long, index: Int) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val entry = exercise.sets.getOrNull(index) ?: return
        val currentSessionId = sessionId ?: return

        val reps = entry.reps.toIntOrNull()
        val seconds = entry.seconds.toIntOrNull()
        val valid = if (exercise.isTimed) seconds != null && seconds > 0 else reps != null && reps > 0
        if (!valid) return

        val setId = entry.id ?: workoutRepository.addSet(
            sessionId = currentSessionId,
            exerciseId = exerciseId,
            setIndex = index + 1,
            // 纯自重动作不给重量输入框，落库时也不要凭空写一个 0。
            weight = if (exercise.showsWeight) entry.weight.toKilogramsOrNull(exercise.weightUnit) else null,
            reps = if (exercise.isTimed) null else reps,
            durationSeconds = if (exercise.isTimed) seconds else null,
        )

        // addSet 写进去的是「未完成」的占位行，勾选状态得再更新一次才落库。
        workoutRepository.updateSet(
            entry.toWorkoutSet(
                id = setId,
                sessionId = currentSessionId,
                exerciseId = exerciseId,
                index = index,
                completed = true,
                weightUnit = exercise.weightUnit,
            ),
        )

        mutate(exerciseId) { current ->
            current.copy(
                sets = current.sets.mapIndexed { i, item ->
                    if (i == index) item.copy(id = setId, completed = true) else item
                },
            )
        }

        // 这一组定下的重量带给后面还没做、也没手改过的组：第一组做了多重，后面就照着来。
        carryWeightToLaterSets(exerciseId, index).forEach { persistEditedSet(exerciseId, it) }

        collapseWhenAllSetsDone(exerciseId)
        startRest(exercise.name, exercise.restSeconds)
        maybeShowProgressHint(exerciseId)
    }

    /**
     * 把刚做完那一组（序号 [index]）的重量带给它后面还没做、也没被用户手改过的组。
     *
     * 用户在第一组定了重量之后，后面几组默认就照这个重量做，不必一组一组再填一遍；
     * 已经手改过的行不会被覆盖，所以某一组临时加重量时也只从那一组往后带。
     * 纯自重动作没有重量输入框、重量留空时都不做任何事。
     *
     * 返回被改写过的组序号，供编辑已结束的训练时把改动写回库。
     */
    private fun carryWeightToLaterSets(exerciseId: Long, index: Int): List<Int> {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return emptyList()
        if (!exercise.showsWeight) return emptyList()
        val weight = exercise.sets.getOrNull(index)?.weight ?: return emptyList()
        if (weight.isBlank()) return emptyList()

        val changed = mutableListOf<Int>()
        mutate(exerciseId) { current ->
            current.copy(
                sets = current.sets.mapIndexed { i, item ->
                    if (i > index && !item.completed && !item.weightEdited && item.weight != weight) {
                        changed += i
                        item.copy(weight = weight)
                    } else {
                        item
                    }
                },
            )
        }
        return changed
    }

    /**
     * 这一组是不是低于计划目标，用于决定要不要弹重填提醒。
     *
     * 只比对这个动作的主计量项，不做跨项「任一项不达」：
     * - 要填重量的动作只看重量（辅助类动作重量越大助力越多、越轻松，所以「高于基准」才算低，
     *   重量留空也算低）；计划与动作库都没给重量基准时不提醒。
     * - 计时动作只看时长；其余动作只看次数。
     */
    private fun isBelowTarget(exercise: LogExercise, entry: SetEntry): Boolean {
        if (exercise.showsWeight) {
            val weightBaseline = exercise.weightBaseline ?: return false
            val weight = entry.weight.toKilogramsOrNull(exercise.weightUnit) ?: return true
            return when (exercise.loadMode) {
                // 容差内算持平：磅换算出的长小数不该被判成低于 / 高于目标。
                ExerciseLoadMode.ASSISTED -> weight.isWeightGreaterThan(weightBaseline)
                else -> weight.isWeightLessThan(weightBaseline)
            }
        }
        if (exercise.isTimed) {
            val secondsBaseline = exercise.secondsBaseline ?: return false
            return (entry.seconds.toIntOrNull() ?: 0) < secondsBaseline
        }
        return (entry.reps.toIntOrNull() ?: 0) < exercise.repsBaseline
    }

    /**
     * 低量提醒点「重新填一次」：关掉提醒，打开设置表单让用户改这一组的数值，
     * 想改的话还能顺手把计划目标也调成更符合实际的水平。这时**不写**任何东西，
     * 也不标记已提醒——等表单里的「保存」或「取消」再收尾。
     */
    fun retryLowTargetSet() {
        val reminder = _lowTargetReminder.value ?: return
        _lowTargetReminder.value = null
        _lowTargetRetry.value = LowTargetRetry(reminder.exerciseId, reminder.index)
    }

    /**
     * 点掉低量提醒的遮罩：等同放弃重填，记下已提醒后关闭，这一组仍然不记录。
     */
    fun dismissLowTargetReminder() {
        val reminder = _lowTargetReminder.value ?: return
        _lowTargetReminder.value = null
        viewModelScope.launch { lowReminderRepository.markShown(reminder.exerciseId, versionCode) }
    }

    /** 重填表单点「取消」：丢弃改动，回到上一层提醒，让用户还能选「就这样」。 */
    fun dismissLowTargetRetry() {
        val retry = _lowTargetRetry.value ?: return
        _lowTargetRetry.value = null
        _lowTargetReminder.value = LowTargetReminder(retry.exerciseId, retry.index)
    }

    /**
     * 重填表单点「保存」：把这一组**实际做的数值直接换成新目标**，并把目标写进动作库默认值与所有计划，
     * 最后标记已提醒。**不**把这一组记为完成，用户确认无误后再自己点「做完了」。
     *
     * 目标值确实变了才写，没动过的字段碰都不碰，免得无谓地清掉渐进提示状态。
     */
    fun applyLowTargetRetry(targets: ExerciseTargetDraft) {
        val retry = _lowTargetRetry.value ?: return
        val exercise = _exercises.value.firstOrNull { it.exerciseId == retry.exerciseId } ?: return
        val index = retry.index
        _lowTargetRetry.value = null

        // 这一组的实际值按新目标覆盖；重量只在有重量框时写，次数 / 时长按计量方式二选一。
        mutateSet(retry.exerciseId, index) { entry ->
            entry.copy(
                weight = if (exercise.showsWeight) {
                    targets.weight.toWeightText(exercise.weightUnit)
                } else {
                    entry.weight
                },
                reps = if (exercise.isTimed) entry.reps else targets.reps.toString(),
                seconds = if (exercise.isTimed) targets.seconds?.toString() ?: entry.seconds else entry.seconds,
            )
        }
        // 编辑已结束的训练时，改完就落库；训练进行中的新组等勾选时再写。
        persistEditedSet(retry.exerciseId, index)

        viewModelScope.launch {
            applyPlanTargets(exercise, index, targets)
            lowReminderRepository.markShown(retry.exerciseId, versionCode)
        }
    }

    /**
     * 把动作卡「默认设置」里的新目标写进动作库默认值与所有计划，并同步卡片上的目标字段；
     * 保存后弹窗问一次要不要把新目标用到这次训练剩下的组（[applyDefaultsToCurrentWorkout]）。
     *
     * 组行先不动：「只改默认」是让这次训练保持原样，用户明确选「应用」才换算。
     */
    fun saveExerciseDefaults(exerciseId: Long, targets: ExerciseTargetDraft) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val previous = exercise.toTargetDraft()
        viewModelScope.launch {
            persistTargets(exercise, targets)
            _defaultsApply.value = DefaultsApply(exerciseId, previous)
        }
    }

    /** 「默认设置」保存后选「只改默认」：这次训练里的组行保持原样。 */
    fun dismissDefaultsApply() {
        _defaultsApply.value = null
    }

    /**
     * 「默认设置」保存后选「应用到剩下的组」：把还没勾选、仍是按旧目标预填的数值换成新目标，
     * 并把组行数补 / 缩到新目标组数。
     *
     * 已完成的组与用户手改过的数值一律保留；缩行只在训练进行中、且只收掉还没落库的尾部行，
     * 编辑已结束的训练时组行都已经在库里，只做数值换算不做增删。
     */
    fun applyDefaultsToCurrentWorkout() {
        val prompt = _defaultsApply.value ?: return
        _defaultsApply.value = null
        val exerciseId = prompt.exerciseId
        val previous = prompt.previous
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        // 编辑已结束的训练时组行都已经落库，缩行会留下对不上的记录，所以只做数值换算。
        val resizable = !editing
        mutate(exerciseId) { current ->
            val rebased = current.sets.map { entry ->
                entry.copy(
                    weight = current.targetWeight
                        ?.let { rebasedWeight(entry, previous.weight, it, current.weightUnit) }
                        ?: entry.weight,
                    reps = if (current.isTimed) {
                        entry.reps
                    } else {
                        rebasedReps(entry, previous.reps, current.targetReps)
                    },
                    seconds = if (current.isTimed) {
                        rebasedSeconds(entry, previous.seconds, current.targetSeconds ?: exercise.targetSeconds ?: 0)
                    } else {
                        entry.seconds
                    },
                )
            }
            current.copy(sets = resizedToTarget(rebased, current, resizable))
        }
        persistSetCount(exerciseId)
        // 编辑已结束的训练时，数值已经在库里，改过就写回；进行中的新组等勾选时再落库。
        if (editing) persistRebasedSets(exerciseId)
    }

    /**
     * 把重填表单里的计划目标写下去，并同步内存里的动作卡及其余组行。
     *
     * [index] 那一行是表单已经直接覆盖过的，不参与换算；其余「按旧目标预填、还没勾选」的组行换成新值。
     */
    private suspend fun applyPlanTargets(
        exercise: LogExercise,
        index: Int,
        targets: ExerciseTargetDraft,
    ) {
        val change = persistTargets(exercise, targets) ?: return
        val exerciseId = exercise.exerciseId
        val previousWeight = exercise.targetWeight
        val previousReps = exercise.targetReps
        val previousSeconds = exercise.targetSeconds
        mutate(exerciseId) { current ->
            current.copy(
                sets = current.sets.mapIndexed { i, entry ->
                    if (i == index) {
                        entry
                    } else {
                        entry.copy(
                            weight = change.weight
                                ?.let { rebasedWeight(entry, previousWeight, it, current.weightUnit) }
                                ?: entry.weight,
                            reps = change.reps?.let { rebasedReps(entry, previousReps, it) } ?: entry.reps,
                            seconds = change.seconds
                                ?.let { rebasedSeconds(entry, previousSeconds, it) }
                                ?: entry.seconds,
                        )
                    }
                },
            )
        }
    }

    /**
     * 把表单里的目标值写下去，并同步内存里动作卡的目标字段（**不动**组行）。
     *
     * 重量 / 次数 / 时长的写入范围与渐进提示一致（动作库默认值 + 所有计划）；
     * 目标组数与组间休息在 `exercise` 表里没有对应列，只写所有计划。返回真正改动过的字段。
     */
    private suspend fun persistTargets(
        exercise: LogExercise,
        targets: ExerciseTargetDraft,
    ): TargetChange? {
        val exerciseId = exercise.exerciseId
        val previousWeight = exercise.targetWeight
        val previousSeconds = exercise.targetSeconds

        // 改了才写：重量用可空表达「没设」，清空输入框不写这一项；次数 / 时长按计量方式二选一。
        // 重量比较带容差：磅换算成千克后是长小数，精确比较会把「同一个值」判成改过。
        val appliedWeight = targets.weight.takeIf { new ->
            exercise.showsWeight && new != null &&
                (previousWeight == null || !sameWeight(previousWeight, new))
        }
        val appliedSeconds = targets.seconds.takeIf {
            exercise.isTimed && it != null && it != previousSeconds
        }
        val appliedReps = targets.reps.takeIf { !exercise.isTimed && it != exercise.targetReps }
        val appliedSets = targets.sets.takeIf { it != exercise.targetSets }
        val appliedRest = targets.restSeconds.takeIf { it != exercise.restSeconds }
        if (appliedWeight == null && appliedSeconds == null && appliedReps == null &&
            appliedSets == null && appliedRest == null
        ) {
            return null
        }

        appliedWeight?.let { updateProgress.applyWeight(exerciseId, it) }
        appliedReps?.let { updateProgress.applyReps(exerciseId, it) }
        appliedSeconds?.let { updateProgress.applySeconds(exerciseId, it) }
        appliedSets?.let { updateProgress.applySets(exerciseId, it) }
        appliedRest?.let { updateProgress.applyRest(exerciseId, it) }

        mutate(exerciseId) { current ->
            current.copy(
                targetSets = appliedSets ?: current.targetSets,
                targetReps = appliedReps ?: current.targetReps,
                targetSeconds = appliedSeconds ?: current.targetSeconds,
                targetWeight = appliedWeight ?: current.targetWeight,
                restSeconds = appliedRest ?: current.restSeconds,
            )
        }
        return TargetChange(
            weight = appliedWeight,
            reps = appliedReps,
            seconds = appliedSeconds,
            sets = appliedSets,
            rest = appliedRest,
        )
    }

    /** 低量提醒点「就这样」：记下已提醒过，关掉弹窗，照原样把这一组记录上。 */
    fun keepLowTargetSet() {
        val reminder = _lowTargetReminder.value ?: return
        _lowTargetReminder.value = null
        viewModelScope.launch {
            lowReminderRepository.markShown(reminder.exerciseId, versionCode)
            completeSet(reminder.exerciseId, reminder.index)
        }
    }

    /**
     * 一个动作的组全部做完时的收尾：把卡片自动收起，把列表腾出来，并给界面发一次礼花信号；
     * 想改自己点「展开」。只在训练进行中生效，编辑已结束的训练时不动卡片。
     *
     * 两个触发点：勾完最后一组（[completeSet]），以及「减一组」正好减掉最后没做的那组、剩下的都已做完
     * （[removeSetRow]）。
     *
     * 礼花同一个动作只发第一次：撤销之后重新做满全部组不再重复放（见 [celebratedExerciseIds]）。
     * 自动收起则每次做满都会发生，和礼花是否放无关。
     */
    private fun collapseWhenAllSetsDone(exerciseId: Long) {
        if (_phase.value != WorkoutPhase.IN_PROGRESS || editing) return
        val sets = _exercises.value.firstOrNull { it.exerciseId == exerciseId }?.sets ?: return
        if (sets.isNotEmpty() && sets.all { it.completed }) {
            _collapsedExerciseIds.value = _collapsedExerciseIds.value + exerciseId
            if (celebratedExerciseIds.add(exerciseId)) {
                _celebrateTick.value += 1
            }
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
                                if (entry.isPrefilledWeight(baseline, exercise.weightUnit)) {
                                    entry.copy(weight = newWeight.toWeightText(exercise.weightUnit))
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
        // 比较一律回到千克空间并带容差，磅换算出的小数不会把同一个重量判成两个不同的值。
        val weightBaseline = if (exercise.showsWeight) {
            exercise.weightBaseline ?: completed
                .mapNotNull { it.weight.toKilogramsOrNull(exercise.weightUnit) }
                .let { weights ->
                    val first = weights.firstOrNull()
                    first?.takeIf { candidate -> weights.all { sameWeight(it, candidate) } }
                }
        } else {
            null
        }
        val repsBaseline = exercise.repsBaseline.takeIf { !exercise.isTimed }
        val secondsBaseline = exercise.secondsBaseline.takeIf { exercise.isTimed }
        if (exercise.isTimed && secondsBaseline == null) return

        val qualified = completed.all { entry ->
            val weight = entry.weight.toKilogramsOrNull(exercise.weightUnit)
            val weightOk = when (exercise.loadMode) {
                ExerciseLoadMode.BODYWEIGHT -> true
                ExerciseLoadMode.EXTERNAL ->
                    weightBaseline == null || (weight?.isWeightAtLeast(weightBaseline) == true)
                ExerciseLoadMode.ASSISTED ->
                    weightBaseline == null || (weight?.isWeightAtMost(weightBaseline) == true)
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
            weightUnit = exercise.weightUnit,
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
            // 已经进到「要不要提高目标」，就等于这个动作本版本有互动过，
            // 顺手把低量提醒标上，免得同一个动作又冒一次低量提示。
            lowReminderRepository.markShown(exerciseId, versionCode)
            _progressHint.value = hint
        }
    }

    /**
     * 用户从「计划外动作」里选中一个动作。
     *
     * 这个动作今天已经排在计划里时（单独排列，或属于某个动作组），重复加进来会让记录页出现
     * 两张同名卡、动作记录被拆成两处，所以只弹一句提醒、让用户去原处操作，不落库；
     * 只在动作确实不在计划里时才真的按计划外动作加进本次训练。
     */
    fun requestExtraExercise(exerciseId: Long) {
        val items = _items.value
        val name = _allExercises.value.firstOrNull { it.id == exerciseId }?.name.orEmpty()
        val standalone = items.any { it is LogItem.Exercise && it.exerciseId == exerciseId }
        val group = items.filterIsInstance<LogItem.Group>().firstOrNull { exerciseId in it.memberIds }
        val notice = when {
            standalone -> ExtraExerciseNotice.AlreadyInWorkout(name)
            group != null -> ExtraExerciseNotice.InGroup(name, group.groupName)
            else -> null
        }
        _extraNotice.value = notice
        // 这个动作之前被滑删移除过，现在又加回来：先把移除标记撤掉，否则重进记录页它又会消失。
        if (exerciseId in excludedExerciseIds) {
            excludedExerciseIds -= exerciseId
            persistState { sessionId ->
                workoutRepository.setExerciseExcluded(sessionId, exerciseId, excluded = false)
            }
        }
        if (notice == null) addExtraExercise(exerciseId)
    }

    /** 点掉「这个动作已经在今天的计划里」的提醒。 */
    fun dismissExtraNotice() {
        _extraNotice.value = null
    }

    /** 把动作库里的动作临时加进本次训练，与新排进计划的动作使用同一套默认目标。 */
    private fun addExtraExercise(exerciseId: Long) {
        if (_exercises.value.any { it.exerciseId == exerciseId }) return
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId) ?: return@launch
            val targets = exercise.defaultTargets()
            val extra = LogExercise(
                exerciseId = exercise.id,
                name = exercise.name,
                targetSets = targets.sets,
                targetReps = targets.reps,
                restSeconds = targets.restSeconds,
                isExtra = true,
                metric = exercise.metric,
                loadMode = exercise.loadMode,
                weightUnit = globalWeightUnit.resolve(exercise.weightUnit),
                weightUnitOverride = exercise.weightUnit,
                muscleGroups = exercise.muscleGroups,
                targetWeight = targets.weight,
                targetSeconds = targets.seconds,
                defaultWeight = exercise.defaultWeight,
                defaultReps = exercise.defaultReps,
                defaultDurationSeconds = exercise.defaultDurationSeconds,
                description = exercise.description,
            )
            _exercises.value = _exercises.value + extra.copy(sets = List(extra.targetSets) { emptyEntry(extra) })
            _items.value = _items.value + LogItem.Exercise(exerciseId)
        }
    }

    fun finishWorkout() {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            skipRest()
            workoutRepository.finishSession(currentSessionId, Clock.System.now())
            // 今日页与日历可能已经取过数，结束训练要让它们重算（否则还会显示「继续训练」）。
            dataRevision.bump()
            widgetManager.updateTodayWidget()
            // 结束训练后不停在只读汇总页，直接退回今日页；今日卡片会标出这次练了哪些动作。
            _exitTick.value += 1
        }
    }

    /** 放弃训练：连同已经记录的组一起删掉，然后由界面退出记录页。 */
    fun abandonWorkout() {
        val currentSessionId = sessionId
        viewModelScope.launch {
            skipRest()
            currentSessionId?.let { workoutRepository.deleteSession(it) }
            // 删完今天可能就什么都没剩了（休息日的「临时训练」放弃），这天自然又变回休息日
            // ——休息日由「没有排期也没有训练」推导，不需要再补标记。
            dataRevision.bump()
            widgetManager.updateTodayWidget()
            _exitTick.value += 1
        }
    }

    /** 跳过休息：撤掉 app 级的休息计时（后台通知与到点闹钟一并撤销）。 */
    fun skipRest() {
        restTimer.cancel()
    }

    private fun startRest(exerciseName: String, seconds: Int) {
        restTimer.start(exerciseName, seconds)
    }

    private suspend fun loadSession(id: Long) {
        val session = workoutRepository.getSession(id) ?: return
        val sets = workoutRepository.getSets(id)
        val states = workoutRepository.getExerciseStates(id).associateBy { it.exerciseId }
        val items = session.routineId?.let { routineRepository.getItems(it) }.orEmpty()
        val plan = items.flatMap { it.exercises }
        val planByExercise = plan.associateBy { it.exerciseId }
        // 本次已经被移除的动作：计划里排过也不展示，它本次已记录的组在移除时已被删掉。
        val excludedIds = states.values.filter { it.excluded }.map { it.exerciseId }.toSet()
        excludedExerciseIds.addAll(excludedIds)
        val plannedExerciseIds = plan.map { it.exerciseId }.filterNot { it in excludedIds }
        val recordedExerciseIds = sets.map { it.exerciseId }.distinct().filterNot { it in excludedIds }
        val exerciseIds = (plannedExerciseIds + recordedExerciseIds).distinct()
        val exercisesById = exerciseRepository.getByIds(exerciseIds).associateBy { it.id }

        sessionId = session.id
        _dismissedWarmupMuscles.value = workoutRepository.getDismissedWarmupMuscles(session.id)
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
        // 组里挑过哪些动作：与今日页共用 `pickedExerciseIds` 的口径；已移除的动作不再算挑中。
        val pickedIds = pickedExerciseIds(recordedExerciseIds, states.values.filterNot { it.excluded })
        // 记录里出现、但计划编排里已经找不到的动作（计划外动作，或计划改过之后被移除的动作）。
        val extraItems = recordedExerciseIds.filterNot { it in plannedExerciseIds }.map { LogItem.Exercise(it) }
        _items.value = items.mapNotNull { it.toLogItem(pickedIds = pickedIds, excludedIds = excludedIds) } + extraItems
        _exercises.value = exerciseIds.map { exerciseId ->
            val routineExercise = planByExercise[exerciseId]
            val libraryExercise = exercisesById[exerciseId]
            // 计划内动作一律以计划设置为准；只有计划外动作才回落到动作库的默认目标。
            val libraryDefaults = if (routineExercise == null) libraryExercise?.defaultTargets() else null
            val ownSets = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }
            // 计量方式 / 负重方式一律以动作库为准；动作库查不到时退回计划编排上的值。
            val metric = libraryExercise?.metric ?: routineExercise?.metric ?: ExerciseMetric.DEFAULT
            val loadMode = libraryExercise?.loadMode ?: routineExercise?.loadMode ?: ExerciseLoadMode.DEFAULT
            // 重量单位：动作库里的覆盖值优先，其次是计划编排上带出来的同一个值；
            // 两者都为空表示跟随全局，这里先把全局值解析成「生效单位」。
            val weightUnitOverride = libraryExercise?.weightUnit ?: routineExercise?.weightUnit
            val weightUnit = globalWeightUnit.resolve(weightUnitOverride)
            val exercise = LogExercise(
                exerciseId = exerciseId,
                name = routineExercise?.exerciseName ?: libraryExercise?.name.orEmpty(),
                routineExerciseId = routineExercise?.id,
                targetSets = routineExercise?.targetSets ?: libraryDefaults?.sets ?: DEFAULT_TARGET_SETS,
                targetReps = routineExercise?.targetReps ?: libraryDefaults?.reps ?: Exercise.DEFAULT_REPS,
                restSeconds = routineExercise?.restSeconds ?: libraryDefaults?.restSeconds ?: DEFAULT_REST_SECONDS,
                isExtra = routineExercise == null,
                metric = metric,
                loadMode = loadMode,
                weightUnit = weightUnit,
                weightUnitOverride = weightUnitOverride,
                muscleGroups = libraryExercise?.muscleGroups ?: routineExercise?.muscleGroups.orEmpty(),
                // 只有计划外动作取动作库默认值；计划内动作保留用户设置，包括未填写的目标。
                targetWeight = if (routineExercise != null) routineExercise.targetWeight else libraryDefaults?.weight,
                targetSeconds = if (routineExercise !=
                    null
                ) {
                    routineExercise.targetSeconds
                } else {
                    libraryDefaults?.seconds
                },
                defaultWeight = libraryExercise?.defaultWeight,
                defaultReps = libraryExercise?.defaultReps,
                defaultDurationSeconds = libraryExercise?.defaultDurationSeconds,
                description = libraryExercise?.description.orEmpty(),
            )
            exercise.copy(
                // 动作组里的子卡没有「跳过动作」入口，老数据里若留着 skipped 标记按未跳过处理，
                // 免得界面上冒出一个「已跳过」却没有恢复按钮的死角。
                skipped = routineExercise?.groupId == null && states[exerciseId]?.skipped == true,
                sets = ownSets
                    .map { set ->
                        SetEntry(
                            id = set.id,
                            weight = set.weight.toWeightText(weightUnit),
                            reps = set.reps?.toString().orEmpty(),
                            seconds = set.durationSeconds?.toString().orEmpty(),
                            completed = set.completed,
                            // 库里存着重量的行都是当时实际用的值，重进后按「手改过」对待，
                            // 不让后面接着完成的组把它覆盖掉。
                            weightEdited = set.weight != null,
                        )
                    }
                    .sizedTo(
                        exercise = exercise,
                        expectedSets = states[exerciseId]?.setCount,
                        editable = !session.isFinished,
                    ),
            )
        }
        collapseFinishedExercises()
    }

    /**
     * 重新打开一次进行中的训练时，把已经全部做完的动作直接摆成收起状态。
     *
     * 和「勾完最后一组就自动收起」保持同一个口径，退出再进来不会先看到一屏练完的组行。
     * 已经结束的训练（回看与编辑）不动卡片，与 [collapseWhenAllSetsDone] 一致。
     */
    private fun collapseFinishedExercises() {
        if (_phase.value != WorkoutPhase.IN_PROGRESS || editing) return
        _collapsedExerciseIds.value =
            _collapsedExerciseIds.value + _exercises.value.filter { it.isDone }.map { it.exerciseId }
    }

    /**
     * 全局单位变了：物理重量不变，只把每张卡上已经铺出来的重量文本按「旧单位 → 千克 → 新单位」换算一遍。
     * 动作自己设了单位的不受影响。
     */
    private fun rebaseToGlobalUnit(previous: WeightUnit, next: WeightUnit) {
        _exercises.value = _exercises.value.map outer@{ exercise ->
            if (exercise.weightUnitOverride != null) return@outer exercise
            exercise.copy(
                weightUnit = next,
                sets = exercise.sets.map { entry ->
                    val kilograms = entry.weight.toKilogramsOrNull(previous) ?: return@map entry
                    entry.copy(weight = kilograms.toWeightText(next))
                },
            )
        }
        // 渐进提示里还开着的话，基准值的显示单位也跟着换；这个动作自己设过单位时不动。
        _progressHint.value = _progressHint.value?.let { hint ->
            val override = _exercises.value.firstOrNull { it.exerciseId == hint.exerciseId }?.weightUnitOverride
            if (override == null) hint.copy(weightUnit = next) else hint
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
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val entry = exercise.sets.getOrNull(index) ?: return
        val setId = entry.id ?: return
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            workoutRepository.updateSet(
                entry.toWorkoutSet(
                    id = setId,
                    sessionId = currentSessionId,
                    exerciseId = exerciseId,
                    index = index,
                    completed = entry.completed,
                    weightUnit = exercise.weightUnit,
                ),
            )
        }
    }

    /**
     * 「默认设置」应用到已结束的训练：每行都有 `workout_set` 记录，换算完逐行写回，退出后也是新数值。
     */
    private fun persistRebasedSets(exerciseId: Long) {
        val exercise = _exercises.value.firstOrNull { it.exerciseId == exerciseId } ?: return
        val currentSessionId = sessionId ?: return
        exercise.sets.forEachIndexed { index, entry ->
            val setId = entry.id ?: return@forEachIndexed
            viewModelScope.launch {
                workoutRepository.updateSet(
                    entry.toWorkoutSet(
                        id = setId,
                        sessionId = currentSessionId,
                        exerciseId = exerciseId,
                        index = index,
                        completed = entry.completed,
                        weightUnit = exercise.weightUnit,
                    ),
                )
            }
        }
    }

    private fun mutateSet(exerciseId: Long, index: Int, transform: (SetEntry) -> SetEntry) {
        mutate(exerciseId) { exercise ->
            exercise.copy(
                sets = exercise.sets.mapIndexed { i, entry -> if (i == index) transform(entry) else entry },
            )
        }
    }
}

/**
 * 一次目标值写入里真正变了的字段；null 表示这一项没动。
 *
 * 用来把「写库 + 同步卡片目标」与「其余组行要不要跟着换算」拆开：
 * 默认设置只做前者，用户选「应用到这次训练」时才做后者。
 */
private class TargetChange(
    val weight: Double?,
    val reps: Int?,
    val seconds: Int?,
    val sets: Int?,
    val rest: Int?,
)

/** 动作卡当前的目标值，作为「默认设置」保存前的快照。 */
private fun LogExercise.toTargetDraft(): ExerciseTargetDraft = ExerciseTargetDraft(
    sets = targetSets,
    reps = targetReps,
    weight = targetWeight,
    seconds = targetSeconds,
    restSeconds = restSeconds,
)

/**
 * 按新目标组数调整组行：不够就补空行；多出来的只在可缩时从尾部收掉，
 * 而且只收还没落库的行（已落库的行删了会与库里的记录对不上）。已完成的组永远保留。
 */
private fun resizedToTarget(
    rows: List<SetEntry>,
    exercise: LogExercise,
    resizable: Boolean,
): List<SetEntry> {
    if (!resizable) return rows
    val wanted = maxOf(exercise.targetSets, rows.count { it.completed })
    if (rows.size >= wanted) {
        var end = rows.size
        while (end > wanted && rows[end - 1].id == null) end -= 1
        return rows.take(end)
    }
    return rows + List(wanted - rows.size) { exercise.appendedEntry() }
}

/**
 * 计划编排的一项转成记录页的动作卡；[globalUnit] 是没有单独设过单位时的兜底。
 *
 * [RoutineExercise.weightUnit] 是动作自己的覆盖值（可能为空），这里一并带上，
 * 这样切换全局单位时能分清「跟随全局」和「显式设过」的动作。
 */
private fun RoutineExercise.toLogExercise(globalUnit: WeightUnit): LogExercise {
    val exercise = LogExercise(
        exerciseId = exerciseId,
        name = exerciseName,
        routineExerciseId = id,
        targetSets = targetSets,
        targetReps = targetReps,
        restSeconds = restSeconds,
        isExtra = false,
        metric = metric,
        loadMode = loadMode,
        weightUnit = globalUnit.resolve(weightUnit),
        weightUnitOverride = weightUnit,
        muscleGroups = muscleGroups,
        targetWeight = targetWeight,
        targetSeconds = targetSeconds,
    )
    return exercise.copy(sets = List(targetSets) { emptyEntry(exercise) })
}

/**
 * 计划编排的一项转成记录页列表项；[pickedIds] 是本次已经挑中的动作 id，只有动作组用得上。
 * 组内动作的先后由计划决定，所以挑中的顺序不会打乱组内顺序。
 *
 * [excludedIds] 是本次已经从训练里移除的动作：单项动作整项丢掉；动作组把移除掉的成员剔出去，
 * 一个成员都不剩时这个组也没得挑了，整组丢掉——这就是「解散整组」在重进后的样子。
 */
private fun RoutineItem.toLogItem(pickedIds: Set<Long>, excludedIds: Set<Long> = emptySet()): LogItem? = when (this) {
    is RoutineItem.Exercise -> if (value.exerciseId in excludedIds) null else LogItem.Exercise(value.exerciseId)

    is RoutineItem.Group -> {
        val memberIds = value.exercises.map { it.exerciseId }.filterNot { it in excludedIds }
        if (memberIds.isEmpty()) {
            null
        } else {
            LogItem.Group(
                groupId = value.id,
                maxPicks = value.maxPicks,
                groupName = value.name,
                memberIds = memberIds,
                pickedIds = memberIds.filter { it in pickedIds },
            )
        }
    }
}

/**
 * 一行的初始录入值：需要重量的动作预填计划目标重量，计时动作预填目标时长，计数动作预填目标次数；
 * 纯自重动作不预填重量（连输入框都没有）。
 */
private fun emptyEntry(exercise: LogExercise): SetEntry = SetEntry(
    weight = if (exercise.showsWeight) exercise.targetWeight.toWeightText(exercise.weightUnit) else "",
    reps = if (exercise.isTimed) "" else exercise.targetReps.toString(),
    seconds = if (exercise.isTimed) exercise.targetSeconds?.toString().orEmpty() else "",
)

/**
 * 「加一组」新增的那一行的初值：重量沿用最后一行手里的值，其余照 [emptyEntry] 的样板预填。
 *
 * 这样在一次训练里连着加组时，新行会接着当前正在做的重量，而不是突然跳回计划目标重量。
 */
private fun LogExercise.appendedEntry(): SetEntry {
    val entry = emptyEntry(this)
    val carried = sets.lastOrNull()?.takeIf { showsWeight }?.weight
    return if (carried.isNullOrBlank()) entry else entry.copy(weight = carried)
}

/**
 * 这一行还是「按基准值预填、且没勾选」的状态：默认值一变，这些行要跟着一起换；
 * 已经完成的记录保留当时实际用的数值，用户手动改过的行也不动。
 *
 * [unit] 是这一行显示用的单位，比较时先换回千克并用容差判断，磅换算出的长小数不会误判。
 */
private fun SetEntry.isPrefilledWeight(baseline: Double, unit: WeightUnit): Boolean {
    if (completed) return false
    val value = weight.toKilogramsOrNull(unit)
    return value == null || sameWeight(value, baseline)
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
 * 目标值改动后，把这一行「按旧目标预填、还没勾选」的数值换成 [next]；
 * 已完成的记录与用户手改过的行保留原样。旧目标为空时无从判断，直接不动。
 *
 * [previous] 与 [next] 都是千克，[unit] 是这一行显示用的单位。
 */
private fun rebasedWeight(entry: SetEntry, previous: Double?, next: Double, unit: WeightUnit): String {
    val baseline = previous ?: return entry.weight
    return if (entry.isPrefilledWeight(baseline, unit)) {
        next.toWeightText(unit)
    } else {
        entry.weight
    }
}

private fun rebasedReps(entry: SetEntry, previous: Int, next: Int): String {
    return if (entry.isPrefilledReps(previous)) next.toString() else entry.reps
}

private fun rebasedSeconds(entry: SetEntry, previous: Int?, next: Int): String {
    val baseline = previous ?: return entry.seconds
    return if (entry.isPrefilledSeconds(baseline)) next.toString() else entry.seconds
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
    weightUnit: WeightUnit,
): WorkoutSet = WorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setIndex = index + 1,
    weight = weight.toKilogramsOrNull(weightUnit),
    reps = reps.toIntOrNull(),
    durationSeconds = seconds.toIntOrNull(),
    completed = completed,
)
