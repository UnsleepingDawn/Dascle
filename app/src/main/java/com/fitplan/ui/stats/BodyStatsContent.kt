package com.fitplan.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.model.BodyMetric
import com.fitplan.domain.model.BodyMetricPoint
import com.fitplan.domain.model.BodyStats
import com.fitplan.presentation.core.components.SectionCard
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.widget.chart.LineChart
import com.fitplan.presentation.widget.chart.LinePoint
import com.fitplan.ui.profile.BodyMetricField
import com.fitplan.ui.profile.formatDate
import com.fitplan.ui.profile.label
import com.fitplan.ui.profile.valueText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * 统计页「身体数据」标签：上面两个记录按钮，下面是体重与体脂率两条折线。
 *
 * 横轴与锻炼数据一样由同一个时间区间决定（[BodyStats.rangeStartDate] 是区间第一天），
 * 只是这里没练的日子也会照实留空档，所以横轴按「区间第几天」排。
 */
@Composable
internal fun BodyStatsContent(
    stats: BodyStats,
    onRecord: (BodyMetricField) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                RecordButton(
                    text = stringResource(R.string.stats_body_record_weight),
                    onClick = { onRecord(BodyMetricField.WEIGHT) },
                    modifier = Modifier.weight(1f),
                )
                RecordButton(
                    text = stringResource(R.string.stats_body_record_body_fat),
                    onClick = { onRecord(BodyMetricField.BODY_FAT) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 一条记录都没有时不摆两张空图，直接告诉用户从上面的按钮开始。
        if (stats.latestWeight == null && stats.latestBodyFat == null) {
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.stats_body_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(title = stringResource(R.string.profile_current)) {
                LatestRow(
                    field = BodyMetricField.WEIGHT,
                    latest = stats.latestWeight,
                    value = stats.latestWeight?.weight,
                )
                LatestRow(
                    field = BodyMetricField.BODY_FAT,
                    latest = stats.latestBodyFat,
                    value = stats.latestBodyFat?.bodyFat,
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.stats_chart_weight)) {
                MetricChart(
                    points = stats.weightPoints,
                    startDate = stats.rangeStartDate,
                    field = BodyMetricField.WEIGHT,
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.stats_chart_body_fat)) {
                MetricChart(
                    points = stats.bodyFatPoints,
                    startDate = stats.rangeStartDate,
                    field = BodyMetricField.BODY_FAT,
                )
            }
        }
    }
}

/** 两条记录按钮用同一种样式，只是文案不同。 */
@Composable
private fun RecordButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.padding.small,
            vertical = MaterialTheme.padding.small,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 当前这一项的最近一次记录：数值 + 记录日期（没有记录就说没有）。 */
@Composable
private fun LatestRow(
    field: BodyMetricField,
    latest: BodyMetric?,
    value: Double?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value?.let { field.valueText(it) } ?: stringResource(R.string.profile_unset),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = latest?.let { stringResource(R.string.stats_body_last_record, formatDate(it.date)) }
                    ?: stringResource(R.string.stats_body_never_recorded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 一条身体数据折线；[field] 决定空态文案与线的颜色。
 *
 * 横轴坐标是「区间第几天」，因此没记录的日子会留出空档；标签用区间起点加偏移还原成日期。
 */
@Composable
private fun MetricChart(
    points: List<BodyMetricPoint>,
    startDate: LocalDate,
    field: BodyMetricField,
) {
    val emptyMessage = stringResource(
        when (field) {
            BodyMetricField.WEIGHT -> R.string.stats_body_weight_empty
            BodyMetricField.BODY_FAT -> R.string.stats_body_fat_empty
        },
    )
    val color = when (field) {
        BodyMetricField.WEIGHT -> MaterialTheme.colorScheme.primary
        BodyMetricField.BODY_FAT -> MaterialTheme.colorScheme.tertiary
    }

    if (points.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val axis = remember(points, startDate) { points.toAxis(startDate) }
    LineChart(points = axis.points, labelAt = axis.labelAt, color = color)
}

/** 把身体数据的点摆到「区间第几天」的横轴上。 */
private fun List<BodyMetricPoint>.toAxis(startDate: LocalDate): ProgressAxis {
    val points = map { LinePoint(x = startDate.daysUntil(it.date), value = it.value) }
    val labelAt: (Int) -> String = { x -> shortDate(startDate.plus(x, DateTimeUnit.DAY)) }
    return ProgressAxis(points, labelAt)
}
