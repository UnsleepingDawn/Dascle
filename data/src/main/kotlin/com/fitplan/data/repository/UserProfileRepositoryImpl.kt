package com.fitplan.data.repository

import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.domain.model.Gender
import com.fitplan.domain.model.UserProfile
import com.fitplan.domain.repository.UserProfileRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate

/**
 * 个人信息存偏好里：只有三个标量，没必要单开一张表。
 *
 * 性别与生日都用 [Preference.isSet] 区分「没填」与「填了默认值」，
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
    private val onboardedAt = preferenceStore.getLong(Preference.appStateKey(KEY_ONBOARDED_AT))

    override suspend fun get(): UserProfile = UserProfile(
        gender = gender.readEnum(),
        birthday = birthday.readDate(),
        onboardedAt = onboardedAt.readDate(),
    )

    override suspend fun save(gender: Gender?, birthday: LocalDate?) {
        this.gender.set(gender?.name.orEmpty())
        this.birthday.set(birthday?.toEpochDays() ?: 0L)
    }

    override suspend fun markOnboarded(date: LocalDate) {
        onboardedAt.set(date.toEpochDays())
    }

    private fun Preference<String>.readEnum(): Gender? = if (!isSet()) {
        null
    } else {
        Gender.entries.firstOrNull { it.name == get() }
    }

    private fun Preference<Long>.readDate(): LocalDate? =
        get().takeIf { isSet() && it > 0L }?.let(LocalDate::fromEpochDays)

    private companion object {
        const val KEY_GENDER = "user_gender"
        const val KEY_BIRTHDAY = "user_birthday"
        const val KEY_ONBOARDED_AT = "onboarded_at"
    }
}
