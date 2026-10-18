package com.fitplan.domain.model

enum class MuscleGroup {
    CHEST,
    BICEPS,
    TRICEPS,
    ABS,
    SHOULDER,
    BACK,
    LEG,
    GLUTE,

    /** 有氧运动分类，不作为肌群参与组数分布统计。 */
    CARDIO,
    ;

    companion object {
        /**
         * 统计页「肌群组数分布」的横轴顺序：按人体从上到下摆。
         *
         * [CARDIO] 不是肌群，不进入这张图。历史上的兜底分组 `ARM_OTHER`（其他手臂）已在
         * schema 20→21 的迁移里清掉并归入 [BICEPS]，枚举里只剩真实肌群。
         */
        val chartOrder: List<MuscleGroup> = listOf(
            SHOULDER,
            CHEST,
            BACK,
            BICEPS,
            TRICEPS,
            ABS,
            GLUTE,
            LEG,
        )

        fun fromValue(value: String): MuscleGroup? = entries.find { it.name == value }
    }
}
