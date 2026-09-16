package com.fitplan.domain.model

import kotlin.time.Instant

data class Exercise(
    val id: Long,
    val name: String,
    /** 主要刺激的部位，[muscleGroups] 里的第一个。 */
    val muscleGroup: MuscleGroup,
    /** 次要刺激的部位，不重复 [muscleGroup]。 */
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val description: String,
    val isCustom: Boolean,
    val createdAt: Instant,
    /** 计量方式（次数 / 时长），由动作库决定，用户不能切换。 */
    val metric: ExerciseMetric = ExerciseMetric.DEFAULT,
    /** 负重方式（外部负重 / 自重 / 辅助），决定要不要记录重量。 */
    val loadMode: ExerciseLoadMode = ExerciseLoadMode.DEFAULT,
    /** 内置动作的默认重量（kg）；辅助类动作表示辅助重量。null 表示自重或种子没有标注。 */
    val defaultWeight: Double? = null,
    /** 内置动作的默认时长（秒）；只在 [ExerciseMetric.DURATION] 下有意义。 */
    val defaultDurationSeconds: Int? = null,
    /** 内置动作的默认次数；渐进提示写入的基准，null 表示还没标过。 */
    val defaultReps: Int? = null,
) {
    /** 该动作涉及的全部部位，主部位在前、次部位去重在后。 */
    val muscleGroups: List<MuscleGroup>
        get() = (listOf(muscleGroup) + secondaryMuscleGroups).distinct()

    /** 按次数还是按时长录入。 */
    val isTimed: Boolean get() = metric == ExerciseMetric.DURATION

    /** 记录页 / 编排页要不要给重量输入框；纯自重动作不给。 */
    val showsWeight: Boolean get() = loadMode != ExerciseLoadMode.BODYWEIGHT

    /** 重量是不是「助力」（辅助引体向上等）：越大越轻松，渐进方向相反。 */
    val weightIsAssistance: Boolean get() = loadMode == ExerciseLoadMode.ASSISTED

    /** 默认次数，没标过时退回 [DEFAULT_REPS]。 */
    val repsOrDefault: Int get() = defaultReps ?: DEFAULT_REPS

    companion object {
        /** 动作库没有标注默认次数时的兜底目标次数。 */
        const val DEFAULT_REPS = 10
    }
}
