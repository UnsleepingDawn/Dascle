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
                onConfirm = { sets, reps, rest ->
                    screenModel.updateTargets(exercise.id, sets, reps, rest)
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
                    text = stringResource(
                        R.string.routine_edit_targets,
                        exercise.targetSets,
                        exercise.targetReps,
                    ) + " · " + stringResource(R.string.routine_edit_rest, exercise.restSeconds),
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

@Composable
private fun TargetsDialog(
    exercise: RoutineExercise,
    onDismiss: () -> Unit,
    onConfirm: (sets: Int, reps: Int, restSeconds: Int) -> Unit,
) {
    var sets by remember { mutableStateOf(exercise.targetSets.toString()) }
    var reps by remember { mutableStateOf(exercise.targetReps.toString()) }
    var rest by remember { mutableStateOf(exercise.restSeconds.toString()) }

    val parsed = Triple(sets.toIntOrNull(), reps.toIntOrNull(), rest.toIntOrNull())
    val valid = parsed.first?.let { it > 0 } == true &&
        parsed.second?.let { it > 0 } == true &&
        parsed.third?.let { it >= 0 } == true

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
                NumberField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = stringResource(R.string.field_target_reps),
                )
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
                    onConfirm(
                        parsed.first ?: return@TextButton,
                        parsed.second ?: return@TextButton,
                        parsed.third ?: return@TextButton,
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit)) },
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
