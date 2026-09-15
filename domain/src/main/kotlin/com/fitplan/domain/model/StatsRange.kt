package com.fitplan.domain.model

/**
 * 统计页的时间区间。
 *
 * [days] 是从今天往前数的天数（含今天），`null` 表示不限时间、看全部记录。
 * 统计页的每个数字、两张图和训练历史都由同一个区间决定。
 */
enum class StatsRange(val days: Int?) {
    /** 近 7 天。 */
    WEEK(7),

    /** 近 30 天。 */
    MONTH(30),

    /** 近 1 年。 */
    YEAR(365),

    /** 全部记录。 */
    ALL(null),
}
