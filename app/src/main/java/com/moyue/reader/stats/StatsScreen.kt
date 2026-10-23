package com.moyue.reader.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.di.Graph
import com.moyue.reader.ui.common.BookCover
import com.moyue.reader.ui.common.fmtDuration
import com.moyue.reader.ui.common.fmtSize
import com.moyue.reader.ui.common.maskTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    data class StatsData(
        val todayMillis: Long = 0,
        val totalMillis: Long = 0,
        val books: List<BookEntity> = emptyList()
    )

    // 隐私模式下排行中的书名/封面同样隐藏
    val prefs by Graph.settingsRepository.prefs
        .collectAsStateWithLifecycle(initialValue = ReaderPrefs())

    val stats by produceState(initialValue = StatsData()) {
        val repo = Graph.bookRepository
        value = StatsData(
            todayMillis = repo.readStatToday(),
            totalMillis = repo.readTimeTotalMillis(),
            books = repo.booksOnce().sortedByDescending { it.readTimeMillis }.take(20)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("今日阅读", style = MaterialTheme.typography.labelMedium)
                        Text(
                            fmtDuration(stats.todayMillis),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("累计阅读", style = MaterialTheme.typography.labelMedium)
                        Text(
                            fmtDuration(stats.totalMillis),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            Text(
                "阅读时长排行（前 20）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (stats.books.isEmpty()) {
                Text(
                    "还没有阅读记录，先去读一会儿书吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn {
                    items(stats.books, key = { it.id }) { book ->
                        val rank = stats.books.indexOf(book) + 1
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$rank",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (rank <= 3) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            BookCover(
                                title = book.title,
                                hue = book.coverHue,
                                fontSize = 14.sp,
                                masked = prefs.privacyMode,
                                modifier = Modifier
                                    .width(30.dp)
                                    .height(42.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    maskTitle(book.title, prefs.privacyMode),
                                    maxLines = 1
                                )
                                Text(
                                    "${book.chapterCount} 章 · ${fmtSize(book.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                fmtDuration(book.readTimeMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
