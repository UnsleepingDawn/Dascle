package com.fitplan.updater

/**
 * 判断 [candidate] 是不是比 [current] 新的版本号。
 *
 * 兼容几种写法：
 * - tag 上的 `v` 前缀（`v1.2.0`）
 * - debug 包的版本后缀（`1.1.1-123`，`-` 之后是 commit 数）
 * - 元数据后缀（`1.1.1+2`）
 *
 * 任何一边解析不出数字（比如 `v1.2` 之外还夹着文字）都返回 false，
 * 宁可漏报也不要误报。
 */
internal fun isNewer(candidate: String, current: String): Boolean {
    val candidateParts = parseVersion(candidate) ?: return false
    val currentParts = parseVersion(current) ?: return false

    for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
        val new = candidateParts.getOrElse(index) { 0 }
        val old = currentParts.getOrElse(index) { 0 }
        if (new != old) return new > old
    }
    return false
}

/** 把 `v1.2.3-4` 拆成 `[1, 2, 3]`；解析不出来返回 null。 */
private fun parseVersion(raw: String): List<Int>? {
    val cleaned = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .trim()
    if (cleaned.isEmpty()) return null

    val parts = mutableListOf<Int>()
    for (segment in cleaned.split('.')) {
        parts += segment.trim().toIntOrNull() ?: return null
    }
    return parts
}
