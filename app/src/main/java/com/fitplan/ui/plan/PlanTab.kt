package com.fitplan.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.TabText
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.plan.calendar.DaySelection
import com.fitplan.ui.plan.calendar.PlanCalendarContent
import com.fitplan.ui.plan.calendar.PlanCalendarScreenModel
import com.fitplan.ui.plan.calendar.RoutineListContent
import com.fitplan.ui.plan.calendar.isEditableDate
import com.fitplan.ui.plan.calendar.toSelection
import com.fitplan.ui.plan.compose.ApplyDialog
import com.fitplan.ui.plan.compose.PlanComposeContent
import com.fitplan.ui.plan.compose.PlanComposeScreenModel
import com.fitplan.ui.plan.compose.composeCycleLength
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 计划页：顶栏下方一条标签栏切换三个子页——训练日历、单日计划、多日循环。
 *
 * 顶栏跟着子页走：日历编辑模式里左边给「全选 / 取消全部」、标题换成「已选 N 天」、右边给「完成」；
 * 切到多日循环时右边给「应用」。编辑态因此由本组合持有，再传给日历子页。
 */
object PlanTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.tab_plan),
            icon = rememberVectorPainter(Icons.Filled.FitnessCenter),
        )

    @Composable
    override fun Content() {
        val screenModel = metroViewModel<PlanTabScreenModel>()
        val page by screenModel.page.collectAsState()

        // 日历编辑态的标题与「全选」要用到当月日子，编排页的「应用」按钮要按格数决定是否可点。
        val calendarModel = metroViewModel<PlanCalendarScreenModel>()
        val days by calendarModel.days.collectAsState()
        val composeModel = metroViewModel<PlanComposeScreenModel>()
        val slots by composeModel.slots.collectAsState()
        val cycleLength = composeCycleLength(slots)

        // 编辑模式：长按今天及以后的日子进入（过去只读）。勾选按日期保存，所以切到别的月份继续勾也不丢。
        // 状态放在这里是因为顶栏也要跟着变，且切 Tab / 从子页 push 出去返回时不能丢。
        var editing by remember { mutableStateOf(false) }
        var selection by remember { mutableStateOf<Map<LocalDate, DaySelection>>(emptyMap()) }
        var showApply by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val calendarEditing = editing && page == PlanPage.CALENDAR

        fun exitEdit() {
            editing = false
            selection = emptyMap()
        }

        // 切走子页时退出编辑，回来不会还挂着「已选 N 天」。
        LaunchedEffect(page) {
            if (page != PlanPage.CALENDAR) exitEdit()
        }

        // 编辑模式里按返回键先退出编辑，而不是直接切走 Tab。
        BackHandler(enabled = calendarEditing) { exitEdit() }

        Scaffold(
            topBar = { scrollBehavior ->
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (calendarEditing) {
                                    stringResource(R.string.calendar_edit_selected, selection.size)
                                } else {
                                    stringResource(R.string.tab_plan)
                                },
                            )
                        },
                        navigationIcon = {
                            if (calendarEditing) {
                                Row {
                                    // 全选是合并语义：把本月可选的日子并进已有勾选，跨月的勾选不丢。
                                    TextButton(
                                        onClick = {
                                            selection = selection + days
                                                .filter(::isEditableDate)
                                                .associate { it.date to it.toSelection() }
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.calendar_edit_select_all))
                                    }
                                    TextButton(
                                        onClick = { selection = emptyMap() },
                                        enabled = selection.isNotEmpty(),
                                    ) {
                                        Text(text = stringResource(R.string.calendar_edit_clear_all))
                                    }
                                }
                            }
                        },
                        actions = {
                            when {
                                calendarEditing -> TextButton(onClick = { exitEdit() }) {
                                    Text(text = stringResource(R.string.calendar_edit_done))
                                }

                                page == PlanPage.CYCLE -> TextButton(
                                    onClick = { showApply = true },
                                    enabled = cycleLength > 0,
                                ) {
                                    Text(text = stringResource(R.string.plan_compose_apply))
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                    TabRow(selectedTabIndex = page.ordinal) {
                        PlanPage.entries.forEach { item ->
                            Tab(
                                selected = item == page,
                                onClick = { screenModel.selectPage(item) },
                                text = { TabText(text = item.label()) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            when (page) {
                PlanPage.CALENDAR -> PlanCalendarContent(
                    contentPadding = contentPadding,
                    editing = editing,
                    selection = selection,
                    onSelectionChange = { selection = it },
                    onEnterEdit = { day ->
                        editing = true
                        selection = mapOf(day.date to day.toSelection())
                    },
                )

                PlanPage.SINGLE_DAY -> RoutineListContent(contentPadding = contentPadding)

                PlanPage.CYCLE -> PlanComposeContent(contentPadding = contentPadding)
            }
        }

        if (showApply) {
            ApplyDialog(
                cycleLength = cycleLength,
                onDismiss = { showApply = false },
                onConfirm = { startDate, cycles, until ->
                    showApply = false
                    // 写完再切回日历：编排页的编排只在内存里，提前离开会打断写库。
                    scope.launch {
                        composeModel.apply(startDate = startDate, cycles = cycles, until = until)
                        screenModel.selectPage(PlanPage.CALENDAR)
                    }
                },
            )
        }
    }
}

@Composable
internal fun PlanPage.label(): String = stringResource(
    when (this) {
        PlanPage.CALENDAR -> R.string.calendar_title
        PlanPage.SINGLE_DAY -> R.string.plan_page_single_day
        PlanPage.CYCLE -> R.string.plan_page_cycle
    },
)
