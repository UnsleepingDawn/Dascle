package com.fitplan.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.alpha
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
import com.fitplan.ui.plan.calendar.DeleteSelectionDialog
import com.fitplan.ui.plan.calendar.InsertDialog
import com.fitplan.ui.plan.calendar.InsertRestDialog
import com.fitplan.ui.plan.calendar.PlanCalendarScreenModel
import com.fitplan.ui.plan.calendar.RoutineListScreen
import com.fitplan.ui.plan.calendar.RoutinePickerDialog
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

        // 编辑模式：长按未来日子进入。勾选按日期保存，所以切到别的月份继续勾也不丢。
        var editing by remember { mutableStateOf(false) }
        // 值表示这一天有没有排期，删除弹窗按「有排期」的天数选用单日还是多日措辞。
        var selection by remember { mutableStateOf<Map<LocalDate, Boolean>>(emptyMap()) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showInsertDialog by remember { mutableStateOf(false) }
        var showInsertRestDialog by remember { mutableStateOf(false) }
        var routinePickerTarget by remember { mutableStateOf<RoutinePickerTarget?>(null) }

        val singleSelected = selection.keys.singleOrNull()
        val plannedSelectedCount = selection.values.count { it }

        fun exitEdit() {
            editing = false
            selection = emptyMap()
        }

        fun toggleSelection(day: CalendarDay) {
            selection = if (selection.containsKey(day.date)) {
                selection - day.date
            } else {
                selection + (day.date to day.planned.isNotEmpty())
            }
        }

        // 编辑模式里按返回键先退出编辑，而不是直接切走 Tab。
        BackHandler(enabled = editing) { exitEdit() }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        Text(
                            text = if (editing) {
                                stringResource(R.string.calendar_edit_selected, selection.size)
                            } else {
                                stringResource(R.string.calendar_title)
                            },
                        )
                    },
                    navigationIcon = {
                        if (editing) {
                            Row {
                                // 全选是合并语义：把本月可选的日子并进已有勾选，跨月的勾选不丢。
                                TextButton(
                                    onClick = {
                                        selection = selection + days
                                            .filter(::isEditableDate)
                                            .associate { it.date to it.planned.isNotEmpty() }
                                    },
                                ) {
                                    Text(text = stringResource(R.string.calendar_edit_select_all))
                                }
                                TextButton(
                                    onClick = { selection = emptyMap() },
                                    enabled = selection.isNotEmpty(),
                                ) {
                                    Text(text = stringResource(R.string.calendar_edit_clear_all))
                                }
                            }
                        }
                    },
                    actions = {
                        if (editing) {
                            TextButton(onClick = { exitEdit() }) {
                                Text(text = stringResource(R.string.calendar_edit_done))
                            }
                        } else {
                            // 两个入口摆在「训练日历」右侧，日历本身占满整个页面。
                            CalendarEntryButton(
                                text = stringResource(R.string.calendar_entry_routine_list),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { navigator.push(RoutineListScreen) },
                            )
                            CalendarEntryButton(
                                text = stringResource(R.string.calendar_entry_compose),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { navigator.push(PlanComposeScreen) },
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
                    editing = editing,
                    selection = selection,
                    modifier = Modifier.weight(1f),
                    onDayClick = { day ->
                        when {
                            !editing -> selectedDate = if (selectedDate == day.date) null else day.date
                            isEditableDate(day) -> toggleSelection(day)
                        }
                    },
                    onDayLongClick = { day ->
                        when {
                            !isEditableDate(day) -> Unit
                            editing -> toggleSelection(day)
                            else -> {
                                editing = true
                                selection = mapOf(day.date to day.planned.isNotEmpty())
                                selectedDate = null
                            }
                        }
                    },
                )
                if (editing) {
                    EditActionBar(
                        insertEnabled = singleSelected != null,
                        planEnabled = singleSelected != null && selection[singleSelected] == false,
                        // 选中的全是休息日也能删：那种情况下删除会把后续排期整体提前。
                        deleteEnabled = selection.isNotEmpty(),
                        onInsert = { showInsertDialog = true },
                        onPlan = { routinePickerTarget = RoutinePickerTarget.ADD_PLAN },
                        onDelete = { showDeleteDialog = true },
                    )
                }
            }
        }

        // 编辑模式下不再弹日明细，避免两种交互叠在一起。
        if (!editing) {
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
                    onEditSession = { sessionId ->
                        selectedDate = null
                        navigator.push(WorkoutLogScreen(sessionId = sessionId, editing = true))
                    },
                    onDeleteSession = { session -> deleteSessionTarget = session },
                )
            }
        }

        if (showDeleteDialog) {
            DeleteSelectionDialog(
                plannedCount = plannedSelectedCount,
                selectedCount = selection.size,
                onRest = {
                    screenModel.removePlansOn(selection.keys.toList())
                    showDeleteDialog = false
                    selection = emptyMap()
                },
                onFlow = {
                    screenModel.compactPlansAfterRemoving(selection.keys.toList())
                    showDeleteDialog = false
                    selection = emptyMap()
                },
                onRemoveRests = {
                    screenModel.removeRestDaysAndCompact(selection.keys.toList())
                    showDeleteDialog = false
                    selection = emptyMap()
                },
                onDismiss = { showDeleteDialog = false },
            )
        }

        if (showInsertDialog) {
            InsertDialog(
                onRest = {
                    showInsertDialog = false
                    showInsertRestDialog = true
                },
                onTraining = {
                    showInsertDialog = false
                    routinePickerTarget = RoutinePickerTarget.INSERT_PLAN
                },
                onDismiss = { showInsertDialog = false },
            )
        }

        if (showInsertRestDialog) {
            InsertRestDialog(
                onConfirm = { count ->
                    singleSelected?.let { date -> screenModel.insertRestDaysBefore(date, count) }
                    showInsertRestDialog = false
                    selection = emptyMap()
                },
                onDismiss = { showInsertRestDialog = false },
            )
        }

        routinePickerTarget?.let { target ->
            RoutinePickerDialog(
                title = stringResource(
                    when (target) {
                        RoutinePickerTarget.ADD_PLAN -> R.string.calendar_edit_plan_title
                        RoutinePickerTarget.INSERT_PLAN -> R.string.calendar_pick_routine
                    },
                ),
                routines = routines,
                onPick = { routineId ->
                    singleSelected?.let { date ->
                        when (target) {
                            RoutinePickerTarget.ADD_PLAN -> screenModel.addPlan(routineId, date)
                            RoutinePickerTarget.INSERT_PLAN -> screenModel.insertPlanBefore(routineId, date)
                        }
                    }
                    routinePickerTarget = null
                    selection = emptyMap()
                },
                onDismiss = { routinePickerTarget = null },
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

/**
 * 训练日历右上角的入口按钮：和「训练日历」标题排在同一条顶栏上。
 *
 * 顶栏空间有限，所以把左右内边距收到 8dp，让两个入口能跟标题挤进一行而不被截断。
 */
@Composable
private fun CalendarEntryButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.padding.small,
            vertical = MaterialTheme.padding.extraSmall,
        ),
        modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
    ) {
        Text(text = text, maxLines = 1)
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
    editing: Boolean,
    selection: Map<LocalDate, Boolean>,
    modifier: Modifier = Modifier,
    onDayClick: (CalendarDay) -> Unit,
    onDayLongClick: (CalendarDay) -> Unit,
) {
    if (days.isEmpty()) return

    val leadingBlanks = days.first().date.dayOfWeek.isoDayNumber - 1
    val cells = buildList<CalendarDay?> {
        repeat(leadingBlanks) { add(null) }
        addAll(days)
        while (size % 7 != 0) add(null)
    }

    Column(
        modifier = modifier
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
                            editing = editing,
                            selected = day.date in selection,
                            selectable = isEditableDate(day),
                            onClick = { onDayClick(day) },
                            onLongClick = { onDayLongClick(day) },
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
    editing: Boolean,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // 编辑模式下「今天 / 过去」不可选，压暗一档，一眼看出哪些能接着勾。
            .alpha(if (editing && !selectable) DISABLED_CELL_ALPHA else 1f)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = MaterialTheme.padding.extraSmall, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 训练日的日期用蓝色（过去压淡一档 primaryContainer，今天及以后用饱和的 primary，
        // 一眼分出「历史」和「待办」）；休息日改用中性底色，让训练日更醒目。
        val (dayNumberBackground, dayNumberColor) = if (day.isTrainingDay) {
            if (day.isPast) {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            }
        } else {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
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
                    when {
                        // 选中比「今天」的描边更醒目，两者同时出现时以选中为准。
                        // 训练日的日期底色本就是蓝色，描边必须换成深黑（与肌群标签同色）才看得见；
                        // 休息日底色是中性的，保留蓝色描边即可。
                        selected -> Modifier.border(
                            width = 2.dp,
                            color = if (day.isTrainingDay) {
                                MaterialTheme.colorScheme.inverseSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        day.isToday -> Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        else -> Modifier
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

        // 休息日没有肌群标签，只在格子里给一行「休息」；过去的日子用灰底灰字，今天及以后保持深绿。
        if (day.isRestDay) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
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

                // 一天非训练即休息：练过就列「实际训练」，今天及以后排了计划就列「当天计划」，
                // 两者都没有（含过去只剩遗留排期的日子）就是休息日。过去的日子不再「规划」，
                // 遗留排期也不在这里显示。
                if (day.actualSessions.isNotEmpty()) {
                    SectionTitle(text = stringResource(R.string.calendar_day_actual))
                    day.actualSessions.forEach { session ->
                        ActualSessionRow(
                            session = session,
                            onEdit = { onEditSession(session.sessionId) },
                            onDelete = { onDeleteSession(session) },
                        )
                    }
                } else if (!day.isPast && day.planned.isNotEmpty()) {
                    SectionTitle(text = stringResource(R.string.calendar_day_plan))
                    day.planned.forEach { plan ->
                        PlannedRoutineRow(
                            plan = plan,
                            onClick = { onOpenRoutine(plan.routineId) },
                            onRemove = { onRemovePlan(plan.entryId) },
                        )
                    }
                } else {
                    RestDayRow()
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

/** 休息日：这一天既没有排期也没有训练记录，读出来一眼能认。 */
@Composable
private fun RestDayRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行一个底框（用主题里的休息色，和右下角「该天休息」按钮呼应）。
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.calendar_rest_day),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(
                start = MaterialTheme.padding.small,
                top = MaterialTheme.padding.extraSmall,
                bottom = MaterialTheme.padding.extraSmall,
            ),
        )
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
 * 明细面板右下角的两个快捷操作：给这一天排一个计划，或把这一天改成休息日。
 * 两者互斥——加计划会让这一天成为训练日，标休息会撤掉这一天的排期。
 *
 * 「添加计划」只在既没有排期、也没有实际训练的空白天可用（一天只能有一个训练计划）；
 * 「该天休息」只在今天及以后、这天排了计划且还没练过时可用（撤掉排期即可）。
 */
@Composable
private fun DayActionsRow(
    day: CalendarDay,
    routines: List<Routine>,
    onAddPlan: (Long) -> Unit,
    onMarkRest: () -> Unit,
) {
    // 一天只能有一个计划：这天已经有排期、或者已经练过了，就不再给「添加计划」入口，
    // 用户只能去改已有排期或改当天已记录的训练。
    // 过去的日子明细里只列「实际训练」，遗留排期看不见，所以不能拿它挡住补记。
    val canAddPlan = if (day.isPast) {
        day.actualSessions.isEmpty()
    } else {
        day.planned.isEmpty() && day.actualSessions.isEmpty()
    }
    // 休息日是被推导出来的：这一天没有排期（或已经练过）时本来就不是训练日，
    // 「该天休息」只在今天及以后排了计划、且还没开练时才有意义（撤掉排期它就变成休息日）。
    val canMarkRest = !day.isPast && day.planned.isNotEmpty() && day.actualSessions.isEmpty()
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { showPicker = true },
            enabled = canAddPlan,
            // 深蓝实底（同编辑模式的「插入 / 添加计划」），比原来的浅蓝容器更醒目。
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
            // 已经休息的日子不再给这个入口；要变成训练日，用「添加计划」排一个。
            enabled = canMarkRest,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Text(text = stringResource(R.string.calendar_mark_rest))
        }
    }

    if (showPicker) {
        RoutinePickerDialog(
            title = stringResource(R.string.calendar_pick_routine),
            routines = routines,
            onPick = { routineId ->
                showPicker = false
                onAddPlan(routineId)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** 日历编辑模式底部的三个动作：插入（单一日子）、计划（单一休息日）、删除（至少一天有排期）。 */
@Composable
private fun EditActionBar(
    insertEnabled: Boolean,
    planEnabled: Boolean,
    deleteEnabled: Boolean,
    onInsert: () -> Unit,
    onPlan: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onInsert,
            enabled = insertEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.calendar_edit_insert))
        }
        Button(
            onClick = onPlan,
            enabled = planEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.calendar_add_plan))
        }
        Button(
            onClick = onDelete,
            enabled = deleteEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(text = stringResource(R.string.calendar_edit_delete))
        }
    }
}

/** 编辑模式只能选「今天之后」的未来日子：今天和过去都不可选。 */
private fun isEditableDate(day: CalendarDay): Boolean = !day.isPast && !day.isToday

/** 「计划 / 插入 → 训练计划」共用一个计划选择弹窗，用它区分选中之后干什么。 */
private enum class RoutinePickerTarget { ADD_PLAN, INSERT_PLAN }

/** 弹窗内容最高占这么高，再高就滚动，避免小屏顶到状态栏。 */
private val SHEET_MAX_HEIGHT = 480.dp

/** 日历格子最多显示几个肌群标签，多出来的用「+N」表示。 */
private const val MAX_CELL_MUSCLES = 3

/** 编辑模式下不可选的日子（今天与过去）压暗到这个透明度。 */
private const val DISABLED_CELL_ALPHA = 0.38f

@Suppress("ConstPropertyName")
private const val TABLET_UI_MIN_SCREEN_WIDTH_DP = 600
