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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import com.moyue.reader.data.repo.ClickZone
import com.moyue.reader.data.repo.PageAnim
import com.moyue.reader.data.repo.ReadMode
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
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(it, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // ---------- 阅读方式 ----------
            SectionCard("阅读方式") {
                ChipRow("阅读模式") {
                    ReadMode.entries.forEach { m ->
                        FilterChip(
                            selected = prefs.readMode == m,
                            onClick = { update { it.copy(readMode = m) } },
                            label = { Text(if (m == ReadMode.PAGE) "左右翻页" else "上下滚动") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                if (prefs.readMode == ReadMode.PAGE) {
                    ChipRow("翻页动画") {
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
                                            PageAnim.CURL -> "仿真"
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
                ChipRow("点击翻页区域") {
                    listOf(
                        ClickZone.LEFT_RIGHT to "左右两侧",
                        ClickZone.TOP_BOTTOM to "上下两段",
                        ClickZone.OFF to "关闭"
                    ).forEach { (zone, label) ->
                        FilterChip(
                            selected = prefs.tapZone == zone,
                            onClick = { update { it.copy(tapZone = zone) } },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                PrefSwitch("音量键翻页", "音量+上一页，音量-下一页", prefs.volumeKeyTurn) {
                    v -> update { it.copy(volumeKeyTurn = v) }
                }
                PrefSlider(
                    label = "朗读语速",
                    valueLabel = String.format("%.1f 倍", prefs.ttsRate),
                    value = prefs.ttsRate,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    onChange = { v -> update { it.copy(ttsRate = (v * 10).toInt() / 10f) } }
                )
                PrefSlider(
                    label = "自动滚动速度",
                    valueLabel = "${prefs.autoScrollSpeed} dp/s",
                    value = prefs.autoScrollSpeed.toFloat(),
                    valueRange = 40f..400f,
                    steps = 35,
                    onChange = { v -> update { it.copy(autoScrollSpeed = v.toInt()) } }
                )
            }

            // ---------- 排版 ----------
            SectionCard("阅读排版") {
                PrefSlider(
                    label = "字号",
                    valueLabel = "${prefs.fontSp} sp",
                    value = prefs.fontSp.toFloat(),
                    valueRange = 10f..40f,
                    steps = 29,
                    onChange = { v -> update { it.copy(fontSp = v.toInt()) } }
                )
                PrefSlider(
                    label = "行距",
                    valueLabel = String.format("%.1f 倍", prefs.lineSpacing),
                    value = prefs.lineSpacing,
                    valueRange = 1.0f..2.4f,
                    steps = 13,
                    onChange = { v -> update { it.copy(lineSpacing = (v * 10).toInt() / 10f) } }
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
            }

            // ---------- 主题与字体 ----------
            SectionCard("主题与字体") {
                PrefSwitch(
                    label = "跟随系统深色模式",
                    subtitle = "夜间自动切换黑底主题",
                    checked = prefs.themeFollowSystem,
                    onChange = { v -> update { it.copy(themeFollowSystem = v) } }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(4.dp))
                    ReaderThemes.selectable().forEach { kind ->
                        val c = ReaderThemes.of(kind)
                        val selected = prefs.theme == kind && !prefs.themeFollowSystem
                        Box(
                            Modifier
                                .padding(end = 12.dp)
                                .size(32.dp)
                                .background(c.bg, CircleShape)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { update { it.copy(theme = kind) } }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = prefs.serif,
                        onClick = { update { it.copy(serif = !it.serif) } },
                        label = { Text(if (prefs.serif) "宋体" else "黑体") }
                    )
                }
            }

            // ---------- 阅读体验 ----------
            SectionCard("阅读体验") {
                PrefSwitch("屏幕常亮", "阅读时保持屏幕不休眠", prefs.keepScreenOn) {
                    v -> update { it.copy(keepScreenOn = v) }
                }
                PrefSwitch(
                    label = "禁止截屏",
                    subtitle = "开启后截屏/录屏黑屏，最近任务无预览（FLAG_SECURE）",
                    checked = prefs.secureMode,
                    onChange = { v -> update { it.copy(secureMode = v) } }
                )
            }

            // ---------- 隐私空间说明 ----------
            SectionCard("隐私空间") {
                Text(
                    "在书架页面下拉过半屏松开即可进入隐私空间；\n首次进入需设置数字或图案密码，两个空间的书籍完全独立。\n忘记密码可通过「设置 → 应用 → 清除数据」重置（会清空书架）。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 备份与恢复 ----------
            SectionCard("备份与恢复") {
                Text(
                    "备份包含：书架（含隐私空间）、阅读进度、书签、设置（不含书籍文件本身）。恢复时按「书名+文件大小」匹配本机书籍。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row {
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
            }

            // ---------- 关于 ----------
            SectionCard("关于") {
                Text(
                    "墨阅 v1.6.0 · 纯本地小说阅读器\n无网络权限、无广告、无遥测",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 分组卡片容器 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
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
            Text(
                valueLabel,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
