package com.fitplan.domain.model

import kotlin.math.roundToInt

/**
 * 代谢估算结果。
 *
 * [basalMetabolism] 是基础代谢（BMR），[dailyExpenditure] 是每日总消耗（TDEE，BMR × 活动系数）；
 * 没选活动程度时 [dailyExpenditure] 为 null。
 */
data class EnergyExpenditure(
    val basalMetabolism: Int,
    val dailyExpenditure: Int?,
)

/**
 * 用 Mifflin-St Jeor 公式估算基础代谢与每日总消耗。
 *
 * 男：`10 × 体重 + 6.25 × 身高 − 5 × 年龄 + 5`；
 * 女：`10 × 体重 + 6.25 × 身高 − 5 × 年龄 − 161`；
 * 性别选「其他」或没填时取男女中点（常数 −78），没填性别就不算。
 *
 * 性别 / 年龄 / 身高 / 体重任一缺失都返回 null，由页面提示补哪些数据。
 */
fun estimateEnergyExpenditure(
    gender: Gender?,
    age: Int?,
    heightCm: Double?,
    weightKg: Double?,
    activityLevel: ActivityLevel?,
): EnergyExpenditure? {
    val offset = when (gender) {
        null -> return null
        Gender.MALE -> 5.0
        Gender.FEMALE -> -161.0
        Gender.OTHER -> (5.0 + -161.0) / 2
    }
    if (age == null || heightCm == null || weightKg == null) return null

    val basal = (10 * weightKg + 6.25 * heightCm - 5 * age + offset).roundToInt()
    return EnergyExpenditure(
        basalMetabolism = basal,
        dailyExpenditure = activityLevel?.let { (basal * it.factor).roundToInt() },
    )
}
