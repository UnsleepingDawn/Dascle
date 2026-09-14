package com.fitplan.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.widget.chart.ChartEntry
import com.fitplan.ui.exercise.label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 区间切换：近 7 天 / 近 30 天。 */
@Composable
internal fun StatsRangeSelector(
    days: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        StatsScreenModel.RANGES.forEach { range ->
            FilterChip(
                selected = range == days,
                onClick = { onSelect(range) },
                label = {
                    Text(
                        text = stringResource(
                            if (range <= WEEK_DAYS) R.string.stats_range_week else R.string.stats_range_month,
                        ),
                    )
                },
            )
        }
    }
}

/** 概览：区间内的训练天数、训练次数、完成组数与近 4 周的平均每周训练天数。 */
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

/** 单个动作的最好重量按天连成折线。 */
internal fun ExerciseProgress.entries(): List<ChartEntry> =
    points.map { ChartEntry(shortDate(it.date), it.weight) }

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

private fun durationOf(item: WorkoutHistoryItem): Long? =
    item.finishedAt?.let { (it - item.startedAt).inWholeSeconds }

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

private const val WEEK_DAYS = 7
