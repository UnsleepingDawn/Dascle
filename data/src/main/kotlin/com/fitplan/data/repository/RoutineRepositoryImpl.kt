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
import com.fitplan.domain.model.RoutineGroup
import com.fitplan.domain.model.RoutineItem
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
    private val routineGroupQueries get() = database.routineGroupQueries
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
        // routine_group / routine_exercise / schedule_entry 由外键 ON DELETE CASCADE 清理，
        // workout_session.routine_id 由 ON DELETE SET NULL 置空。
        routineQueries.deleteById(id)
    }

    override suspend fun getExercises(routineId: Long): List<RoutineExercise> =
        loadItems(routineId).flatMap { it.exercises }

    override suspend fun getItems(routineId: Long): List<RoutineItem> = loadItems(routineId)

    override suspend fun addExercise(
        routineId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ): Long = database.transactionWithResult {
        val position = nextTopLevelPosition(routineId)
        routineExerciseQueries.insert(
            routine_id = routineId,
            exercise_id = exerciseId,
            position = position,
            group_id = null,
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

    override suspend fun reorderItems(routineId: Long, orderedItems: List<RoutineItem>) {
        database.transaction {
            orderedItems.forEachIndexed { index, item ->
                when (item) {
                    is RoutineItem.Exercise ->
                        routineExerciseQueries.updatePosition(position = index.toLong(), id = item.value.id)

                    is RoutineItem.Group ->
                        routineGroupQueries.updatePosition(position = index.toLong(), id = item.value.id)
                }
            }
        }
    }

    override suspend fun addGroup(routineId: Long, maxPicks: Int, name: String?): Long =
        database.transactionWithResult {
            routineGroupQueries.insert(
                routine_id = routineId,
                position = nextTopLevelPosition(routineId),
                max_picks = maxPicks.coerceAtLeast(MIN_MAX_PICKS).toLong(),
                name = name?.takeIf { it.isNotBlank() },
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun updateGroupMaxPicks(groupId: Long, maxPicks: Int) {
        routineGroupQueries.updateMaxPicks(max_picks = clampMaxPicks(groupId, maxPicks).toLong(), id = groupId)
    }

    override suspend fun updateGroupName(groupId: Long, name: String?) {
        routineGroupQueries.updateName(name = name?.takeIf { it.isNotBlank() }, id = groupId)
    }

    override suspend fun removeGroup(groupId: Long) {
        // 组内动作由 routine_exercise.group_id 的 ON DELETE CASCADE 一起删掉。
        routineGroupQueries.deleteById(groupId)
    }

    override suspend fun addExerciseToGroup(
        groupId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ) {
        database.transaction {
            val group = routineGroupQueries.selectById(groupId).awaitAsOneOrNull() ?: return@transaction
            routineExerciseQueries.insert(
                routine_id = group.routine_id,
                exercise_id = exerciseId,
                position = routineExerciseQueries.selectMaxPositionInGroup(groupId).awaitAsOne() + 1,
                group_id = groupId,
                target_sets = targetSets.toLong(),
                target_reps = targetReps.toLong(),
                rest_seconds = restSeconds.toLong(),
                target_weight = targetWeight,
                target_seconds = targetSeconds?.toLong(),
            )
        }
    }

    override suspend fun updateTargetWeightForExercise(exerciseId: Long, weight: Double) {
        routineExerciseQueries.updateTargetWeightByExerciseId(target_weight = weight, exercise_id = exerciseId)
    }

    override suspend fun updateTargetRepsForExercise(exerciseId: Long, reps: Int) {
        routineExerciseQueries.updateTargetRepsByExerciseId(target_reps = reps.toLong(), exercise_id = exerciseId)
    }

    override suspend fun updateTargetSecondsForExercise(exerciseId: Long, seconds: Int) {
        routineExerciseQueries.updateTargetSecondsByExerciseId(
            target_seconds = seconds.toLong(),
            exercise_id = exerciseId,
        )
    }

    override suspend fun removeExercise(id: Long) {
        database.transaction {
            // 删掉的若是组内最后一个动作，组的「做其中 x 个」要跟着收敛，免得出现「做其中 3 个」只剩 1 个动作。
            val groupId = routineExerciseQueries.selectGroupIdById(id).awaitAsOneOrNull()?.group_id
            routineExerciseQueries.deleteById(id)
            groupId?.let { routineGroupQueries.updateMaxPicks(max_picks = clampMaxPicks(it, null).toLong(), id = it) }
        }
    }

    /** 一次读完计划里的动作与动作组，拼成顶层编排（组内已含动作，并且动作带了次部位）。 */
    private suspend fun loadItems(routineId: Long): List<RoutineItem> {
        val exercises = routineExerciseQueries.selectByRoutineId(routineId).awaitAsList().map { it.toDomain() }
        // 次部位是补充信息，计划里一个动作都没有时就不查了（空 IN 列表没有意义）。
        val withSecondary = if (exercises.isEmpty()) {
            emptyList()
        } else {
            val secondaryByExerciseId = database.exerciseSecondaryMuscleQueries
                .selectByExerciseIds(exercises.map { it.exerciseId }.distinct())
                .awaitAsList()
                .groupBy({ it.exercise_id }, { it.toMuscleGroup() })
            exercises.map { it.copy(secondaryMuscleGroups = secondaryByExerciseId[it.exerciseId].orEmpty()) }
        }

        // 空动作组也要显示出来（刚新建、还没往里加动作），所以不能因为动作列表为空就提前返回。
        val membersByGroupId = withSecondary.filter { it.groupId != null }.groupBy { it.groupId }
        val groups = routineGroupQueries.selectByRoutineId(routineId).awaitAsList().map { row ->
            val group = row.toDomain()
            group.copy(exercises = membersByGroupId[group.id].orEmpty().sortedBy { it.position })
        }
        val items = buildList {
            withSecondary.filter { it.groupId == null }.forEach { add(RoutineItem.Exercise(it)) }
            groups.forEach { add(RoutineItem.Group(it)) }
        }
        return items.sortedWith(TOP_LEVEL_ORDER)
    }

    /** 顶层的下一个序号：单独动作与动作组共用一套序号，取两边最大值再加一。 */
    private suspend fun nextTopLevelPosition(routineId: Long): Long {
        val ungrouped = routineExerciseQueries.selectMaxPosition(routineId).awaitAsOne()
        val groups = routineGroupQueries.selectMaxPosition(routineId).awaitAsOne()
        return maxOf(ungrouped, groups) + 1
    }

    /** 「做其中 x 个」的取值范围是 1..组内动作数；[maxPicks] 为 null 表示收敛当前值。 */
    private suspend fun clampMaxPicks(groupId: Long, maxPicks: Int?): Int {
        val members = routineExerciseQueries.countByGroupId(groupId).awaitAsOne().toInt()
        val upper = members.coerceAtLeast(MIN_MAX_PICKS)
        return (maxPicks ?: upper).coerceIn(MIN_MAX_PICKS, upper)
    }

    private companion object {
        const val MIN_MAX_PICKS = 1
    }
}

/** 顶层顺序：先比共用的 `position`，同号时动作组排在单独动作前面。 */
private val TOP_LEVEL_ORDER = compareBy<RoutineItem>(
    { it.position },
    { if (it is RoutineItem.Group) 0 else 1 },
)
