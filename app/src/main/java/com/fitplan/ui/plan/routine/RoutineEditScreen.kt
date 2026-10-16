package com.fitplan.ui.plan.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineGroup
import com.fitplan.domain.model.RoutineItem
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import com.fitplan.ui.common.DialogButtonColumn
import com.fitplan.ui.common.DialogCancelButton
import com.fitplan.ui.common.DialogPrimaryButton
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
        val showWeightIntro by screenModel.showWeightIntro.collectAsState()

        LaunchedEffect(routineId) { screenModel.load(routineId) }

        var editTarget by remember { mutableStateOf<RoutineExercise?>(null) }
        var groupRenameTarget by remember { mutableStateOf<RoutineGroup?>(null) }
        var showGroupActionDialog by remember { mutableStateOf(false) }
        // true 表示正在改这个计划的名字（点顶栏标题触发）。
        var showRenameDialog by remember { mutableStateOf(false) }
        // true 表示正在提醒「这一天同时排了胸和背」，用户确认之后才真的退出编排页。
        var showImbalanceDialog by remember { mutableStateOf(false) }

        // 返回前先看一眼编排：同一天既排了胸又排了背就先提醒，确认之后才退出。
        val requestExit = {
            if (items.hasChestAndBack()) showImbalanceDialog = true else navigator.pop()
        }
        // 系统返回键与顶栏返回箭头走同一套判断；提醒弹窗开着时由弹窗自己处理返回。
        BackHandler(enabled = !showImbalanceDialog) { requestExit() }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        // 标题即改名入口：点一下弹改名弹窗，右侧小铅笔图标提示这里能改。
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { showRenameDialog = true }
                                .padding(
                                    horizontal = MaterialTheme.padding.extraSmall,
                                    vertical = MaterialTheme.padding.small,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = routineName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.plan_rename),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = MaterialTheme.padding.small)
                                    .size(PLAN_TITLE_EDIT_ICON_SIZE),
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestExit() }) {
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
                    onAddGroup = { showGroupActionDialog = true },
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
                    onClickRenameGroup = { groupRenameTarget = it },
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

        if (showGroupActionDialog) {
            GroupActionDialog(
                onDismiss = { showGroupActionDialog = false },
                onAddEmptyGroup = {
                    showGroupActionDialog = false
                    screenModel.addGroup()
                },
                onPickPreset = {
                    showGroupActionDialog = false
                    navigator.push(GroupPresetPickerScreen(routineId))
                },
            )
        }

        groupRenameTarget?.let { group ->
            GroupRenameDialog(
                initialName = group.name.orEmpty(),
                onDismiss = { groupRenameTarget = null },
                onConfirm = { name ->
                    screenModel.updateGroupName(group.id, name)
                    groupRenameTarget = null
                },
            )
        }

        if (showWeightIntro) {
            WeightIntroDialog(onDismiss = screenModel::dismissWeightIntro)
        }

        if (showRenameDialog) {
            RoutineRenameDialog(
                initialName = routineName,
                onDismiss = { showRenameDialog = false },
                onConfirm = { name ->
                    screenModel.updateName(name)
                    showRenameDialog = false
                },
            )
        }

        if (showImbalanceDialog) {
            ImbalanceDialog(
                onDismiss = { showImbalanceDialog = false },
                onConfirm = {
                    showImbalanceDialog = false
                    navigator.pop()
                },
            )
        }
    }
}

/**
 * 这一天是不是同时排了胸和背的动作。
 *
 * 动作组按组内全部动作算：组里既有胸又有背，也算这一天会同时练到两块大肌群。
 */
internal fun List<RoutineItem>.hasChestAndBack(): Boolean {
    val muscles = flatMap { it.exercises }.flatMap { it.muscleGroups }
    return MuscleGroup.CHEST in muscles && MuscleGroup.BACK in muscles
}

/**
 * 返回前的提醒：同一天既排了胸又排了背。
 *
 * 「返回修改」只收起弹窗，让用户回去接着调整；「仍然返回」才真的退出编排页。
 */
@Composable
private fun ImbalanceDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.routine_edit_imbalance_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(text = stringResource(R.string.routine_edit_imbalance_message))
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.routine_edit_imbalance_back),
                        onClick = onDismiss,
                    )
                    DialogPrimaryButton(
                        text = stringResource(R.string.routine_edit_imbalance_keep),
                        onClick = onConfirm,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * 第一次进编排页时的「怎么定重量」说明：提前设的重量只是起点，
 * 真正合适的重量要在训练中边做边找。
 */
@Composable
private fun WeightIntroDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.routine_edit_weight_intro_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(text = stringResource(R.string.routine_edit_weight_intro_message))
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.routine_edit_weight_intro_ok),
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * 改这个计划的名字；编排页顶栏点标题进来。空名不让保存，避免计划在列表里变成一条空白卡片。
 */
@Composable
private fun RoutineRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.field_routine_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                    )
                    DialogCancelButton(onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * 新建动作组的入口：空组自己往里加动作，预设组直接整组带动作加进来。
 */
@Composable
private fun GroupActionDialog(
    onDismiss: () -> Unit,
    onAddEmptyGroup: () -> Unit,
    onPickPreset: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.routine_edit_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(text = stringResource(R.string.routine_edit_group_dialog_message))
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.routine_edit_group_add_empty),
                        onClick = onAddEmptyGroup,
                    )
                    DialogPrimaryButton(
                        text = stringResource(R.string.routine_edit_group_add_preset),
                        onClick = onPickPreset,
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/** 改动作组的名字；清空保存表示不要名字，卡片回到默认的「动作组」。 */
@Composable
private fun GroupRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.routine_edit_group_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.field_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onConfirm(name.trim()) },
                    )
                    DialogCancelButton(onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
    )
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
    onClickRenameGroup: (RoutineGroup) -> Unit,
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
                        onClickRename = { onClickRenameGroup(item.value) },
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

/** 动作组卡片：组内动作、以及「建议做 x 个」都在卡片里直接管，不用进二级页面。 */
@Composable
private fun ReorderableCollectionItemScope.RoutineGroupCard(
    group: RoutineGroup,
    modifier: Modifier = Modifier,
    onClickEditExercise: (RoutineExercise) -> Unit,
    onClickRemoveExercise: (Long) -> Unit,
    onClickRemoveGroup: () -> Unit,
    onClickRename: () -> Unit,
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
                        text = group.name ?: stringResource(R.string.routine_edit_group_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.routine_edit_group_member_count, group.exercises.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClickRename) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.routine_edit_group_rename),
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

/** 「建议做 x 个」：读数配一对加减按钮，范围是 1..组内动作数。 */
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
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.action_save),
                        enabled = valid,
                        onClick = {
                            val parsedSets = setsValue ?: return@DialogPrimaryButton
                            val parsedRest = restValue ?: return@DialogPrimaryButton
                            // 计时类动作不需要次数目标，计数类动作不需要时长目标，顺手清掉另一侧的旧值。
                            onConfirm(
                                parsedSets,
                                if (timed) exercise.targetReps else (repsValue ?: return@DialogPrimaryButton),
                                parsedRest,
                                if (showsWeight) weightValue else null,
                                if (timed) secondsValue else null,
                            )
                        },
                    )
                    DialogCancelButton(onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
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

/** 顶栏标题右侧那个「可改名」铅笔图标的大小：比正文小一号，不喧宾夺主。 */
private val PLAN_TITLE_EDIT_ICON_SIZE = 18.dp
