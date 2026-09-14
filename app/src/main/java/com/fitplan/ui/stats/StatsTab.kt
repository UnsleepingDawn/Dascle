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
        val days by screenModel.days.collectAsState()
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
            when {
                current == null -> LoadingScreen(modifier = Modifier.padding(contentPadding))

                current.isEmpty -> EmptyScreen(
                    message = stringResource(R.string.stats_empty),
                    modifier = Modifier.padding(contentPadding),
                )

                else -> StatsContent(
                    stats = current,
                    days = days,
                    selectedExerciseId = selectedExerciseId,
                    contentPadding = contentPadding,
                    onSelectDays = screenModel::selectDays,
                    onSelectExercise = screenModel::selectExercise,
                    onOpenHistory = { sessionId -> navigator.push(WorkoutLogScreen(sessionId = sessionId)) },
                )
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: WorkoutStats,
    days: Int,
    selectedExerciseId: Long?,
    contentPadding: PaddingValues,
    onSelectDays: (Int) -> Unit,
    onSelectExercise: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
) {
    val muscleEntries = stats.muscleEntries()
    val selectedProgress = stats.exerciseProgress.firstOrNull { it.exerciseId == selectedExerciseId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            StatsRangeSelector(days = days, onSelect = onSelectDays)
        }

        item {
            SectionCard {
                StatsOverviewRow(stats = stats)
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
            SectionCard(title = stringResource(R.string.stats_chart_progress)) {
                if (selectedProgress == null) {
                    Text(
                        text = stringResource(R.string.stats_chart_progress_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        ExerciseProgressPicker(
                            progress = stats.exerciseProgress,
                            selectedId = selectedExerciseId,
                            onSelect = onSelectExercise,
                        )
                        LineChart(entries = selectedProgress.entries())
                    }
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
                    text = stringResource(R.string.stats_history_count, stats.history.size),
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
