package com.fitplan.ui.workout

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
        val exitTick by screenModel.exitTick.collectAsState()
        val allExercises by screenModel.allExercises.collectAsState()
        val progressHint by screenModel.progressHint.collectAsState()
        val progressTargetInput by screenModel.progressTargetInput.collectAsState()
        val progressTargetConfirm by screenModel.progressTargetConfirm.collectAsState()
        val lowTargetReminder by screenModel.lowTargetReminder.collectAsState()
        val lowTargetRetry by screenModel.lowTargetRetry.collectAsState()
        val extraNotice by screenModel.extraNotice.collectAsState()
        val collapsedIds by screenModel.collapsedExerciseIds.collectAsState()
        val celebrateTick by screenModel.celebrateTick.collectAsState()

        val listState = rememberLazyListState()

        // 各张卡在列表里的位置：进入训练 / 挑中动作组里的动作后，据此平滑滚动过去。
        val scrollTracker = remember { WorkoutScrollTracker() }
        var revealRequest by remember { mutableStateOf<RevealRequest?>(null) }

        // 计划外训练（没有计划，计划名也就是空的）用的兜底名字，也是这场的 session 名。
        val freeName = stringResource(R.string.workout_free)

        // 从「今日」页进来时只带参数，真正的状态由 ScreenModel 按 sessionId / routineId 还原。
        LaunchedEffect(sessionId, routineId, editing, reopen) {
            screenModel.load(
                sessionId = sessionId,
                routineId = routineId,
                editing = editing,
                reopen = reopen,
            )
        }

        // 进入训练中：等这一场铺出来以后，平滑滚到第一个还没做完的动作；只滚一次。
        var enteredScrollDone by remember(sessionId, routineId, editing, reopen) { mutableStateOf(false) }
        LaunchedEffect(items, phase) {
            if (enteredScrollDone || phase != WorkoutPhase.IN_PROGRESS) return@LaunchedEffect
            enteredScrollDone = true
            val byId = exercises.associateBy { it.exerciseId }
            val index = items.indexOfFirst { it.isPending(byId) }
            if (index >= 0) {
                // 目标可能在屏幕外、还没铺出来，量不到位置，所以先按下标粗滚一次。
                revealRequest = RevealRequest(key = "item:${items[index].key}", itemIndex = index)
            }
        }

        LaunchedEffect(exitTick) {
            if (exitTick > 0) navigator.pop()
        }

        var showAbandonDialog by remember { mutableStateOf(false) }
        var showExtraDialog by remember { mutableStateOf(false) }

        // 左滑删除：同一时间只允许一张卡是滑开的，key 用列表项的 key。
        var revealedKey by remember { mutableStateOf<String?>(null) }
        // 非空表示正在确认「解散整个动作组」，确认后才真的移除。
        var removeGroupTarget by remember { mutableStateOf<LogItem.Group?>(null) }
        // 非空表示这一格正在播删除动画（卡片右移飞出、剩下的卡片补上来），播完才真的删记录。
        var removingTarget by remember { mutableStateOf<LogItem?>(null) }

        // 非空表示「减一组」减到最后一组，正在问要不要直接跳过这个动作。
        var lastSetSkipId by remember { mutableStateOf<Long?>(null) }

        // 非空表示正在给某一组计时：计时弹窗只活在前台，不进 ScreenModel。
        var timerTarget by remember { mutableStateOf<TimerTarget?>(null) }

        val readOnly = phase == WorkoutPhase.FINISHED && !editing

        // 一张动作卡：单独排的动作直接用它，动作组里挑中的动作作为子卡（nested）用它。
        // positionKey 是这张卡在滚动跟踪里的名字，用来把它平滑滚进视口。
        val logExerciseCard: @Composable (LogExercise, Boolean, String, Modifier) -> Unit =
            { exercise, nested, positionKey, titleModifier ->
                LogExerciseCard(
                    exercise = exercise,
                    readOnly = readOnly,
                    editableCompletedSets = editing,
                    nested = nested,
                    collapsed = exercise.exerciseId in collapsedIds,
                    modifier = Modifier.onGloballyPositioned {
                        scrollTracker.onItemPositioned(positionKey, it)
                    },
                    titleModifier = titleModifier,
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
                    onStartTimer = { index ->
                        // 倒数器的原点就是打开弹窗这一刻输入框里的时长。
                        timerTarget = TimerTarget(
                            exerciseId = exercise.exerciseId,
                            index = index,
                            name = exercise.name,
                            seconds = exercise.sets.getOrNull(index)?.seconds?.toIntOrNull() ?: 0,
                        )
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
        val dismissedWarmupMuscles by screenModel.dismissedWarmupMuscles.collectAsState()
        // 某个部位第一次被练到的那些卡片：在它们上方插一条该部位的练前热身提示。
        // 判定只看计划顺序，与勾选状态无关，所以录入过程中提示条不会上下跳；
        // 本次训练里已经左滑划走的部位整条摘掉，不再出现在任何一张卡上方。
        val warmupByItem = remember(items, exercises, dismissedWarmupMuscles) {
            warmupMusclesByItemKey(items) { exerciseId -> exercisesById[exerciseId]?.muscleGroups.orEmpty() }
                .withoutDismissedWarmup(dismissedWarmupMuscles)
        }

        // 正在被删的那一格上，删完之后再也不会出现的热身提示：这些要跟着卡片一起收掉。
        val leavingWarmupMuscles = remember(items, removingTarget, dismissedWarmupMuscles, exercisesById) {
            removingTarget?.let { removing ->
                warmupMusclesLeavingWith(
                    items = items,
                    removingKey = removing.key,
                    dismissed = dismissedWarmupMuscles,
                    musclesOf = { exerciseId -> exercisesById[exerciseId]?.muscleGroups.orEmpty() },
                )
            }.orEmpty()
        }

        // 删除动画播完之后才真的去删记录：先删数据再补动画会闪一帧。红区也一起收回去。
        val commitRemoval: (LogItem) -> Unit = { removed ->
            revealedKey = null
            removingTarget = null
            when (removed) {
                is LogItem.Exercise -> screenModel.removeExercise(removed.exerciseId)
                is LogItem.Group -> screenModel.removeGroup(removed.groupId)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                phase == WorkoutPhase.NOT_STARTED -> stringResource(R.string.workout_plan_title)
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
                            onConfirm = { screenModel.finishWorkout() },
                        )
                    }
                }
            },
        ) { contentPadding ->
            val topInsetPx = with(LocalDensity.current) { contentPadding.calculateTopPadding().toPx() }

            // 把待滚动的卡平滑送进视口。滚完清掉请求。
            LaunchedEffect(revealRequest) {
                val request = revealRequest ?: return@LaunchedEffect
                if (request.itemIndex != null) {
                    // 顶层卡片：按下标滚。目标可能在屏幕外还没铺出来，量不到位置；
                    // 滚到下标处正好是内容顶部，也就是顶栏下沿。
                    listState.animateScrollToItem(request.itemIndex)
                } else {
                    // 动作组里的子卡没有自己的下标，只能量出它现在的位置再滚过去。
                    scrollTracker.awaitScrollDelta(request.key, topInsetPx)?.let { listState.animateScrollBy(it) }
                }
                revealRequest = null
            }

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
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { scrollTracker.onListPositioned(it) },
                    contentPadding = contentPadding,
                ) {
                    val currentSummary = summary
                    if (readOnly && currentSummary != null) {
                        // 这一列不再用 spacedBy：每一格自己带着与下一格之间的间距，删掉一格时
                        // 才能把这段间距跟着高度一起收掉，收尾不会突然少掉一截空隙。
                        item {
                            Column {
                                WorkoutSummaryCard(summary = currentSummary)
                                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                            }
                        }
                    }
                    items(items, key = { it.key }) { item ->
                        // 每一格整体交给 RemovingSlot：删除时先把卡片右移飞出，再把整格高度
                        // 收到 0（连热身提示与尾部间距一起收），收完才真的删记录。
                        RemovingSlot(
                            removing = removingTarget?.key == item.key,
                            onDismissed = { commitRemoval(item) },
                        ) { cardRegion, bannerCollapsing ->
                            Column {
                                // 热身提示是这一项的前置兄弟，不跟着卡片飞出；它自己支持左滑划走，
                                // 划走即视为这个部位已经热过身，本次训练不再提示。
                                warmupByItem[item.key]?.forEach { muscle ->
                                    // 按部位取 key：划走一条后这一项剩下的提示会被顶上同一个组合槽位，
                                    // 不加 key 会把已经飞出去的那条的状态（位移与透明度）一起顶过来，新提示直接隐身。
                                    key(muscle) {
                                        SwipeToDismissWarmup(
                                            onDismiss = { screenModel.dismissWarmupHint(muscle) },
                                            // 下面这张卡正在被删、而这个部位后面又不会再提示时，
                                            // 提示要跟着卡片同时收掉，否则收尾会整块跳一下。
                                            collapsing = bannerCollapsing && muscle in leavingWarmupMuscles,
                                        ) {
                                            WarmupBanner(muscle)
                                        }
                                    }
                                }
                                // 卡片本体（含它身后的红区）连同与下一格之间的间距放在这一块里：
                                // 删除时先横向飞出、再把整块高度收到 0。
                                Column(modifier = cardRegion) {
                                    when (item) {
                                        is LogItem.Exercise -> {
                                            val exercise = exercisesById[item.exerciseId]
                                            // 单独排的动作卡可以左滑删除；动作组里的子卡不给这个入口。
                                            if (exercise != null) {
                                                SwipeToRevealRemove(
                                                    revealed = revealedKey == item.key,
                                                    onRevealedChange = {
                                                        revealedKey = if (it) item.key else null
                                                    },
                                                    onRequestRemove = {
                                                        // 前一格的收起动画还没播完就先不接新的删除，
                                                        // 免得两格动画叠在一起、前面那格的删除被顶掉。
                                                        if (removingTarget == null) removingTarget = item
                                                    },
                                                    enabled = !readOnly,
                                                ) { titleModifier ->
                                                    logExerciseCard(
                                                        exercise,
                                                        false,
                                                        "item:${item.key}",
                                                        titleModifier,
                                                    )
                                                }
                                            }
                                        }

                                        // 动作组卡：先在芯片行里挑动作，挑中的在卡内展开成子卡。
                                        // 左滑删除的是整组，先弹一次确认（组内已练的记录会一起删）。
                                        is LogItem.Group -> SwipeToRevealRemove(
                                            revealed = revealedKey == item.key,
                                            onRevealedChange = { revealedKey = if (it) item.key else null },
                                            onRequestRemove = {
                                                // 同上：动画没播完就不再接新的删除。
                                                if (removingTarget == null) removeGroupTarget = item
                                            },
                                            enabled = !readOnly,
                                        ) { titleModifier ->
                                            LogGroupCard(
                                                group = item,
                                                exercises = exercises,
                                                readOnly = readOnly,
                                                onTogglePick = { exerciseId ->
                                                    if (exerciseId in item.pickedIds) {
                                                        screenModel.unpickGroupExercise(
                                                            item.groupId,
                                                            exerciseId,
                                                        )
                                                    } else {
                                                        screenModel.pickGroupExercise(item.groupId, exerciseId)
                                                        // 挑中的动作会在组卡里展开子卡，滚过去让用户直接接着录入。
                                                        revealRequest = RevealRequest("card:$exerciseId")
                                                    }
                                                },
                                                titleModifier = titleModifier,
                                            ) { exercise ->
                                                logExerciseCard(
                                                    exercise,
                                                    true,
                                                    "card:${exercise.exerciseId}",
                                                    Modifier,
                                                )
                                            }
                                        }
                                    }
                                    // 与下一格之间的间距；这一列已经不用 spacedBy。
                                    Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
                                }
                            }
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

        removeGroupTarget?.let { target ->
            RemoveGroupDialog(
                onConfirm = {
                    // 确认之后才播「卡片右移飞出 + 剩下的卡片补上来」，播完由 commitRemoval 删记录。
                    removeGroupTarget = null
                    if (removingTarget == null) removingTarget = target
                },
                onDismiss = { removeGroupTarget = null },
            )
        }

        if (showExtraDialog) {
            ExtraExerciseDialog(
                exercises = allExercises,
                // 今天计划里已有的动作仍然列出来（标一句提示），点了由 ScreenModel 决定是加还是弹提醒。
                presentIds = exercises.map { it.exerciseId }.toSet(),
                onPick = { exerciseId ->
                    showExtraDialog = false
                    screenModel.requestExtraExercise(exerciseId)
                },
                onDismiss = { showExtraDialog = false },
            )
        }

        // 计划外动作今天已经排在计划里：不重复添加，提醒去原有的卡片或动作组里操作。
        extraNotice?.let { notice ->
            ExtraExerciseNoticeDialog(
                notice = notice,
                onDismiss = screenModel::dismissExtraNotice,
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

        // 低于计划目标：这一组还没记录，先问重填还是照原样记上。点遮罩等同放弃重填。
        lowTargetReminder?.let { reminder ->
            exercisesById[reminder.exerciseId]?.let { exercise ->
                LowTargetReminderDialog(
                    exercise = exercise,
                    onRetry = screenModel::retryLowTargetSet,
                    onKeep = screenModel::keepLowTargetSet,
                    onDismiss = screenModel::dismissLowTargetReminder,
                )
            }
        }

        // 选了「重新填一次」：打开设置表单改计划目标，保存后按新目标覆盖这一组的实际值，不自动打勾。
        lowTargetRetry?.let { retry ->
            val exercise = exercisesById[retry.exerciseId]
            if (exercise != null) {
                LowTargetRetryDialog(
                    exercise = exercise,
                    onConfirm = screenModel::applyLowTargetRetry,
                    onDismiss = screenModel::dismissLowTargetRetry,
                )
            }
        }

        // 计时动作的计时弹窗：3-2-1 → 开始训练！ → 正/倒数计时，底部「返回」/「已经做完了」。
        timerTarget?.let { target ->
            WorkoutTimerDialog(
                exerciseName = target.name,
                setLabel = stringResource(R.string.workout_set_index, target.index + 1),
                countdownSeconds = target.seconds,
                onFinish = {
                    // 先关弹窗再记这一组：低于计划目标时后面还会弹重填提醒，不该压在计时弹窗上。
                    timerTarget = null
                    screenModel.toggleCompleted(target.exerciseId, target.index)
                },
                onDismiss = { timerTarget = null },
            )
        }

        // 礼花：一个动作全部做完时放一轮。放在所有弹窗之后，且比它们更晚挂载窗口才盖得住它们。
        WorkoutCelebrationOverlay(tick = celebrateTick)
    }
}

/**
 * 一次「给某一组计时」的目标。
 *
 * 计时显示只活在前台弹窗里，不落库也不需要跨页面存活，所以不进 ScreenModel。
 * [seconds] 是打开弹窗那一刻输入框里填的时长，也是倒数器的原点。
 */
private data class TimerTarget(
    val exerciseId: Long,
    val index: Int,
    val name: String,
    val seconds: Int,
)

/**
 * 一次「把目标滚进视口」的请求。
 *
 * 用普通类、靠实例身份区分：连着两次请求同一处也要能重新触发，所以不能是 data class。
 * [itemIndex] 非空时先按下标粗滚一次——目标可能在屏幕外、还没铺出来，那时量不到位置。
 */
private class RevealRequest(
    val key: String,
    val itemIndex: Int? = null,
)

/**
 * 这一项是不是「还没做完」，进训练时据此找第一个该接着练的位置：
 * 跳过与已做完的动作不算；动作组还没挑、或挑中的动作里还有没做完的也要算。
 */
private fun LogItem.isPending(byId: Map<Long, LogExercise>): Boolean = when (this) {
    is LogItem.Exercise -> byId[exerciseId]?.let { !it.skipped && !it.isDone } == true
    is LogItem.Group -> pickedIds.isEmpty() ||
        pickedIds.any { id -> byId[id]?.let { !it.skipped && !it.isDone } == true }
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
