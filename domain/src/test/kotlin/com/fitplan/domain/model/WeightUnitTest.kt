package com.fitplan.domain.model

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** 磅与千克的换算、容差比较与有效单位解析。 */
class WeightUnitTest {

    @Test
    fun `一磅等于精确的千克换算系数`() {
        WeightUnit.LB.toKilograms(1.0) shouldBe (0.45359237 plusOrMinus 1e-12)
        WeightUnit.KG.toKilograms(1.0) shouldBe 1.0
    }

    @Test
    fun `磅与千克来回换算不漂移`() {
        val kilograms = WeightUnit.LB.toKilograms(100.0)
        WeightUnit.LB.fromKilograms(kilograms) shouldBe (100.0 plusOrMinus 1e-9)
        WeightUnit.KG.fromKilograms(kilograms) shouldBe (45.359237 plusOrMinus 1e-9)
    }

    @Test
    fun `容差内视为同一个重量`() {
        sameWeight(45.359237, 45.36) shouldBe true
        sameWeight(45.359237, 45.5) shouldBe false
    }

    @Test
    fun `大于小于的比较在容差内算持平`() {
        // 100 lb 落库后换回来的值与界面显示的 45.4 kg 只差一点点，不该被判成「更重」。
        (45.4).isWeightGreaterThan(45.359237) shouldBe false
        (45.4).isWeightLessThan(45.359237) shouldBe false
        (46.0).isWeightGreaterThan(45.359237) shouldBe true
        (44.0).isWeightLessThan(45.359237) shouldBe true
    }

    @Test
    fun `不小于不大于在容差内算通过`() {
        (45.4).isWeightAtLeast(45.359237) shouldBe true
        (45.359237).isWeightAtMost(45.4) shouldBe true
        (44.0).isWeightAtLeast(45.359237) shouldBe false
    }

    @Test
    fun `动作覆盖优先于全局`() {
        WeightUnit.KG.resolve(WeightUnit.LB) shouldBe WeightUnit.LB
        WeightUnit.KG.resolve(null) shouldBe WeightUnit.KG
    }

    @Test
    fun `认不出来的取值按跟随全局处理`() {
        WeightUnit.fromValue("LB") shouldBe WeightUnit.LB
        WeightUnit.fromValue(null) shouldBe null
        WeightUnit.fromValue("STONE") shouldBe null
    }
}
