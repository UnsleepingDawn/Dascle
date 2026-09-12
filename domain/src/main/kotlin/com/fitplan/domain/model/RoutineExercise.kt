package com.fitplan.domain.model

data class RoutineExercise(
    val id: Long,
    val routineId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
)
