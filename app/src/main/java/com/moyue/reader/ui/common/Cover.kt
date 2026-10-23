package com.moyue.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 自动生成封面：书名首字 + 色调渐变 */
@Composable
fun BookCover(
    title: String,
    hue: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp
) {
    val brush = remember(hue) {
        val light = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.42f, 0.66f)))
        val dark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.52f, 0.42f)))
        Brush.linearGradient(listOf(light, dark))
    }
    Box(
        modifier
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.firstOrNull()?.toString() ?: "书",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "墨阅",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 书架条目的标题/进度文字组合 */
@Composable
fun BookMeta(title: String, progressPercent: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Text(
            if (progressPercent > 0) "已读 $progressPercent%" else "未开始",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
    }
}
