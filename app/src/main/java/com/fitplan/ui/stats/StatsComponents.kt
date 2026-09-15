package com.fitplan.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.model.ExerciseProgress
import com.fitplan.domain.model.StatsRange
import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.widget.chart.ChartEntry
import com.fitplan.presentation.widget.chart.LinePoint
import com.fitplan.ui.exercise.label
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 区间切换：近 7 天 / 近 30 天 / 近 1 年 / 全部。 */
@Composable
internal fun StatsRangeSelector(
    range: StatsRange,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        StatsScreenModel.RANGES.forEach { item ->
            FilterChip(
                selected = item == range,
                onClick = { onSelect(item) },
                label = { Text(text = item.label()) },
            )
        }
    }
}

@Composable
internal fun StatsRange.label(): String = stringResource(
    when (this) {
        StatsRange.WEEK -> R.string.stats_range_week
        StatsRange.MONTH -> R.string.stats_range_month
        StatsRange.YEAR -> R.string.stats_range_year
        StatsRange.ALL -> R.string.stats_range_all
    },
)

/** 概览：区间内的训练天数、训练次数、完成组数与平均每周训练天数。 */
@Composable
internal fun StatsOverviewRow(
    stats: WorkoutStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            OverviewTile(
                value = stats.trainingDays.toString(),
                label = stringResource(R.string.stats_overview_days),
                modifier = Modifier.weight(1f),
            )
            OverviewTile(
                value = stats.totalSessions.toString(),
                label = stringResource(R.string.stats_overview_sessions),
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OverviewTile(
                value = stats.totalSets.toString(),
                label = stringResource(R.string.stats_overview_sets),
                modifier = Modifier.weight(1f),
            )
            OverviewTile(
                value = formatWeeklyDays(stats.weeklyTrainingDays),
                label = stringResource(R.string.stats_overview_weekly_days),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 重量进步图的横轴显示方式。 */
@Composable
internal fun ProgressAxisMode.label(): String = stringResource(
    when (this) {
        ProgressAxisMode.INDEX -> R.string.stats_progress_axis_index
        ProgressAxisMode.DATE -> R.string.stats_progress_axis_date
    },
)

/** 重量进步图的横轴模式选择器，放在动作选择器之上。 */
@Composable
internal fun ProgressAxisSelector(
    mode: ProgressAxisMode,
    onSelect: (ProgressAxisMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        ProgressAxisMode.entries.forEach { item ->
            FilterChip(
                selected = item == mode,
                onClick = { onSelect(item) },
                label = { Text(text = item.label()) },
            )
        }
    }
}

/** 重量进步图上面的动作选择器。 */
@Composable
internal fun ExerciseProgressPicker(
    progress: List<ExerciseProgress>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        progress.forEach { item ->
            FilterChip(
                selected = item.exerciseId == selectedId,
                onClick = { onSelect(item.exerciseId) },
                label = { Text(text = item.exerciseName) },
            )
        }
    }
}

/** 训练历史列表的一项，点进去看那次的训练详情。 */
@Composable
internal fun WorkoutHistoryRow(
    item: WorkoutHistoryItem,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { stringResource(R.string.workout_free) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = listOf(
                        fullDate(dateOf(item.startedAt)),
                        stringResource(R.string.workout_summary_sets, item.completedSets),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            durationOf(item)?.let { seconds ->
                Text(
                    text = stringResource(R.string.workout_summary_duration, durationText(seconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 肌群分布的横轴标签要查字符串资源，只能在组合里拼。 */
@Composable
internal fun WorkoutStats.muscleEntries(): List<ChartEntry> =
    muscleGroupSets.map { ChartEntry(it.muscleGroup.label(), it.sets) }

/** 折线图的横轴：数据点与「坐标 -> 标签文案」的换算。 */
internal data class ProgressAxis(
    val points: List<LinePoint>,
    val labelAt: (Int) -> String,
)

/**
 * 把重量进步曲线摆到横轴上。
 *
 * [ProgressAxisMode.INDEX] 只关心练了几次，横轴永远均匀；
 * [ProgressAxisMode.DATE] 把横轴换成「区间第几天」，没训练的日子会留出空档。
 */
internal fun ExerciseProgress.axis(mode: ProgressAxisMode, startDate: LocalDate): ProgressAxis {
    val points = points.mapIndexed { index, point ->
        val x = when (mode) {
            ProgressAxisMode.INDEX -> index
            ProgressAxisMode.DATE -> startDate.daysUntil(point.date)
        }
        LinePoint(x = x, value = point.weight)
    }
    val labelAt: (Int) -> String = when (mode) {
        // 等间距模式下每个坐标都有数据点，直接取那天的日期。
        ProgressAxisMode.INDEX -> { x -> this.points.getOrNull(x)?.let { shortDate(it.date) }.orEmpty() }
        // 按日期模式除了数据点还会问到空档处的坐标，用区间起点加偏移还原日期。
        ProgressAxisMode.DATE -> { x -> shortDate(startDate.plus(x, DateTimeUnit.DAY)) }
    }
    return ProgressAxis(points, labelAt)
}

@Composable
private fun OverviewTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 时长为 0（日历里补记的训练不知道具体时间）就不显示，免得历史列表出现「0 分钟」。 */
private fun durationOf(item: WorkoutHistoryItem): Long? =
    item.finishedAt?.let { (it - item.startedAt).inWholeSeconds }?.takeIf { it > 0 }

@Composable
private fun durationText(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes < 60) {
        stringResource(R.string.workout_duration_minutes, minutes)
    } else {
        stringResource(R.string.workout_duration_hours, minutes / 60, minutes % 60)
    }
}

/** 横轴标签短一点，30 天的柱子才排得下。 */
private fun shortDate(date: LocalDate): String = "${date.month.ordinal + 1}/${date.day}"

/** 历史列表里日期要写全，跨年的记录才不会看成今年。 */
private fun fullDate(date: LocalDate): String = "${date.year}/${date.month.ordinal + 1}/${date.day}"

internal fun dateOf(instant: Instant): LocalDate =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

/** 平均每周训练天数保留一位小数，整数就不带小数点。 */
private fun formatWeeklyDays(days: Double): String =
    if (days == days.toLong().toDouble()) days.toLong().toString() else "%.1f".format(days)
