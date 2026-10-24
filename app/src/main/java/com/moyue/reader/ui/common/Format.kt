package com.moyue.reader.ui.common

import android.content.Context
import com.moyue.reader.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDuration(context: Context, millis: Long): String {
    if (millis <= 0) return context.getString(R.string.dur_zero)
    val minutes = millis / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h <= 0 -> context.getString(R.string.dur_minutes, m)
        m == 0L -> context.getString(R.string.dur_hours, h)
        else -> context.getString(R.string.dur_hours_minutes, h, m)
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
