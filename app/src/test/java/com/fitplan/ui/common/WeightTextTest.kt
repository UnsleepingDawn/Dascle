package com.fitplan.ui.common

import com.fitplan.domain.model.WeightUnit
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 训练重量的显示格式化与按单位解析。 */
class WeightTextTest {

    @Test
    fun `千克整数不带小数点`() {
        (60.0).toWeightText(WeightUnit.KG) shouldBe "60"
        (60.5).toWeightText(WeightUnit.KG) shouldBe "60.5"
    }

    @Test
    fun `显示最多一位小数`() {
        (60.04).toWeightText(WeightUnit.KG) shouldBe "60"
        (45.359237).toWeightText(WeightUnit.LB) shouldBe "100"
        (50.0).toWeightText(WeightUnit.LB) shouldBe "110.2"
    }

    @Test
    fun `空重量显示为空串`() {
        (null as Double?).toWeightText(WeightUnit.LB) shouldBe ""
    }

    @Test
    fun `按显示单位解析成千克`() {
        "60".toKilogramsOrNull(WeightUnit.KG) shouldBe 60.0
        "100".toKilogramsOrNull(WeightUnit.LB) shouldBe (45.359237 plusOrMinus 1e-9)
    }

    @Test
    fun `空串与非法输入解析为空`() {
        "".toKilogramsOrNull(WeightUnit.KG) shouldBe null
        "abc".toKilogramsOrNull(WeightUnit.KG) shouldBe null
    }

    @Test
    fun `磅与千克往返不出现长小数`() {
        val kilograms = "100".toKilogramsOrNull(WeightUnit.LB) ?: error("应当能解析")
        kilograms.toWeightText(WeightUnit.LB) shouldBe "100"
        kilograms.toWeightText(WeightUnit.KG) shouldBe "45.4"
    }
}
