package com.fitplan.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.domain.interactor.CalendarDay
import com.fitplan.domain.interactor.CalendarPlan
import com.fitplan.domain.interactor.CalendarSession
import com.fitplan.domain.model.Routine
import com.fitplan.presentation.core.components.AdaptiveSheet
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.exercise.label
import com.fitplan.ui.plan.calendar.PlanCalendarScreenModel
import com.fitplan.ui.plan.calendar.RoutineListScreen
import com.fitplan.ui.plan.routine.RoutineEditScreen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** 按月查看训练安排：过去看实际练到的肌群，今天及以后看排期计划，点某天看当天明细。 */
object PlanTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.tab_plan),
            icon = rememberVectorPainter(Icons.Filled.FitnessCenter),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<PlanCalendarScreenModel>()
        val month by screenModel.month.collectAsState()
        val days by screenModel.days.collectAsState()
        val routines by screenModel.routines.collectAsState()

        LaunchedEffect(Unit) { screenModel.refresh() }

        var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
        val selectedDay = selectedDate?.let { date -> days.firstOrNull { it.date == date } }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.calendar_title)) },
                    actions = {
                        IconButton(onClick = { navigator.push(RoutineListScreen) }) {
                            Icon(
                                imageVector = Icons.Filled.FitnessCenter,
                                contentDescription = stringResource(R.string.plan_title),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                MonthSwitcher(
                    month = month,
                    onPrevious = screenModel::showPreviousMonth,
                    onNext = screenModel::showNextMonth,
                    onCurrent = screenModel::showCurrentMonth,
                )
                WeekdayHeader()
                MonthGrid(
                    days = days,
                    onDayClick = { selectedDate = it.date },
                )
            }
        }

        selectedDay?.let { day ->
            DayDetailSheet(
                day = day,
                routines = routines,
                onDismiss = { selectedDate = null },
                onOpenRoutine = { routineId ->
                    selectedDate = null
                    navigator.push(RoutineEditScreen(routineId))
                },
                onAddPlan = { routineId, weekly -> screenModel.addPlan(routineId, day.date, weekly) },
                onRemovePlan = screenModel::removePlan,
            )
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = stringResource(R.string.calendar_prev_month),
            )
        }
        Text(
            text = stringResource(R.string.calendar_month, month.year, month.month.ordinal + 1),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
        TextButton(onClick = onCurrent) {
            Text(text = stringResource(R.string.calendar_back_to_month))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val names = stringArrayResource(R.array.weekday_short_names)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.small),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    onDayClick: (CalendarDay) -> Unit,
) {
    if (days.isEmpty()) return

    val leadingBlanks = days.first().date.dayOfWeek.isoDayNumber - 1
    val cells = buildList<CalendarDay?> {
        repeat(leadingBlanks) { add(null) }
        addAll(days)
        while (size % 7 != 0) add(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.extraSmall),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        DayCell(
                            day = day,
                            onClick = { onDayClick(day) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.extraSmall, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    if (day.isTrainingDay) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                .then(
                    if (day.isToday) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (day.isTrainingDay) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        day.muscleGroups.take(MAX_CELL_MUSCLES).forEach { muscle ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = muscle.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val hiddenMuscles = day.muscleGroups.size - MAX_CELL_MUSCLES
        if (hiddenMuscles > 0) {
            Text(
                text = stringResource(R.string.calendar_more_muscles, hiddenMuscles),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayDetailSheet(
    day: CalendarDay,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onAddPlan: (Long, Boolean) -> Unit,
    onRemovePlan: (Long) -> Unit,
) {
    val isTabletUi = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_UI_MIN_SCREEN_WIDTH_DP
    val weekdayNames = stringArrayResource(R.array.weekday_names)

    AdaptiveSheet(
        isTabletUi = isTabletUi,
        enableImplicitDismiss = true,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(
                    R.string.today_date,
                    day.date.month.ordinal + 1,
                    day.date.day,
                    weekdayNames[day.date.dayOfWeek.isoDayNumber - 1],
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            if (day.actualSessions.isNotEmpty()) {
                SectionTitle(text = stringResource(R.string.calendar_day_actual))
                day.actualSessions.forEach { session -> ActualSessionRow(session = session) }
            }

            SectionTitle(text = stringResource(R.string.calendar_day_plan))
            if (day.planned.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                day.planned.forEach { plan ->
                    PlannedRoutineRow(
                        plan = plan,
                        weekdayShort = weekdayShort(day.date),
                        onClick = { onOpenRoutine(plan.routineId) },
                        onRemove = { onRemovePlan(plan.entryId) },
                    )
                }
            }

            AddPlanRow(
                day = day,
                routines = routines,
                onAddPlan = onAddPlan,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ActualSessionRow(session: CalendarSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.name.ifBlank { stringResource(R.string.workout_free) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.calendar_sets_count, session.completedSets),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlannedRoutineRow(
    plan: CalendarPlan,
    weekdayShort: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
        ) {
            Text(
                text = plan.routineName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val scheduleLabel = if (plan.isWeekly) {
                stringResource(R.string.calendar_weekly, weekdayShort)
            } else {
                stringResource(R.string.calendar_once)
            }
            Text(
                text = "${stringResource(R.string.calendar_exercise_count, plan.exerciseCount)} · $scheduleLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.calendar_remove_plan),
            )
        }
    }
}

@Composable
private fun AddPlanRow(
    day: CalendarDay,
    routines: List<Routine>,
    onAddPlan: (Long, Boolean) -> Unit,
) {
    val plannedIds = day.planned.map { it.routineId }.toSet()
    val selectable = routines.filterNot { it.id in plannedIds }
    val weekdayShort = weekdayShort(day.date)
    var showPicker by remember { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<Routine?>(null) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        TextButton(onClick = { showPicker = true }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
            )
            Text(text = stringResource(R.string.calendar_add_plan))
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(text = stringResource(R.string.calendar_pick_routine)) },
            text = {
                if (selectable.isEmpty()) {
                    Text(text = stringResource(R.string.calendar_no_routine_left))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        selectable.forEach { routine ->
                            Text(
                                text = routine.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPicker = false
                                        pendingRoutine = routine
                                    }
                                    .padding(vertical = MaterialTheme.padding.small),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingRoutine?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingRoutine = null },
            title = { Text(text = stringResource(R.string.calendar_insert_mode_title, routine.name)) },
            text = { Text(text = stringResource(R.string.calendar_insert_mode_hint)) },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            onAddPlan(routine.id, false)
                            pendingRoutine = null
                        },
                    ) {
                        Text(text = stringResource(R.string.calendar_once))
                    }
                    TextButton(
                        onClick = {
                            onAddPlan(routine.id, true)
                            pendingRoutine = null
                        },
                    ) {
                        Text(text = stringResource(R.string.calendar_weekly, weekdayShort))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoutine = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun weekdayShort(date: LocalDate): String =
    stringArrayResource(R.array.weekday_short_names)[date.dayOfWeek.isoDayNumber - 1]

/** 弹窗内容最高占这么高，再高就滚动，避免小屏顶到状态栏。 */
private val SHEET_MAX_HEIGHT = 480.dp

/** 日历格子最多显示几个肌群标签，多出来的用「+N」表示。 */
private const val MAX_CELL_MUSCLES = 3

@Suppress("ConstPropertyName")
private const val TABLET_UI_MIN_SCREEN_WIDTH_DP = 600
