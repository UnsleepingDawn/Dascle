package com.fitplan.domain.interactor

import com.fitplan.domain.model.BodyMetricPoint
import com.fitplan.domain.model.BodyStats
import com.fitplan.domain.model.StatsRange
import com.fitplan.domain.repository.BodyMetricRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 统计页「身体数据」的取数：取出 [range] 区间内的体重与体脂率折线，
 * 再附上全量最新的那两条记录（页面上的「当前」与记录输入框的预填值）。
 *
 * 区间语义与 [GetWorkoutStats] 一致：近 7 天就只看这 7 天，「全部」回溯到最早一条身体数据。
 */
@Inject
class GetBodyStats(
    private val bodyMetricRepository: BodyMetricRepository,
) {

    suspend operator fun invoke(range: StatsRange): BodyStats {
        val zone = TimeZone.currentSystemDefault()
        val endDate = Clock.System.now().toLocalDateTime(zone).date

        // 「全部」要顺着最早的记录往回退，只有这一种情况需要把记录全取出来。
        val all = if (range.days == null) bodyMetricRepository.getAll() else null
        val rangeStartDate = range.days?.let { endDate.minus(it - 1, DateTimeUnit.DAY) }
            ?: all?.minOfOrNull { it.date }
            ?: endDate
        val records = (all ?: bodyMetricRepository.getBetween(rangeStartDate, endDate))
            .sortedBy { it.date }

        return BodyStats(
            rangeStartDate = rangeStartDate,
            weightPoints = records.mapNotNull { metric ->
                metric.weight?.let { BodyMetricPoint(metric.date, it) }
            },
            bodyFatPoints = records.mapNotNull { metric ->
                metric.bodyFat?.let { BodyMetricPoint(metric.date, it) }
            },
            latestWeight = bodyMetricRepository.getLatestWeight(),
            latestBodyFat = bodyMetricRepository.getLatestBodyFat(),
        )
    }
}
