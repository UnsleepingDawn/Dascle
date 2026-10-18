package com.fitplan.ui.workout

import com.fitplan.core.common.preference.InMemoryPreferenceStore
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.repository.ExerciseLowReminderRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.presentation.unit.WeightUnitPreferences
import com.fitplan.reminder.RestTimer
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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

/** 「做完了」之后把这一组的重量带给后面还没做、也没手改过的组。 */
class WorkoutLogWeightCarryTest {

    private val workoutRepository = mockk<WorkoutRepository>()
    private val routineRepository = mockk<RoutineRepository>()
    private val exerciseRepository = mockk<ExerciseRepository>()
    private val lowReminderRepository = mockk<ExerciseLowReminderRepository>()

    /** 休息计时是 app 级单例，模型构造时就会读它的两个状态流，这里给两个空流顶住。 */
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
    fun `做完一组后把该组重量带给后面还没做的组`() = runTest {
        stubSession()
        coEvery { workoutRepository.addSet(any(), any(), any(), any(), any(), any()) } returns 100L
        coEvery { workoutRepository.updateSet(any()) } returns Unit
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        model.updateWeight(bench.id, index = 0, value = "50")
        model.toggleCompleted(bench.id, index = 0)
        advanceUntilIdle()

        val sets = model.exercises.value.single().sets
        sets shouldBe listOf(
            SetEntry(id = 100L, weight = "50", reps = "10", completed = true, weightEdited = true),
            SetEntry(weight = "50", reps = "10"),
            SetEntry(weight = "50", reps = "10"),
        )
    }

    @Test
    fun `后面已经手改过的组不会被带过去的重量覆盖`() = runTest {
        stubSession()
        coEvery { workoutRepository.addSet(any(), any(), any(), any(), any(), any()) } returns 100L
        coEvery { workoutRepository.updateSet(any()) } returns Unit
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        // 第三组已经自己填过重量，做完第一组时不能把它改成第一组的重量。
        model.updateWeight(bench.id, index = 2, value = "70")
        model.updateWeight(bench.id, index = 0, value = "50")
        model.toggleCompleted(bench.id, index = 0)
        advanceUntilIdle()

        val sets = model.exercises.value.single().sets
        sets shouldBe listOf(
            SetEntry(id = 100L, weight = "50", reps = "10", completed = true, weightEdited = true),
            SetEntry(weight = "50", reps = "10"),
            SetEntry(weight = "70", reps = "10", weightEdited = true),
        )
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
