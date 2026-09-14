package com.fitplan.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.plan.routine.targetText
import com.fitplan.ui.workout.WorkoutLogScreen
import com.fitplan.ui.workout.toClockText
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.isoDayNumber

object TodayTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(R.string.tab_today),
            icon = rememberVectorPainter(Icons.Filled.Today),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<TodayScreenModel>()
        val date by screenModel.date.collectAsState()
        val routines by screenModel.routines.collectAsState()
        val unfinished by screenModel.unfinished.collectAsState()
        val loaded by screenModel.loaded.collectAsState()

        // 从训练记录页返回时本组合会重建，顺带刷新一次。
        LaunchedEffect(Unit) { screenModel.refresh() }

        val weekdayNames = stringArrayResource(R.array.weekday_names)

        // 没排进今日计划里的未完成训练（例如计划外训练）单独在顶部给一张卡片。
        val standaloneUnfinished = unfinished?.takeIf { session ->
            routines.none { it.routine.id == session.routineId }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        Column {
                            Text(text = stringResource(R.string.today_title))
                            Text(
                                text = stringResource(
                                    R.string.today_date,
                                    // Month 枚举从 JANUARY(0) 开始，换算成 1..12
                                    date.month.ordinal + 1,
                                    date.day,
                                    weekdayNames[date.dayOfWeek.isoDayNumber - 1],
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                routines.isNotEmpty() || standaloneUnfinished != null -> {
                    LazyColumn(
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        standaloneUnfinished?.let { session ->
                            item(key = "unfinished") {
                                UnfinishedSessionCard(
                                    session = session,
                                    onClickResume = { navigator.push(WorkoutLogScreen(sessionId = session.id)) },
                                )
                            }
                        }

                        items(routines, key = { it.routine.id }) { scheduled ->
                            val resumable = unfinished?.takeIf { it.routineId == scheduled.routine.id }
                            ScheduledRoutineCard(
                                scheduled = scheduled,
                                isResuming = resumable != null,
                                onClick = {
                                    if (resumable != null) {
                                        navigator.push(WorkoutLogScreen(sessionId = resumable.id))
                                    } else {
                                        navigator.push(WorkoutLogScreen(routineId = scheduled.routine.id))
                                    }
                                },
                            )
                        }
                    }
                }

                loaded -> EmptyScreen(
                    message = stringResource(R.string.today_empty),
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@Composable
private fun ScheduledRoutineCard(
    scheduled: ScheduledRoutine,
    isResuming: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = scheduled.routine.name,
                style = MaterialTheme.typography.titleMedium,
            )
            if (scheduled.routine.note.isNotBlank()) {
                Text(
                    text = scheduled.routine.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.today_exercise_count, scheduled.exercises.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            scheduled.exercises.forEach { exercise ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = exercise.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = exercise.targetText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(
                    text = stringResource(
                        if (isResuming) R.string.workout_continue else R.string.workout_start,
                    ),
                )
            }
        }
    }
}

/** 上次没练完就退出的训练，点一下接着练。 */
@Composable
private fun UnfinishedSessionCard(
    session: WorkoutSession,
    onClickResume: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = stringResource(R.string.workout_unfinished),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = session.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.workout_started_at, session.startedAt.toClockText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClickResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.workout_continue))
            }
        }
    }
}
