package com.fitplan.ui.exercise

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class RoutineTargetsTest {

    @Test
    fun `有氧默认连续练一组并带入时长且不预填重量`() {
        val cardio = exercise.copy(
            muscleGroup = MuscleGroup.CARDIO,
            equipment = Equipment.MACHINE,
            metric = ExerciseMetric.DURATION,
            loadMode = ExerciseLoadMode.BODYWEIGHT,
            defaultDurationSeconds = 1200,
        )

        val targets = cardio.defaultTargets()

        targets.sets shouldBe 1
        targets.restSeconds shouldBe 0
        targets.seconds shouldBe 1200
        targets.weight shouldBe null
    }

    @Test
    fun `力量动作保留三组休息九十秒和动作库的重量次数`() {
        val targets = exercise.copy(defaultReps = 12).defaultTargets()

        targets.sets shouldBe 3
        targets.restSeconds shouldBe 90
        targets.reps shouldBe 12
        targets.weight shouldBe 20.0
        targets.seconds shouldBe null
    }

    @Test
    fun `非有氧计时动作仍按三组并使用动作库时长`() {
        val targets = exercise.copy(
            name = "负重平板支撑",
            muscleGroup = MuscleGroup.ABS,
            metric = ExerciseMetric.DURATION,
            defaultDurationSeconds = 60,
        ).defaultTargets()

        targets.sets shouldBe 3
        targets.restSeconds shouldBe 90
        targets.seconds shouldBe 60
        targets.weight shouldBe 20.0
    }

    private val exercise = Exercise(
        id = 1L,
        name = "杠铃卧推",
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        description = "",
        isCustom = false,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        defaultWeight = 20.0,
    )
}
