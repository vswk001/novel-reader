package com.moyue.reader.data.repo

import com.moyue.reader.di.Graph
import java.io.File

/**
 * 阅读进度诊断日志：记录每次保存/恢复的完整决策链（环形，≤200KB）。
 * 用于用户侧复现"重开位置偏移"后导出取证；平时开销极小（几行文本追加）。
 */
object PosLog {

    private val file: File get() = File(Graph.appContext.filesDir, "poslog.txt")

    @Synchronized
    fun log(tag: String, vararg kv: Any?) {
        runCatching {
            val sb = StringBuilder(96)
            sb.append(System.currentTimeMillis()).append(' ').append(tag)
            for (v in kv) sb.append(' ').append(v)
            sb.append('\n')
            val f = file
            f.appendText(sb.toString())
            if (f.length() > 200_000) {
                val keep = f.readLines().takeLast(300)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun dump(): String = runCatching { file.readText() }.getOrDefault("")

    fun clear() {
        runCatching { file.delete() }
    }
}
