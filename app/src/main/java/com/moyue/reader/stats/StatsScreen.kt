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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyue.reader.R
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.di.Graph
import com.moyue.reader.ui.common.BookCover
import com.moyue.reader.ui.common.fmtDuration
import com.moyue.reader.ui.common.fmtSize
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(space: Int = 0, onBack: () -> Unit) {
    data class StatsData(
        val todayMillis: Long = 0,
        val totalMillis: Long = 0,
        val streak: Int = 0,
        val books: List<BookEntity> = emptyList()
    )

    val context = LocalContext.current
    val prefs by Graph.settingsRepository.prefs
        .collectAsStateWithLifecycle(initialValue = ReaderPrefs())
    val stats by produceState(initialValue = StatsData(), space) {
        val repo = Graph.bookRepository
        value = StatsData(
            todayMillis = repo.readStatToday(),
            totalMillis = repo.readTimeTotalMillis(),
            streak = repo.streakDays(),
            books = repo.booksOnce(space).sortedByDescending { it.readTimeMillis }.take(20)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (space == 1) stringResource(R.string.stats_title_private)
                        else stringResource(R.string.stats_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                        Text(stringResource(R.string.stats_today), style = MaterialTheme.typography.labelMedium)
                        Text(
                            fmtDuration(context, stats.todayMillis),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.stats_total), style = MaterialTheme.typography.labelMedium)
                        Text(
                            fmtDuration(context, stats.totalMillis),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }

            // 今日目标 + 连续天数
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stats_goal),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                stringResource(
                                    R.string.stats_goal_value,
                                    (stats.todayMillis / 60_000L).toInt(), prefs.dailyGoalMin
                                ) + " · " + fmtDuration(context, stats.todayMillis),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        Text(
                            stringResource(R.string.stats_streak, stats.streak),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (stats.streak > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (prefs.dailyGoalMin <= 0) 0f
                            else (stats.todayMillis / (prefs.dailyGoalMin * 60_000f))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.stats_goal_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = prefs.dailyGoalMin.toFloat(),
                        onValueChange = { v ->
                            scope.launch {
                                Graph.settingsRepository.update {
                                    it.copy(dailyGoalMin = (v / 5).toInt() * 5)
                                }
                            }
                        },
                        valueRange = 10f..180f
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            Text(
                stringResource(R.string.stats_rank),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (stats.books.isEmpty()) {
                Text(
                    stringResource(R.string.stats_empty),
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
                                fontSize = 13.sp,
                                maxChars = 2,
                                modifier = Modifier
                                    .width(30.dp)
                                    .height(42.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(book.title, maxLines = 1)
                                Text(
                                    stringResource(R.string.chapters_count, book.chapterCount) +
                                        " · " + fmtSize(book.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                fmtDuration(context, book.readTimeMillis),
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
