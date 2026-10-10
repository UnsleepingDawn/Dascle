package com.fitplan.ui.plan.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen
import com.fitplan.ui.exercise.FilterRow
import com.fitplan.ui.exercise.label
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch

/**
 * 挑一个预设动作组加进 [routineId] 对应的计划。
 *
 * 与动作选择器同款：只按部位筛选（预设本身不区分器械），单点一个预设组就整组加入并返回。
 * 计划里已经排过的组成动作会被跳过，加入后就是普通动作组，可继续增删改。
 */
class GroupPresetPickerScreen(
    private val routineId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = metroViewModel<GroupPresetPickerScreenModel>()
        val presets by screenModel.presets.collectAsState()
        val muscleOptions by screenModel.muscleOptions.collectAsState()
        val muscleFilter by screenModel.muscleFilter.collectAsState()

        LaunchedEffect(routineId) { screenModel.load(routineId) }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.group_preset_title)) },
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
                    muscleOptions.forEach { muscle ->
                        FilterChip(
                            selected = muscleFilter == muscle,
                            onClick = { screenModel.setMuscleFilter(muscle) },
                            label = { Text(text = muscle.label()) },
                        )
                    }
                }

                if (presets.isEmpty()) {
                    EmptyScreen(message = stringResource(R.string.group_preset_empty))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(presets, key = { it.preset.name }) { item ->
                            GroupPresetCard(
                                item = item,
                                onAdd = {
                                    scope.launch {
                                        screenModel.addPreset(routineId, item)
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

/**
 * 一张预设卡片：标题一行是组名与「几个动作 · 建议做几个」，整行可点，点一下展开看组里有哪些动作。
 *
 * 展开后列出预设的全部动作，已经排进计划的标「已添加」置灰；底部「加入计划」只把没排过的
 * 动作加进计划（一个都加不进去时按钮置灰）。
 */
@Composable
private fun GroupPresetCard(
    item: GroupPresetItem,
    onAdd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val picks = minOf(item.preset.maxPicks, item.members.size)
    val addableIds = remember(item) { item.addable.map { it.id }.toSet() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = item.preset.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.group_preset_member_count, item.members.size, picks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.group_preset_collapse else R.string.group_preset_expand,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                        bottom = MaterialTheme.padding.small,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                item.members.forEach { exercise ->
                    val added = exercise.id !in addableIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (added) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (added) {
                            Text(
                                text = stringResource(R.string.group_preset_member_added),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = onAdd,
                    enabled = item.addable.isNotEmpty(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(R.string.group_preset_add))
                }
            }
        }
    }
}
