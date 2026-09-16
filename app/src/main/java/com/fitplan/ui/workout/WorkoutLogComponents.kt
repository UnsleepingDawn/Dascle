package com.fitplan.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitplan.app.R
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.ui.exercise.label
import com.fitplan.ui.exercise.muscleLabels
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** 训练还没开始时的计划预览列表：只展示动作与目标，不能录入。 */
@Composable
internal fun WorkoutPlanList(
    items: List<LogItem>,
    exercises: List<LogExercise>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val byId = remember(exercises) { exercises.associateBy { it.exerciseId } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        item {
            Text(
                text = stringResource(R.string.workout_plan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
        }
        items(items, key = { it.key }) { item ->
            when (item) {
                is LogItem.Exercise -> {
                    val exercise = byId[item.exerciseId]
                    if (exercise != null) PlanExerciseCard(exercise)
                }

                // 动作组：预览里也把组内动作列出来，并说明会从里面挑几个练。
                is LogItem.Group -> PlanGroupCard(
                    group = item,
                    members = item.memberIds.mapNotNull(byId::get),
                )
            }
        }
    }
}

@Composable
private fun PlanExerciseCard(exercise: LogExercise) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = exercise.targetHint(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanGroupCard(
    group: LogItem.Group,
    members: List<LogExercise>,
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.workout_group_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.workout_group_picks, group.maxPicks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            members.forEach { exercise ->
                Column {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = exercise.targetHint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 记录页里的一个动作组卡：先在芯片行里挑最多「做其中 x 个」个动作，
 * 挑中的动作在卡片内以子卡展开，记录能力与单独排的动作完全一致。
 *
 * 已经练过（有已完成组）的动作不给取消挑选，免得用户以为记录也跟着没了。
 */
@Composable
internal fun LogGroupCard(
    group: LogItem.Group,
    exercises: List<LogExercise>,
    readOnly: Boolean,
    onTogglePick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    exerciseCard: @Composable (LogExercise) -> Unit,
) {
    val byId = remember(exercises) { exercises.associateBy { it.exerciseId } }
    val picked = group.pickedIds.mapNotNull(byId::get)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.workout_group_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.workout_group_picked, group.pickedIds.size, group.maxPicks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                group.memberIds.forEach { exerciseId ->
                    val exercise = byId[exerciseId] ?: return@forEach
                    val selected = exerciseId in group.pickedIds
                    val alreadyTrained = exercise.completedSets > 0
                    FilterChip(
                        selected = selected,
                        enabled = !readOnly &&
                            (if (selected) !alreadyTrained else group.canPickMore),
                        onClick = { onTogglePick(exerciseId) },
                        label = { Text(text = exercise.name) },
                    )
                }
            }

            if (picked.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_group_pick_hint, group.maxPicks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            picked.forEach { exercise -> exerciseCard(exercise) }
        }
    }
}

/** 记录页里的一个动作卡：[readOnly] 为 true 时只展示、不可改。 */
@Composable
internal fun LogExerciseCard(
    exercise: LogExercise,
    readOnly: Boolean,
    editableCompletedSets: Boolean,
    onWeightChange: (Int, String) -> Unit,
    onRepsChange: (Int, String) -> Unit,
    onSecondsChange: (Int, String) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onToggleSkipped: () -> Unit,
    modifier: Modifier = Modifier,
    /** true 表示这是动作组卡里的子卡：少一层外边距、用更紧凑的圆角。 */
    nested: Boolean = false,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (nested) 0.dp else MaterialTheme.padding.medium),
        shape = if (nested) MaterialTheme.shapes.large else MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            // 动作名一行：右侧依次挂「计划外」「已完成 N 组」和跳过动作的按钮。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (exercise.isExtra) {
                    Text(
                        text = stringResource(R.string.workout_extra),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = MaterialTheme.padding.small),
                    )
                }
                if (exercise.completedSets > 0) {
                    Text(
                        text = stringResource(R.string.workout_completed_sets, exercise.completedSets),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = MaterialTheme.padding.small),
                    )
                }
                if (exercise.skipped) {
                    Text(
                        text = stringResource(R.string.workout_skipped),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = MaterialTheme.padding.small),
                    )
                }
                if (!readOnly) {
                    TextButton(
                        onClick = onToggleSkipped,
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
                    ) {
                        Text(
                            text = if (exercise.skipped) {
                                stringResource(R.string.workout_unskip)
                            } else {
                                stringResource(R.string.workout_skip)
                            },
                        )
                    }
                }
            }

            // 动作库里的训练提示：放在动作名下方，和「目标 N 组」那行同一个字号。
            if (exercise.description.isNotBlank()) {
                Text(
                    text = exercise.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!exercise.isExtra) {
                Text(
                    text = exercise.targetHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 跳过的动作不再展示组行，只保留上面的动作信息与「恢复动作」入口。
            if (!exercise.skipped) {
                exercise.sets.forEachIndexed { index, entry ->
                    SetEntryRow(
                        index = index,
                        entry = entry,
                        timed = exercise.isTimed,
                        showsWeight = exercise.showsWeight,
                        weightIsAssistance = exercise.weightIsAssistance,
                        readOnly = readOnly,
                        editableCompletedSets = editableCompletedSets,
                        onWeightChange = { onWeightChange(index, it) },
                        onRepsChange = { onRepsChange(index, it) },
                        onSecondsChange = { onSecondsChange(index, it) },
                        onToggleCompleted = { onToggleCompleted(index) },
                    )
                }

                if (!readOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        TextButton(onClick = onAddSet) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                            )
                            Text(text = stringResource(R.string.workout_add_set))
                        }
                        TextButton(onClick = onRemoveSet) {
                            Text(text = stringResource(R.string.workout_remove_set))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetEntryRow(
    index: Int,
    entry: SetEntry,
    timed: Boolean,
    showsWeight: Boolean,
    weightIsAssistance: Boolean,
    readOnly: Boolean,
    editableCompletedSets: Boolean,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onToggleCompleted: () -> Unit,
) {
    val editable = !readOnly && (editableCompletedSets || !entry.completed)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = stringResource(R.string.workout_set_index, index + 1),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
        )

        // 纯自重动作没有重量输入框，一行只剩次数（或时长）。
        if (showsWeight) {
            OutlinedTextField(
                value = entry.weight,
                onValueChange = onWeightChange,
                label = {
                    Text(
                        text = if (weightIsAssistance) {
                            stringResource(R.string.field_weight_assist)
                        } else {
                            stringResource(R.string.field_weight)
                        },
                    )
                },
                enabled = editable,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        if (timed) {
            OutlinedTextField(
                value = entry.seconds,
                onValueChange = onSecondsChange,
                label = { Text(text = stringResource(R.string.field_seconds)) },
                enabled = editable,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedTextField(
                value = entry.reps,
                onValueChange = onRepsChange,
                label = { Text(text = stringResource(R.string.field_target_reps)) },
                enabled = editable,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        when {
            readOnly && entry.completed -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            readOnly -> Unit

            entry.completed -> IconButton(onClick = onToggleCompleted) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.workout_set_undo),
                )
            }

            else -> IconButton(onClick = onToggleCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.workout_set_done),
                )
            }
        }
    }
}

/** 组间休息条：贴在 Scaffold 的 bottomBar 上。 */
@Composable
internal fun RestBar(
    rest: RestState,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.workout_rest),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = rest.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = if (rest.remainingSeconds > 0) {
                        stringResource(R.string.workout_rest_remaining, rest.remainingSeconds)
                    } else {
                        stringResource(R.string.workout_rest_done)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(R.string.workout_rest_skip))
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (rest.totalSeconds == 0) 0f else rest.remainingSeconds.toFloat() / rest.totalSeconds
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 已结束训练的汇总卡片。 */
@Composable
internal fun WorkoutSummaryCard(
    summary: WorkoutSummary,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
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
                text = stringResource(R.string.workout_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.workout_summary_sets, summary.completedSets),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.workout_summary_duration, durationText(summary.durationSeconds)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 计划外动作选择弹窗：可按肌群与器械筛选。 */
@Composable
internal fun ExtraExerciseDialog(
    exercises: List<Exercise>,
    existingIds: Set<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var muscleFilter by remember { mutableStateOf<MuscleGroup?>(null) }
    var equipmentFilter by remember { mutableStateOf<Equipment?>(null) }

    val selectable = remember(exercises, existingIds, muscleFilter, equipmentFilter) {
        exercises
            .filterNot { it.id in existingIds }
            .filter { muscleFilter == null || muscleFilter in it.muscleGroups }
            .filter { equipmentFilter == null || it.equipment == equipmentFilter }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_add_extra)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                FilterChipRow {
                    FilterChip(
                        selected = muscleFilter == null,
                        onClick = { muscleFilter = null },
                        label = { Text(text = stringResource(R.string.exercise_filter_all)) },
                    )
                    MuscleGroup.entries.forEach { muscleGroup ->
                        FilterChip(
                            selected = muscleFilter == muscleGroup,
                            onClick = { muscleFilter = muscleGroup },
                            label = { Text(text = muscleGroup.label()) },
                        )
                    }
                }
                FilterChipRow {
                    FilterChip(
                        selected = equipmentFilter == null,
                        onClick = { equipmentFilter = null },
                        label = { Text(text = stringResource(R.string.equipment_filter_all)) },
                    )
                    Equipment.entries.forEach { equipment ->
                        FilterChip(
                            selected = equipmentFilter == equipment,
                            onClick = { equipmentFilter = equipment },
                            label = { Text(text = equipment.label()) },
                        )
                    }
                }
                if (selectable.isEmpty()) {
                    Text(
                        text = stringResource(R.string.exercise_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(selectable, key = { it.id }) { exercise ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(exercise.id) }
                                    .padding(vertical = MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "${muscleLabels(exercise.muscleGroups)} · ${exercise.equipment.label()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FilterChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        content()
    }
}

/** 结束训练前填备注的弹窗。 */
@Composable
internal fun FinishWorkoutDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_finish)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(text = stringResource(R.string.workout_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 放弃训练前的确认：本次记录会被删除，避免误触。 */
@Composable
internal fun AbandonWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_abandon_confirm_title)) },
        text = { Text(text = stringResource(R.string.workout_abandon_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.workout_abandon),
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

/** 把时间点格式化成 HH:mm。 */
internal fun Instant.toClockText(): String {
    val dateTime = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

@Composable
private fun durationText(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes < 60) {
        stringResource(R.string.workout_duration_minutes, minutes)
    } else {
        stringResource(R.string.workout_duration_hours, minutes / 60, minutes % 60)
    }
}

/**
 * 目标提示：计时是「目标 3 组 × 60 秒」，计数是「目标 3 组 × 10 次」；
 * 需要记录重量的动作在有目标重量时再插入「20 kg」（辅助类写成「辅助 40 kg」）。
 */
@Composable
private fun LogExercise.targetHint(): String {
    val target = when {
        isTimed && targetSeconds != null -> stringResource(R.string.workout_target_timed, targetSets, targetSeconds)
        // 计时动作没设目标时长时只报组数，不硬凑一个「0 秒」。
        isTimed -> stringResource(R.string.workout_target_sets, targetSets)
        else -> stringResource(R.string.workout_target_counted, targetSets, targetReps)
    }
    val weight = when {
        !showsWeight || targetWeight == null -> null
        weightIsAssistance -> stringResource(R.string.weight_kg_assist, targetWeight.toWeightText())
        else -> stringResource(R.string.weight_kg, targetWeight.toWeightText())
    }
    return listOfNotNull(target, weight, stringResource(R.string.workout_target_rest, restSeconds))
        .joinToString(" · ")
}
