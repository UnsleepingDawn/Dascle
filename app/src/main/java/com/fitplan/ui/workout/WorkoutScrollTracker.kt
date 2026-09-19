package com.fitplan.ui.workout

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * 训练记录页里各张卡在列表中的位置，用来把「刚挑中的组内子卡」平滑滚进视口。
 *
 * 顶层卡片能按下标滚（`animateScrollToItem`），不需要量位置；只有动作组里的子卡没有自己的下标，
 * 才要靠这里量出的实际位置去滚。位置由 `onGloballyPositioned` 在布局后写，滚动时在协程里读，
 * 所以用普通可变字段承载、不参与重组。列表里量不到位置（还没铺出来）的项返回 null，调用方按需重试。
 */
internal class WorkoutScrollTracker {

    private var listCoordinates: LayoutCoordinates? = null

    private val itemCoordinates = mutableMapOf<String, LayoutCoordinates>()

    fun onListPositioned(coordinates: LayoutCoordinates) {
        listCoordinates = coordinates
    }

    fun onItemPositioned(key: String, coordinates: LayoutCoordinates) {
        itemCoordinates[key] = coordinates
    }

    /**
     * [key] 这张卡要贴到视口顶部（让开 [topInsetPx] 的顶部内容内边距）还需要滚多少像素：
     * 正数往下滚、负数往回滚。位置还没量到时返回 null。
     *
     * 量到的是窗口坐标，而列表第一项贴住内容顶部时正好在 `topInsetPx` 处，所以减去它就是要滚的距离。
     */
    fun scrollDeltaToReveal(key: String, topInsetPx: Float): Float? {
        val list = listCoordinates ?: return null
        val item = itemCoordinates[key] ?: return null
        if (!list.isAttached || !item.isAttached) return null
        return list.localPositionOf(item, Offset.Zero).y - topInsetPx
    }

    /**
     * 等列表把 [key] 的位置量出来再给滚动量：新加进列表的卡要等重组与布局跑完
     * （最多等 [SCROLL_MEASURE_FRAMES] 帧）才量得到；一直量不到就放弃，不去打扰用户。
     */
    suspend fun awaitScrollDelta(key: String, topInsetPx: Float): Float? {
        repeat(SCROLL_MEASURE_FRAMES) {
            withFrameNanos { }
            scrollDeltaToReveal(key, topInsetPx)?.let { return it }
        }
        return null
    }
}

/** 量位置最多等几帧；正常情况下第一帧就够，多等几帧只是给刚铺出来的卡留余量。 */
private const val SCROLL_MEASURE_FRAMES = 4
