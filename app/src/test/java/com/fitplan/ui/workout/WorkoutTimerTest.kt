package com.fitplan.ui.workout

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 计时弹窗里那两个纯函数的用例：时间格式化与倒数读数。 */
class WorkoutTimerTest {

    @Test
    fun `计时文本按整秒向下取整并补零`() {
        timerText(0L) shouldBe "00:00"
        timerText(1_000L) shouldBe "00:01"
        // 59.999 秒还没满一分钟，显示的是已经满了的 59 秒。
        timerText(59_999L) shouldBe "00:59"
        timerText(60_000L) shouldBe "01:00"
        timerText(3_599_999L) shouldBe "59:59"
    }

    @Test
    fun `计时文本超过一小时改用时分秒`() {
        timerText(3_600_000L) shouldBe "1:00:00"
        timerText(3_661_000L) shouldBe "1:01:01"
        timerText(7_384_000L) shouldBe "2:03:04"
    }

    @Test
    fun `计时文本把负数当成零`() {
        timerText(-1L) shouldBe "00:00"
        timerText(-60_000L) shouldBe "00:00"
    }

    @Test
    fun `倒数器按整秒反算剩余秒数`() {
        countdownRemainingSeconds(90, 0L) shouldBe 90
        countdownRemainingSeconds(90, 1_000L) shouldBe 89
        countdownRemainingSeconds(90, 89_999L) shouldBe 1
    }

    @Test
    fun `倒数器数过之后停在零不再变负`() {
        countdownRemainingSeconds(90, 90_000L) shouldBe 0
        countdownRemainingSeconds(90, 600_000L) shouldBe 0
        countdownRemainingSeconds(90, -1_000L) shouldBe 90
    }
}
