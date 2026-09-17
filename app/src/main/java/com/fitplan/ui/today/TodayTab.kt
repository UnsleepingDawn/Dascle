package com.fitplan.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.interactor.UpcomingTrainingPlan
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.plan.routine.targetText
import com.fitplan.ui.workout.WorkoutLogScreen
import com.fitplan.ui.workout.toClockText
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
        val loaded by screenModel.loaded.collectAsState()
        val upcomingPlan by screenModel.upcomingPlan.collectAsState()
        val startRoutineRequest by screenModel.startRoutineRequest.collectAsState()

        // 从训练记录页返回时本组合会重建，顺带刷新一次。
        LaunchedEffect(Unit) { screenModel.refresh() }

        // 休息日改用之后最近的方案之后，直接进训练记录页把今天的训练开起来。
        LaunchedEffect(startRoutineRequest) {
            val routineId = startRoutineRequest ?: return@LaunchedEffect
            screenModel.consumeStartRequest()
            navigator.push(WorkoutLogScreen(routineId = routineId))
        }

        val weekdayNames = stringArrayResource(R.array.weekday_names)

        // 今天的训练已经做完：计划卡片的「开始训练」置灰，下方改给一张「计划外训练」卡片。
        val finishedSession = todaySession?.takeIf { it.isFinished }

        // 鼓励语每次进今日页随机取一条，取完就固定，不跟着列表滚动换句子。
        val encouragements = stringArrayResource(R.array.today_encouragements)
        val encouragement = remember(encouragements) { encouragements.random() }

        // 没排进今日计划里的未完成训练（例如计划外训练）单独在顶部给一张卡片。
        val standaloneUnfinished = unfinished?.takeIf { session ->
            routines.none { it.routine.id == session.routineId }
        }

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

                // 今天被编排成休息日：整页换成休息页，「计划外训练」之类的入口一个都不给。
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
                    // 「今天休息」，把鼓励语与「计划外训练」的入口照常摆出来。
                    finishedSession?.let { session ->
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
    }
}

@Composable
private fun ScheduledRoutineCard(
    scheduled: ScheduledRoutine,
    isResuming: Boolean,
    startEnabled: Boolean,
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
            Text(
                text = scheduled.routine.name,
                style = MaterialTheme.typography.titleMedium,
            )
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = exercise.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = exercise.targetText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
 * 今天练完之后才出现的「计划外训练」卡片：与计划卡片同规格，但只给一个入口，
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

        // 问到「开练！」时寄语让位给下面的按钮。
        AnimatedVisibility(visible = shownStage < REST_LAST_STAGE) {
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
