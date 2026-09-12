package com.fitplan.domain.model

enum class MuscleGroup {
    CHEST,
    ARM,
    ABS,
    SHOULDER,
    BACK,
    LEG,
    GLUTE,
    ;

    companion object {
        fun fromValue(value: String): MuscleGroup? = entries.find { it.name == value }
    }
}
