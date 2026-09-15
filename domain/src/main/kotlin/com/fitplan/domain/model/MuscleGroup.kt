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

    /**
     * 兼容旧数据：早期只有笼统的「手臂」，自建动作无法判定属于二头还是三头，统一归到这里。
     * 内置动作在迁移与种子里都已经拆成 [BICEPS] / [TRICEPS]，不会落在这个分组。
     */
    ARM_OTHER,
    ;

    companion object {
        /**
         * 统计页「肌群组数分布」的横轴顺序：按人体从上到下摆。
         *
         * [ARM_OTHER] 只是兼容旧数据的兜底分组，新数据都会落到 [BICEPS] / [TRICEPS]，
         * 所以不进球图，横轴上不会出现它。
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
