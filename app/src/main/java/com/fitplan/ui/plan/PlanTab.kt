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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.key
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
import com.fitplan.ui.plan.compose.PlanComposeScreen
import com.fitplan.ui.plan.routine.RoutineEditScreen
import com.fitplan.ui.workout.WorkoutLogScreen
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
        var deleteSessionTarget by remember { mutableStateOf<CalendarSession?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.calendar_title)) },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    ExtendedFloatingActionButton(
                        onClick = { navigator.push(RoutineListScreen) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(text = stringResource(R.string.plan_title))
                    }
                    ExtendedFloatingActionButton(
                        onClick = { navigator.push(PlanComposeScreen) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(text = stringResource(R.string.plan_compose_entry))
                    }
                }
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
                    onDayClick = { day -> selectedDate = if (selectedDate == day.date) null else day.date },
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
                onAddPlan = { routineId -> screenModel.addPlan(routineId, day.date) },
                onMarkRest = screenModel::markRestDay,
                onRemovePlan = screenModel::removePlan,
                onRemoveRest = screenModel::removeRestDay,
                onEditSession = { sessionId ->
                    selectedDate = null
                    navigator.push(WorkoutLogScreen(sessionId = sessionId, editing = true))
                },
                onDeleteSession = { session -> deleteSessionTarget = session },
            )
        }

        deleteSessionTarget?.let { session ->
            DeleteSessionDialog(
                sessionName = session.name.ifBlank { stringResource(R.string.workout_free) },
                onConfirm = {
                    screenModel.deleteSession(session.sessionId)
                    deleteSessionTarget = null
                },
                onDismiss = { deleteSessionTarget = null },
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
                style = MaterialTheme.typography.labelLarge,
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
        // 训练日与休息日共用同一套蓝色，是不是休息由格子里那行「休息」说明，不再靠颜色区分。
        // 已经过去的日子压淡一档（primaryContainer），今天及以后用饱和的 primary，
        // 这样仍然能一眼分出「历史」和「待办」。
        val (dayNumberBackground, dayNumberColor) = when {
            day.isTrainingDay || day.isRestDay ->
                if (day.isPast) {
                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                }

            else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
        }
        // 肌群标签：过去的用浅底，今天及以后用反色深底。过去的休息日沿用这一套灰。
        val muscleBackground =
            if (day.isPast) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.inverseSurface
        val muscleColor =
            if (day.isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.inverseOnSurface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(dayNumberBackground)
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
                style = MaterialTheme.typography.titleSmall,
                color = dayNumberColor,
            )
        }

        if (day.isRestDay && !day.isTrainingDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    // 过去的休息日跟肌群标签同为灰色；今天及以后的休息日保持原来的深绿。
                    .background(if (day.isPast) muscleBackground else MaterialTheme.colorScheme.tertiary)
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.calendar_rest_day),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.isPast) muscleColor else MaterialTheme.colorScheme.onTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        day.muscleGroups.take(MAX_CELL_MUSCLES).forEach { muscle ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(muscleBackground)
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = muscle.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = muscleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val hiddenMuscles = day.muscleGroups.size - MAX_CELL_MUSCLES
        if (hiddenMuscles > 0) {
            Text(
                text = stringResource(R.string.calendar_more_muscles, hiddenMuscles),
                style = MaterialTheme.typography.labelMedium,
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
    onAddPlan: (Long) -> Unit,
    onMarkRest: (LocalDate) -> Unit,
    onRemovePlan: (Long) -> Unit,
    onRemoveRest: (LocalDate) -> Unit,
    onEditSession: (Long) -> Unit,
    onDeleteSession: (CalendarSession) -> Unit,
) {
    val isTabletUi = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_UI_MIN_SCREEN_WIDTH_DP
    val weekdayNames = stringArrayResource(R.array.weekday_names)

    AdaptiveSheet(
        isTabletUi = isTabletUi,
        enableImplicitDismiss = true,
        onDismissRequest = onDismiss,
        // 遮罩不拦点击：这样在面板打开时点日历上的另一天，能直接切换到那一天的详情。
        dismissOnOutsideClick = false,
    ) {
        key(day.date) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SHEET_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.today_date,
                            day.date.month.ordinal + 1,
                            day.date.day,
                            weekdayNames[day.date.dayOfWeek.isoDayNumber - 1],
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }

                // 过去的日子不可能再「规划」，所以只呈现那天到底练没练；休息标记是那天的事实，
                // 仍留在这里（带取消入口），否则过去标过的休息日就没法撤掉了。
                if (day.isPast) {
                    SectionTitle(text = stringResource(R.string.calendar_day_actual))
                    if (day.actualSessions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.calendar_day_no_actual),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        day.actualSessions.forEach { session ->
                            ActualSessionRow(
                                session = session,
                                onEdit = { onEditSession(session.sessionId) },
                                onDelete = { onDeleteSession(session) },
                            )
                        }
                    }
                    if (day.isRestDay) {
                        RestDayRow(onRemove = { onRemoveRest(day.date) })
                    }
                } else {
                    if (day.actualSessions.isNotEmpty()) {
                        SectionTitle(text = stringResource(R.string.calendar_day_actual))
                        day.actualSessions.forEach { session ->
                            ActualSessionRow(
                                session = session,
                                onEdit = { onEditSession(session.sessionId) },
                                onDelete = { onDeleteSession(session) },
                            )
                        }
                    }

                    SectionTitle(text = stringResource(R.string.calendar_day_plan))
                    if (day.isRestDay) {
                        RestDayRow(onRemove = { onRemoveRest(day.date) })
                    }
                    if (day.planned.isEmpty()) {
                        if (!day.isRestDay) {
                            Text(
                                text = stringResource(R.string.calendar_day_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        day.planned.forEach { plan ->
                            PlannedRoutineRow(
                                plan = plan,
                                onClick = { onOpenRoutine(plan.routineId) },
                                onRemove = { onRemovePlan(plan.entryId) },
                            )
                        }
                    }
                }

                DayActionsRow(
                    day = day,
                    routines = routines,
                    onAddPlan = onAddPlan,
                    onMarkRest = { onMarkRest(day.date) },
                )
            }
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

/** 编排出来的休息日：读出来一眼能认，也留一个撤销入口。 */
@Composable
private fun RestDayRow(onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行一个底框（用主题里的休息色，和右下角「该天休息」按钮呼应），
            // 右侧的取消按钮也一起包进来，三个条目的形状才统一。
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calendar_rest_day),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.calendar_remove_rest),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ActualSessionRow(
    session: CalendarSession,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与「当天计划」条目同形状的底框，用肌群标签那套灰表示已经练完的记录；
            // 底框连右侧的编辑与删除一起包住。
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
        ) {
            Text(
                text = session.name.ifBlank { stringResource(R.string.workout_free) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.calendar_completed_sets, session.completedSets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.calendar_edit_session),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 删除统一用叉号，和「当天计划」条目上的移除排期一致。
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.calendar_delete_session),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 删除一次训练前的确认：连带所有组一起删掉，避免误触。 */
@Composable
private fun DeleteSessionDialog(
    sessionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.calendar_delete_session_title)) },
        text = { Text(text = stringResource(R.string.calendar_delete_session_message, sessionName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun PlannedRoutineRow(
    plan: CalendarPlan,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 与「休息」条目同形状的底框，配色对齐编排页「训练」按钮（primaryContainer）；
            // 底框连右侧的移除排期一起包住。
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(
                    start = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
        ) {
            Text(
                text = plan.routineName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.calendar_exercise_count, plan.exerciseCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.calendar_remove_plan),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * 明细面板右下角的两个快捷操作：给这一天排一个计划，或把这一天标成休息日。
 * 两者互斥——加计划会撤掉休息标记，标休息会撤掉这一天的排期。
 */
@Composable
private fun DayActionsRow(
    day: CalendarDay,
    routines: List<Routine>,
    onAddPlan: (Long) -> Unit,
    onMarkRest: () -> Unit,
) {
    // 今天及以后按排期去重；过去的日子加计划等于补记训练，只按已补记的训练去重——
    // 那些日子遗留的排期在明细里已经看不到了，不能再用它把计划挡在候选之外。
    val listedIds = if (day.isPast) {
        day.actualSessions.mapNotNull { it.routineId }.toSet()
    } else {
        day.planned.map { it.routineId }.toSet()
    }
    val selectable = routines.filterNot { it.id in listedIds }
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { showPicker = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
            )
            Text(text = stringResource(R.string.calendar_add_plan))
        }
        Button(
            onClick = onMarkRest,
            // 已经是休息日就不再重复标；要取消休息，用上面「休息」条目上的 ×。
            enabled = !day.isRestDay,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Text(text = stringResource(R.string.calendar_mark_rest))
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
                                        onAddPlan(routine.id)
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
}

/** 弹窗内容最高占这么高，再高就滚动，避免小屏顶到状态栏。 */
private val SHEET_MAX_HEIGHT = 480.dp

/** 日历格子最多显示几个肌群标签，多出来的用「+N」表示。 */
private const val MAX_CELL_MUSCLES = 3

@Suppress("ConstPropertyName")
private const val TABLET_UI_MIN_SCREEN_WIDTH_DP = 600
