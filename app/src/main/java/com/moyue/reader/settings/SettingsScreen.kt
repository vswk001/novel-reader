package com.moyue.reader.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyue.reader.data.backup.BackupManager
import com.moyue.reader.data.repo.PageAnim
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.data.repo.ReaderThemeKind
import com.moyue.reader.di.Graph
import com.moyue.reader.reader.ReaderThemes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs by Graph.settingsRepository.prefs
        .collectAsStateWithLifecycle(initialValue = ReaderPrefs())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    fun update(transform: (ReaderPrefs) -> ReaderPrefs) {
        scope.launch { Graph.settingsRepository.update(transform) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            Graph.backupManager.exportTo(uri)
                .onSuccess { message = "备份成功：导出 $it 本书的数据" }
                .onFailure { message = "备份失败：${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            Graph.backupManager.importFrom(uri)
                .onSuccess { (matched, merged) ->
                    message = "恢复完成：匹配到 $matched 本书（书籍文件需已在本机），合并进度 $merged 本"
                }
                .onFailure { message = "恢复失败：${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            SectionTitle("阅读排版")
            PrefSlider(
                label = "字号",
                valueLabel = "${prefs.fontSp} sp",
                value = prefs.fontSp.toFloat(),
                valueRange = 10f..40f,
                steps = 29,
                onChange = { v ->
                    update { it.copy(fontSp = v.toInt()) }
                }
            )
            PrefSlider(
                label = "行距",
                valueLabel = String.format("%.1f 倍", prefs.lineSpacing),
                value = prefs.lineSpacing,
                valueRange = 1.0f..2.4f,
                steps = 13,
                onChange = { v ->
                    update { it.copy(lineSpacing = (v * 10).toInt() / 10f) }
                }
            )
            PrefSlider(
                label = "水平边距",
                valueLabel = "${prefs.paddingH} dp",
                value = prefs.paddingH.toFloat(),
                valueRange = 8f..48f,
                steps = 39,
                onChange = { v -> update { it.copy(paddingH = v.toInt()) } }
            )
            PrefSlider(
                label = "上下边距",
                valueLabel = "${prefs.paddingV} dp",
                value = prefs.paddingV.toFloat(),
                valueRange = 4f..64f,
                steps = 59,
                onChange = { v -> update { it.copy(paddingV = v.toInt()) } }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("主题与字体")
            PrefSwitch(
                label = "跟随系统深色模式",
                subtitle = "夜间自动切换黑底主题",
                checked = prefs.themeFollowSystem,
                onChange = { v -> update { it.copy(themeFollowSystem = v) } }
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderThemes.selectable().forEach { kind ->
                    val c = ReaderThemes.of(kind)
                    val selected = prefs.theme == kind && !prefs.themeFollowSystem
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .size(30.dp)
                            .background(c.bg, CircleShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { update { it.copy(theme = kind) } }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("默认字体", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = prefs.serif,
                    onClick = { update { it.copy(serif = !it.serif) } },
                    label = { Text(if (prefs.serif) "宋体" else "黑体") }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("翻页")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageAnim.entries.forEach { anim ->
                    FilterChip(
                        selected = prefs.pageAnim == anim,
                        onClick = { update { it.copy(pageAnim = anim) } },
                        label = {
                            Text(
                                when (anim) {
                                    PageAnim.SLIDE -> "滑动"
                                    PageAnim.COVER -> "覆盖"
                                    PageAnim.FADE -> "淡入"
                                    PageAnim.NONE -> "无"
                                }
                            )
                        }
                    )
                }
            }
            PrefSwitch("音量键翻页", "音量+上一页，音量-下一页", prefs.volumeKeyTurn) {
                v -> update { it.copy(volumeKeyTurn = v) }
            }
            PrefSwitch("点击区域翻页", "左/右侧点击翻页，中间呼出菜单", prefs.tapTurn) {
                v -> update { it.copy(tapTurn = v) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("阅读体验")
            PrefSwitch("屏幕常亮", "阅读时保持屏幕不休眠", prefs.keepScreenOn) {
                v -> update { it.copy(keepScreenOn = v) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("隐私")
            PrefSwitch(
                label = "隐私模式",
                subtitle = "禁止截屏与最近任务预览；书架和统计中隐藏书名与封面",
                checked = prefs.privacyMode,
                onChange = { v -> update { it.copy(privacyMode = v) } }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("备份与恢复")
            Text(
                "备份包含：书架、阅读进度、书签、设置（不含书籍文件本身）。\n恢复时按「书名+文件大小」匹配本机书籍。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.padding(vertical = 8.dp)) {
                Button(onClick = { exportLauncher.launch(BackupManager.suggestFileName()) }) {
                    Text("导出备份")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                }) {
                    Text("导入备份")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("关于")
            Text("墨阅 v1.0.0 · 纯本地小说阅读器\n无网络权限、无广告、无遥测", fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun PrefSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(valueLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun PrefSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
