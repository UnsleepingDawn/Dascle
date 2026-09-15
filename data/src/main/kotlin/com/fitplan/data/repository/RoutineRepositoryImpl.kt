package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.data.mapper.toMuscleGroup
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.repository.RoutineRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RoutineRepositoryImpl(
    private val database: Database,
) : RoutineRepository {

    private val routineQueries get() = database.routineQueries
    private val routineExerciseQueries get() = database.routineExerciseQueries
    private val utilQueries get() = database.utilQueries

    override suspend fun getAll(): List<Routine> = routineQueries.selectAll().awaitAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): Routine? =
        routineQueries.selectById(id).awaitAsOneOrNull()?.toDomain()

    override suspend fun count(): Long = routineQueries.countAll().awaitAsOneOrNull() ?: 0L

    override suspend fun insert(name: String, note: String, createdAt: Instant): Long =
        database.transactionWithResult {
            routineQueries.insert(
                name = name,
                note = note,
                created_at = createdAt.toDbValue(),
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun update(id: Long, name: String, note: String) {
        routineQueries.update(name = name, note = note, id = id)
    }

    override suspend fun deleteById(id: Long) {
        // routine_exercise / schedule_entry 由外键 ON DELETE CASCADE 清理，
        // workout_session.routine_id 由 ON DELETE SET NULL 置空。
        routineQueries.deleteById(id)
    }

    override suspend fun getExercises(routineId: Long): List<RoutineExercise> {
        val exercises = routineExerciseQueries.selectByRoutineId(routineId).awaitAsList().map { it.toDomain() }
        if (exercises.isEmpty()) return emptyList()
        val secondaryByExerciseId = database.exerciseSecondaryMuscleQueries
            .selectByExerciseIds(exercises.map { it.exerciseId }.distinct())
            .awaitAsList()
            .groupBy({ it.exercise_id }, { it.toMuscleGroup() })
        return exercises.map { it.copy(secondaryMuscleGroups = secondaryByExerciseId[it.exerciseId].orEmpty()) }
    }

    override suspend fun addExercise(
        routineId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ): Long = database.transactionWithResult {
        val position = routineExerciseQueries.selectMaxPosition(routineId).awaitAsOne() + 1
        routineExerciseQueries.insert(
            routine_id = routineId,
            exercise_id = exerciseId,
            position = position,
            target_sets = targetSets.toLong(),
            target_reps = targetReps.toLong(),
            rest_seconds = restSeconds.toLong(),
            target_weight = targetWeight,
            target_seconds = targetSeconds?.toLong(),
        )
        utilQueries.lastInsertRowId().awaitAsOne()
    }

    override suspend fun updateExerciseTargets(
        id: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ) {
        routineExerciseQueries.updateTargets(
            target_sets = targetSets.toLong(),
            target_reps = targetReps.toLong(),
            rest_seconds = restSeconds.toLong(),
            target_weight = targetWeight,
            target_seconds = targetSeconds?.toLong(),
            id = id,
        )
    }

    override suspend fun reorderExercises(routineId: Long, orderedRoutineExerciseIds: List<Long>) {
        database.transaction {
            orderedRoutineExerciseIds.forEachIndexed { index, id ->
                routineExerciseQueries.updatePosition(position = index.toLong(), id = id)
            }
        }
    }

    override suspend fun updateTargetWeightForExercise(exerciseId: Long, weight: Double) {
        routineExerciseQueries.updateTargetWeightByExerciseId(target_weight = weight, exercise_id = exerciseId)
    }

    override suspend fun removeExercise(id: Long) {
        routineExerciseQueries.deleteById(id)
    }
}
