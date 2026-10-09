package com.fitplan.ui.workout

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WorkoutExerciseState
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
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

class WorkoutLogCardioTest {

    private val workoutRepository = mockk<WorkoutRepository>()
    private val routineRepository = mockk<RoutineRepository>()
    private val exerciseRepository = mockk<ExerciseRepository>()

    /** 休息计时是 app 级单例，模型构造时就会读它的两个状态流，这里给两个空流顶住。 */
    private val restTimer = mockk<RestTimer> {
        every { rest } returns MutableStateFlow(null)
        every { finishedTick } returns MutableStateFlow(0)
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
    fun `临时加入有氧只预填一组时长且不记录重量和次数`() = runTest {
        coEvery { exerciseRepository.getById(cardio.id) } returns cardio
        val model = createModel()

        model.requestExtraExercise(cardio.id)
        advanceUntilIdle()

        val exercise = model.exercises.value.single()
        exercise.targetSets shouldBe 1
        exercise.restSeconds shouldBe 0
        exercise.targetSeconds shouldBe 1200
        exercise.showsWeight shouldBe false
        exercise.sets shouldBe listOf(SetEntry(seconds = "1200"))
    }

    @Test
    fun `恢复已完成一组的计划外有氧不会补成三组或丢失时长目标`() = runTest {
        stubSession()
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        val exercise = model.exercises.value.single()
        exercise.routineExerciseId shouldBe null
        exercise.targetSets shouldBe 1
        exercise.restSeconds shouldBe 0
        exercise.targetSeconds shouldBe 1200
        exercise.sets shouldBe listOf(SetEntry(id = completedSet.id, seconds = "900", completed = true))
        exercise.isDone shouldBe true
    }

    @Test
    fun `恢复有氧保留用户添加的组并给新增空行预填时长`() = runTest {
        stubSession(
            states = listOf(WorkoutExerciseState(sessionId = session.id, exerciseId = cardio.id, setCount = 2)),
        )
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        val exercise = model.exercises.value.single()
        exercise.targetSets shouldBe 1
        exercise.sets shouldBe listOf(
            SetEntry(id = completedSet.id, seconds = "900", completed = true),
            SetEntry(seconds = "1200"),
        )
    }

    @Test
    fun `恢复计划内有氧保留用户设置的组数休息和时长`() = runTest {
        stubSession(plannedExercise = plannedCardio)
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        val exercise = model.exercises.value.single()
        exercise.isExtra shouldBe false
        exercise.routineExerciseId shouldBe plannedCardio.id
        exercise.targetSets shouldBe 2
        exercise.restSeconds shouldBe 30
        exercise.targetSeconds shouldBe 600
        exercise.sets shouldBe listOf(
            SetEntry(id = completedSet.id, seconds = "900", completed = true),
            SetEntry(seconds = "600"),
        )
    }

    @Test
    fun `计划内动作未填的时长不会被动作库默认值覆盖`() = runTest {
        stubSession(plannedExercise = plannedCardio.copy(targetSeconds = null))
        val model = createModel()

        model.load(sessionId = session.id)
        advanceUntilIdle()

        val exercise = model.exercises.value.single()
        exercise.targetSeconds shouldBe null
        exercise.sets.last().seconds shouldBe ""
    }

    private fun stubSession(
        plannedExercise: RoutineExercise? = null,
        states: List<WorkoutExerciseState> = emptyList(),
    ) {
        coEvery { workoutRepository.getSession(session.id) } returns session.copy(
            routineId = plannedExercise?.routineId,
        )
        coEvery { workoutRepository.getSets(session.id) } returns listOf(completedSet)
        coEvery { workoutRepository.getExerciseStates(session.id) } returns states
        coEvery { exerciseRepository.getByIds(listOf(cardio.id)) } returns listOf(cardio)
        if (plannedExercise != null) {
            coEvery { routineRepository.getItems(plannedExercise.routineId) } returns listOf(
                RoutineItem.Exercise(plannedExercise),
            )
        }
    }

    private fun createModel() = WorkoutLogScreenModel(
        workoutRepository = workoutRepository,
        routineRepository = routineRepository,
        exerciseRepository = exerciseRepository,
        hintRepository = mockk(),
        lowReminderRepository = mockk(),
        updateProgress = mockk(),
        widgetManager = mockk(),
        restTimer = restTimer,
        dataRevision = mockk(),
    )

    private val cardio = Exercise(
        id = 1L,
        name = "跑步机跑步",
        muscleGroup = MuscleGroup.CARDIO,
        equipment = Equipment.MACHINE,
        description = "",
        isCustom = false,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        metric = ExerciseMetric.DURATION,
        loadMode = ExerciseLoadMode.BODYWEIGHT,
        defaultDurationSeconds = 1200,
    )

    private val session = WorkoutSession(
        id = 1L,
        routineId = null,
        name = "有氧训练",
        startedAt = cardio.createdAt,
        finishedAt = null,
    )

    private val completedSet = WorkoutSet(
        id = 1L,
        sessionId = session.id,
        exerciseId = cardio.id,
        setIndex = 1,
        weight = null,
        reps = null,
        durationSeconds = 900,
        completed = true,
    )

    private val plannedCardio = RoutineExercise(
        id = 1L,
        routineId = 1L,
        exerciseId = cardio.id,
        exerciseName = cardio.name,
        muscleGroup = cardio.muscleGroup,
        equipment = cardio.equipment,
        position = 0,
        targetSets = 2,
        targetReps = 10,
        restSeconds = 30,
        metric = cardio.metric,
        loadMode = cardio.loadMode,
        targetSeconds = 600,
    )
}
