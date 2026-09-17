package com.fitplan.data.repository

import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.domain.model.ActivityLevel
import com.fitplan.domain.model.Gender
import com.fitplan.domain.model.UserProfile
import com.fitplan.domain.repository.UserProfileRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate

/**
 * 个人信息存偏好里：都是标量，没必要单开一张表。
 *
 * 性别、生日、身高、活动程度都用 [Preference.isSet] 区分「没填」与「填了默认值」，
 * 引导完成日期是内部状态，用 `appStateKey` 前缀，不进备份。
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class UserProfileRepositoryImpl(
    preferenceStore: PreferenceStore,
) : UserProfileRepository {

    private val gender = preferenceStore.getString(KEY_GENDER)
    private val birthday = preferenceStore.getLong(KEY_BIRTHDAY)
    private val height = preferenceStore.getString(KEY_HEIGHT)
    private val activityLevel = preferenceStore.getString(KEY_ACTIVITY_LEVEL)
    private val onboardedAt = preferenceStore.getLong(Preference.appStateKey(KEY_ONBOARDED_AT))

    override suspend fun get(): UserProfile = UserProfile(
        gender = gender.readEnum(),
        birthday = birthday.readDate(),
        heightCm = height.readHeight(),
        activityLevel = activityLevel.readEnum(),
        onboardedAt = onboardedAt.readDate(),
    )

    override suspend fun save(
        gender: Gender?,
        birthday: LocalDate?,
        heightCm: Double?,
        activityLevel: ActivityLevel?,
    ) {
        this.gender.set(gender?.name.orEmpty())
        this.birthday.set(birthday?.toEpochDays() ?: 0L)
        this.height.set(heightCm?.toString().orEmpty())
        this.activityLevel.set(activityLevel?.name.orEmpty())
    }

    override suspend fun markOnboarded(date: LocalDate) {
        onboardedAt.set(date.toEpochDays())
    }

    /** 枚举名反查；写进去的名字认不出来（或没填过）都当没填。 */
    private inline fun <reified T : Enum<T>> Preference<String>.readEnum(): T? =
        get().takeIf { isSet() }?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

    private fun Preference<String>.readHeight(): Double? =
        get().takeIf { isSet() }?.toDoubleOrNull()

    private fun Preference<Long>.readDate(): LocalDate? =
        get().takeIf { isSet() && it > 0L }?.let(LocalDate::fromEpochDays)

    private companion object {
        const val KEY_GENDER = "user_gender"
        const val KEY_BIRTHDAY = "user_birthday"
        const val KEY_HEIGHT = "user_height"
        const val KEY_ACTIVITY_LEVEL = "user_activity_level"
        const val KEY_ONBOARDED_AT = "onboarded_at"
    }
}
