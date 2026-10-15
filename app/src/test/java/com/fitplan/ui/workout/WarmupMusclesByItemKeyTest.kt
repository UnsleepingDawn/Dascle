package com.fitplan.ui.workout

import com.fitplan.domain.model.MuscleGroup
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
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

    @Test
    fun `划走的部位整条摘掉，不在更靠后的卡片上再出现`() {
        val warmup = mapOf(
            "e1" to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            "e2" to listOf(MuscleGroup.BACK),
        )

        val result = warmup.withoutDismissedWarmup(setOf(MuscleGroup.TRICEPS))

        result shouldContainExactly mapOf(
            "e1" to listOf(MuscleGroup.CHEST),
            "e2" to listOf(MuscleGroup.BACK),
        )
    }

    @Test
    fun `一张卡上的部位全被划走时这一项整条去掉`() {
        val warmup = mapOf("e1" to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS))

        warmup.withoutDismissedWarmup(setOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)) shouldContainExactly emptyMap()
    }

    @Test
    fun `没有划走过任何部位时原样返回`() {
        val warmup = mapOf("e1" to listOf(MuscleGroup.CHEST))

        warmup.withoutDismissedWarmup(emptySet()) shouldContainExactly warmup
    }

    @Test
    fun `删掉一格时后面还有同部位的卡片，提示原地留着不用收`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.CHEST),
        )

        val result = warmupMusclesLeavingWith(items, "e1", emptySet()) { muscles.getValue(it) }

        result shouldBe emptySet<MuscleGroup>()
    }

    @Test
    fun `删掉一格时这个部位只此一张，提示要跟着一起收掉`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.BACK),
        )

        val result = warmupMusclesLeavingWith(items, "e1", emptySet()) { muscles.getValue(it) }

        result shouldBe setOf(MuscleGroup.CHEST)
    }

    @Test
    fun `一张卡带两个新部位时只收掉后面不再出现的那个`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            2L to listOf(MuscleGroup.TRICEPS),
        )

        val result = warmupMusclesLeavingWith(items, "e1", emptySet()) { muscles.getValue(it) }

        result shouldBe setOf(MuscleGroup.CHEST)
    }

    @Test
    fun `已经被划走的部位不会再被算一次`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.BACK),
        )

        val result = warmupMusclesLeavingWith(items, "e1", setOf(MuscleGroup.CHEST)) { muscles.getValue(it) }

        result shouldBe emptySet<MuscleGroup>()
    }

    @Test
    fun `删掉的是本来就没有提示的那张卡时什么都不用收`() {
        val items = listOf(LogItem.Exercise(1L), LogItem.Exercise(2L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.CHEST),
        )

        val result = warmupMusclesLeavingWith(items, "e2", emptySet()) { muscles.getValue(it) }

        result shouldBe emptySet<MuscleGroup>()
    }

    @Test
    fun `删掉动作组时按全部组内成员的部位算`() {
        val group = LogItem.Group(
            groupId = 7L,
            maxPicks = 2,
            memberIds = listOf(1L, 2L),
            pickedIds = emptyList(),
        )
        val items = listOf(group, LogItem.Exercise(3L))
        val muscles = mapOf(
            1L to listOf(MuscleGroup.CHEST),
            2L to listOf(MuscleGroup.LEG),
            3L to listOf(MuscleGroup.LEG),
        )

        val result = warmupMusclesLeavingWith(items, "g7", emptySet()) { muscles.getValue(it) }

        result shouldBe setOf(MuscleGroup.CHEST)
    }
}
