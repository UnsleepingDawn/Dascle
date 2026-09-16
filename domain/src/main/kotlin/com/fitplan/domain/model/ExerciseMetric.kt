package com.fitplan.domain.model

/**
 * 动作的计量方式：按次数还是按时长。由动作库（种子数据）决定，记录页与编排页都不允许用户切换。
 */
enum class ExerciseMetric {
    /** 按次数录入（大多数力量动作）。 */
    REPS,

    /** 按时长录入（平板支撑、农夫行走等静力 / 计时动作）。 */
    DURATION,
    ;

    companion object {
        /** 数据库里为空（老数据 / 自建动作）时按次数处理。 */
        val DEFAULT: ExerciseMetric = REPS

        fun fromValue(value: String?): ExerciseMetric =
            entries.find { it.name == value } ?: DEFAULT
    }
}
