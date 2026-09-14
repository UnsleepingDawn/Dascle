package com.fitplan.app.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 排期在「页面自己之外」被改动后的刷新信号。
 *
 * 目前只有启动时的漏练弹窗会改排期（顺延 / 跳过），而那时今日页与日历页早就取过数了；
 * 相关 ScreenModel 在 `init` 里 `revision.drop(1).collect { refresh() }` 就能跟上。
 */
@Inject
@SingleIn(AppScope::class)
class DataRevision {

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bump() {
        _revision.value += 1
    }
}
