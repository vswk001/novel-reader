package com.moyue.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.R

/**
 * 自动生成封面：竖排书名（像真实书脊）+ 色调渐变，圆角卡片。
 * @param maxChars 竖排最多显示的字符数（小尺寸封面传小值）
 */
@Composable
fun BookCover(
    title: String,
    hue: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    maxChars: Int = 4
) {
    val brush = remember(hue) {
        val light = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.42f, 0.66f)))
        val dark = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.52f, 0.42f)))
        Brush.linearGradient(listOf(light, dark))
    }
    val shown = title.take(maxChars).let {
        if (title.length > maxChars) it.dropLast(1) + "…" else it
    }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(brush)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .align(Alignment.Center)
                .padding(top = 10.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            shown.forEach { c ->
                Text(
                    c.toString(),
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = stringResource(R.string.cover_brand),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
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
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            if (progressPercent > 0) stringResource(R.string.progress_read, progressPercent)
            else stringResource(R.string.progress_unread),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** 细进度条（阅读百分比） */
@Composable
fun BookProgressBar(percent: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (percent.coerceIn(0, 100)) / 100f },
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .height(3.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round
    )
}
