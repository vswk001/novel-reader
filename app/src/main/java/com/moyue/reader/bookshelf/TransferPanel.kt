package com.moyue.reader.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyue.reader.R
import com.moyue.reader.data.transfer.TransferServer
import com.moyue.reader.di.Graph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wi-Fi 传书面板：地址 + 配对码 + 倒计时 + 实时传输记录 */
@Composable
fun TransferPanel(space: Int, onDismiss: () -> Unit) {
    val server = Graph.transferServer
    val state by server.state.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    DisposableEffect(space) {
        server.start(space)
        onDispose { server.stop() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.transfer_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (!state.running) {
                    Text(
                        stringResource(R.string.transfer_no_wifi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.btn_close))
                    }
                    return@Column
                }

                Text(
                    stringResource(R.string.transfer_open_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                // 访问地址（多个网卡则全部展示）
                state.urls.forEach { url ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            url,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.transfer_copy),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 配对码 + 倒计时
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.transfer_pair_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            state.pairCode,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        stringResource(R.string.transfer_closing_fmt, state.secondsLeft / 60),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(10.dp))

                // 传输记录
                Text(
                    stringResource(R.string.transfer_log),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (state.events.isEmpty()) {
                    Text(
                        stringResource(R.string.transfer_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(Modifier.height(160.dp)) {
                        items(state.events.asReversed()) { ev ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ev.time)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ev.fileName,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        ev.message,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        color = if (ev.ok) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    if (ev.ok) "✓" else "✗",
                                    color = if (ev.ok) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.transfer_wifi_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.transfer_stop))
                }
            }
        }
    }
}
