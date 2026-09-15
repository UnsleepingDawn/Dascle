package com.fitplan.ui.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** 训练记录页：逐组录入重量/次数，勾选一组立刻落库并开始组间休息。 */
class WorkoutLogScreen(
    private val sessionId: Long? = null,
    private val routineId: Long? = null,
    /** true 表示编辑一次已经结束的训练：输入框解锁、可增删组，改动即时落库。 */
    private val editing: Boolean = false,
    /** true 表示把这次已结束的训练重新置为进行中（「计划外训练」入口），新加内容追加到同一次训练。 */
    private val reopen: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<WorkoutLogScreenModel>()
        val phase by screenModel.phase.collectAsState()
        val sessionName by screenModel.sessionName.collectAsState()
        val exercises by screenModel.exercises.collectAsState()
        val rest by screenModel.rest.collectAsState()
        val summary by screenModel.summary.collectAsState()
        val restFinishedTick by screenModel.restFinishedTick.collectAsState()
        val exitTick by screenModel.exitTick.collectAsState()
        val allExercises by screenModel.allExercises.collectAsState()

        // 从「今日」页进来时只带参数，真正的状态由 ScreenModel 按 sessionId / routineId 还原。
        LaunchedEffect(sessionId, routineId, editing, reopen) {
            screenModel.load(sessionId, routineId, editing, reopen)
        }

        val vibrateOnce = rememberVibrateOnce()
        LaunchedEffect(restFinishedTick) {
            if (restFinishedTick > 0) vibrateOnce()
        }
        LaunchedEffect(exitTick) {
            if (exitTick > 0) navigator.pop()
        }

        var showFinishDialog by remember { mutableStateOf(false) }
        var showAbandonDialog by remember { mutableStateOf(false) }
        var showExtraDialog by remember { mutableStateOf(false) }

        val freeName = stringResource(R.string.workout_free)
        val readOnly = phase == WorkoutPhase.FINISHED && !editing

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                phase == WorkoutPhase.NOT_STARTED -> stringResource(R.string.workout_title)
                                editing -> stringResource(R.string.workout_editing_title, sessionName)
                                else -> sessionName
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        if (phase == WorkoutPhase.IN_PROGRESS) {
                            // 放弃训练是破坏性操作，放回原「结束训练」的位置并保留确认。
                            TextButton(onClick = { showAbandonDialog = true }) {
                                Text(
                                    text = stringResource(R.string.workout_abandon),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                val currentRest = rest
                when {
                    phase == WorkoutPhase.NOT_STARTED -> ActionBar {
                        Button(
                            onClick = { screenModel.startWorkout(freeName) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                            )
                            Text(text = stringResource(R.string.workout_start))
                        }
                    }

                    // 编辑已经结束的训练：一样能加计划外动作，加完点「完成编辑」退出。
                    editing -> ExtraActionBar(
                        confirmText = stringResource(R.string.workout_edit_done),
                        onAddExtra = {
                            screenModel.loadExercises()
                            showExtraDialog = true
                        },
                        onConfirm = navigator::pop,
                    )

                    readOnly -> ActionBar {
                        Button(onClick = navigator::pop, modifier = Modifier.fillMaxWidth()) {
                            Text(text = stringResource(R.string.workout_summary_done))
                        }
                    }

                    else -> Column(modifier = Modifier.fillMaxWidth()) {
                        currentRest?.let { RestBar(rest = it, onSkip = screenModel::skipRest) }
                        ExtraActionBar(
                            confirmText = stringResource(R.string.workout_finish),
                            onAddExtra = {
                                screenModel.loadExercises()
                                showExtraDialog = true
                            },
                            onConfirm = { showFinishDialog = true },
                        )
                    }
                }
            },
        ) { contentPadding ->
            if (phase == WorkoutPhase.NOT_STARTED) {
                if (exercises.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(R.string.workout_plan_empty),
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    WorkoutPlanList(exercises = exercises, contentPadding = contentPadding)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    val currentSummary = summary
                    if (readOnly && currentSummary != null) {
                        item { WorkoutSummaryCard(summary = currentSummary) }
                    }
                    items(exercises, key = { it.exerciseId }) { exercise ->
                        LogExerciseCard(
                            exercise = exercise,
                            readOnly = readOnly,
                            editableCompletedSets = editing,
                            onWeightChange = { index, value ->
                                screenModel.updateWeight(exercise.exerciseId, index, value)
                            },
                            onRepsChange = { index, value ->
                                screenModel.updateReps(exercise.exerciseId, index, value)
                            },
                            onSecondsChange = { index, value ->
                                screenModel.updateSeconds(exercise.exerciseId, index, value)
                            },
                            onToggleCompleted = { index ->
                                screenModel.toggleCompleted(exercise.exerciseId, index)
                            },
                            onAddSet = { screenModel.addSetRow(exercise.exerciseId) },
                            onRemoveSet = { screenModel.removeSetRow(exercise.exerciseId) },
                            onToggleTimed = { screenModel.toggleTimed(exercise.exerciseId) },
                            onToggleSkipped = { screenModel.toggleSkipped(exercise.exerciseId) },
                        )
                    }
                }
            }
        }

        if (showAbandonDialog) {
            AbandonWorkoutDialog(
                onConfirm = {
                    showAbandonDialog = false
                    screenModel.abandonWorkout()
                },
                onDismiss = { showAbandonDialog = false },
            )
        }

        if (showFinishDialog) {
            FinishWorkoutDialog(
                onConfirm = { note ->
                    showFinishDialog = false
                    screenModel.finishWorkout(note)
                },
                onDismiss = { showFinishDialog = false },
            )
        }

        if (showExtraDialog) {
            ExtraExerciseDialog(
                exercises = allExercises,
                existingIds = exercises.map { it.exerciseId }.toSet(),
                onPick = { exerciseId ->
                    showExtraDialog = false
                    screenModel.addExtraExercise(exerciseId)
                },
                onDismiss = { showExtraDialog = false },
            )
        }
    }
}

/**
 * 「添加计划外动作」与右侧主操作并排的半宽按钮条：
 * 训练中主操作是「结束训练」（红），编辑已结束的训练时是「完成编辑」（红）。
 */
@Composable
private fun ExtraActionBar(
    confirmText: String,
    onAddExtra: () -> Unit,
    onConfirm: () -> Unit,
) {
    ActionBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Button(
                onClick = onAddExtra,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.workout_add_extra))
            }
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = confirmText)
            }
        }
    }
}

/** 贴在底部的整宽按钮条。 */
@Composable
private fun ActionBar(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
        ) {
            content()
        }
    }
}

/** 休息倒计时归零时震动一下，提醒可以开始下一组。 */
@Composable
private fun rememberVibrateOnce(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrate: () -> Unit = { context.vibrateShort() }
        vibrate
    }
}

private fun Context.vibrateShort() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
}

private const val VIBRATION_MILLIS = 400L
