package com.moyue.reader.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moyue.reader.R

/** 数字密码键盘（4-8 位，手动确认） */
@Composable
fun PinPad(
    entered: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 已输入指示点（最多 8 位）
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(8) { i ->
                val filled = i < entered.length
                Box(
                    Modifier
                        .size(12.dp)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf(' ', '0', '⌫')
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.height(70.dp)
            ) {
                row.forEach { c ->
                    when (c) {
                        ' ' -> Spacer(Modifier.size(64.dp))
                        '⌫' -> Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { onBackspace() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.cd_backspace))
                        }
                        else -> Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onDigit(c) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(c.toString(), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConfirm,
            enabled = entered.length in 4..8
        ) {
            Text(stringResource(R.string.btn_confirm))
        }
    }
}
