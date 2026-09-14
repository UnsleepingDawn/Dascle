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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
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

/** 编辑某个计划里的动作编排：增删动作、拖拽排序、设置目标组数/次数/组间休息。 */
class RoutineEditScreen(
    private val routineId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<RoutineEditScreenModel>()
        val routineName by screenModel.routineName.collectAsState()
        val exercises by screenModel.exercises.collectAsState()

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
            floatingActionButton = {
                FloatingActionButton(onClick = { navigator.push(ExercisePickerScreen(routineId)) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.routine_edit_add_exercise),
                    )
                }
            },
        ) { contentPadding ->
            if (exercises.isEmpty()) {
                EmptyScreen(
                    message = stringResource(R.string.routine_edit_empty),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                RoutineExerciseList(
                    exercises = exercises,
                    contentPadding = contentPadding,
                    onClickEdit = { editTarget = it },
                    onClickRemove = { screenModel.removeExercise(it.id) },
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

@Composable
private fun RoutineExerciseList(
    exercises: List<RoutineExercise>,
    contentPadding: PaddingValues,
    onClickEdit: (RoutineExercise) -> Unit,
    onClickRemove: (RoutineExercise) -> Unit,
    onChangeOrder: (List<Long>) -> Unit,
) {
    // 拖拽过程中先改本地列表，松手后再按新顺序回写数据库。
    val exercisesState = remember { exercises.toMutableStateList() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState, contentPadding) { from, to ->
        exercisesState.add(to.index, exercisesState.removeAt(from.index))
        onChangeOrder(exercisesState.map { it.id })
    }

    LaunchedEffect(exercises) {
        if (!reorderableState.isAnyItemDragging) {
            exercisesState.clear()
            exercisesState.addAll(exercises)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(exercisesState, key = { it.id }) { exercise ->
            ReorderableItem(reorderableState, exercise.id) {
                RoutineExerciseCard(
                    exercise = exercise,
                    modifier = Modifier.animateItem(),
                    onClickEdit = { onClickEdit(exercise) },
                    onClickRemove = { onClickRemove(exercise) },
                )
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

/**
 * 动作目标文案：计数是「3 组 × 10 次」，计时是「3 组 × 60 秒」，
 * 计数动作设置了默认重量时再追加「· 20 kg」。
 */
@Composable
internal fun RoutineExercise.targetText(): String {
    val base = if (isTimed) {
        stringResource(R.string.routine_edit_targets_timed, targetSets, targetSeconds ?: 0)
    } else {
        stringResource(R.string.routine_edit_targets, targetSets, targetReps)
    }
    val weight = if (isTimed || targetWeight == null) {
        null
    } else {
        stringResource(R.string.weight_kg, targetWeight.toWeightText())
    }
    return listOfNotNull(base, weight).joinToString(" · ")
}

@Composable
private fun TargetsDialog(
    exercise: RoutineExercise,
    onDismiss: () -> Unit,
    onConfirm: (sets: Int, reps: Int, restSeconds: Int, targetWeight: Double?, targetSeconds: Int?) -> Unit,
) {
    var timed by remember { mutableStateOf(exercise.isTimed) }
    var sets by remember { mutableStateOf(exercise.targetSets.toString()) }
    var reps by remember { mutableStateOf(exercise.targetReps.toString()) }
    var weight by remember { mutableStateOf(exercise.targetWeight.toWeightText()) }
    var seconds by remember { mutableStateOf(exercise.targetSeconds?.toString().orEmpty()) }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }

    val setsValue = sets.toIntOrNull()
    val repsValue = reps.toIntOrNull()
    val restValue = rest.toIntOrNull()
    val weightValue = if (weight.isBlank()) null else weight.toDoubleOrNull()
    val secondsValue = seconds.toIntOrNull()
    val valid = setsValue?.let { it > 0 } == true &&
        restValue?.let { it >= 0 } == true &&
        (weight.isBlank() || weightValue?.let { it > 0 } == true) &&
        (if (timed) secondsValue?.let { it > 0 } == true else repsValue?.let { it > 0 } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = exercise.exerciseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !timed,
                        onClick = { timed = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text(text = stringResource(R.string.routine_edit_counted)) },
                    )
                    SegmentedButton(
                        selected = timed,
                        onClick = { timed = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text(text = stringResource(R.string.routine_edit_timed)) },
                    )
                }
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
                    NumberField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = stringResource(R.string.field_target_weight),
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
                    val parsedReps = repsValue ?: return@TextButton
                    val parsedRest = restValue ?: return@TextButton
                    // 计时类动作不需要重量，切过去时顺手清掉，避免留下永远用不上的数据。
                    onConfirm(
                        parsedSets,
                        parsedReps,
                        parsedRest,
                        if (timed) null else weightValue,
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
