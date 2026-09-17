package com.fitplan.domain.model

/**
 * 日常活动程度，[factor] 用来把基础代谢换算成每日总消耗（TDEE）。
 *
 * 用户没选就不填，页面显示「未填写」。
 */
enum class ActivityLevel(val factor: Double) {
    /** 久坐不动：办公室工作，几乎不运动。 */
    SEDENTARY(1.2),

    /** 轻度活动：每周运动 1-3 次，或日均 5000~8000 步。 */
    LIGHT(1.375),

    /** 中度活动：每周运动 3-5 次，或日均 8000~12000 步。 */
    MODERATE(1.55),

    /** 高强度活动：每周运动 6-7 次，或体力劳动者。 */
    ACTIVE(1.725),

    /** 运动员级别：每天高强度训练。 */
    ATHLETE(1.9),
}
