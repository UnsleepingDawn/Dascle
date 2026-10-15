package com.fitplan.ui.plan.routine

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineGroup
import com.fitplan.domain.model.RoutineItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 编排页返回前的「胸 + 背」判定：返回 true 时才弹不合理提醒。 */
class RoutineImbalanceTest {

    @Test
    fun `只排胸不会提醒`() {
        listOf(RoutineItem.Exercise(exercise(id = 1, muscle = MuscleGroup.CHEST)))
            .hasChestAndBack() shouldBe false
    }

    @Test
    fun `只排背不会提醒`() {
        listOf(RoutineItem.Exercise(exercise(id = 1, muscle = MuscleGroup.BACK)))
            .hasChestAndBack() shouldBe false
    }

    @Test
    fun `同时排了胸和背就会提醒`() {
        listOf(
            RoutineItem.Exercise(exercise(id = 1, muscle = MuscleGroup.CHEST)),
            RoutineItem.Exercise(exercise(id = 2, muscle = MuscleGroup.BACK)),
        ).hasChestAndBack() shouldBe true
    }

    @Test
    fun `次部位是背也算同时在练背`() {
        listOf(
            RoutineItem.Exercise(exercise(id = 1, muscle = MuscleGroup.CHEST)),
            RoutineItem.Exercise(exercise(id = 2, muscle = MuscleGroup.SHOULDER, secondary = listOf(MuscleGroup.BACK))),
        ).hasChestAndBack() shouldBe true
    }

    @Test
    fun `动作组里同时有胸和背也会提醒`() {
        val group = RoutineGroup(
            id = 1L,
            routineId = 1L,
            name = null,
            position = 0,
            maxPicks = 2,
            exercises = listOf(
                exercise(id = 1, muscle = MuscleGroup.CHEST, groupId = 1L),
                exercise(id = 2, muscle = MuscleGroup.BACK, groupId = 1L),
            ),
        )
        listOf(RoutineItem.Group(group)).hasChestAndBack() shouldBe true
    }

    @Test
    fun `没有动作不会提醒`() {
        emptyList<RoutineItem>().hasChestAndBack() shouldBe false
    }

    private fun exercise(
        id: Long,
        muscle: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        groupId: Long? = null,
    ) = RoutineExercise(
        id = id,
        routineId = 1L,
        exerciseId = id,
        exerciseName = "动作 $id",
        muscleGroup = muscle,
        secondaryMuscleGroups = secondary,
        equipment = Equipment.BARBELL,
        position = id.toInt(),
        targetSets = 3,
        targetReps = 10,
        restSeconds = 60,
        groupId = groupId,
    )
}
