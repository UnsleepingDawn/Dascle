package com.fitplan.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    collapsed: Boolean,
    onWeightChange: (Int, String) -> Unit,
    onRepsChange: (Int, String) -> Unit,
    onSecondsChange: (Int, String) -> Unit,
    onToggleCompleted: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onToggleSkipped: () -> Unit,
    onToggleCollapsed: () -> Unit,
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
            // 动作名一行：右侧依次挂「计划外」「已完成 N 组」和跳过动作的按钮。收起 / 跳过时这一行常驻。
            // 收起后不再单独占一行放「展开」按钮，点动作名所在的这一整行即可展开或收起；跳过的动作不响应。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !exercise.skipped, onClick = onToggleCollapsed),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // 跳过的动作整卡变灰，和「已跳过」标签呼应。
                        color = if (exercise.skipped) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 组全部做完时，名字右边立刻补一颗主色圆圈勾，和今日页「已练」的标记同款。
                    if (exercise.isDone) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.workout_exercise_done),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = MaterialTheme.padding.small)
                                .size(DONE_CHECK_SIZE),
                        )
                    }
                }
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
                        // 动过任意一组之后就只能一组一组地撤销，不能再整卡跳过；「恢复动作」始终可点。
                        enabled = exercise.skipped || exercise.completedSets == 0,
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

            // 说明、目标、组行与增删组按钮一起折叠：手动「收起」与「跳过动作」共用这段过渡。
            AnimatedVisibility(
                visible = !exercise.skipped && !collapsed,
                enter = expandVertically(animationSpec = tween(SET_EXPAND_MILLIS)) +
                    fadeIn(animationSpec = tween(SET_FADE_MILLIS, delayMillis = SET_EXPAND_MILLIS)),
                exit = fadeOut(animationSpec = tween(SET_FADE_MILLIS)) +
                    shrinkVertically(animationSpec = tween(SET_EXPAND_MILLIS, delayMillis = SET_FADE_MILLIS)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        }
                        TextButton(
                            onClick = onToggleCollapsed,
                            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
                        ) {
                            Text(text = stringResource(R.string.workout_collapse))
                        }
                    }

                    val nextSetIndex = exercise.nextSetIndex
                    val lastCompletedIndex = exercise.lastCompletedIndex
                    exercise.sets.forEachIndexed { index, entry ->
                        SetEntryRow(
                            index = index,
                            entry = entry,
                            timed = exercise.isTimed,
                            showsWeight = exercise.showsWeight,
                            weightIsAssistance = exercise.weightIsAssistance,
                            readOnly = readOnly,
                            editableCompletedSets = editableCompletedSets,
                            // 做完的一组只有「最后一组已完成的」能撤销，没做完的只有下一组能做。
                            actionable = index == if (entry.completed) lastCompletedIndex else nextSetIndex,
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
                            TextButton(
                                onClick = onRemoveSet,
                                // 只能减没做过的组，全部做完时按钮变灰。
                                enabled = exercise.sets.any { !it.completed },
                            ) {
                                Text(text = stringResource(R.string.workout_remove_set))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「减一组」减到只剩最后一组时的确认：这一组减掉就什么都不剩了，
 * 所以改成问一句「要不要直接跳过这个动作」，确定后走跳过动作的逻辑。
 */
@Composable
internal fun SkipLastSetDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_last_set_title)) },
        text = { Text(text = stringResource(R.string.workout_last_set_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.workout_skip))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 某一组低于计划目标时的重填提醒：这一组还没记录，用户可以先改数值再回来勾，
 * 也可以确认「就这样」把这组照原样记上。主操作是「重新填一次」。
 */
@Composable
internal fun LowTargetReminderDialog(
    exercise: LogExercise,
    onRetry: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_low_target_title, exercise.name)) },
        text = {
            Text(
                text = stringResource(
                    R.string.workout_low_target_message,
                    exercise.targetHint(),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.workout_low_target_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text(text = stringResource(R.string.workout_low_target_keep))
            }
        },
    )
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
    actionable: Boolean,
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

        // 只读（已经结束且不再编辑）时末尾只留一颗勾，没有可点的操作。
        if (readOnly) {
            if (entry.completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.workout_exercise_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            SetActionControl(
                completed = entry.completed,
                actionable = actionable,
                onComplete = onToggleCompleted,
                onUndo = onToggleCompleted,
            )
        }
    }
}

/**
 * 一行的末尾操作。顺序即视觉上的推进方向：从「还没轮到」到「可以做了」再到「已完成」，
 * 最后是「做过了但不能再撤」；[ordinal] 用来决定切换时贴面往哪个方向滑。
 */
private enum class SetAction { LOCKED, DONE, UNDO, UNDO_LOCKED }

/**
 * 组行末尾的「做完了 / 撤销」按钮。
 *
 * 按钮的外框、位置和尺寸全程不动，切换时只让里面那张「贴面」（整块底色加一行文字）
 * 横向滑过：新贴面从一侧滑进来、旧贴面从另一侧滑出去，像一条传送带，避免整颗按钮
 * 上下跳或者原地变大变小。顺序往前推进（锁定 → 可做 → 已完成）时从右侧滑入，退回来时反向。
 *
 * 四种形态对应记录的推进方向：还没轮到（淡边框的「做完了」）、轮到这一组（蓝色
 * 「做完了」）、刚做完可以撤回（灰色「撤销」）、做过了但要先撤后面的（淡边框的
 * 「撤销」）。
 */
@Composable
private fun SetActionControl(
    completed: Boolean,
    actionable: Boolean,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
) {
    val action = when {
        completed && actionable -> SetAction.UNDO
        completed -> SetAction.UNDO_LOCKED
        actionable -> SetAction.DONE
        else -> SetAction.LOCKED
    }

    Button(
        onClick = if (action == SetAction.UNDO) onUndo else onComplete,
        enabled = action == SetAction.DONE || action == SetAction.UNDO,
        colors = ButtonDefaults.buttonColors(
            // 外框保持透明：切换时动的只有里面滑动的贴面，按钮本身一丝不动。
            containerColor = Color.Transparent,
            contentColor = if (completed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            // 两档不可点的形态由贴面自己给配色（透明底 + 淡边框 + 浅字）。
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        // 去掉按钮自带的内边距，让贴面正好铺满按钮，滑动时看不到留白。
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.width(SET_ACTION_WIDTH),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MinHeight)
                .clip(ButtonDefaults.shape),
        ) {
            AnimatedContent(
                targetState = action,
                transitionSpec = {
                    // 往前推进时新贴面从右边进来、旧贴面往左边出去；往回退时整体反向。
                    val enterFrom = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally(tween(SET_ACTION_SLIDE_MILLIS)) { width -> enterFrom * width }
                        .togetherWith(
                            slideOutHorizontally(tween(SET_ACTION_SLIDE_MILLIS)) { width -> -enterFrom * width },
                        )
                },
                contentAlignment = Alignment.Center,
                label = "setAction",
            ) { target ->
                when (target) {
                    SetAction.DONE -> SetActionFace(
                        text = stringResource(R.string.workout_set_done),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )

                    SetAction.UNDO -> SetActionFace(
                        text = stringResource(R.string.workout_set_undo),
                        containerColor = undoContainerColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )

                    // 做过了、但要先把后面的组撤回来才能动它。
                    SetAction.UNDO_LOCKED -> SetActionFace(
                        text = stringResource(R.string.workout_set_undo),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_CONTENT_ALPHA),
                        border = BorderStroke(LOCKED_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                    )

                    // 还没轮到这一组。
                    SetAction.LOCKED -> SetActionFace(
                        text = stringResource(R.string.workout_set_done),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_CONTENT_ALPHA),
                        border = BorderStroke(LOCKED_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

/** 按钮里滑动的那一层贴面：一整块底色加一行居中文字，尺寸始终是按钮的大小。 */
@Composable
private fun SetActionFace(
    text: String,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor, ButtonDefaults.shape)
            .then(
                if (border == null) {
                    Modifier
                } else {
                    Modifier.border(border, ButtonDefaults.shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * 「撤销」按钮的灰色底。
 *
 * 主题里的 `surfaceContainerHighest` / `surfaceContainer` 在浅色模式下都接近纯白
 * （默认主题是 #FCF7FF），铺在卡片上几乎看不出形状，所以改成用 `onSurface` 按比例
 * 叠在卡片色上：浅色模式得到浅灰、深色模式得到深灰，各套配色都能和蓝色的
 * 「做完了」拉开差别。
 */
@Composable
private fun undoContainerColor(): Color = MaterialTheme.colorScheme.onSurface
    .copy(alpha = UNDO_CONTAINER_ALPHA)
    .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

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

/** 动作卡内容淡出 / 淡入的时长（毫秒）。 */
private const val SET_FADE_MILLIS = 160

/** 动作卡内容撑开 / 收缩的时长（毫秒）；与 [SET_FADE_MILLIS] 错开，做出「先消失、再收缩」。 */
private const val SET_EXPAND_MILLIS = 220

/** 「做完了 / 撤销」按钮里贴面横向滑过的时长（毫秒）。 */
private const val SET_ACTION_SLIDE_MILLIS = 220

/** 组行末尾按钮的固定宽度；三种状态同宽，切换时不挤动左边的输入框。 */
private val SET_ACTION_WIDTH = 84.dp

/** 「撤销」灰色底的叠色比例：`onSurface` 按这个透明度盖在卡片色上。 */
private const val UNDO_CONTAINER_ALPHA = 0.14f

/** 还没轮到的「做完了」里的字色透明度；比可点的按钮淡，一眼看出不能按。 */
private const val LOCKED_CONTENT_ALPHA = 0.38f

/** 还没轮到的「做完了」的描边粗细。 */
private val LOCKED_BORDER_WIDTH = 1.dp

/** 动作做完全部组后，名字右边那颗「已完成」勾的尺寸；与今日页「已练」标记同规格。 */
private val DONE_CHECK_SIZE = 18.dp
