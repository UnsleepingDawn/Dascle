package com.fitplan.domain.interactor

import com.fitplan.domain.model.BodyMetricReminder
import com.fitplan.domain.repository.BodyMetricRepository
import com.fitplan.domain.repository.UserProfileRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 判断要不要提醒用户补记身体数据：某项已经超过 [THRESHOLD_DAYS] 天没记录了就来提醒。
 *
 * 基准日取该项最近一次记录的日期；从来没记过就退回「走完引导那天」，
 * 这样刚装好 App 的头一周不会被反复催。引导还没走完（[UserProfileRepository.get] 里
 * `onboardedAt` 为 null）时一律不提醒。
 */
@Inject
class GetBodyMetricReminder(
    private val bodyMetricRepository: BodyMetricRepository,
    private val userProfileRepository: UserProfileRepository,
) {

    suspend operator fun invoke(): BodyMetricReminder? {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val onboardedAt = userProfileRepository.get().onboardedAt ?: return null

        val weightBaseline = bodyMetricRepository.getLatestWeight()?.date ?: onboardedAt
        val bodyFatBaseline = bodyMetricRepository.getLatestBodyFat()?.date ?: onboardedAt
        val weightGap = weightBaseline.daysUntil(today)
        val bodyFatGap = bodyFatBaseline.daysUntil(today)
        val needWeight = weightGap >= THRESHOLD_DAYS
        val needBodyFat = bodyFatGap >= THRESHOLD_DAYS
        if (!needWeight && !needBodyFat) return null

        return BodyMetricReminder(
            needWeight = needWeight,
            needBodyFat = needBodyFat,
            daysSinceLastRecord = maxOf(weightGap, bodyFatGap),
        )
    }
}

/** 「长达一周」的阈值：超过这个天数没记录就提醒一次。 */
const val THRESHOLD_DAYS = 7
