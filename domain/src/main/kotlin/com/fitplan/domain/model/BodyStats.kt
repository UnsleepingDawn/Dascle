package com.fitplan.domain.model

import kotlinx.datetime.LocalDate

/** 身体数据折线上的一个点：[value] 是当天的体重或体脂率。 */
data class BodyMetricPoint(
    val date: LocalDate,
    val value: Double,
)

/** 统计页「身体数据」标签一次取数的结果。 */
data class BodyStats(
    /** 所选区间的第一天；「全部」时是最早一条身体数据的日期。 */
    val rangeStartDate: LocalDate,
    val weightPoints: List<BodyMetricPoint>,
    val bodyFatPoints: List<BodyMetricPoint>,
    /** 最近一次有体重 / 体脂的记录（不受区间限制），用于「当前」与输入框预填。 */
    val latestWeight: BodyMetric?,
    val latestBodyFat: BodyMetric?,
) {
    val isEmpty: Boolean get() = weightPoints.isEmpty() && bodyFatPoints.isEmpty()
}

/**
 * 身体数据补记提醒：哪些项该提醒、以及缺得最久的那一项距上次记录多少天。
 *
 * [daysSinceLastRecord] 用于文案；两项都缺时取较大的那个间隔。
 */
data class BodyMetricReminder(
    val needWeight: Boolean,
    val needBodyFat: Boolean,
    val daysSinceLastRecord: Int,
)
