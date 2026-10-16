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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.ui.common.DialogButtonColumn
import com.fitplan.ui.common.DialogCancelButton
import com.fitplan.ui.common.DialogDestructiveButton
import com.fitplan.ui.common.DialogPrimaryButton
import com.fitplan.ui.plan.PlanScreenModel
import com.fitplan.ui.plan.routine.RoutineEditScreen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 「计划」页的第二个子页：训练计划列表。新建 / 删除计划，点卡片进到计划里编排动作。
 *
 * 新建走底部整条按钮，并直接落到动作编排页，省得用户再点一次卡片；
 * 卡片本身不摆按钮——点一下进编排页，长按、或者把卡片往左滑露出红色删除键，都会先弹确认框再删。
 * 改名搬到编排页顶栏（点标题即可改），列表这层不再提供入口。
 */
@Composable
internal fun RoutineListContent(contentPadding: PaddingValues) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = metroViewModel<PlanScreenModel>()
    val items by screenModel.items.collectAsState()
    val loaded by screenModel.loaded.collectAsState()

    // 从编辑页返回时本组合会重建，顺带刷新一次列表。
    LaunchedEffect(Unit) { screenModel.refresh() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Routine?>(null) }
    // 同时只允许一张卡片滑开，点别处或弹确认框时自动收回。
    var revealedId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        when {
            items.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = MaterialTheme.padding.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(items, key = { it.id }) { routine ->
                        SwipeToRevealDelete(
                            revealed = revealedId == routine.id,
                            onRevealedChange = { open ->
                                revealedId = if (open) routine.id else null
                            },
                            onRequestDelete = {
                                revealedId = null
                                deleteTarget = routine
                            },
                            onClick = { navigator.push(RoutineEditScreen(routine.id)) },
                        ) {
                            RoutineCard(name = routine.name)
                        }
                    }
                }
            }

            loaded -> EmptyScreen(
                message = stringResource(R.string.plan_empty),
                modifier = Modifier.weight(1f),
            )
        }

        // 整条铺满的「新增单日计划」，比右下角的加号更容易被发现。
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
    }

    if (showCreateDialog) {
        RoutineDetailDialog(
            title = stringResource(R.string.plan_new),
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                // 先收弹窗再跳，避免从编排页返回时弹窗还压在上面。
                showCreateDialog = false
                screenModel.create(name) { id -> navigator.push(RoutineEditScreen(id)) }
            },
        )
    }

    deleteTarget?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.plan_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                    Text(text = stringResource(R.string.plan_delete_confirm, routine.name))
                    DialogButtonColumn {
                        DialogDestructiveButton(
                            text = stringResource(R.string.action_delete),
                            onClick = {
                                screenModel.delete(routine.id)
                                deleteTarget = null
                            },
                        )
                        DialogCancelButton(onClick = { deleteTarget = null })
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * 左滑露出红色删除键的容器，卡片本身长按也能触发同一个删除入口。
 *
 * [revealed] 由列表持有，保证同一时间只有一张卡片是滑开的；点一下卡片，或者点了删除键，
 * 都会把它收回原处。卡片滑开时点击先归位、不触发 [onClick]，免得顺手点开一个并不想进的计划。
 */
@Composable
private fun SwipeToRevealDelete(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    onClick: () -> Unit,
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
                        // 卡片的主操作是进编排页；滑开的卡片上点一下只是收回。
                        onClick = { if (revealed) onRevealedChange(false) else onClick() },
                        onLongClick = onRequestDelete,
                    ),
            ) {
                content()
            }
        }
    }
}

/** 计划卡片：只有计划名一行，整张卡片可点进编排页，高度收到 [ROUTINE_CARD_MIN_HEIGHT]。 */
@Composable
private fun RoutineCard(name: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROUTINE_CARD_MIN_HEIGHT)
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoutineDetailDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.field_routine_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogButtonColumn {
                    DialogPrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                    )
                    DialogCancelButton(onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
    )
}

/** 卡片高度下限：一行 titleMedium（24dp）加上下各 8dp 内边距，保证不小于最小触控区。 */
private val ROUTINE_CARD_MIN_HEIGHT = 56.dp

/** 左滑后露出的红色删除键宽度。 */
private val DELETE_ACTION_WIDTH = 96.dp
