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
    /** 默认重量（kg）；为 null 表示自重或不需要记录重量。 */
    val targetWeight: Double? = null,
    /** 默认时长（秒）；非 null 表示这是计时类动作，记录页会按秒录入。 */
    val targetSeconds: Int? = null,
) {
    /** 该动作涉及的全部部位，主部位在前、次部位去重在后。 */
    val muscleGroups: List<MuscleGroup>
        get() = (listOf(muscleGroup) + secondaryMuscleGroups).distinct()

    /** 计时类动作（平板支撑等）；由是否设置了默认时长决定。 */
    val isTimed: Boolean get() = targetSeconds != null
}
