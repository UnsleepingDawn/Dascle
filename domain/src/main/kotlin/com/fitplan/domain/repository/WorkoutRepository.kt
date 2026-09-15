package com.fitplan.domain.repository

import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.model.WorkoutSetDraft
import kotlin.time.Instant

interface WorkoutRepository {

    suspend fun getSessions(): List<WorkoutSession>

    suspend fun getSession(id: Long): WorkoutSession?

    suspend fun getLatestSession(): WorkoutSession?

    suspend fun getUnfinishedSessions(): List<WorkoutSession>

    /** 已结束的训练，按开始时间倒序，供统计页使用。 */
    suspend fun getFinishedSessions(): List<WorkoutSession>

    /** [start, end) 区间内已结束的训练，供统计页按周/月取数。 */
    suspend fun getFinishedSessionsBetween(start: Instant, end: Instant): List<WorkoutSession>

    /** [start, end) 区间内已开始（无论是否结束）的训练，供漏练检查判断某天用过 App 没有。 */
    suspend fun getSessionsBetween(start: Instant, end: Instant): List<WorkoutSession>

    /** [start, end) 区间内已结束训练的全部已完成组，供统计页聚合。 */
    suspend fun getCompletedSetsBetween(start: Instant, end: Instant): List<WorkoutSet>

    /** [start, end) 区间内已结束的训练，按开始时间倒序，每项带上完成组数，供统计页历史列表使用。 */
    suspend fun getFinishedSessionsWithSummary(start: Instant, end: Instant): List<WorkoutHistoryItem>

    /** 已结束的训练次数，供统计页使用。 */
    suspend fun countFinishedSessions(): Long

    suspend fun startSession(routineId: Long?, name: String, startedAt: Instant): Long

    /**
     * 一次性写入一次已经结束的训练：session 与它的全部组在同一个事务里落库，
     * 中途出错不会留下只有一半记录的补记训练。
     */
    suspend fun insertFinishedSession(
        routineId: Long?,
        name: String,
        startedAt: Instant,
        finishedAt: Instant,
        sets: List<WorkoutSetDraft>,
    ): Long

    suspend fun finishSession(id: Long, finishedAt: Instant)

    /**
     * 把一次已结束的训练重新置为进行中（`finished_at` 置空），供「开始训练（计划外）」继续往里加动作；
     * 这次训练原有的记录都留着，之后点「结束训练」会写回新的结束时间。
     */
    suspend fun reopenSession(id: Long)

    suspend fun updateSessionName(id: Long, name: String)

    suspend fun updateSessionNote(id: Long, note: String)

    suspend fun deleteSession(id: Long)

    suspend fun getSets(sessionId: Long): List<WorkoutSet>

    suspend fun getSetsOfExercise(sessionId: Long, exerciseId: Long): List<WorkoutSet>

    suspend fun addSet(
        sessionId: Long,
        exerciseId: Long,
        setIndex: Int,
        weight: Double?,
        reps: Int?,
        durationSeconds: Int?,
    ): Long

    suspend fun updateSet(set: WorkoutSet)

    suspend fun deleteSet(id: Long)
}
