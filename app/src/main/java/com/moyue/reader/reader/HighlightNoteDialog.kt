package com.moyue.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.R
import com.moyue.reader.ui.common.MoyueDialog

/** 划线/笔记编辑对话框：摘录 + 笔记输入 + 颜色选择；可删除（编辑已有时） */
@Composable
fun HighlightNoteDialog(
    excerpt: String,
    initialNote: String,
    initialColor: Int,
    onSave: (note: String, color: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf(initialNote) }
    var color by remember { mutableIntStateOf(initialColor) }

    MoyueDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.hl_note_title),
        confirmText = stringResource(R.string.hl_save),
        onConfirm = { onSave(note, color) },
        dismissText = stringResource(R.string.btn_cancel)
    ) {
        Column {
            Text(
                excerpt,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.hl_note_hint)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.hl_color),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(10.dp))
                PageSpans.HIGHLIGHT_COLORS.forEachIndexed { i, c ->
                    val selected = i == color
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 26.dp else 20.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(c),
                                CircleShape
                            )
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { color = i },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color.White,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    Text(
                        stringResource(R.string.hl_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onDelete() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
