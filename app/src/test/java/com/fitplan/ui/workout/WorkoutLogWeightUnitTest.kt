package com.fitplan.ui.workout

import com.fitplan.core.common.preference.InMemoryPreferenceStore
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WeightUnit
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.repository.ExerciseLowReminderRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.presentation.unit.WeightUnitPreferences
import com.fitplan.reminder.RestTimer
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** 动作级重量单位切换：已完成的组只换算显示，手改过的行保留数字，预填行按新单位重填。 */
class WorkoutLogWeightUnitTest {

    private val workoutRepository = mockk<WorkoutRepository>()
    private val routineRepository = mockk<RoutineRepository>()
    private val exerciseRepository = mockk<ExerciseRepository>()
    private val lowReminderRepository = mockk<ExerciseLowReminderRepository>()

    private val restTimer = mockk<RestTimer> {
        every { rest } returns MutableStateFlow(null)
        every { finishedTick } returns MutableStateFlow(0)
        every { start(any(), any()) } returns Unit
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `切换到磅后已完成组换算显示、预填行按新单位重填`() = runTest {
        stubSession()
        coEvery { workoutRepository.addSet(any(), any(), any(), any(), any(), any()) } returns 100L
        coEvery { workoutRepository.updateSet(any()) } returns Unit
        coEvery { exerciseRepository.updateWeightUnit(any(), any()) } returns Unit
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        model.updateWeight(bench.id, index = 0, value = "50")
        model.toggleCompleted(bench.id, index = 0)
        advanceUntilIdle()

        model.updateWeightUnit(bench.id, WeightUnit.LB)
        advanceUntilIdle()

        val sets = model.exercises.value.single().sets
        // 50 kg → 110.2 lb；30 kg → 66.1 lb。已完成的组仍然保留 50 kg 的记录，只是换了显示。
        sets shouldBe listOf(
            SetEntry(id = 100L, weight = "110.2", reps = "10", completed = true, weightEdited = true),
            SetEntry(weight = "66.1", reps = "10"),
            SetEntry(weight = "66.1", reps = "10"),
        )
        coVerify { exerciseRepository.updateWeightUnit(bench.id, WeightUnit.LB) }
    }

    @Test
    fun `未完成且手改过的行切换单位时保留输入框里的数字`() = runTest {
        stubSession()
        coEvery { exerciseRepository.updateWeightUnit(any(), any()) } returns Unit
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        model.updateWeight(bench.id, index = 1, value = "100")
        model.updateWeightUnit(bench.id, WeightUnit.LB)
        advanceUntilIdle()

        val sets = model.exercises.value.single().sets
        sets shouldBe listOf(
            SetEntry(weight = "66.1", reps = "10"),
            SetEntry(weight = "100", reps = "10", weightEdited = true),
            SetEntry(weight = "66.1", reps = "10"),
        )
    }

    @Test
    fun `切回跟随全局时按千克重新显示`() = runTest {
        stubSession()
        coEvery { workoutRepository.addSet(any(), any(), any(), any(), any(), any()) } returns 100L
        coEvery { workoutRepository.updateSet(any()) } returns Unit
        coEvery { exerciseRepository.updateWeightUnit(any(), any()) } returns Unit
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        model.updateWeight(bench.id, index = 0, value = "50")
        model.toggleCompleted(bench.id, index = 0)
        advanceUntilIdle()

        model.updateWeightUnit(bench.id, WeightUnit.LB)
        model.updateWeightUnit(bench.id, null)
        advanceUntilIdle()

        val sets = model.exercises.value.single().sets
        sets shouldBe listOf(
            SetEntry(id = 100L, weight = "50", reps = "10", completed = true, weightEdited = true),
            SetEntry(weight = "30", reps = "10"),
            SetEntry(weight = "30", reps = "10"),
        )
        coVerify { exerciseRepository.updateWeightUnit(bench.id, null) }
    }

    /** 计划里排了卧推 3 组 × 10 次，目标重量 30kg；这一次训练还没记录任何组。 */
    private fun stubSession() {
        coEvery { workoutRepository.getSession(session.id) } returns session
        coEvery { workoutRepository.getSets(session.id) } returns emptyList()
        coEvery { workoutRepository.getExerciseStates(session.id) } returns emptyList()
        coEvery { workoutRepository.getDismissedWarmupMuscles(session.id) } returns emptySet()
        coEvery { exerciseRepository.getByIds(listOf(bench.id)) } returns listOf(bench)
        coEvery { routineRepository.getItems(routineId) } returns listOf(RoutineItem.Exercise(plannedBench))
        coEvery { lowReminderRepository.isShown(any(), any()) } returns true
    }

    private fun createModel() = WorkoutLogScreenModel(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
        exerciseRepository = exerciseRepository,
        hintRepository = mockk(relaxed = true),
        lowReminderRepository = lowReminderRepository,
        updateProgress = mockk(relaxed = true),
        widgetManager = mockk(relaxed = true),
        restTimer = restTimer,
        dataRevision = mockk(relaxed = true),
        weightUnitPreferences = WeightUnitPreferences(InMemoryPreferenceStore()),
    )

    private val routineId = 1L
    private val bench = Exercise(
        id = 2L,
        name = "杠铃卧推",
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        description = "",
        isCustom = false,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        metric = ExerciseMetric.DEFAULT,
        loadMode = ExerciseLoadMode.EXTERNAL,
        defaultReps = 10,
    )

    private val plannedBench = RoutineExercise(
        id = 1L,
        routineId = routineId,
        exerciseId = bench.id,
        exerciseName = bench.name,
        muscleGroup = bench.muscleGroup,
        equipment = bench.equipment,
        position = 0,
        targetSets = 3,
        targetReps = 10,
        restSeconds = 60,
        metric = bench.metric,
        loadMode = bench.loadMode,
        targetWeight = 30.0,
    )

    private val session = WorkoutSession(
        id = 1L,
        routineId = routineId,
        name = "胸",
        startedAt = bench.createdAt,
        finishedAt = null,
    )
}
