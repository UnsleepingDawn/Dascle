package com.fitplan.ui.plan.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.ScheduleEntry
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/** 用「星期 × 计划」的网格给计划排期，只支持每周循环的排法。 */
object ScheduleScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<ScheduleScreenModel>()
        val entries by screenModel.entries.collectAsState()

        LaunchedEffect(Unit) { screenModel.load() }

        var pickDay by remember { mutableStateOf<DayOfWeek?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.schedule_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            val weekdayNames = stringArrayResource(R.array.weekday_names)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(DayOfWeek.entries) { day ->
                    DayScheduleCard(
                        dayLabel = weekdayNames[day.isoDayNumber - 1],
                        entries = entries.filter { it.dayOfWeek == day },
                        onClickAdd = { pickDay = day },
                        onClickRemove = { screenModel.remove(it.id) },
                    )
                }
            }
        }

        pickDay?.let { day ->
            val selectable = screenModel.selectableRoutines(day)
            AlertDialog(
                onDismissRequest = { pickDay = null },
                title = { Text(text = stringResource(R.string.schedule_pick_routine)) },
                text = {
                    if (selectable.isEmpty()) {
                        Text(text = stringResource(R.string.schedule_no_routine_left))
                    } else {
                        Column {
                            selectable.forEach { routine ->
                                Text(
                                    text = routine.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            screenModel.addWeekly(routine.id, day)
                                            pickDay = null
                                        }
                                        .padding(vertical = MaterialTheme.padding.small),
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pickDay = null }) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun DayScheduleCard(
    dayLabel: String,
    entries: List<ScheduleEntry>,
    onClickAdd: () -> Unit,
    onClickRemove: (ScheduleEntry) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleMedium,
            )

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.routineName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onClickRemove(entry) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.schedule_remove),
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onClickAdd,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.schedule_add_for_day))
            }
        }
    }
}
