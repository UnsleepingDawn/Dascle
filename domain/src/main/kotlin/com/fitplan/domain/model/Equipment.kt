package com.fitplan.domain.model

enum class Equipment {
    BARBELL,
    DUMBBELL,
    MACHINE,
    CABLE,
    BODYWEIGHT,
    ;

    companion object {
        fun fromValue(value: String): Equipment? = entries.find { it.name == value }
    }
}
