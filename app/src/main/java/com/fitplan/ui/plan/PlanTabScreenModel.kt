package com.fitplan.ui.plan

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 计划页的三个子页面：训练日历、单日计划、多日循环。 */
enum class PlanPage {
    CALENDAR,
    SINGLE_DAY,
    CYCLE,
}

/**
 * 计划页当前选中的子页面。
 *
 * 状态放在 ViewModel（AppScope 单例）而不是组合里：从子页 push 出去的动作编排页、训练记录页
 * 返回时 Tab 内容会重建，用 `remember` 会掉回「训练日历」。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlanTabScreenModel : ViewModel() {

    private val _page = MutableStateFlow(PlanPage.CALENDAR)
    val page: StateFlow<PlanPage> = _page.asStateFlow()

    fun selectPage(page: PlanPage) {
        _page.value = page
    }
}
