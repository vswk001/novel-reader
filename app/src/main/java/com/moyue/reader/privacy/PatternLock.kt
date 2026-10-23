package com.moyue.reader.privacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 3x3 图案密码控件：拖动连线，松手回调点序（如 "0-4-8"）。
 */
@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    onPattern: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var active by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                fun hit(p: Offset): Int? {
                    val cell = size.width / 4f
                    for (i in 0 until 9) {
                        val c = Offset(cell + (i % 3) * cell, cell + (i / 3) * cell)
                        if ((p - c).getDistance() <= cell * 0.45f) return i
                    }
                    return null
                }
                detectDragGestures(
                    onDragStart = { p ->
                        active = true
                        selected = emptyList()
                        finger = p
                        hit(p)?.let { selected = listOf(it) }
                    },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)?.let {
                            if (it !in selected) selected = selected + it
                        }
                    },
                    onDragEnd = {
                        active = false
                        val pattern = selected
                        selected = emptyList()
                        if (pattern.isNotEmpty()) onPattern(pattern.joinToString("-"))
                    },
                    onDragCancel = {
                        active = false
                        selected = emptyList()
                    }
                )
            }
    ) {
        val cell = size.width / 4f
        fun center(i: Int) = Offset(cell + (i % 3) * cell, cell + (i / 3) * cell)

        for (i in 0 until 9) {
            val isSel = i in selected
            drawCircle(
                color = if (isSel) primary else outline,
                radius = if (isSel) cell * 0.15f else cell * 0.10f,
                center = center(i)
            )
        }
        if (selected.size >= 2) {
            val path = Path()
            val pts = selected.map { center(it) }
            path.moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { path.lineTo(it.x, it.y) }
            drawPath(path, color = primary, style = Stroke(width = cell * 0.06f, cap = StrokeCap.Round))
        }
        if (active && selected.isNotEmpty()) {
            drawLine(
                color = primary.copy(alpha = 0.5f),
                start = center(selected.last()),
                end = finger,
                strokeWidth = cell * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}
