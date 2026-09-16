package com.fitplan.ui.plan.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineGroup
import com.fitplan.domain.model.RoutineItem
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import com.fitplan.ui.exercise.ExercisePickerScreen
import com.fitplan.ui.workout.toWeightText
import dev.zacsweers.metrox.viewmodel.metroViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 编辑某个计划里的编排：增删动作与动作组、拖拽排序、设置目标组数/次数/组间休息。
 *
 * 动作组是一组可替换的动作（如「三个推类动作里挑两个做」），训练时从组里挑最多 x 个来练。
 */
class RoutineEditScreen(
    private val routineId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<RoutineEditScreenModel>()
        val routineName by screenModel.routineName.collectAsState()
        val items by screenModel.items.collectAsState()

        LaunchedEffect(routineId) { screenModel.load(routineId) }

        var editTarget by remember { mutableStateOf<RoutineExercise?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = routineName) },
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
            bottomBar = {
                // 两个入口并排：先加一个空的动作组，或者直接把某个动作排到计划里。
                AddActionBar(
                    onAddExercise = { navigator.push(ExercisePickerScreen(routineId)) },
                    onAddGroup = screenModel::addGroup,
                )
            },
        ) { contentPadding ->
            if (items.isEmpty()) {
                EmptyScreen(
                    message = stringResource(R.string.routine_edit_empty),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                RoutineItemList(
                    items = items,
                    contentPadding = contentPadding,
                    onClickEdit = { editTarget = it },
                    onClickRemoveExercise = { screenModel.removeExercise(it) },
                    onClickRemoveGroup = screenModel::removeGroup,
                    onChangeGroupPicks = screenModel::updateGroupMaxPicks,
                    onClickAddExerciseToGroup = { groupId ->
                        navigator.push(ExercisePickerScreen(routineId, groupId = groupId))
                    },
                    onChangeOrder = screenModel::reorder,
                )
            }
        }

        editTarget?.let { exercise ->
            TargetsDialog(
                exercise = exercise,
                onDismiss = { editTarget = null },
                onConfirm = { sets, reps, rest, weight, seconds ->
                    screenModel.updateTargets(exercise.id, sets, reps, rest, weight, seconds)
                    editTarget = null
                },
            )
        }
    }
}

/**
 * 贴在底部的两个蓝色按钮：「单独动作」从动作库挑一个排进来，「动作组」新建一个空组。
 *
 * 风格与训练记录页的「计划外动作 / 结束训练」按钮条保持一致。
 */
@Composable
private fun AddActionBar(
    onAddExercise: () -> Unit,
    onAddGroup: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Button(
                onClick = onAddExercise,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.routine_edit_add_exercise))
            }
            Button(
                onClick = onAddGroup,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.routine_edit_add_group))
            }
        }
    }
}

@Composable
private fun RoutineItemList(
    items: List<RoutineItem>,
    contentPadding: PaddingValues,
    onClickEdit: (RoutineExercise) -> Unit,
    onClickRemoveExercise: (Long) -> Unit,
    onClickRemoveGroup: (Long) -> Unit,
    onChangeGroupPicks: (Long, Int) -> Unit,
    onClickAddExerciseToGroup: (Long) -> Unit,
    onChangeOrder: (List<RoutineItem>) -> Unit,
) {
    // 拖拽过程中先改本地列表，松手后再按新顺序回写数据库。
    val itemsState = remember { items.toMutableStateList() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
        itemsState.add(to.index, itemsState.removeAt(from.index))
        onChangeOrder(itemsState.toList())
    }

    LaunchedEffect(items) {
        if (!reorderableState.isAnyItemDragging) {
            itemsState.clear()
            itemsState.addAll(items)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(itemsState, key = { it.listKey }) { item ->
            ReorderableItem(reorderableState, item.listKey) {
                when (item) {
                    is RoutineItem.Exercise -> RoutineExerciseCard(
                        exercise = item.value,
                        modifier = Modifier.animateItem(),
                        onClickEdit = { onClickEdit(item.value) },
                        onClickRemove = { onClickRemoveExercise(item.value.id) },
                    )

                    is RoutineItem.Group -> RoutineGroupCard(
                        group = item.value,
                        modifier = Modifier.animateItem(),
                        onClickEditExercise = onClickEdit,
                        onClickRemoveExercise = onClickRemoveExercise,
                        onClickRemoveGroup = { onClickRemoveGroup(item.value.id) },
                        onChangePicks = { picks -> onChangeGroupPicks(item.value.id, picks) },
                        onClickAddExercise = { onClickAddExerciseToGroup(item.value.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.RoutineExerciseCard(
    exercise: RoutineExercise,
    modifier: Modifier = Modifier,
    onClickEdit: () -> Unit,
    onClickRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.routine_edit_drag_handle),
                modifier = Modifier
                    .padding(end = MaterialTheme.padding.small)
                    .draggableHandle(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = exercise.targetText() + " · " +
                        stringResource(R.string.routine_edit_rest, exercise.restSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClickEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.routine_edit_edit_target),
                )
            }
            IconButton(onClick = onClickRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.routine_edit_remove_exercise),
                )
            }
        }
    }
}

/** 动作组卡片：组内动作、以及「做其中 x 个」都在卡片里直接管，不用进二级页面。 */
@Composable
private fun ReorderableCollectionItemScope.RoutineGroupCard(
    group: RoutineGroup,
    modifier: Modifier = Modifier,
    onClickEditExercise: (RoutineExercise) -> Unit,
    onClickRemoveExercise: (Long) -> Unit,
    onClickRemoveGroup: () -> Unit,
    onChangePicks: (Int) -> Unit,
    onClickAddExercise: () -> Unit,
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
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.routine_edit_drag_handle),
                    modifier = Modifier
                        .padding(end = MaterialTheme.padding.small)
                        .draggableHandle(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.routine_edit_group_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.routine_edit_group_member_count, group.exercises.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClickRemoveGroup) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.routine_edit_remove_group),
                    )
                }
            }

            GroupPicksRow(
                picks = group.maxPicks,
                memberCount = group.exercises.size,
                onChangePicks = onChangePicks,
            )

            if (group.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.routine_edit_group_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                group.exercises.forEach { exercise ->
                    GroupExerciseRow(
                        exercise = exercise,
                        onClickEdit = { onClickEditExercise(exercise) },
                        onClickRemove = { onClickRemoveExercise(exercise.id) },
                    )
                }
            }

            TextButton(onClick = onClickAddExercise) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.routine_edit_group_add_exercise))
            }
        }
    }
}

/** 「做其中 x 个」：读数配一对加减按钮，范围是 1..组内动作数。 */
@Composable
private fun GroupPicksRow(
    picks: Int,
    memberCount: Int,
    onChangePicks: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.routine_edit_group_picks_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChangePicks(picks - 1) },
            enabled = picks > 1,
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.routine_edit_group_picks_decrease),
            )
        }
        Text(
            text = picks.toString(),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onChangePicks(picks + 1) },
            enabled = picks < memberCount,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.routine_edit_group_picks_increase),
            )
        }
    }
}

/** 组内动作：目标值与组间休息照旧可单独编辑，只是排在组卡片内部。 */
@Composable
private fun GroupExerciseRow(
    exercise: RoutineExercise,
    onClickEdit: () -> Unit,
    onClickRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MaterialTheme.padding.medium, top = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = exercise.targetText() + " · " +
                        stringResource(R.string.routine_edit_rest, exercise.restSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClickEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.routine_edit_edit_target),
                )
            }
            IconButton(onClick = onClickRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.routine_edit_remove_exercise),
                )
            }
        }
    }
}

/**
 * 动作目标文案：计时是「3 组 × 60 秒」，计数是「3 组 × 10 次」，
 * 需要记录重量的动作设了目标重量时再追加「· 20 kg」（辅助类写成「辅助 40 kg」）。
 */
@Composable
internal fun RoutineExercise.targetText(): String {
    val base = if (isTimed) {
        stringResource(R.string.routine_edit_targets_timed, targetSets, targetSeconds ?: 0)
    } else {
        stringResource(R.string.routine_edit_targets, targetSets, targetReps)
    }
    val weight = when {
        !showsWeight || targetWeight == null -> null
        weightIsAssistance -> stringResource(R.string.weight_kg_assist, targetWeight.toWeightText())
        else -> stringResource(R.string.weight_kg, targetWeight.toWeightText())
    }
    return listOfNotNull(base, weight).joinToString(" · ")
}

@Composable
private fun TargetsDialog(
    exercise: RoutineExercise,
    onDismiss: () -> Unit,
    onConfirm: (sets: Int, reps: Int, restSeconds: Int, targetWeight: Double?, targetSeconds: Int?) -> Unit,
) {
    var sets by remember { mutableStateOf(exercise.targetSets.toString()) }
    var reps by remember { mutableStateOf(exercise.targetReps.toString()) }
    var weight by remember { mutableStateOf(exercise.targetWeight.toWeightText()) }
    var seconds by remember { mutableStateOf(exercise.targetSeconds?.toString().orEmpty()) }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }

    // 计数 / 计时与是否需要重量都由动作库决定，这里只按类型展示对应的输入框，不给切换入口。
    val timed = exercise.isTimed
    val showsWeight = exercise.showsWeight

    val setsValue = sets.toIntOrNull()
    val repsValue = reps.toIntOrNull()
    val restValue = rest.toIntOrNull()
    val weightValue = if (weight.isBlank()) null else weight.toDoubleOrNull()
    val secondsValue = seconds.toIntOrNull()
    val valid = setsValue?.let { it > 0 } == true &&
        restValue?.let { it >= 0 } == true &&
        (!showsWeight || weight.isBlank() || weightValue?.let { it > 0 } == true) &&
        (if (timed) secondsValue?.let { it > 0 } == true else repsValue?.let { it > 0 } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = exercise.exerciseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                NumberField(
                    value = sets,
                    onValueChange = { sets = it },
                    label = stringResource(R.string.field_target_sets),
                )
                if (timed) {
                    NumberField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = stringResource(R.string.field_target_seconds),
                    )
                } else {
                    NumberField(
                        value = reps,
                        onValueChange = { reps = it },
                        label = stringResource(R.string.field_target_reps),
                    )
                }
                // 纯自重动作没有重量输入框，只保留次数 / 时长。
                if (showsWeight) {
                    NumberField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = stringResource(
                            if (exercise.weightIsAssistance) {
                                R.string.field_target_weight_assist
                            } else {
                                R.string.field_target_weight
                            },
                        ),
                        allowDecimal = true,
                    )
                }
                NumberField(
                    value = rest,
                    onValueChange = { rest = it },
                    label = stringResource(R.string.field_rest_seconds),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val parsedSets = setsValue ?: return@TextButton
                    val parsedRest = restValue ?: return@TextButton
                    // 计时类动作不需要次数目标，计数类动作不需要时长目标，顺手清掉另一侧的旧值。
                    onConfirm(
                        parsedSets,
                        if (timed) exercise.targetReps else (repsValue ?: return@TextButton),
                        parsedRest,
                        if (showsWeight) weightValue else null,
                        if (timed) secondsValue else null,
                    )
                },
            ) {
                Text(text = stringResource(R.string.action_save))
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
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    allowDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            onValueChange(
                if (allowDecimal) new.filter { it.isDigit() || it == '.' } else new.filter(Char::isDigit),
            )
        },
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
