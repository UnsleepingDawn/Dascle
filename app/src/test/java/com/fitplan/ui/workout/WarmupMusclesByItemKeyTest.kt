package com.fitplan.ui.workout

import com.fitplan.domain.model.MuscleGroup
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainKey
import org.junit.jupiter.api.Test

class WarmupMusclesByItemKeyTest {

    @Test
    fun `同一部位只在最靠前的那张卡上方提示一次`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L), LogItem.Exercise(3L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.CHEST),
            3L to listOf(MuscleGroup.BACK),
        )

        val result = warmupMusclesByItemKey(items) { muscles.getValue(it) }

        result shouldContainExactly mapOf(
            "e1" to listOf(MuscleGroup.CHEST),
            "e3" to listOf(MuscleGroup.BACK),
        )
    }

    @Test
    fun `同一张卡首次涉及多个部位时并列提示`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            2L to listOf(MuscleGroup.TRICEPS),
        )

        val result = warmupMusclesByItemKey(items) { muscles.getValue(it) }

        result shouldContainExactly mapOf(
            "e1" to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
        )
    }

    @Test
    fun `动作组按全部组内成员算部位，不看挑中了谁`() {
        val group = LogItem.Group(
            groupId = 7L,
            maxPicks = 2,
            memberIds = listOf(1L, 2L),
            pickedIds = listOf(2L),
        )
        val items = listOf(group, LogItem.Exercise(3L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.LEG),
            3L to listOf(MuscleGroup.LEG),
        )

        val result = warmupMusclesByItemKey(items) { muscles.getValue(it) }

        result shouldContainExactly mapOf(
            "g7" to listOf(MuscleGroup.CHEST, MuscleGroup.LEG),
        )
    }

    @Test
    fun `查不到部位的动作不产生提示`() {
        val items = listOf(LogItem.Exercise(1L))

        val result = warmupMusclesByItemKey(items) { emptyList() }

        result shouldNotContainKey "e1"
    }
}
