package com.fitplan.domain.interactor

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class CompactTrainingDatesTest {

    @Test
    fun `删掉中间一天时其后的训练日顶入这一天`() {
        val moves = compactTrainingDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            deletedDates = setOf(date(10, 14)),
        )

        moves shouldBe listOf(date(10, 16) to date(10, 14))
    }

    @Test
    fun `删掉第一天的训练日时其后整体前顶`() {
        val moves = compactTrainingDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            deletedDates = setOf(date(10, 12)),
        )

        moves shouldBe listOf(date(10, 14) to date(10, 12), date(10, 16) to date(10, 14))
    }

    @Test
    fun `删掉最后一天的训练日时其余不动`() {
        val moves = compactTrainingDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            deletedDates = setOf(date(10, 16)),
        )

        moves shouldBe emptyList()
    }

    @Test
    fun `多选删除时保留的训练日按顺序顶进最靠前的日子`() {
        val moves = compactTrainingDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16), date(10, 18)),
            deletedDates = setOf(date(10, 14), date(10, 18)),
        )

        moves shouldBe listOf(date(10, 16) to date(10, 14))
    }

    @Test
    fun `被删的休息日本来就没有排期不影响映射`() {
        val moves = compactTrainingDateMoves(
            scheduledDates = listOf(date(10, 12), date(10, 16)),
            deletedDates = setOf(date(10, 14)),
        )

        moves shouldBe emptyList()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate(2026, month, day)
}
