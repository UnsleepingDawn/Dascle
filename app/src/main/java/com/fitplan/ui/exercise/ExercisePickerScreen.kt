package com.fitplan.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch

/** 从动作库里挑一个动作加进 [routineId] 对应的计划，选中后自动返回。 */
class ExercisePickerScreen(
    private val routineId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = metroViewModel<ExercisePickerScreenModel>()
        val exercises by screenModel.exercises.collectAsState()
        val muscleFilter by screenModel.muscleFilter.collectAsState()
        val equipmentFilter by screenModel.equipmentFilter.collectAsState()

        LaunchedEffect(Unit) { screenModel.load() }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.exercise_picker_title)) },
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
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                FilterRow {
                    FilterChip(
                        selected = muscleFilter == null,
                        onClick = { screenModel.setMuscleFilter(null) },
                        label = { Text(text = stringResource(R.string.exercise_filter_all)) },
                    )
                    MuscleGroup.entries.forEach { muscleGroup ->
                        FilterChip(
                            selected = muscleFilter == muscleGroup,
                            onClick = { screenModel.setMuscleFilter(muscleGroup) },
                            label = { Text(text = muscleGroup.label()) },
                        )
                    }
                }
                FilterRow {
                    FilterChip(
                        selected = equipmentFilter == null,
                        onClick = { screenModel.setEquipmentFilter(null) },
                        label = { Text(text = stringResource(R.string.equipment_filter_all)) },
                    )
                    Equipment.entries.forEach { equipment ->
                        FilterChip(
                            selected = equipmentFilter == equipment,
                            onClick = { screenModel.setEquipmentFilter(equipment) },
                            label = { Text(text = equipment.label()) },
                        )
                    }
                }

                if (exercises.isEmpty()) {
                    EmptyScreen(message = stringResource(R.string.exercise_picker_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(exercises, key = { it.id }) { exercise ->
                            ExerciseListItem(
                                exercise = exercise,
                                onClick = {
                                    scope.launch {
                                        screenModel.addToRoutine(routineId, exercise.id)
                                        navigator.pop()
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        content()
    }
}

@Composable
private fun ExerciseListItem(
    exercise: Exercise,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
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
        if (exercise.description.isNotBlank()) {
            Text(
                text = exercise.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MuscleGroup.label(): String = stringResource(
    when (this) {
        MuscleGroup.CHEST -> R.string.muscle_chest
        MuscleGroup.ARM -> R.string.muscle_arm
        MuscleGroup.ABS -> R.string.muscle_abs
        MuscleGroup.SHOULDER -> R.string.muscle_shoulder
        MuscleGroup.BACK -> R.string.muscle_back
        MuscleGroup.LEG -> R.string.muscle_leg
        MuscleGroup.GLUTE -> R.string.muscle_glute
    },
)

/** 多部位动作的标签，如「胸 / 手臂」；[MuscleGroup.label] 只能在组合里调用。 */
@Composable
internal fun muscleLabels(groups: List<MuscleGroup>): String =
    groups.map { it.label() }.joinToString(" / ")

@Composable
internal fun Equipment.label(): String = stringResource(
    when (this) {
        Equipment.BARBELL -> R.string.equipment_barbell
        Equipment.DUMBBELL -> R.string.equipment_dumbbell
        Equipment.MACHINE -> R.string.equipment_machine
        Equipment.CABLE -> R.string.equipment_cable
        Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    },
)
