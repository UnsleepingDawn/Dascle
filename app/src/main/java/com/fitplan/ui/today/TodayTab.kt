package com.fitplan.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.interactor.UpcomingTrainingPlan
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.plan.routine.targetText
import com.fitplan.ui.workout.WorkoutLogScreen
import com.fitplan.ui.workout.toClockText
import com.fitplan.ui.workout.toWeightText
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.isoDayNumber

object TodayTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(R.string.tab_today),
            icon = rememberVectorPainter(Icons.Filled.Today),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<TodayScreenModel>()
        val date by screenModel.date.collectAsState()
        val routines by screenModel.routines.collectAsState()
        val restDay by screenModel.restDay.collectAsState()
        val unfinished by screenModel.unfinished.collectAsState()
        val todaySession by screenModel.todaySession.collectAsState()
        val todaySessionExercises by screenModel.todaySessionExercises.collectAsState()
        val todaySessionProgress by screenModel.todaySessionProgress.collectAsState()
        val loaded by screenModel.loaded.collectAsState()
        val upcomingPlan by screenModel.upcomingPlan.collectAsState()
        val nextRestDay by screenModel.nextRestDay.collectAsState()
        val startRoutineRequest by screenModel.startRoutineRequest.collectAsState()

        // 训练卡片右上角「今日休息」的选择框是否展开。
        var showRestDialog by remember { mutableStateOf(false) }

        // 从训练记录页返回时本组合会重建，顺带刷新一次。
        LaunchedEffect(Unit) { screenModel.refresh() }

        // 休息日改用之后最近的方案之后，直接进训练记录页把今天的训练开起来。
        LaunchedEffect(startRoutineRequest) {
            val routineId = startRoutineRequest ?: return@LaunchedEffect
            screenModel.consumeStartRequest()
            navigator.push(WorkoutLogScreen(routineId = routineId))
        }

        val weekdayNames = stringArrayResource(R.array.weekday_names)

        // 今天的训练已经做完：计划卡片的「开始训练」置灰，下方改给一张「还想练？」卡片。
        val finishedSession = todaySession?.takeIf { it.isFinished }

        // 鼓励语每次进今日页随机取一条，取完就固定，不跟着列表滚动换句子。
        val encouragements = stringArrayResource(R.array.today_encouragements)
        val encouragement = remember(encouragements) { encouragements.random() }

        // 没排进今日计划里的未完成训练（例如计划外训练）单独在顶部给一张卡片。
        val standaloneUnfinished = unfinished?.takeIf { session ->
            routines.none { it.routine.id == session.routineId }
        }

        // 今天这次已结束的训练没有对应的计划卡片（休息日「临时加一个方案」这类计划外训练）时，
        // 页面原本只剩鼓励语，看不到今天到底练了什么，这里补一张总结卡片放在鼓励语上面。
        // 练完的动作一条都没记录（组数全没勾）时不给空卡片。
        val standaloneFinished = finishedSession?.takeIf { session ->
            routines.none { it.routine.id == session.routineId }
        }
        val showSessionCard = standaloneFinished != null && todaySessionExercises.isNotEmpty()

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        Column {
                            Text(text = stringResource(R.string.today_title))
                            Text(
                                text = stringResource(
                                    R.string.today_date,
                                    // Month 枚举从 JANUARY(0) 开始，换算成 1..12
                                    date.month.ordinal + 1,
                                    date.day,
                                    weekdayNames[date.dayOfWeek.isoDayNumber - 1],
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                !loaded -> Unit

                // 今天被编排成休息日：整页换成休息页，「还想练？」之类的入口一个都不给。
                restDay -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    // 撑满剩余空间，让休息内容在整页里居中；下面有未完成的训练时它退到上半屏。
                    // 今天还没开练时小人图标可连点「升级」，最后给出两个开练的出口。
                    RestDayBlock(
                        interactive = unfinished == null && finishedSession == null,
                        upcomingPlan = upcomingPlan,
                        onUseUpcoming = screenModel::useUpcomingPlan,
                        onStartFreeWorkout = { navigator.push(WorkoutLogScreen(tempPlan = true)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    // 已经开始的训练仍留着入口，否则休息日会把用户关在这次训练之外。
                    unfinished?.let { session ->
                        UnfinishedSessionCard(
                            session = session,
                            onClickResume = { navigator.push(WorkoutLogScreen(sessionId = session.id)) },
                        )
                    }

                    // 休息日之前已经练完过一场（例如事后把今天改成了休息日）：别让今日页退成一片
                    // 「今天休息」，把训练总结、鼓励语与「还想练？」的入口照常摆出来。
                    finishedSession?.let { session ->
                        if (showSessionCard) {
                            TodaySessionCard(
                                name = session.name,
                                exercises = todaySessionExercises,
                                progress = todaySessionProgress,
                                modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                            )
                        }
                        Text(
                            text = encouragement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.padding.large,
                                    vertical = MaterialTheme.padding.small,
                                ),
                        )
                        ExtraWorkoutCard(
                            modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
                            onClick = { navigator.push(WorkoutLogScreen(sessionId = session.id, reopen = true)) },
                        )
                    }
                }

                routines.isNotEmpty() || standaloneUnfinished != null || finishedSession != null -> {
                    LazyColumn(
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        standaloneUnfinished?.let { session ->
                            item(key = "unfinished") {
                                UnfinishedSessionCard(
                                    session = session,
                                    onClickResume = { navigator.push(WorkoutLogScreen(sessionId = session.id)) },
                                )
                            }
                        }

                        items(routines, key = { it.routine.id }) { scheduled ->
                            val resumable = unfinished?.takeIf { it.routineId == scheduled.routine.id }
                            ScheduledRoutineCard(
                                scheduled = scheduled,
                                isResuming = resumable != null,
                                // 今天的训练已经结束，就不再从计划卡片开新的一次训练。
                                startEnabled = resumable != null || finishedSession == null,
                                // 练完了就没有「今日休息」可言，只有还没结束的今天才给这个入口。
                                restEnabled = finishedSession == null,
                                onRestToday = { showRestDialog = true },
                                // 今天这次训练就是照着这张计划练的：标出各动作练到哪了。
                                progress = todaySessionProgress
                                    ?.takeIf { it.routineId == scheduled.routine.id },
                                onClick = {
                                    if (resumable != null) {
                                        navigator.push(WorkoutLogScreen(sessionId = resumable.id))
                                    } else {
                                        navigator.push(WorkoutLogScreen(routineId = scheduled.routine.id))
                                    }
                                },
                            )
                        }

                        if (finishedSession != null) {
                            if (showSessionCard) {
                                item(key = "session") {
                                    TodaySessionCard(
                                        name = finishedSession.name,
                                        exercises = todaySessionExercises,
                                        progress = todaySessionProgress,
                                    )
                                }
                            }
                            item(key = "encouragement") {
                                Text(
                                    text = encouragement,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = MaterialTheme.padding.large),
                                )
                            }
                            item(key = "extra") {
                                ExtraWorkoutCard(
                                    onClick = {
                                        navigator.push(
                                            WorkoutLogScreen(sessionId = finishedSession.id, reopen = true),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                loaded -> EmptyScreen(
                    message = stringResource(R.string.today_empty),
                    modifier = Modifier.padding(contentPadding),
                )

                else -> Unit
            }
        }

        if (showRestDialog) {
            TodayRestDialog(
                nextRestDay = nextRestDay,
                // 今天这次训练还没结束：确认顺延时先把这次训练作废。
                hasOngoingSession = todaySession?.let { !it.isFinished } == true,
                onConfirm = { mode ->
                    showRestDialog = false
                    screenModel.restToday(mode)
                },
                onDismiss = { showRestDialog = false },
            )
        }
    }
}

@Composable
private fun ScheduledRoutineCard(
    scheduled: ScheduledRoutine,
    isResuming: Boolean,
    startEnabled: Boolean,
    restEnabled: Boolean,
    onRestToday: () -> Unit,
    progress: TodaySessionProgress?,
    onClick: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            // 方案名占满整行，右上角留给「今日休息」；名字太长时只挤自己，不把按钮顶出卡片。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scheduled.routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (restEnabled) {
                    FilledTonalButton(
                        onClick = onRestToday,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = RestTodayColor,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.padding.small,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.today_rest_button),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (scheduled.routine.note.isNotBlank()) {
                Text(
                    text = scheduled.routine.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.today_exercise_count, scheduled.exercises.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            scheduled.exercises.forEach { exercise ->
                TodayExerciseRow(
                    name = exercise.exerciseName,
                    trailing = exercise.targetText(),
                    status = progress?.statusOf(exercise) ?: TodayExerciseStatus.PLANNED,
                )
            }

            Button(
                onClick = onClick,
                enabled = startEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(
                    text = stringResource(
                        if (isResuming) R.string.workout_continue else R.string.workout_start,
                    ),
                )
            }
        }
    }
}

/**
 * 今日卡片里一个动作的训练状态：
 * [TRAINED] 练过（有已完成组），[NOT_DONE] 跳过或动作组里没挑中，[PLANNED] 计划里还没轮到。
 */
private enum class TodayExerciseStatus { TRAINED, NOT_DONE, PLANNED }

/**
 * 判定计划里一个动作今天的训练状态。只有今天这场训练确实照这张计划练（`routineId` 对上）时才会问到这里。
 * 动作组里没挑中的动作也算「不用做」，与跳过一样打删除线。
 */
private fun TodaySessionProgress.statusOf(exercise: RoutineExercise): TodayExerciseStatus = when {
    exercise.exerciseId in completedExerciseIds -> TodayExerciseStatus.TRAINED
    exercise.exerciseId in skippedExerciseIds -> TodayExerciseStatus.NOT_DONE
    exercise.groupId != null && exercise.exerciseId !in pickedExerciseIds -> TodayExerciseStatus.NOT_DONE
    else -> TodayExerciseStatus.PLANNED
}

/** 动作名前那颗「已练」勾的尺寸；没练的动作也占同样宽度，好让动作名对齐。 */
private val TrainedCheckSize = 18.dp

/**
 * 今日页一个动作一行：左边动作名、右边目标或实际训练量。
 *
 * [TodayExerciseStatus.TRAINED] 动作名前一颗主色（蓝）圆圈勾，表示今天已经练过；
 * [TodayExerciseStatus.NOT_DONE] 只有动作名打删除线并转灰，表示跳过或组内没挑中、今天不用做。
 * 右侧的训练量只跟着变灰，不打删除线。
 */
@Composable
private fun TodayExerciseRow(
    name: String,
    trailing: String,
    status: TodayExerciseStatus,
) {
    val struck = status == TodayExerciseStatus.NOT_DONE
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == TodayExerciseStatus.TRAINED) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.today_exercise_trained),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(TrainedCheckSize),
            )
        } else {
            // 没练的动作也占好勾的位置，同一张卡片里的动作名才对得齐。
            Spacer(modifier = Modifier.size(TrainedCheckSize))
        }
        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (struck) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 今天这场训练没有对应的计划卡片（休息日「临时加一个方案」这类计划外训练的入口是「开始训练」，
 * 练完之后原先只在页面上留一句鼓励语）时，在鼓励语上面补一张总结卡片，
 * 让用户看得到今天到底练了什么：方案名 + 动作数 + 每个动作的实际训练量。
 *
 * 版式与计划卡片同规格，但只展示、不给入口。[progress] 非空表示这张卡就是今天这场训练的总结，
 * 卡里列的都是练过的动作，整行按「已训练」带勾渲染，与计划卡片的标记一致。
 */
@Composable
private fun TodaySessionCard(
    name: String,
    exercises: List<TodaySessionExercise>,
    progress: TodaySessionProgress?,
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
                text = name.ifBlank { stringResource(R.string.workout_free) },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.today_exercise_count, exercises.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            exercises.forEach { exercise ->
                TodayExerciseRow(
                    name = exercise.name,
                    trailing = exercise.volumeText(),
                    status = if (progress != null) TodayExerciseStatus.TRAINED else TodayExerciseStatus.PLANNED,
                )
            }
        }
    }
}

/** 总结卡片里一个动作的实际训练量，写法与计划卡片的 `targetText()` 一致：组数 × 次数 · 重量。 */
@Composable
private fun TodaySessionExercise.volumeText(): String {
    val base = if (isTimed) {
        stringResource(R.string.routine_edit_targets_timed, completedSets, seconds ?: 0)
    } else {
        stringResource(R.string.routine_edit_targets, completedSets, reps ?: 0)
    }
    val weightText = weight
        ?.takeIf { showsWeight }
        ?.let { value ->
            if (weightIsAssistance) {
                stringResource(R.string.weight_kg_assist, value.toWeightText())
            } else {
                stringResource(R.string.weight_kg, value.toWeightText())
            }
        }
    return listOfNotNull(base, weightText).joinToString(" · ")
}

/**
 * 今天练完之后才出现的「还想练？」卡片：与计划卡片同规格，但只给一个入口，
 * 点进去把今天的训练重新变成进行中，往里加的动作都追加到同一次训练上。
 */
@Composable
private fun ExtraWorkoutCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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
                text = stringResource(R.string.today_extra_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.today_start_extra))
            }
        }
    }
}

/** 上次没练完就退出的训练，点一下接着练。 */
@Composable
private fun UnfinishedSessionCard(
    session: WorkoutSession,
    onClickResume: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = stringResource(R.string.workout_unfinished),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = session.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.workout_started_at, session.startedAt.toClockText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClickResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                )
                Text(text = stringResource(R.string.workout_continue))
            }
        }
    }
}

/**
 * 今天是休息日：居中给一句休息的话。
 *
 * [interactive] 为 true（今天还没开练）时，小人图标可以连点三下「升级」——
 * 图标 →「不想休息？」→「还想练？」→「开练！」，字号一步步变大，最后浮出两个开练的出口：
 * 「使用明天的方案」把之后最近一次训练挪到今天，或「临时加一个方案」直接开一场计划外训练。
 * 不满足条件时退回静态的休息页，不摆这些出口。
 */
@Composable
private fun RestDayBlock(
    interactive: Boolean,
    upcomingPlan: UpcomingTrainingPlan?,
    onUseUpcoming: () -> Unit,
    onStartFreeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 寄语每次进今日页随机取一条，进来就固定。这里不能用 stringArrayResource 的结果当
    // remember 的 key——它每次重组都返回新数组，点一下图标就会换一句。
    val messages = stringArrayResource(R.array.today_rest_encouragements)
    val message = remember { messages.random() }

    // 点了几次图标。用 rememberSaveable：转屏、切到别的 Tab 再回来都停在原来那一档，
    // 不会把已经点出来的「开练！」又收回去。
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var showUpcomingDialog by remember { mutableStateOf(false) }
    // 条件不满足时按第一档渲染，但不动 stage，免得条件一变回就跳过中间几档。
    val shownStage = if (interactive) stage else 0

    Column(
        modifier = modifier.padding(horizontal = MaterialTheme.padding.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = shownStage,
            transitionSpec = {
                (fadeIn(tween(REST_STAGE_ANIM_MILLIS)) + scaleIn(initialScale = 0.7f))
                    .togetherWith(fadeOut(tween(REST_STAGE_ANIM_MILLIS)))
            },
            contentAlignment = Alignment.Center,
            label = "restDayEscalate",
        ) { current ->
            if (current == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.SelfImprovement,
                        contentDescription = if (interactive) {
                            stringResource(R.string.today_rest_tap_hint)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(enabled = interactive) { stage = 1 },
                        tint = RestDayAccentColor,
                    )
                    Text(
                        text = stringResource(R.string.today_rest_title),
                        modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            } else {
                Text(
                    text = stringResource(
                        when (current) {
                            1 -> R.string.today_rest_ask_one
                            2 -> R.string.today_rest_ask_two
                            else -> R.string.today_rest_go
                        },
                    ),
                    // 字号一档比一档大，最后「开练！」占满一行。
                    style = when (current) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineLarge
                        else -> MaterialTheme.typography.displayMedium
                    },
                    color = RestDayAccentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(enabled = interactive && current < REST_LAST_STAGE) { stage = current + 1 }
                        .padding(MaterialTheme.padding.medium),
                )
            }
        }

        // 问到「开练！」时寄语让位给下面的按钮。退场不做过场：这一档要寄语当帧就消失，
        // 否则淡出的那句话会和稍后冒出来的按钮叠在同一屏上。
        AnimatedVisibility(
            visible = shownStage < REST_LAST_STAGE,
            exit = ExitTransition.None,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // 稍晚一点淡入，做出「先出现开练、再冒按钮」的层次。
        AnimatedVisibility(
            visible = shownStage == REST_LAST_STAGE,
            enter = fadeIn(tween(REST_STAGE_ANIM_MILLIS, delayMillis = REST_STAGE_ANIM_MILLIS)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.padding.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Button(
                    onClick = { showUpcomingDialog = true },
                    // 之后没有训练安排时没东西可搬，只能走下面的临时方案。
                    enabled = upcomingPlan != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.today_rest_use_upcoming))
                }
                if (upcomingPlan == null) {
                    Text(
                        text = stringResource(R.string.today_rest_no_upcoming),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = onStartFreeWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.today_rest_start_free))
                }
            }
        }
    }

    // 把之后最近的安排挪到今天会动到整份日历，先问一句再执行。
    val plan = upcomingPlan
    if (showUpcomingDialog && plan != null) {
        AlertDialog(
            onDismissRequest = { showUpcomingDialog = false },
            title = { Text(text = stringResource(R.string.today_rest_upcoming_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.today_rest_upcoming_message,
                        plan.routineName,
                        stringResource(R.string.date_month_day, plan.date.month.ordinal + 1, plan.date.day),
                        plan.dayOffset,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpcomingDialog = false
                        onUseUpcoming()
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpcomingDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 休息页三档升级的档数：0 小人图标，1「不想休息？」，2「还想练？」，3「开练！」+ 两个出口。 */
private const val REST_LAST_STAGE = 3

/** 休息页每档之间的淡入淡出时长（毫秒）。 */
private const val REST_STAGE_ANIM_MILLIS = 220

/** 休息页的小人图标与「开练！」文字的颜色：深蓝色，不跟随主题的 tertiary（绿／粉／灰）。 */
private val RestDayAccentColor = Color(0xFF1565C0)

/** 训练卡片右上角「今日休息」按钮的颜色：绿色。 */
private val RestTodayColor = Color(0xFF2E7D32)
