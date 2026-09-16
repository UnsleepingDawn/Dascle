package com.fitplan.domain.model

import kotlinx.datetime.LocalDate

/** 性别，只用于个人信息展示与后续可能的代谢估算。 */
enum class Gender {
    MALE,
    FEMALE,
    OTHER,
}

/**
 * 用户个人信息。字段都可以为空：用户不想填就空着，页面上显示「未填写」。
 *
 * [onboardedAt] 是走完（或跳过）首次引导的日期，用来判断要不要再弹引导页；属于内部状态。
 */
data class UserProfile(
    val gender: Gender?,
    val birthday: LocalDate?,
    val onboardedAt: LocalDate?,
) {
    val isOnboarded: Boolean get() = onboardedAt != null

    /** 按 [date] 推算周岁；没填生日或生日还没到就返回 null。 */
    fun ageOn(date: LocalDate): Int? = ageOn(birthday, date)
}

/** 由生日推算 [date] 当天的周岁；生日为空或还没到就返回 null。 */
fun ageOn(birthday: LocalDate?, date: LocalDate): Int? {
    if (birthday == null || birthday > date) return null
    val passedThisYear = date.month.ordinal > birthday.month.ordinal ||
        (date.month == birthday.month && date.day >= birthday.day)
    return date.year - birthday.year - if (passedThisYear) 0 else 1
}
