package com.fitplan.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
import com.fitplan.presentation.util.Tab
import com.fitplan.reminder.RestState
import com.fitplan.ui.common.DialogButtonColumn
import com.fitplan.ui.common.DialogCancelButton
import com.fitplan.ui.common.DialogPrimaryButton
import com.fitplan.ui.plan.routine.targetText
import com.fitplan.ui.workout.WorkoutLogScreen
import com.fitplan.ui.workout.toClockText
import com.fitplan.ui.workout.toWeightText
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        val rest by screenModel.rest.collectAsState()
        val restFinishedTick by screenModel.restFinishedTick.collectAsState()

        // 训练卡片右上角「今日休息」的选择框是否展开。
        var showRestDialog by remember { mutableStateOf(false) }

        // 从训练记录页返回时本组合会重建，顺带刷新一次。
        LaunchedEffect(Unit) { screenModel.refresh() }

        // 休息日改用之后最近的计划之后，直接进训练记录页把今天的训练开起来。
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

        // 今天这次已结束的训练没有对应的计划卡片（休息日「临时加一场训练」这类计划外训练）时，
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
                            progress = todaySessionProgress?.takeIf { it.routineId == session.routineId },
                            rest = rest.takeIf { todaySession?.id == session.id },
                            restFinishedTick = restFinishedTick,
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

                // 既没有休息、其余情况都按计划卡片渲染（今天没排期但有训练记录时会走这里的
                // 「未完成的训练」或「还想练？」卡片）。
                else -> {
                    LazyColumn(
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        standaloneUnfinished?.let { session ->
                            item(key = "unfinished") {
                                UnfinishedSessionCard(
                                    session = session,
                                    progress = todaySessionProgress?.takeIf { it.routineId == session.routineId },
                                    rest = rest.takeIf { todaySession?.id == session.id },
                                    restFinishedTick = restFinishedTick,
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
                                // 组间休息只挂在正在训练的那张计划卡片上；找不到对应卡片的
                                // 计划外训练不显示（那张卡片本来也不是计划卡片）。
                                rest = rest.takeIf { todaySessionProgress?.routineId == scheduled.routine.id },
                                restFinishedTick = restFinishedTick,
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
    rest: RestState?,
    restFinishedTick: Int,
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
            // 计划名占满整行，右侧留给「今日休息」或休息中的「进度条 + 闹钟」；
            // 名字太长时只挤自己，不把右端顶出卡片。
            CardTitleRow(
                reserveTrailing = restEnabled || rest != null,
                title = {
                    Text(
                        text = scheduled.routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailing = {
                    RestSlot(
                        rest = rest,
                        finishedTick = restFinishedTick,
                        restEnabled = restEnabled,
                        onRestToday = onRestToday,
                    )
                },
            )
            val blocks = scheduled.exercises.todayBlocksForCard(progress)
            val extraExercises = progress?.extraExercises.orEmpty()
            Text(
                text = stringResource(
                    R.string.today_exercise_count,
                    blocks.sumOf { it.exercises.size } + extraExercises.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            blocks.forEach { block ->
                TodayExerciseBlockRow(block = block, progress = progress)
            }

            // 训练中临时加、且已经练过至少一组的动作：跟在计划动作后面，动作名后标「临时」。
            extraExercises.forEach { exercise ->
                TodayExerciseRow(
                    name = exercise.name,
                    trailing = exercise.volumeText(),
                    status = TodayExerciseStatus.TRAINED,
                    isExtra = true,
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
 * 计划卡片标题行：左边计划名，右边一块**贴右边缘**的区域（休息中的「进度条 + 闹钟」或「今日休息」按钮）。
 *
 * 不能直接用 `Row` + 两个 `weight(1f)`：标题用不完的那一半不会自动让给右端，右端只拿到自己那半，
 * 进度条与闹钟就停在行中间、到不了右边。这里先量标题的自然宽度（最多占「整行减去右端最小宽度」），
 * 右端再占满剩下的整段宽度，于是闹钟始终顶格、进度条正好铺在标题与闹钟之间。
 *
 * 行高固定为 [TODAY_CARD_HEADER_HEIGHT] 与「标题行高」中的较大者。右端的两种形态（休息指示 / 按钮）
 * 都比这个高度矮，于是「休息结束、按钮回来」时行高不变，卡片高度也就不会跳。
 *
 * [reserveTrailing] 为 false（没有右端内容）时只铺标题，不白白留出右端那截。
 */
@Composable
private fun CardTitleRow(
    reserveTrailing: Boolean,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    if (!reserveTrailing) {
        Box(modifier = Modifier.fillMaxWidth()) { title() }
        return
    }
    Layout(
        content = {
            Box { title() }
            trailing()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TODAY_CARD_HEADER_HEIGHT),
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val titleMax = (maxWidth - CARD_TRAILING_MIN_WIDTH.roundToPx()).coerceAtLeast(0)
        val titlePlaceable = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = titleMax))
        val trailingWidth = (maxWidth - titlePlaceable.width).coerceAtLeast(0)
        val trailingPlaceable = measurables[1].measure(
            constraints.copy(minWidth = trailingWidth, maxWidth = trailingWidth),
        )
        // 下限取 heightIn 给进来的 minHeight：右端换形态时行高不变。
        val height = maxOf(titlePlaceable.height, trailingPlaceable.height, constraints.minHeight)
        layout(maxWidth, height) {
            titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
            trailingPlaceable.placeRelative(titlePlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}

/**
 * 计划卡片标题行右端的那块区域：休息中显示「进度条 + 闹钟」，否则给「今日休息」按钮。
 *
 * [rest] 由 app 级的休息计时器共享，所以从训练记录页退出来之后这里仍能看到倒计时在走。
 * 归零后计时器还会保留约两秒「休息结束」的状态，期间闹钟摇摆；等状态真的清空，
 * [AnimatedVisibility] 的退场把整块缩小淡化收掉，收干净了（[MutableTransitionState.isIdle]）
 * 「今日休息」才重新出现，免得两者叠在一起。
 */
@Composable
private fun RestSlot(
    rest: RestState?,
    finishedTick: Int,
    restEnabled: Boolean,
    onRestToday: () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = rest != null

    // 退场期间 rest 已经变成 null，但内容还要继续按最后一个非空值渲染，所以缓存下来。
    var lastRest by remember { mutableStateOf(rest) }
    if (rest != null) lastRest = rest

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(REST_INDICATOR_ENTER_MILLIS)),
            exit = fadeOut(tween(REST_INDICATOR_EXIT_MILLIS)) +
                scaleOut(tween(REST_INDICATOR_EXIT_MILLIS), targetScale = REST_INDICATOR_EXIT_SCALE),
        ) {
            lastRest?.let { RestIndicator(rest = it, finishedTick = finishedTick) }
        }
        if (restEnabled && rest == null && visibleState.isIdle) {
            RestTodayButton(onRestToday)
        }
    }
}

/**
 * 休息指示：一段从满到空的进度条，右端一颗闹钟。进度条占满标题与闹钟之间的区域。
 *
 * 到点（[finishedTick] 自增）时按下面这套动作收尾：
 *
 * 1. 进度条快速淡化消失；
 * 2. **同时**闹钟开始左右摇摆，并较快地横移到「进度条那一段的中心」（不是卡片中心），
 *    一边移一边放大到 1.5 倍——放大走 `graphicsLayer` 的绘制缩放，不参与布局，
 *    所以不会把标题行、卡片顶高；移到中心即停止放大；
 * 3. 摇摆满 1.5 秒后，闹钟在原地（保持放大倍数）淡化消失。
 *
 * 之后的清场由 [RestSlot] 的 `AnimatedVisibility` 退场负责，那时内容已经透明，看不见。
 */
@Composable
private fun RestIndicator(
    rest: RestState,
    finishedTick: Int,
) {
    // 摇摆角度、放大倍数、横移距离、进度条透明度、闹钟透明度，全部用绘制层做，不动布局。
    val angle = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val shift = remember { Animatable(0f) }
    val barAlpha = remember { Animatable(1f) }
    val iconAlpha = remember { Animatable(1f) }
    // 记下进来时的归零计数：同一场休息反复组合时不要重放动画，只在计数真的变化时播。
    var seenTick by remember { mutableIntStateOf(finishedTick) }

    // 进度条那一段的宽度（含它左右的内边距）与闹钟宽度，用来算「闹钟移到进度条中心」要走多远。
    var barWidth by remember { mutableIntStateOf(0) }
    var iconWidth by remember { mutableIntStateOf(0) }
    // 进度条的视觉中心就在它那一段的正中（左右内边距相等）；闹钟中心原本在它右侧，
    // 所以要左移「两段宽度和的一半」才落到进度条中心。
    val shiftTarget = -((barWidth + iconWidth) / 2f)

    LaunchedEffect(finishedTick) {
        if (finishedTick == seenTick) return@LaunchedEffect
        seenTick = finishedTick
        angle.snapTo(0f)
        scale.snapTo(1f)
        shift.snapTo(0f)
        barAlpha.snapTo(1f)
        iconAlpha.snapTo(1f)

        coroutineScope {
            // 进度条快速淡出。
            launch { barAlpha.animateTo(0f, tween(REST_BAR_FADE_MILLIS)) }
            // 横移与放大同时起、同时止：移到进度条中心的那一刻放大也到位，之后保持。
            launch {
                shift.animateTo(
                    targetValue = shiftTarget,
                    animationSpec = tween(REST_ALARM_MOVE_MILLIS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                scale.animateTo(
                    targetValue = REST_ALARM_END_SCALE,
                    animationSpec = tween(REST_ALARM_MOVE_MILLIS, easing = FastOutSlowInEasing),
                )
            }
            // 摇摆与上面同时开始：摇 [REST_WOBBLE_SWINGS] 个来回，最后一下回正，
            // 合计 (REST_WOBBLE_SWINGS + 1) × REST_WOBBLE_SWING_MILLIS = 1.5 秒。
            launch {
                repeat(REST_WOBBLE_SWINGS) { index ->
                    angle.animateTo(
                        targetValue = if (index % 2 == 0) REST_WOBBLE_ANGLE else -REST_WOBBLE_ANGLE,
                        animationSpec = tween(REST_WOBBLE_SWING_MILLIS, easing = LinearEasing),
                    )
                }
                angle.animateTo(0f, animationSpec = tween(REST_WOBBLE_SWING_MILLIS, easing = LinearEasing))
            }
        }
        // 摇完再淡化消失；位置与放大倍数就停在这儿。
        iconAlpha.animateTo(0f, tween(REST_ALARM_FADE_MILLIS))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { barWidth = it.width },
        ) {
            LinearProgressIndicator(
                progress = {
                    if (rest.totalSeconds <= 0) 0f else rest.remainingSeconds.toFloat() / rest.totalSeconds
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // 左右各留出间距，别贴着计划名、也别贴着闹钟。
                    .padding(horizontal = MaterialTheme.padding.small)
                    .graphicsLayer { alpha = barAlpha.value },
            )
        }
        Icon(
            imageVector = Icons.Filled.Alarm,
            contentDescription = stringResource(R.string.today_rest_counting),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(REST_ALARM_SIZE)
                .onSizeChanged { iconWidth = it.width }
                .graphicsLayer {
                    // 这三个都是绘制期的变换，不改变布局尺寸，所以放大不会顶高卡片。
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = shift.value
                    rotationZ = angle.value
                    alpha = iconAlpha.value
                },
        )
    }
}

/** 卡片标题行右端的「今日休息」按钮。 */
@Composable
private fun RestTodayButton(onClick: () -> Unit) {
    // M3 按钮默认容器 40dp、最小触控区 48dp，摆在卡片头部会比计划名高出一截，还会在出现 / 消失时
    // 顶动整张卡片的高度。这里连容器带最小触控区一起收到 [TODAY_CARD_HEADER_HEIGHT]。
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides TODAY_CARD_HEADER_HEIGHT,
    ) {
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = RestTodayColor,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
            modifier = Modifier.heightIn(min = TODAY_CARD_HEADER_HEIGHT),
        ) {
            Text(
                text = stringResource(R.string.today_rest_button),
                maxLines = 1,
            )
        }
    }
}

/**
 * 今日卡片里一个动作的训练状态：
 * [TRAINED] 练过（有已完成组），[NOT_DONE] 单独排列的动作被「跳过动作」，[PLANNED] 计划里还没轮到。
 */
private enum class TodayExerciseStatus { TRAINED, NOT_DONE, PLANNED }

/**
 * 判定计划里一个动作今天的训练状态。只有今天这场训练确实照这张计划练（`routineId` 对上）时才会问到这里。
 *
 * 只有单独排列的动作才会打删除线；动作组里的成员一律不打（组内该做哪几个由数量规则决定，
 * 见 [todayBlocksForCard]），所以这里对组内动作不再看跳过 / 挑中。
 */
private fun TodaySessionProgress.statusOf(exercise: RoutineExercise): TodayExerciseStatus = when {
    exercise.exerciseId in completedExerciseIds -> TodayExerciseStatus.TRAINED
    exercise.groupId == null && exercise.exerciseId in skippedExerciseIds -> TodayExerciseStatus.NOT_DONE
    else -> TodayExerciseStatus.PLANNED
}

/**
 * 计划卡片里的一块动作：单独排列的动作各成一块，同一动作组的成员并成一块。
 * 是不是动作组由 [exercises] 的第一个动作的 `groupId` 决定——组内成员这一项必然一起排列。
 */
private data class TodayExerciseBlock(val exercises: List<RoutineExercise>) {
    /** 同组的动作要圈在同一个暗色圆角框里；单独排的动作不圈。 */
    val isGroup: Boolean get() = exercises.first().groupId != null
}

/**
 * 把计划里的动作切成若干块：顺序扫一遍，遇到相同的 `groupId` 就并进当前块。
 *
 * 动作按顶层项的顺序展开（`RoutineRepository.getExercises` 把每个动作组的成员连着吐出来），
 * 所以组内成员在列表里是连续的，一个 `groupId` 只会对应一块。
 */
private fun List<RoutineExercise>.toTodayBlocks(): List<TodayExerciseBlock> {
    val blocks = mutableListOf<TodayExerciseBlock>()
    var index = 0
    while (index < size) {
        val groupId = this[index].groupId
        val members = mutableListOf(this[index])
        index++
        if (groupId != null) {
            while (index < size && this[index].groupId == groupId) {
                members += this[index]
                index++
            }
        }
        blocks += TodayExerciseBlock(members)
    }
    return blocks
}

/**
 * 计划卡片最终要渲染的动作块：在 [toTodayBlocks] 切好块之后，按今天这场训练的进度过滤，再交回去逐块绘制。
 *
 * 规则：
 * - 训练中被「移除」的动作一律剔掉，组被剔空就整块不要，单独排的动作被移除也整块不要；
 * - 动作组在下面两种情况下只保留练过的成员（[TodaySessionProgress.completedExerciseIds]），
 *   一个都没练就整组不要：
 *   1. 训练**已结束**——练完了就不再用删除线罗列没做的动作；
 *   2. 训练**进行中**且这一组已经做够「建议做 x 个」（练过的成员数 >= `groupMaxPicks`）。
 * - 除此之外（进行中还没做够）显示组的全部成员，都不打删除线；单独排的动作保持原样，
 *   没做的仍然打删除线。
 *
 * `progress` 为 null（今天还没照着这张计划开练）时原样返回。调用方保证 `progress` 只在这张
 * 卡片确实对应今天这场训练时传进来（`routineId` 对上），所以这里不用再比对计划 id。
 */
private fun List<RoutineExercise>.todayBlocksForCard(
    progress: TodaySessionProgress?,
): List<TodayExerciseBlock> {
    val blocks = toTodayBlocks()
    if (progress == null) return blocks
    return blocks.mapNotNull { block ->
        val kept = block.exercises.filterNot { it.exerciseId in progress.excludedExerciseIds }
        val trained = kept.filter { it.exerciseId in progress.completedExerciseIds }
        val maxPicks = block.exercises.first().groupMaxPicks
        val collapseToTrained = block.isGroup &&
            (progress.finished || (maxPicks != null && trained.size >= maxPicks))
        val shown = if (collapseToTrained) trained else kept
        shown.takeIf { it.isNotEmpty() }?.let { TodayExerciseBlock(it) }
    }
}

/**
 * 计划卡片里一块动作：单独排的动作直接铺一行；同一动作组的成员套一层与卡片底色拉开深浅的
 * 圆角底，一眼看出它们是「选着练」的一组。
 */
@Composable
private fun TodayExerciseBlockRow(
    block: TodayExerciseBlock,
    progress: TodaySessionProgress?,
) {
    if (!block.isGroup) {
        TodayPlanExerciseRow(block.exercises.single(), progress)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 外距离让相邻两块之间留出缝，暗色底不至于上下贴成一片。
            .padding(vertical = MaterialTheme.padding.extraSmall)
            .clip(MaterialTheme.shapes.large)
            .background(groupContainerColor())
            .padding(MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        block.exercises.forEach { exercise ->
            TodayPlanExerciseRow(exercise, progress)
        }
    }
}

/**
 * 动作组底色的叠色比例：`onSurface` 按这个透明度盖在卡片色上，得到比卡片「稍暗」的一层。
 *
 * 不能直接用 `surfaceContainerHigh`：本主题浅色模式下它是 #FCF7FF，比 ElevatedCard 的底色
 * `surfaceContainerLow`（#F7F2FA）还亮，铺上去会是「更浅」而不是更暗；`surfaceContainerHighest`
 * 同值，`surfaceContainer` 也几乎看不出差别，单色主题下更是全白 / 全黑。
 * 这里沿用记录页「撤销」按钮的做法（见 `WorkoutLogComponents.undoContainerColor`），
 * 让底色随 `onSurface` 自己算：浅色模式叠出浅灰、深色模式叠出更亮的一层，各套配色都拉得开。
 */
private const val GROUP_CONTAINER_ALPHA = 0.06f

@Composable
private fun groupContainerColor(): Color = MaterialTheme.colorScheme.onSurface
    .copy(alpha = GROUP_CONTAINER_ALPHA)
    .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

/** 计划卡片里的一个计划动作：目标文案与今天的训练状态都由 [RoutineExercise] 现算。 */
@Composable
private fun TodayPlanExerciseRow(
    exercise: RoutineExercise,
    progress: TodaySessionProgress?,
) {
    TodayExerciseRow(
        name = exercise.exerciseName,
        trailing = exercise.targetText(),
        status = progress?.statusOf(exercise) ?: TodayExerciseStatus.PLANNED,
    )
}

/** 动作名前那颗「已练」勾的尺寸；没练的动作也占同样宽度，好让动作名对齐。 */
private val TrainedCheckSize = 18.dp

/**
 * 今日页一个动作一行：左边动作名、右边目标或实际训练量。
 *
 * [TodayExerciseStatus.TRAINED] 动作名前一颗主色（蓝）圆圈勾，表示今天已经练过；
 * [TodayExerciseStatus.NOT_DONE] 只有动作名打删除线并转灰，表示这个单独排列的动作被「跳过动作」、今天不用做。
 * 右侧的训练量只跟着变灰，不打删除线。[isExtra] 为 true 时动作名后跟一个主色小字「临时」，
 * 表示这是训练中临时加进来的动作，不在计划编排里。
 */
@Composable
private fun TodayExerciseRow(
    name: String,
    trailing: String,
    status: TodayExerciseStatus,
    isExtra: Boolean = false,
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
        // 外层占满剩余宽度，内层两个元素贴靠：让「临时」紧跟动作名，训练量仍然贴右边缘。
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (struck) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isExtra) {
                Text(
                    text = stringResource(R.string.today_extra_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = MaterialTheme.padding.small),
                )
            }
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 今天这场训练没有对应的计划卡片（休息日「临时加一场训练」这类计划外训练的入口是「开始训练」，
 * 练完之后原先只在页面上留一句鼓励语）时，在鼓励语上面补一张总结卡片，
 * 让用户看得到今天到底练了什么：计划名 + 动作数 + 每个动作的实际训练量。
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

/**
 * 还没结束的训练（含休息日「临时加一场训练」这类计划外训练），点一下接着练。
 *
 * 与计划卡片**同地位**：标题就是这次训练的名字（计划外训练叫「临时训练」），
 * 右侧同样能放下组间休息的「进度条 + 闹钟」；已经练过的动作照计划卡片的样式铺出来。
 */
@Composable
private fun UnfinishedSessionCard(
    session: WorkoutSession,
    progress: TodaySessionProgress?,
    rest: RestState?,
    restFinishedTick: Int,
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
            // 计划名占满整行，右侧留给休息中的「进度条 + 闹钟」；名字太长时只挤自己。
            CardTitleRow(
                reserveTrailing = rest != null,
                title = {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailing = {
                    RestSlot(
                        rest = rest,
                        finishedTick = restFinishedTick,
                        restEnabled = false,
                        onRestToday = {},
                    )
                },
            )
            Text(
                text = stringResource(R.string.workout_started_at, session.startedAt.toClockText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 计划外训练没有计划编排可比，练过的动作都在 extraExercises 里，全部铺出来。
            progress?.extraExercises?.forEach { exercise ->
                TodayExerciseRow(
                    name = exercise.name,
                    trailing = exercise.volumeText(),
                    status = TodayExerciseStatus.TRAINED,
                    isExtra = true,
                )
            }

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
 * 「使用明天的计划」把之后最近一次训练挪到今天，或「临时加一场训练」直接开一场计划外训练。
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
                    // 之后没有训练安排时没东西可搬，只能走下面的临时训练。
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
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                    Text(
                        text = stringResource(
                            R.string.today_rest_upcoming_message,
                            plan.routineName,
                            stringResource(R.string.date_month_day, plan.date.month.ordinal + 1, plan.date.day),
                            plan.dayOffset,
                        ),
                    )
                    DialogButtonColumn {
                        DialogPrimaryButton(
                            text = stringResource(R.string.action_ok),
                            onClick = {
                                showUpcomingDialog = false
                                onUseUpcoming()
                            },
                        )
                        DialogCancelButton(onClick = { showUpcomingDialog = false })
                    }
                }
            },
            confirmButton = {},
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

/**
 * 卡片标题行的高度（也是「今日休息」按钮的容器与最小触控区高度）。
 *
 * 取值只求「比 M3 默认的 40dp / 48dp 矮、又不比计划名的行高（24dp）高出太多」。
 * 关键是它同时用在 [CardTitleRow] 的 `heightIn(min = ...)` 上：休息中右端是约 20dp 的
 * 「进度条 + 闹钟」，结束后换成这个高度的按钮，两边行高都被抬到同一个下限，
 * 于是按钮出现 / 消失不会改变卡片高度。
 */
private val TODAY_CARD_HEADER_HEIGHT = 28.dp

/** 休息指示淡入的时长（毫秒）。 */
private const val REST_INDICATOR_ENTER_MILLIS = 200

/** 休息指示整体退场（[RestSlot] 的 `AnimatedVisibility` 缩小淡出）的时长（毫秒）。 */
private const val REST_INDICATOR_EXIT_MILLIS = 300

/** 休息指示退场时缩到多小：缩小并淡化消失。 */
private const val REST_INDICATOR_EXIT_SCALE = 0.5f

/** 到点后进度条快速淡化消失的时长（毫秒）。 */
private const val REST_BAR_FADE_MILLIS = 180

/** 到点后闹钟横移到进度条中心、并放到 [REST_ALARM_END_SCALE] 的时长（毫秒）；要「较快」。 */
private const val REST_ALARM_MOVE_MILLIS = 300

/** 闹钟到位后的放大倍数。 */
private const val REST_ALARM_END_SCALE = 1.5f

/** 摇完最后淡出消失的时长（毫秒）。 */
private const val REST_ALARM_FADE_MILLIS = 250

/** 闹钟摇摆的单侧摆幅（度）。 */
private const val REST_WOBBLE_ANGLE = 14f

/**
 * 闹钟从一侧摆到另一侧的时长（毫秒）。
 *
 * 摇摆总时长 = ([REST_WOBBLE_SWINGS] + 1) × 本值 = (5 + 1) × 250 = 1500 毫秒（最后一下是回正）。
 */
private const val REST_WOBBLE_SWING_MILLIS = 250

/** 闹钟摇摆的单侧次数；配合 [REST_WOBBLE_SWING_MILLIS] 让摇摆正好 1.5 秒。 */
private const val REST_WOBBLE_SWINGS = 5

/** 卡片标题行里闹钟图标的尺寸。 */
private val REST_ALARM_SIZE = 20.dp

/** 标题行右端至少留出的宽度：约「闹钟 + 一小段进度条」，保证长计划名下闹钟与进度条仍在。 */
private val CARD_TRAILING_MIN_WIDTH = 96.dp
