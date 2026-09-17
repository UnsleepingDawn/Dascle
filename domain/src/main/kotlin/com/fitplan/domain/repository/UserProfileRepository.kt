package com.fitplan.domain.repository

import com.fitplan.domain.model.ActivityLevel
import com.fitplan.domain.model.Gender
import com.fitplan.domain.model.UserProfile
import kotlinx.datetime.LocalDate

interface UserProfileRepository {

    suspend fun get(): UserProfile

    /** 保存性别与生日；传 null 表示这一项留空（清掉原来的值）。 */
    suspend fun save(
        gender: Gender?,
        birthday: LocalDate?,
        heightCm: Double?,
        activityLevel: ActivityLevel?,
    )

    /** 记下「引导页已经走过」，之后不再弹。 */
    suspend fun markOnboarded(date: LocalDate)
}
