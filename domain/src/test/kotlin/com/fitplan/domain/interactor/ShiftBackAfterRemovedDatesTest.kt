package com.fitplan.domain.interactor

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class ShiftBackAfterRemovedDatesTest {

    @Test
    fun `删掉中间的休息日时其后的日子整体提前`() {
        val moves = shiftBackAfterRemovedDates(
            scheduledDates = listOf(date(10, 12), date(10, 14), date(10, 16)),
            removedDates = setOf(date(10, 13)),
        )

        moves shouldBe listOf(date(10, 14) to date(10, 13), date(10, 16) to date(10, 15))
    }

    @Test
    fun `删掉的第一个训练日时其后全体含休息日一起前移`() {
        // 排期只有 10/12、10/14、10/16 三天有训练，10/13、10/15 是休息日。
        // 删掉 10/12 之后，10/14 落到 10/13、10/16 落到 10/15，
        // 于是整体变成 10/12 休、10/13 练、10/14 休、10/15 练、10/16 休，练休节奏不变。
        val remaining = listOf(date(10, 12), date(10, 14), date(10, 16))
            .filterNot { it in setOf(date(10, 12)) }

        val moves = shiftBackAfterRemovedDates(
            scheduledDates = remaining,
            removedDates = setOf(date(10, 12)),
        )

        moves shouldBe listOf(date(10, 14) to date(10, 13), date(10, 16) to date(10, 15))
    }

    @Test
    fun `同时删掉训练日与休息日时按被删个数整体提前`() {
        val remaining = listOf(date(10, 12), date(10, 14), date(10, 16), date(10, 18))
            .filterNot { it in setOf(date(10, 14)) }

        val moves = shiftBackAfterRemovedDates(
            scheduledDates = remaining,
            removedDates = setOf(date(10, 13), date(10, 14)),
        )

        moves shouldBe listOf(date(10, 16) to date(10, 14), date(10, 18) to date(10, 16))
    }

    @Test
    fun `被删的日子在所有训练日之后时训练日不受影响`() {
        val moves = shiftBackAfterRemovedDates(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedDates = setOf(date(10, 16)),
        )

        moves shouldBe emptyList()
    }

    @Test
    fun `被删的日子在训练日之前时前面那几天不受影响`() {
        val moves = shiftBackAfterRemovedDates(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedDates = setOf(date(10, 10)),
        )

        moves shouldBe listOf(date(10, 12) to date(10, 11), date(10, 14) to date(10, 13))
    }

    @Test
    fun `没有可删的日子时映射为空`() {
        val moves = shiftBackAfterRemovedDates(
            scheduledDates = listOf(date(10, 12), date(10, 14)),
            removedDates = emptySet(),
        )

        moves shouldBe emptyList()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate(2026, month, day)
}
