package com.fitplan.domain.model

data class RoutineExercise(
    val id: Long,
    val routineId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    /** 主要刺激的部位，[muscleGroups] 里的第一个。 */
    val muscleGroup: MuscleGroup,
    /** 次要刺激的部位，不重复 [muscleGroup]。 */
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    /** 计量方式（次数 / 时长），跟随动作库，编排页不允许切换。 */
    val metric: ExerciseMetric = ExerciseMetric.DEFAULT,
    /** 负重方式（外部负重 / 自重 / 辅助），决定要不要填目标重量。 */
    val loadMode: ExerciseLoadMode = ExerciseLoadMode.DEFAULT,
    /** 默认重量（kg）；辅助类动作表示辅助重量。为 null 表示自重或不需要记录重量。 */
    val targetWeight: Double? = null,
    /** 默认时长（秒）；只在 [ExerciseMetric.DURATION] 下有意义。 */
    val targetSeconds: Int? = null,
    /** 所属的动作组 id；为 null 表示这个动作在计划里单独排列，不参与组内挑选。 */
    val groupId: Long? = null,
) {
    /** 该动作涉及的全部部位，主部位在前、次部位去重在后。 */
    val muscleGroups: List<MuscleGroup>
        get() = (listOf(muscleGroup) + secondaryMuscleGroups).distinct()

    /** 计时类动作（平板支撑、农夫行走等）。 */
    val isTimed: Boolean get() = metric == ExerciseMetric.DURATION

    /** 编排页要不要给「默认重量」输入框；纯自重动作不给。 */
    val showsWeight: Boolean get() = loadMode != ExerciseLoadMode.BODYWEIGHT

    /** 重量是不是「助力」（辅助引体向上等）。 */
    val weightIsAssistance: Boolean get() = loadMode == ExerciseLoadMode.ASSISTED
}
