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
    /** true 表示把这次已结束的训练重新置为进行中（今日页「还想练？」入口），新加内容追加到同一次训练。 */
    private val reopen: Boolean = false,
    /**
     * true 表示这是休息日「临时加一个方案」开出来的训练，只影响这场训练的名字（叫「临时方案」）。
     *
     * 撤掉 / 还原今天的休息日标记不靠这个参数：开始训练时一律 `ClearRestDay`，放弃时由
     * `RestoreRestDay` 自己判断今天还剩不剩训练——中途退出再点「继续训练」进来时这个标记早就丢了，
     * 只有库里的事实靠得住。
     */
    private val tempPlan: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<WorkoutLogScreenModel>()
        val phase by screenModel.phase.collectAsState()
        val sessionName by screenModel.sessionName.collectAsState()
        val exercises by screenModel.exercises.collectAsState()
        val items by screenModel.items.collectAsState()
        val rest by screenModel.rest.collectAsState()
        val summary by screenModel.summary.collectAsState()
        val restFinishedTick by screenModel.restFinishedTick.collectAsState()
        val exitTick by screenModel.exitTick.collectAsState()
        val allExercises by screenModel.allExercises.collectAsState()
        val progressHint by screenModel.progressHint.collectAsState()
        val progressTargetInput by screenModel.progressTargetInput.collectAsState()
        val progressTargetConfirm by screenModel.progressTargetConfirm.collectAsState()
        val collapsedIds by screenModel.collapsedExerciseIds.collectAsState()

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

        // 非空表示「减一组」减到最后一组，正在问要不要直接跳过这个动作。
        var lastSetSkipId by remember { mutableStateOf<Long?>(null) }

        val freeName = stringResource(
            if (tempPlan) R.string.today_rest_temp_plan else R.string.workout_free,
        )
        val readOnly = phase == WorkoutPhase.FINISHED && !editing

        // 一张动作卡：单独排的动作直接用它，动作组里挑中的动作作为子卡（nested）用它。
        val logExerciseCard: @Composable (LogExercise, Boolean) -> Unit = { exercise, nested ->
            LogExerciseCard(
                exercise = exercise,
                readOnly = readOnly,
                editableCompletedSets = editing,
                nested = nested,
                collapsed = exercise.exerciseId in collapsedIds,
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
                onRemoveSet = {
                    // 只剩一组且还没做时没有可减的组，改问要不要跳过这个动作。
                    if (exercise.sets.size == 1 && exercise.sets.none { it.completed }) {
                        lastSetSkipId = exercise.exerciseId
                    } else {
                        screenModel.removeSetRow(exercise.exerciseId)
                    }
                },
                onToggleSkipped = { screenModel.toggleSkipped(exercise.exerciseId) },
                onToggleCollapsed = { screenModel.toggleExerciseCollapsed(exercise.exerciseId) },
            )
        }
        val exercisesById = remember(exercises) { exercises.associateBy { it.exerciseId } }

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
                if (items.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(R.string.workout_plan_empty),
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    WorkoutPlanList(
                        items = items,
                        exercises = exercises,
                        contentPadding = contentPadding,
                    )
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
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is LogItem.Exercise -> {
                                val exercise = exercisesById[item.exerciseId]
                                if (exercise != null) logExerciseCard(exercise, false)
                            }

                            // 动作组卡：先在芯片行里挑动作，挑中的在卡内展开成子卡。
                            is LogItem.Group -> LogGroupCard(
                                group = item,
                                exercises = exercises,
                                readOnly = readOnly,
                                onTogglePick = { exerciseId ->
                                    if (exerciseId in item.pickedIds) {
                                        screenModel.unpickGroupExercise(item.groupId, exerciseId)
                                    } else {
                                        screenModel.pickGroupExercise(item.groupId, exerciseId)
                                    }
                                },
                            ) { exercise -> logExerciseCard(exercise, true) }
                        }
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

        // 「减一组」减到最后一组：确认后直接跳过这个动作。
        lastSetSkipId?.let { exerciseId ->
            SkipLastSetDialog(
                onConfirm = {
                    lastSetSkipId = null
                    screenModel.toggleSkipped(exerciseId)
                },
                onDismiss = { lastSetSkipId = null },
            )
        }

        // 渐进提示：先问要不要提高目标，选了方向填「增加到多少」，最后再确认一次才写库。
        progressHint?.let { hint ->
            ProgressHintDialog(
                hint = hint,
                onIncrease = screenModel::promptProgressIncrease,
                onLater = screenModel::dismissProgressHint,
                onSnooze = screenModel::snoozeProgressHint,
                onDismiss = screenModel::dismissProgressHint,
            )
        }

        progressTargetInput?.let { input ->
            ProgressTargetDialog(
                input = input,
                onConfirm = screenModel::confirmProgressTarget,
                onDismiss = screenModel::dismissProgressTargetInput,
            )
        }

        progressTargetConfirm?.let { confirm ->
            ProgressConfirmDialog(
                confirm = confirm,
                onConfirm = screenModel::applyProgressTarget,
                onDismiss = screenModel::dismissProgressTargetConfirm,
            )
        }
    }
}

/**
 * 「计划外动作」与右侧主操作并排的半宽按钮条：
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
