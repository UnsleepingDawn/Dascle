package com.fitplan.ui.plan.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.rememberCoroutineScope
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
                                onClick = {
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

/** 一张预设卡片：组名、「几个动作 · 做其中几个」、组内动作名；组里动作都排过了就置灰。 */
@Composable
private fun GroupPresetCard(
    item: GroupPresetItem,
    onClick: () -> Unit,
) {
    val addable = item.addable
    val enabled = addable.isNotEmpty()
    val picks = minOf(item.preset.maxPicks, addable.size)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = item.preset.name,
            style = MaterialTheme.typography.titleMedium,
        )
        if (enabled) {
            Text(
                text = stringResource(R.string.group_preset_member_count, addable.size, picks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = addable.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.group_preset_added_all),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
