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
) {
    /** 该动作涉及的全部部位，主部位在前、次部位去重在后。 */
    val muscleGroups: List<MuscleGroup>
        get() = (listOf(muscleGroup) + secondaryMuscleGroups).distinct()
}
