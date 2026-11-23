package com.moyue.reader

import android.os.Bundle

/**
 * 请求码双向映射：androidx 的框架码（65536..Int.MAX 的随机数）↔ 系统可接受的小整数（1..65535）。
 *
 * 背景：Activity.startActivityForResult 只接受低 16 位请求码，而 androidx 的
 * ActivityResultRegistry 给每个 launcher 随机分配的是大于 65535 的值，直接传给系统会抛
 * IllegalArgumentException: Can only use lower 16 bits for requestCode。
 *
 * 映射随 Activity 实例存活；用户停在选择器期间进程若被回收，Activity 重建后映射为空，
 * 因此提供 [save]/[restore] 把映射写入实例状态，保证迟到的结果仍能路由回原 launcher。
 *
 * 线程安全：启动与回调都在主线程，但加同步以防其它路径访问。
 */
class RequestCodeMapper {

    private val toSmallMap = HashMap<Int, Int>()
    private val toFrameworkMap = HashMap<Int, Int>()

    /** 下一个待分配的小整数（从 65534 向下，避开系统与三方库常用的小值） */
    private var next = FIRST

    /** 框架码 → 小整数（同一框架码始终映射到同一小整数） */
    @Synchronized
    fun toSmall(frameworkCode: Int): Int {
        if (frameworkCode in 0..0xFFFF) {
            // 本身就是 16 位内的码（如三方库直接传入）：原样透传，登记以便回程识别
            toFrameworkMap[frameworkCode] = frameworkCode
            return frameworkCode
        }
        toSmallMap[frameworkCode]?.let { return it }
        var candidate = next
        var guard = 0
        // 跳过已被占用的值（含上面直接登记的小值）
        while (candidate in toFrameworkMap && guard < RANGE) {
            candidate = if (candidate > LAST) candidate - 1 else FIRST
            guard++
        }
        next = if (candidate > LAST) candidate - 1 else FIRST
        toSmallMap[frameworkCode] = candidate
        toFrameworkMap[candidate] = frameworkCode
        return candidate
    }

    /** 小整数 → 框架码；不是我们映射过的码返回 null */
    @Synchronized
    fun toFramework(smallCode: Int): Int? = toFrameworkMap[smallCode]

    @Synchronized
    fun save(out: Bundle) {
        out.putIntArray(STATE_SMALL, toSmallMap.keys.toIntArray())
        out.putIntArray(STATE_SMALL_VALUES, toSmallMap.values.toIntArray())
        // 透传登记的小值不在 toSmallMap 里（key == value），单独记录
        out.putIntArray(STATE_PASSTHROUGH, toFrameworkMap.filter { it.key == it.value }.keys.toIntArray())
        out.putInt(STATE_NEXT, next)
    }

    @Synchronized
    fun restore(state: Bundle?) {
        state ?: return
        val keys = state.getIntArray(STATE_SMALL)
        val values = state.getIntArray(STATE_SMALL_VALUES)
        if (keys != null && values != null && keys.size == values.size) {
            for (i in keys.indices) {
                toSmallMap[keys[i]] = values[i]
                toFrameworkMap[values[i]] = keys[i]
            }
        }
        state.getIntArray(STATE_PASSTHROUGH)?.forEach { toFrameworkMap[it] = it }
        next = state.getInt(STATE_NEXT, FIRST)
    }

    @Synchronized
    fun clear() {
        toSmallMap.clear()
        toFrameworkMap.clear()
        next = FIRST
    }

    companion object {
        private const val FIRST = 0xFFFE   // 65534
        private const val LAST = 0x0001
        private const val RANGE = FIRST - LAST + 1

        private const val STATE_SMALL = "rcm.small"
        private const val STATE_SMALL_VALUES = "rcm.smallValues"
        private const val STATE_PASSTHROUGH = "rcm.passthrough"
        private const val STATE_NEXT = "rcm.next"
    }
}
