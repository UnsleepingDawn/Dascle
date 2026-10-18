package com.fitplan.domain.model

import kotlin.math.abs

/**
 * 训练重量的计量单位。
 *
 * 数据库里的全部重量（动作库默认重量、计划目标重量、每组实际重量）**一律以千克存储**，
 * 单位只决定录入与展示时换算成什么样子。这样同一列里不会混着两种量纲，
 * 统计取最大值、渐进提示比较大小都不需要关心用户当时用的是哪种单位。
 */
enum class WeightUnit {
    KG,
    LB,
    ;

    /** 把「以本单位表示的数值」换算成千克。 */
    fun toKilograms(value: Double): Double = when (this) {
        KG -> value
        LB -> value * KG_PER_LB
    }

    /** 把千克换算成「以本单位表示的数值」。 */
    fun fromKilograms(kilograms: Double): Double = when (this) {
        KG -> kilograms
        LB -> kilograms / KG_PER_LB
    }

    companion object {
        /** 英制常衡磅的精确换算系数，不要用近似值，否则来回切换会漂移。 */
        const val KG_PER_LB = 0.45359237

        /** 没设置过时的默认单位。 */
        val DEFAULT: WeightUnit = KG

        fun fromValue(value: String?): WeightUnit? = entries.find { it.name == value }
    }
}

/**
 * 重量比较的容差（千克）。
 *
 * 磅换算成千克后是 [WeightUnit.KG_PER_LB] 这样的长小数，界面显示又会取整，
 * 精确相等比较会把「用户没动过的预填值」判成手改过、把「刚好达标」判成没达标。
 * 约 0.05 kg 相当于 0.11 磅，远小于任何真实可调的重量档位。
 */
const val WEIGHT_COMPARE_TOLERANCE_KG = 0.05

/** 有效单位：动作自己设了就用动作的，没设过就跟随全局。 */
fun WeightUnit.resolve(override: WeightUnit?): WeightUnit = override ?: this

/** 两个以千克为单位的重量在容差内是否相等。 */
fun sameWeight(a: Double, b: Double): Boolean = abs(a - b) <= WEIGHT_COMPARE_TOLERANCE_KG

/** 本重量是否明显大于 [other]（超出容差）；两侧都是千克。 */
fun Double.isWeightGreaterThan(other: Double): Boolean = this - other > WEIGHT_COMPARE_TOLERANCE_KG

/** 本重量是否明显小于 [other]（超出容差）；两侧都是千克。 */
fun Double.isWeightLessThan(other: Double): Boolean = other - this > WEIGHT_COMPARE_TOLERANCE_KG

/** 本重量是否不小于 [other]（容差内算相等）；两侧都是千克。 */
fun Double.isWeightAtLeast(other: Double): Boolean = !isWeightLessThan(other)

/** 本重量是否不大于 [other]（容差内算相等）；两侧都是千克。 */
fun Double.isWeightAtMost(other: Double): Boolean = !isWeightGreaterThan(other)
