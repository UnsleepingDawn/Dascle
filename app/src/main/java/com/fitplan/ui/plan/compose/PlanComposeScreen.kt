package com.fitplan.ui.plan.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.Routine
import com.fitplan.presentation.core.components.LabeledCheckbox
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.util.Screen
import com.fitplan.ui.exercise.muscleLabels
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 编排健身计划：自上而下是 Day 1 / Day 2 … 的格子，每格安排一个训练计划或休息日，
 * 点顶部「应用」把这套编排按「循环次数」或「截止到」批量铺到日历上。
 */
object PlanComposeScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<PlanComposeScreenModel>()
        val slots by screenModel.slots.collectAsState()
        val routines by screenModel.routines.collectAsState()

        // 编排只在内存里，每次进来都从「只有一个空白格」开始。
        LaunchedEffect(Unit) {
            screenModel.reset()
            screenModel.load()
        }

        var editIndex by remember { mutableStateOf<Int?>(null) }
        var showApply by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val cycleLength = composeCycleLength(slots)

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.plan_compose_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showApply = true },
                            enabled = cycleLength > 0,
                        ) {
                            Text(text = stringResource(R.string.plan_compose_apply))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                itemsIndexed(slots) { index, slot ->
                    ComposeSlotCard(
                        slot = slot,
                        dayIndex = index + 1,
                        onClick = { editIndex = index },
                    )
                }
                if (cycleLength == 0) {
                    item {
                        Text(
                            text = stringResource(R.string.plan_compose_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        editIndex?.let { index ->
            SlotPickerDialog(
                dayIndex = index + 1,
                slot = slots.getOrNull(index),
                routines = routines,
                onDismiss = { editIndex = null },
                onPickRoutine = { routine ->
                    screenModel.setTraining(index, routine)
                    editIndex = null
                },
                onRest = {
                    screenModel.setRest(index)
                    editIndex = null
                },
                onClear = {
                    screenModel.clearSlot(index)
                    editIndex = null
                },
            )
        }

        if (showApply) {
            ApplyDialog(
                cycleLength = cycleLength,
                onDismiss = { showApply = false },
                onConfirm = { startDate, cycles, until ->
                    showApply = false
                    // 写完再 pop：编排页的 ViewModel 会随 pop 一起被清理，提前离开会打断写库。
                    scope.launch {
                        screenModel.apply(startDate = startDate, cycles = cycles, until = until)
                        navigator.pop()
                    }
                },
            )
        }
    }
}

@Composable
private fun ComposeSlotCard(
    slot: ComposeSlot,
    dayIndex: Int,
    onClick: () -> Unit,
) {
    val containerColor: Color
    val outlineColor: Color
    val contentColor: Color
    when {
        slot.isRest -> {
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
            outlineColor = MaterialTheme.colorScheme.tertiary
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        }

        slot.isEmpty -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            outlineColor = MaterialTheme.colorScheme.outline
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        }

        else -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            outlineColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    // Day 标签放在卡片左侧，跟着卡片状态走色，一眼能对上「Day N 是哪一格」。
    val dayNumberColor = when {
        slot.isRest -> MaterialTheme.colorScheme.tertiary
        slot.isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(DAY_LABEL_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.plan_compose_day_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dayIndex.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = dayNumberColor,
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(SLOT_HEIGHT)
                .clip(shape)
                .background(containerColor)
                .then(
                    if (slot.isEmpty) {
                        Modifier.dashedBorder(color = outlineColor, shape = shape, width = SLOT_BORDER_WIDTH)
                    } else {
                        Modifier.border(SLOT_BORDER_WIDTH, outlineColor, shape)
                    },
                )
                .clickable(onClick = onClick)
                .padding(MaterialTheme.padding.medium),
            contentAlignment = Alignment.Center,
        ) {
            when {
                slot.isRest -> Text(
                    text = stringResource(R.string.plan_compose_rest),
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                )

                slot.isEmpty -> Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(SLOT_ADD_ICON_SIZE),
                )

                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = slot.routineName,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val muscles = muscleLabels(slot.muscleGroups)
                    if (muscles.isNotEmpty()) {
                        Text(
                            text = muscles,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 点某一格后的弹窗：底部「训练」（淡蓝）与「休息」（淡绿）两个按钮；
 * 选了训练再展开计划列表勾选一个保存。已经填过的格子还能整格移除。
 */
@Composable
private fun SlotPickerDialog(
    dayIndex: Int,
    slot: ComposeSlot?,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onPickRoutine: (Routine) -> Unit,
    onRest: () -> Unit,
    onClear: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(slot?.routineId) }

    val picked = routines.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_compose_pick_title, dayIndex)) },
        text = {
            if (!picking) {
                Text(text = stringResource(R.string.plan_compose_pick_hint))
            } else if (routines.isEmpty()) {
                Text(text = stringResource(R.string.plan_compose_pick_empty))
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = PICKER_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    routines.forEach { routine ->
                        LabeledCheckbox(
                            label = routine.name,
                            checked = routine.id == selectedId,
                            onCheckedChange = { selectedId = routine.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (picking) {
                TextButton(
                    enabled = picked != null,
                    onClick = { picked?.let(onPickRoutine) },
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Button(
                        onClick = { picking = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(text = stringResource(R.string.plan_compose_training))
                    }
                    Button(
                        onClick = onRest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    ) {
                        Text(text = stringResource(R.string.plan_compose_rest))
                    }
                }
            }
        },
        dismissButton = {
            if (picking) {
                TextButton(onClick = { picking = false }) {
                    Text(text = stringResource(R.string.action_back))
                }
            } else {
                Row {
                    if (slot != null && !slot.isEmpty) {
                        TextButton(onClick = onClear) {
                            Text(text = stringResource(R.string.plan_compose_remove_day))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                }
            }
        },
    )
}

/** 点顶部「应用」后的弹窗：选起始日期，再选「循环次数」或「截止到」。 */
@Composable
private fun ApplyDialog(
    cycleLength: Int,
    onDismiss: () -> Unit,
    onConfirm: (startDate: LocalDate, cycles: Int?, until: LocalDate?) -> Unit,
) {
    val today = remember { today() }
    var startDate by remember { mutableStateOf(today) }
    var mode by remember { mutableStateOf(ApplyMode.CYCLES) }
    var cyclesText by remember { mutableStateOf(DEFAULT_CYCLES.toString()) }
    var until by remember { mutableStateOf(today.plus(cycleLength * DEFAULT_CYCLES - 1, DateTimeUnit.DAY)) }
    var picking by remember { mutableStateOf<DateField?>(null) }

    val cycles = cyclesText.toIntOrNull()?.takeIf { it > 0 }
    val pickedCycles = if (mode == ApplyMode.CYCLES) cycles else null
    val pickedUntil = if (mode == ApplyMode.UNTIL) until else null
    val untilValid = mode != ApplyMode.UNTIL || until >= startDate
    val days = composePlanDays(
        cycleLength = cycleLength,
        startDate = startDate,
        cycles = pickedCycles,
        until = pickedUntil,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_compose_apply_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(
                    text = stringResource(R.string.plan_compose_cycle_days, cycleLength),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateRow(
                    label = stringResource(R.string.plan_compose_start_date),
                    date = startDate,
                    onClick = { picking = DateField.START },
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == ApplyMode.CYCLES,
                        onClick = { mode = ApplyMode.CYCLES },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text(text = stringResource(R.string.plan_compose_mode_cycles)) },
                    )
                    SegmentedButton(
                        selected = mode == ApplyMode.UNTIL,
                        onClick = { mode = ApplyMode.UNTIL },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text(text = stringResource(R.string.plan_compose_mode_until)) },
                    )
                }
                if (mode == ApplyMode.CYCLES) {
                    OutlinedTextField(
                        value = cyclesText,
                        onValueChange = { cyclesText = it.filter(Char::isDigit).take(2) },
                        label = { Text(text = stringResource(R.string.plan_compose_cycles_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DateRow(
                        label = stringResource(R.string.plan_compose_mode_until),
                        date = until,
                        onClick = { picking = DateField.UNTIL },
                    )
                }
                Text(
                    text = stringResource(R.string.plan_compose_total_days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!untilValid) {
                    Text(
                        text = stringResource(R.string.plan_compose_until_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days > 0 && untilValid,
                onClick = { onConfirm(startDate, pickedCycles, pickedUntil) },
            ) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )

    picking?.let { field ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (field == DateField.START) startDate else until).toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = millis.toUtcDate()
                            if (field == DateField.START) startDate = date else until = date
                        }
                        picking = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun DateRow(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = date.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 虚线圆角描边：`Modifier.border` 只画实线，这里自己按 path 描一遍虚线。 */
private fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    width: Dp,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    if (strokeWidth <= 0f || size.width <= strokeWidth || size.height <= strokeWidth) return@drawBehind
    val outline = shape.createOutline(
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        layoutDirection = layoutDirection,
        density = this,
    )
    val path = Path().apply {
        addOutline(outline)
        translate(Offset(strokeWidth / 2f, strokeWidth / 2f))
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_LENGTH.toPx(), DASH_GAP.toPx())),
        ),
    )
}

private enum class ApplyMode { CYCLES, UNTIL }

private enum class DateField { START, UNTIL }

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

/** 格子高度：够放下居中的计划名与肌群两行。 */
private val SLOT_HEIGHT = 96.dp

/** 左侧 Day 标签占的宽度（含与卡片之间的间隙）。 */
private val DAY_LABEL_WIDTH = 52.dp

private val SLOT_BORDER_WIDTH = 1.5.dp

private val SLOT_ADD_ICON_SIZE = 40.dp

private val DASH_LENGTH = 8.dp

private val DASH_GAP = 6.dp

/** 计划勾选列表最高占这么高，再多就滚动，避免小屏顶到状态栏。 */
private val PICKER_MAX_HEIGHT = 320.dp

private const val DEFAULT_CYCLES = 4
