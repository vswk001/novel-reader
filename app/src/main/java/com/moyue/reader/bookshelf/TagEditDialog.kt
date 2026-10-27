package com.moyue.reader.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.R
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.BookTags
import com.moyue.reader.ui.common.MoyueDialog

/** 书籍标签编辑：已有标签点选 + 输入新建，最多 6 个（书架筛选条保持简洁） */
@Composable
fun TagEditDialog(
    book: BookEntity,
    allTags: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember(book.id) { BookTags.parse(book.tags).toMutableList() }
    var input by remember { mutableStateOf("") }

    fun addTag(t: String) {
        val tag = t.trim().take(12)
        if (tag.isNotEmpty() && tag !in selected && selected.size < 6) {
            selected += tag
        }
    }

    MoyueDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.action_tags),
        subtitle = book.title,
        confirmText = stringResource(R.string.hl_save),
        onConfirm = { onSave(selected.toList()) },
        dismissText = stringResource(R.string.btn_cancel)
    ) {
        Column {
            if (allTags.isNotEmpty()) {
                Column(
                    Modifier
                        .height(120.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    allTags.chunked(3).forEach { rowTags ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTags.forEach { t ->
                                FilterChip(
                                    selected = t in selected,
                                    onClick = {
                                        if (t in selected) selected.remove(t) else addTag(t)
                                    },
                                    label = { Text(t, fontSize = 12.sp) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(12) },
                    placeholder = { Text(stringResource(R.string.tag_new_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { addTag(input); input = "" }, enabled = input.isNotBlank()) {
                    Text(stringResource(R.string.tag_add))
                }
            }
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        selected.joinToString(" / "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
