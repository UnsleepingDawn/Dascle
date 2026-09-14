package com.fitplan.ui.plan.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

/** 训练计划列表：新建 / 重命名 / 删除计划，点卡片进入动作编排。 */
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
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.plan_new),
                    )
                }
            },
        ) { contentPadding ->
            when {
                items.isNotEmpty() -> {
                    LazyColumn(
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(items, key = { it.routine.id }) { item ->
                            RoutineCard(
                                item = item,
                                onClick = { navigator.push(RoutineEditScreen(item.routine.id)) },
                                onRename = { renameTarget = item.routine },
                                onDelete = { deleteTarget = item.routine },
                            )
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
                    screenModel.create(name, note)
                    showCreateDialog = false
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

@Composable
private fun RoutineCard(
    item: RoutineListItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.routine.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.routine.note.ifBlank { stringResource(R.string.plan_note_empty) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.plan_exercise_count, item.exerciseCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_rename),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
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
