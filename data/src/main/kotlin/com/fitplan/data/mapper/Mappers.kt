package com.fitplan.data.mapper

import com.fitplan.data.SelectFinishedWithSummary
import com.fitplan.domain.model.BodyMetric
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.ScheduleEntry
import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import com.fitplan.data.Body_metric as BodyMetricRow
import com.fitplan.data.Exercise as ExerciseRow
import com.fitplan.data.Exercise_secondary_muscle as ExerciseSecondaryMuscleRow
import com.fitplan.data.Routine as RoutineRow
import com.fitplan.data.Schedule_entry as ScheduleEntryRow
import com.fitplan.data.SelectAll as ScheduleEntryWithRoutineRow
import com.fitplan.data.SelectByRoutineId as RoutineExerciseRow
import com.fitplan.data.SelectEnabledBySpecificDate as EnabledByDateRow
import com.fitplan.data.SelectFinished as FinishedSessionRow
import com.fitplan.data.SelectFinishedBetween as FinishedSessionBetweenRow
import com.fitplan.data.Workout_session as WorkoutSessionRow
import com.fitplan.data.Workout_set as WorkoutSetRow

// ---------- 基础类型 <-> 数据库列 ----------

internal fun Boolean.toDbValue(): Long = if (this) 1L else 0L

internal fun Long.toBoolean(): Boolean = this != 0L

internal fun Instant.toDbValue(): Long = toEpochMilliseconds()

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun LocalDate.toDbValue(): Long = toEpochDays()

internal fun Long.toLocalDate(): LocalDate = LocalDate.fromEpochDays(this)

internal fun MuscleGroup.toDbValue(): String = name

internal fun Equipment.toDbValue(): String = name

internal fun String.toMuscleGroup(): MuscleGroup =
    requireNotNull(MuscleGroup.fromValue(this)) { "未知的肌群取值: $this" }

private fun String.toEquipment(): Equipment =
    requireNotNull(Equipment.fromValue(this)) { "未知的器械取值: $this" }

// ---------- 数据库行 -> 领域模型 ----------

internal fun ExerciseRow.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    muscleGroup = muscle_group.toMuscleGroup(),
    equipment = equipment.toEquipment(),
    description = description,
    isCustom = is_custom.toBoolean(),
    createdAt = created_at.toInstant(),
    defaultWeight = default_weight,
    defaultDurationSeconds = default_duration_seconds?.toInt(),
)

/** `exercise_secondary_muscle` 的一行转成次部位；取值非法时抛异常，与主部位一致。 */
internal fun ExerciseSecondaryMuscleRow.toMuscleGroup(): MuscleGroup = muscle_group.toMuscleGroup()

internal fun RoutineRow.toDomain(): Routine = Routine(
    id = id,
    name = name,
    note = note,
    createdAt = created_at.toInstant(),
)

internal fun RoutineExerciseRow.toDomain(): RoutineExercise = RoutineExercise(
    id = id,
    routineId = routine_id,
    exerciseId = exercise_id,
    exerciseName = exercise_name,
    muscleGroup = muscle_group.toMuscleGroup(),
    equipment = equipment.toEquipment(),
    position = position.toInt(),
    targetSets = target_sets.toInt(),
    targetReps = target_reps.toInt(),
    restSeconds = rest_seconds.toInt(),
    targetWeight = target_weight,
    targetSeconds = target_seconds?.toInt(),
)

private fun scheduleEntry(
    id: Long,
    routineId: Long,
    routineName: String,
    specificDate: Long?,
    enabled: Long,
): ScheduleEntry = ScheduleEntry(
    id = id,
    routineId = routineId,
    routineName = routineName,
    specificDate = specificDate?.toLocalDate(),
    enabled = enabled.toBoolean(),
)

internal fun ScheduleEntryWithRoutineRow.toDomain(): ScheduleEntry =
    scheduleEntry(id, routine_id, routine_name, specific_date, enabled)

internal fun EnabledByDateRow.toDomain(): ScheduleEntry =
    scheduleEntry(id, routine_id, routine_name, specific_date, enabled)

internal fun ScheduleEntryRow.toDomain(): ScheduleEntry =
    scheduleEntry(id, routine_id, "", specific_date, enabled)

private fun workoutSession(
    id: Long,
    routineId: Long?,
    name: String,
    startedAt: Long,
    finishedAt: Long?,
    note: String,
): WorkoutSession = WorkoutSession(
    id = id,
    routineId = routineId,
    name = name,
    startedAt = startedAt.toInstant(),
    finishedAt = finishedAt?.toInstant(),
    note = note,
)

internal fun WorkoutSessionRow.toDomain(): WorkoutSession =
    workoutSession(id, routine_id, name, started_at, finished_at, note)

internal fun FinishedSessionRow.toDomain(): WorkoutSession =
    workoutSession(id, routine_id, name, started_at, finished_at, note)

internal fun FinishedSessionBetweenRow.toDomain(): WorkoutSession =
    workoutSession(id, routine_id, name, started_at, finished_at, note)

internal fun SelectFinishedWithSummary.toDomain(): WorkoutHistoryItem = WorkoutHistoryItem(
    sessionId = id,
    name = name,
    startedAt = started_at.toInstant(),
    finishedAt = finished_at.toInstant(),
    completedSets = completed_sets.toInt(),
)

internal fun WorkoutSetRow.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    sessionId = session_id,
    exerciseId = exercise_id,
    setIndex = set_index.toInt(),
    weight = weight,
    reps = reps?.toInt(),
    durationSeconds = duration_seconds?.toInt(),
    completed = completed.toBoolean(),
)

internal fun BodyMetricRow.toDomain(): BodyMetric = BodyMetric(
    id = id,
    date = date.toLocalDate(),
    weight = weight,
    bodyFat = body_fat,
)
