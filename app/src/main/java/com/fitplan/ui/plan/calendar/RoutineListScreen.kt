package com.fitplan.ui.plan.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.Routine
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import com.fitplan.ui.plan.PlanScreenModel
import com.fitplan.ui.plan.RoutineListItem
import com.fitplan.ui.plan.routine.RoutineEditScreen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 训练计划列表：新建 / 重命名 / 删除计划，或者进到计划里编排动作。
 *
 * 新建走底部整条按钮，并直接落到动作编排页，省得用户再点一次卡片；
 * 删除不摆常驻按钮——长按卡片、或者把卡片往左滑露出红色删除键，都会先弹确认框。
 */
object RoutineListScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<PlanScreenModel>()
        val items by screenModel.items.collectAsState()
        val loaded by screenModel.loaded.collectAsState()

        // 从编辑页返回时本组合会重建，顺带刷新一次列表。
        LaunchedEffect(Unit) { screenModel.refresh() }

        var showCreateDialog by remember { mutableStateOf(false) }
        var renameTarget by remember { mutableStateOf<Routine?>(null) }
        var deleteTarget by remember { mutableStateOf<Routine?>(null) }
        // 同时只允许一张卡片滑开，点别处或弹确认框时自动收回。
        var revealedId by remember { mutableStateOf<Long?>(null) }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.plan_title)) },
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
            bottomBar = {
                // 整条铺满的「新增单日方案」，比右下角的加号更容易被发现。
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.padding.medium),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                        )
                        Text(text = stringResource(R.string.plan_new))
                    }
                }
            },
        ) { contentPadding ->
            when {
                items.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(items, key = { it.routine.id }) { item ->
                            SwipeToRevealDelete(
                                revealed = revealedId == item.routine.id,
                                onRevealedChange = { open ->
                                    revealedId = if (open) item.routine.id else null
                                },
                                onRequestDelete = {
                                    revealedId = null
                                    deleteTarget = item.routine
                                },
                            ) {
                                RoutineCard(
                                    item = item,
                                    onSchedule = { navigator.push(RoutineEditScreen(item.routine.id)) },
                                    onRename = { renameTarget = item.routine },
                                )
                            }
                        }
                    }
                }

                loaded -> EmptyScreen(
                    message = stringResource(R.string.plan_empty),
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }

        if (showCreateDialog) {
            RoutineDetailDialog(
                title = stringResource(R.string.plan_new),
                initialName = "",
                initialNote = "",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, note ->
                    // 先收弹窗再跳，避免从编排页返回时弹窗还压在上面。
                    showCreateDialog = false
                    screenModel.create(name, note) { id -> navigator.push(RoutineEditScreen(id)) }
                },
            )
        }

        renameTarget?.let { routine ->
            RoutineDetailDialog(
                title = stringResource(R.string.plan_rename),
                initialName = routine.name,
                initialNote = routine.note,
                onDismiss = { renameTarget = null },
                onConfirm = { name, note ->
                    screenModel.update(routine.id, name, note)
                    renameTarget = null
                },
            )
        }

        deleteTarget?.let { routine ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(text = stringResource(R.string.plan_delete)) },
                text = { Text(text = stringResource(R.string.plan_delete_confirm, routine.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.delete(routine.id)
                            deleteTarget = null
                        },
                    ) {
                        Text(text = stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/**
 * 左滑露出红色删除键的容器，卡片本身长按也能触发同一个删除入口。
 *
 * [revealed] 由列表持有，保证同一时间只有一张卡片是滑开的；点一下卡片、或者点了删除键，
 * 都会把它收回原处。
 */
@Composable
private fun SwipeToRevealDelete(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { DELETE_ACTION_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 外部状态变化（另滑开一张、弹出确认框）时，把卡片动画归位。
    LaunchedEffect(revealed) {
        offsetX.animateTo(if (revealed) -revealPx else 0f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        // 铺满整张卡片的红色底，只有卡片左移之后右侧那一截才露出来。
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.error),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    onRevealedChange(false)
                    onRequestDelete()
                },
                modifier = Modifier
                    .width(DELETE_ACTION_WIDTH)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offsetX.value < -revealPx / 2
                            // 目标状态没变时 LaunchedEffect 不会重跑，这里自己补一次回弹。
                            if (open == revealed) {
                                scope.launch { offsetX.animateTo(if (open) -revealPx else 0f) }
                            } else {
                                onRevealedChange(open)
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        // 卡片没有主操作，点一下只用来把滑开的卡片收回。
                        onClick = { if (revealed) onRevealedChange(false) },
                        onLongClick = onRequestDelete,
                    ),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RoutineCard(
    item: RoutineListItem,
    onSchedule: () -> Unit,
    onRename: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 有备注和没备注的卡片保持一样高，所以高度由按钮那一列决定。
                .heightIn(min = ROUTINE_CARD_MIN_HEIGHT)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 没写备注就整行不占位，靠竖直居中让三行内容看起来仍然是居中的。
                if (item.routine.note.isNotBlank()) {
                    Text(
                        text = item.routine.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.plan_exercise_count, item.exerciseCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.padding(start = MaterialTheme.padding.small),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                CompactActionButton(
                    text = stringResource(R.string.plan_action_schedule),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onSchedule,
                )
                CompactActionButton(
                    text = stringResource(R.string.plan_action_rename),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onRename,
                )
            }
        }
    }
}

/**
 * 卡片用的紧凑操作按钮：M3 按钮容器默认 40dp、最小触控区 48dp，两枚竖排会把卡片顶到 136dp，
 * 这里把容器与触控区一起收到 36dp，左右内边距也从默认的 24dp 收到 8dp。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactActionButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides COMPACT_ACTION_BUTTON_HEIGHT) {
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.padding.small,
                vertical = MaterialTheme.padding.extraSmall,
            ),
            modifier = Modifier.heightIn(min = COMPACT_ACTION_BUTTON_HEIGHT),
        ) {
            Text(text = text, maxLines = 1)
        }
    }
}

@Composable
private fun RoutineDetailDialog(
    title: String,
    initialName: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, note: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.field_routine_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(text = stringResource(R.string.field_routine_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), note.trim()) },
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

/** 卡片里的紧凑按钮高度：容器与最小触控区都收到这个值，比 M3 默认的 40dp / 48dp 矮一截。 */
private val COMPACT_ACTION_BUTTON_HEIGHT = 36.dp

/** 卡片高度下限：36 + 8 + 36 的按钮列，加上下各 8dp 内边距，有没有备注都不变。 */
private val ROUTINE_CARD_MIN_HEIGHT = 96.dp

/** 左滑后露出的红色删除键宽度。 */
private val DELETE_ACTION_WIDTH = 96.dp
