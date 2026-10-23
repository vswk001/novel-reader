package com.moyue.reader.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDuration(millis: Long): String {
    if (millis <= 0) return "0 分钟"
    val minutes = millis / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h <= 0 -> "$m 分钟"
        m == 0L -> "$h 小时"
        else -> "$h 小时 $m 分钟"
    }
}

fun fmtSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1fMB".format(bytes / 1048576.0)
    bytes >= 1 shl 10 -> "%.0fKB".format(bytes / 1024.0)
    else -> "${bytes}B"
}

fun fmtDateTime(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    ).format(Date(epochMillis))

/** 隐私模式下对外展示的书名占位 */
const val PRIVACY_MASK_TITLE = "🔒 私密图书"

fun maskTitle(title: String, privacyMode: Boolean): String =
    if (privacyMode) PRIVACY_MASK_TITLE else title
