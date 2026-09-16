package com.fitplan.domain.model

/**
 * 动作的负重方式，决定记录时要不要填重量、以及渐进提示往哪个方向走。
 *
 * 注意不能靠 [Equipment] 推断：负重平板支撑、单杠悬挂这类动作器械仍是自重的，但可以额外负重。
 */
enum class ExerciseLoadMode {
    /** 外部负重：重量越大越难，提示加重量或加次数 / 时长。 */
    EXTERNAL,

    /** 纯自重：不记录重量，提示加次数 / 时长。 */
    BODYWEIGHT,

    /** 辅助类（器械辅助引体向上等）：重量是助力，越大越轻松，提示减少辅助重量或加次数。 */
    ASSISTED,
    ;

    companion object {
        /** 数据库里为空（老数据 / 自建动作）时按外部负重处理。 */
        val DEFAULT: ExerciseLoadMode = EXTERNAL

        fun fromValue(value: String?): ExerciseLoadMode =
            entries.find { it.name == value } ?: DEFAULT
    }
}
