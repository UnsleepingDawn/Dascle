package com.fitplan.domain.interactor

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class RestRemovalDateMovesTest {

    @Test
    fun `删掉中间的休息日时其后的训练日整体提前`() {
        val moves = restRemovalDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            removedRestDates = setOf(date(10, 13)),
        )

        moves shouldBe listOf(date(10, 14) to date(10, 13), date(10, 16) to date(10, 15))
    }

    @Test
    fun `删掉多个休息日时按被删个数整体提前`() {
        val moves = restRemovalDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            removedRestDates = setOf(date(10, 13), date(10, 15)),
        )

        moves shouldBe listOf(date(10, 14) to date(10, 13), date(10, 16) to date(10, 14))
    }

    @Test
    fun `休息日在所有训练日之后时训练日不受影响`() {
        val moves = restRemovalDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedRestDates = setOf(date(10, 16)),
        )

        moves shouldBe emptyList()
    }

    @Test
    fun `休息日在训练日之前时前面那几天不受影响`() {
        val moves = restRemovalDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedRestDates = setOf(date(10, 10)),
        )

        moves shouldBe listOf(date(10, 12) to date(10, 11), date(10, 14) to date(10, 13))
    }

    @Test
    fun `没有可删的休息日时映射为空`() {
        val moves = restRemovalDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedRestDates = emptySet(),
        )

        moves shouldBe emptyList()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate(2026, month, day)
}
