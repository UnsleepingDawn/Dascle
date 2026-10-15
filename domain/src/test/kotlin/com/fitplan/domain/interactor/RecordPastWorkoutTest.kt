package com.fitplan.domain.interactor

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.WorkoutSetDraft
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * 盯住过去日期补记训练的判据。
 *
 * 训练日历给过去的日子加计划会走这里：判据一旦比日历显示的更严，就会出现
 * 「点了添加计划却还是休息」——库里有未结束且一组都没勾的空壳训练时正是这种情形。
 */
class RecordPastWorkoutTest {

    private val routineRepository = mockk<RoutineRepository>()
    private val workoutRepository = mockk<WorkoutRepository>()
    private val pastDay = LocalDate(2026, 10, 10)

    @Test
    fun `这一天只剩一条没勾过组的空壳训练时，补记照常写进去`() = runTest {
        stubPastDay(finished = emptyList(), started = listOf(orphanSession))
        coEvery { workoutRepository.getSets(orphanSession.id) } returns emptyList()
        coEvery { workoutRepository.deleteSession(orphanSession.id) } returns Unit

        RecordPastWorkout(routineRepository, workoutRepository).invoke(routineId = 1L, date = pastDay)

        coVerify { workoutRepository.deleteSession(orphanSession.id) }
        val drafts = slot<List<WorkoutSetDraft>>()
        coVerify { workoutRepository.insertFinishedSession(1L, "验证-补记", any(), any(), capture(drafts)) }
        drafts.captured.size shouldBe 3
    }

    @Test
    fun `这一天已经有已结束的训练时不再重复补记`() = runTest {
        stubPastDay(finished = listOf(orphanSession.copy(finishedAt = dayStart())), started = listOf(orphanSession))

        RecordPastWorkout(routineRepository, workoutRepository).invoke(routineId = 1L, date = pastDay)

        coVerify(exactly = 0) { workoutRepository.insertFinishedSession(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { workoutRepository.deleteSession(any()) }
    }

    private fun stubPastDay(finished: List<WorkoutSession>, started: List<WorkoutSession>) {
        coEvery { workoutRepository.getFinishedSessionsBetween(any(), any()) } returns finished
        coEvery { workoutRepository.getSessionsBetween(any(), any()) } returns started
        coEvery { routineRepository.getById(routineId) } returns
            Routine(id = routineId, name = "验证-补记", createdAt = dayStart())
        coEvery { routineRepository.getItems(routineId) } returns listOf(RoutineItem.Exercise(bench))
        coEvery {
            workoutRepository.insertFinishedSession(any(), any(), any(), any(), any())
        } returns 1L
    }

    private fun dayStart(): Instant = Instant.parse("2026-10-09T16:00:00Z")

    private val routineId = 1L

    /** 重复点「开始训练」留下的空壳：未结束，一组都没记。 */
    private val orphanSession = WorkoutSession(
        id = 10L,
        routineId = null,
        name = "临时方案",
        startedAt = dayStart(),
        finishedAt = null,
    )

    private val bench = RoutineExercise(
        id = 1L,
        routineId = routineId,
        exerciseId = 1L,
        exerciseName = "平板杠铃卧推",
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        position = 0,
        targetSets = 3,
        targetReps = 10,
        restSeconds = 60,
        metric = ExerciseMetric.DEFAULT,
        loadMode = ExerciseLoadMode.EXTERNAL,
        targetWeight = 30.0,
    )
}
