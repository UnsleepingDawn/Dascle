package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WorkoutRepositoryImpl(
    private val database: Database,
) : WorkoutRepository {

    private val sessionQueries get() = database.workoutSessionQueries
    private val setQueries get() = database.workoutSetQueries
    private val utilQueries get() = database.utilQueries

    override suspend fun getSessions(): List<WorkoutSession> =
        sessionQueries.selectAll().awaitAsList().map { it.toDomain() }

    override suspend fun getSession(id: Long): WorkoutSession? =
        sessionQueries.selectById(id).awaitAsOneOrNull()?.toDomain()

    override suspend fun getLatestSession(): WorkoutSession? =
        sessionQueries.selectLatest().awaitAsOneOrNull()?.toDomain()

    override suspend fun getUnfinishedSessions(): List<WorkoutSession> =
        sessionQueries.selectUnfinished().awaitAsList().map { it.toDomain() }

    override suspend fun getFinishedSessions(): List<WorkoutSession> =
        sessionQueries.selectFinished().awaitAsList().map { it.toDomain() }

    override suspend fun getFinishedSessionsBetween(start: Instant, end: Instant): List<WorkoutSession> =
        sessionQueries.selectFinishedBetween(
            started_at = start.toDbValue(),
            started_at_ = end.toDbValue(),
        ).awaitAsList().map { it.toDomain() }

    override suspend fun getSessionsBetween(start: Instant, end: Instant): List<WorkoutSession> =
        sessionQueries.selectStartedBetween(
            started_at = start.toDbValue(),
            started_at_ = end.toDbValue(),
        ).awaitAsList().map { it.toDomain() }

    override suspend fun getCompletedSetsBetween(start: Instant, end: Instant): List<WorkoutSet> =
        setQueries.selectCompletedBetween(
            started_at = start.toDbValue(),
            started_at_ = end.toDbValue(),
        ).awaitAsList().map { it.toDomain() }

    override suspend fun getFinishedSessionsWithSummary(): List<WorkoutHistoryItem> =
        sessionQueries.selectFinishedWithSummary().awaitAsList().map { it.toDomain() }

    override suspend fun countFinishedSessions(): Long =
        sessionQueries.countFinished().awaitAsOneOrNull() ?: 0L

    override suspend fun startSession(routineId: Long?, name: String, startedAt: Instant): Long =
        database.transactionWithResult {
            sessionQueries.insert(
                routine_id = routineId,
                name = name,
                started_at = startedAt.toDbValue(),
                finished_at = null,
                note = "",
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun finishSession(id: Long, finishedAt: Instant) {
        sessionQueries.finish(finished_at = finishedAt.toDbValue(), id = id)
    }

    override suspend fun updateSessionName(id: Long, name: String) {
        sessionQueries.updateName(name = name, id = id)
    }

    override suspend fun updateSessionNote(id: Long, note: String) {
        sessionQueries.updateNote(note = note, id = id)
    }

    override suspend fun deleteSession(id: Long) {
        // workout_set 由外键 ON DELETE CASCADE 清理。
        sessionQueries.deleteById(id)
    }

    override suspend fun getSets(sessionId: Long): List<WorkoutSet> =
        setQueries.selectBySessionId(sessionId).awaitAsList().map { it.toDomain() }

    override suspend fun getSetsOfExercise(sessionId: Long, exerciseId: Long): List<WorkoutSet> =
        setQueries.selectBySessionAndExercise(sessionId, exerciseId).awaitAsList().map { it.toDomain() }

    override suspend fun addSet(
        sessionId: Long,
        exerciseId: Long,
        setIndex: Int,
        weight: Double?,
        reps: Int?,
        durationSeconds: Int?,
    ): Long = database.transactionWithResult {
        setQueries.insert(
            session_id = sessionId,
            exercise_id = exerciseId,
            set_index = setIndex.toLong(),
            weight = weight,
            reps = reps?.toLong(),
            duration_seconds = durationSeconds?.toLong(),
            completed = 0L,
        )
        utilQueries.lastInsertRowId().awaitAsOne()
    }

    override suspend fun updateSet(set: WorkoutSet) {
        setQueries.update(
            weight = set.weight,
            reps = set.reps?.toLong(),
            duration_seconds = set.durationSeconds?.toLong(),
            completed = set.completed.toDbValue(),
            id = set.id,
        )
    }

    override suspend fun deleteSet(id: Long) {
        setQueries.deleteById(id)
    }
}
