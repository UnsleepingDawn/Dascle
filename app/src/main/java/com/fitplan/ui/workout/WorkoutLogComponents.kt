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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** ??????????????????????? */
@Composable
internal fun WorkoutPlanList(
    exercises: List<LogExercise>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
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
        items(exercises, key = { it.exerciseId }) { exercise ->
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
                        text = stringResource(
                            R.string.workout_target_hint,
                            exercise.targetSets,
                            exercise.targetReps,
                            exercise.restSeconds,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** ??????????????[readOnly] ?????????? */
@Composable
internal fun LogExerciseCard(
    exercise: LogExercise,
    readOnly: Boolean,
    onWeightChange: (Int, String) -> Unit,
    onRepsChange: (Int, String) -> Unit,
    onSecondsChange: (Int, String) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onToggleTimed: () -> Unit,
    onToggleSkipped: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
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
            }

            if (!exercise.isExtra) {
                Text(
                    text = stringResource(
                        R.string.workout_target_hint,
                        exercise.targetSets,
                        exercise.targetReps,
                        exercise.restSeconds,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (exercise.skipped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.workout_skipped),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!readOnly) {
                        TextButton(onClick = onToggleSkipped) {
                            Text(text = stringResource(R.string.workout_unskip))
                        }
                    }
                }
            } else {
                if (!readOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ????????????????????????????????
                        val switchEnabled = exercise.sets.none { it.completed }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                            SegmentedButton(
                                selected = !exercise.isTimed,
                                onClick = { if (exercise.isTimed) onToggleTimed() },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                enabled = switchEnabled,
                                label = { Text(text = stringResource(R.string.workout_counted)) },
                            )
                            SegmentedButton(
                                selected = exercise.isTimed,
                                onClick = { if (!exercise.isTimed) onToggleTimed() },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                enabled = switchEnabled,
                                label = { Text(text = stringResource(R.string.workout_timed)) },
                            )
                        }
                        TextButton(onClick = onToggleSkipped) {
                            Text(text = stringResource(R.string.workout_skip))
                        }
                    }
                }

                exercise.sets.forEachIndexed { index, entry ->
                    SetEntryRow(
                        index = index,
                        entry = entry,
                        timed = exercise.isTimed,
                        readOnly = readOnly,
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
    readOnly: Boolean,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onToggleCompleted: () -> Unit,
) {
    val editable = !readOnly && !entry.completed

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
                value = entry.weight,
                onValueChange = onWeightChange,
                label = { Text(text = stringResource(R.string.field_weight)) },
                enabled = editable,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
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

/** ??????????? Scaffold ? bottomBar ?? */
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

/** ????????????? */
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
            if (summary.volume > 0.0) {
                Text(
                    text = stringResource(R.string.workout_summary_volume, formatVolume(summary.volume)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.workout_summary_duration, durationText(summary.durationSeconds)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** ?????????????????????????????? */
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
            .filter { muscleFilter == null || it.muscleGroup == muscleFilter }
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
                                    text = "${exercise.muscleGroup.label()} ? ${exercise.equipment.label()}",
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

/** ?????????????? */
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

/** ?????????????????????????? */
@Composable
internal fun ExitWorkoutDialog(
    onKeep: () -> Unit,
    onAbandon: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_exit_title)) },
        text = {
            Column {
                Text(text = stringResource(R.string.workout_exit_message))
                TextButton(onClick = onAbandon) {
                    Text(
                        text = stringResource(R.string.workout_abandon),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeep) {
                Text(text = stringResource(R.string.workout_exit_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.workout_exit_continue))
            }
        },
    )
}

/** ????????????????? */
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

private fun formatVolume(volume: Double): String =
    if (volume == volume.toLong().toDouble()) volume.toLong().toString() else "%.1f".format(volume)
