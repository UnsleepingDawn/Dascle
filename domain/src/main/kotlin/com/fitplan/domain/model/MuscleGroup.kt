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
        fun fromValue(value: String): MuscleGroup? = entries.find { it.name == value }
    }
}
