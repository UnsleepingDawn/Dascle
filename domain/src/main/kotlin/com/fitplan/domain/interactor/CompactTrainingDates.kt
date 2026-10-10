package com.fitplan.domain.interactor

import kotlinx.datetime.LocalDate

/**
 * 压缩删除后的日期映射：把 [scheduledDates]（升序、每个训练日一条）里被 [deletedDates] 删掉的
 * 训练日去掉后，剩下的排期按原顺序依次前顶到最靠前的那些训练日上。
 *
 * 返回「原日期 -> 新日期」的配对，只含仍然保留的排期。目标日期取自 [scheduledDates] 的前若干项，
 * 两两不同，所以日期只会往前走、不会互相撞上，末尾空出来的训练日自然变成休息日。
 *
 * 例：`[10/12, 10/14, 10/16]` 只删 `10/14` 时返回 `[(10/16 -> 10/14)]`，
 * 等价于 `10/12` 不动、`10/16` 顶入 `10/14`，末尾 `10/16` 变休息。
 *
 * 被删的日期若本来就没有排期（休息日），它不会出现在 [scheduledDates] 里，映射不受影响。
 */
fun compactTrainingDateMoves(
    scheduledDates: List<LocalDate>,
    deletedDates: Set<LocalDate>,
): List<Pair<LocalDate, LocalDate>> {
    val survivors = scheduledDates.filterNot { it in deletedDates }
    return survivors.mapIndexedNotNull { index, date ->
        val target = scheduledDates[index]
        // 落到原地的排期不必重写（删最后一天时整段都不动），只返回真正挪动的那些。
        if (date == target) null else date to target
    }
}
