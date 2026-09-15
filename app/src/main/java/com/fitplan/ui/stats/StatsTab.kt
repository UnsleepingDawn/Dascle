package com.fitplan.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.presentation.core.components.SectionCard
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.core.screens.LoadingScreen
import com.fitplan.presentation.util.Tab
import com.fitplan.presentation.widget.chart.ColumnChart
import com.fitplan.presentation.widget.chart.LineChart
import com.fitplan.ui.workout.WorkoutLogScreen
import dev.zacsweers.metrox.viewmodel.metroViewModel

object StatsTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = stringResource(R.string.tab_stats),
            icon = rememberVectorPainter(Icons.Filled.BarChart),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<StatsScreenModel>()
        val range by screenModel.range.collectAsState()
        val axisMode by screenModel.axisMode.collectAsState()
        val stats by screenModel.stats.collectAsState()
        val selectedExerciseId by screenModel.selectedExerciseId.collectAsState()

        // 从历史详情页返回时本组合会重建，顺带刷新一次。
        LaunchedEffect(Unit) { screenModel.refresh() }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.tab_stats)) },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val current = stats
            // 区间选择器固定在外面：切到还没练过的区间时下面会变成空态，得留一条路切回去。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                StatsRangeSelector(
                    range = range,
                    onSelect = screenModel::selectRange,
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
                )

                when {
                    current == null -> LoadingScreen()

                    current.isEmpty -> EmptyScreen(message = stringResource(R.string.stats_range_empty))

                    else -> StatsContent(
                        stats = current,
                        axisMode = axisMode,
                        selectedExerciseId = selectedExerciseId,
                        onSelectAxisMode = screenModel::selectAxisMode,
                        onSelectExercise = screenModel::selectExercise,
                        onOpenHistory = { sessionId ->
                            navigator.push(WorkoutLogScreen(sessionId = sessionId))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: WorkoutStats,
    axisMode: ProgressAxisMode,
    selectedExerciseId: Long?,
    onSelectAxisMode: (ProgressAxisMode) -> Unit,
    onSelectExercise: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
) {
    val muscleEntries = stats.muscleEntries()
    val selectedProgress = stats.exerciseProgress.firstOrNull { it.exerciseId == selectedExerciseId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            SectionCard {
                StatsOverviewRow(stats = stats)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.stats_chart_progress)) {
                if (selectedProgress == null) {
                    Text(
                        text = stringResource(R.string.stats_chart_progress_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val axis = remember(selectedProgress, axisMode, stats.rangeStartDate) {
                        selectedProgress.axis(mode = axisMode, startDate = stats.rangeStartDate)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        ProgressAxisSelector(mode = axisMode, onSelect = onSelectAxisMode)
                        ExerciseProgressPicker(
                            progress = stats.exerciseProgress,
                            selectedId = selectedExerciseId,
                            onSelect = onSelectExercise,
                        )
                        LineChart(points = axis.points, labelAt = axis.labelAt)
                    }
                }
            }
        }

        if (muscleEntries.isNotEmpty()) {
            item {
                SectionCard(title = stringResource(R.string.stats_chart_muscle)) {
                    ColumnChart(entries = muscleEntries)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.extraLarge),
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.stats_history_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.stats_history_count, stats.totalSessions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(stats.history, key = { it.sessionId }) { item ->
            WorkoutHistoryRow(
                item = item,
                onClick = { onOpenHistory(item.sessionId) },
            )
        }
    }
}
