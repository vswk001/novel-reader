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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyue.reader.R
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
    val context = LocalContext.current
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
                .onSuccess { message = context.getString(R.string.msg_backup_ok, it) }
                .onFailure { message = context.getString(R.string.msg_backup_fail, it.message ?: "") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            Graph.backupManager.importFrom(uri)
                .onSuccess { (matched, merged) ->
                    message = context.getString(R.string.msg_restore_ok, matched, merged)
                }
                .onFailure { message = context.getString(R.string.msg_restore_fail, it.message ?: "") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            SectionCard(stringResource(R.string.sec_reading)) {
                ChipRow(stringResource(R.string.label_read_mode)) {
                    ReadMode.entries.forEach { m ->
                        FilterChip(
                            selected = prefs.readMode == m,
                            onClick = { update { it.copy(readMode = m) } },
                            label = {
                                Text(
                                    if (m == ReadMode.PAGE) stringResource(R.string.mode_page)
                                    else stringResource(R.string.mode_scroll)
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                if (prefs.readMode == ReadMode.PAGE) {
                    ChipRow(stringResource(R.string.label_page_anim)) {
                        PageAnim.entries.forEach { anim ->
                            FilterChip(
                                selected = prefs.pageAnim == anim,
                                onClick = { update { it.copy(pageAnim = anim) } },
                                label = {
                                    Text(
                                        when (anim) {
                                            PageAnim.SLIDE -> stringResource(R.string.anim_slide)
                                            PageAnim.COVER -> stringResource(R.string.anim_cover)
                                            PageAnim.FADE -> stringResource(R.string.anim_fade)
                                            PageAnim.NONE -> stringResource(R.string.anim_none)
                                            PageAnim.CURL -> stringResource(R.string.anim_curl)
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
                ChipRow(stringResource(R.string.label_tap_zone)) {
                    listOf(
                        ClickZone.LEFT_RIGHT to stringResource(R.string.zone_left_right),
                        ClickZone.TOP_BOTTOM to stringResource(R.string.zone_top_bottom),
                        ClickZone.OFF to stringResource(R.string.zone_off)
                    ).forEach { (zone, label) ->
                        FilterChip(
                            selected = prefs.tapZone == zone,
                            onClick = { update { it.copy(tapZone = zone) } },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                PrefSwitch(
                    stringResource(R.string.sw_volume_key),
                    stringResource(R.string.sw_volume_key_sub),
                    prefs.volumeKeyTurn
                ) {
                    v -> update { it.copy(volumeKeyTurn = v) }
                }
                PrefSlider(
                    label = stringResource(R.string.label_tts_rate),
                    valueLabel = stringResource(R.string.fmt_x_times, prefs.ttsRate),
                    value = prefs.ttsRate,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    onChange = { v -> update { it.copy(ttsRate = (v * 10).toInt() / 10f) } }
                )
                PrefSlider(
                    label = stringResource(R.string.label_autoscroll_speed),
                    valueLabel = "${prefs.autoScrollSpeed} dp/s",
                    value = prefs.autoScrollSpeed.toFloat(),
                    valueRange = 40f..400f,
                    steps = 35,
                    onChange = { v -> update { it.copy(autoScrollSpeed = v.toInt()) } }
                )
            }

            // ---------- 排版 ----------
            SectionCard(stringResource(R.string.sec_layout)) {
                PrefSlider(
                    label = stringResource(R.string.label_font_size),
                    valueLabel = "${prefs.fontSp} sp",
                    value = prefs.fontSp.toFloat(),
                    valueRange = 10f..40f,
                    steps = 29,
                    onChange = { v -> update { it.copy(fontSp = v.toInt()) } }
                )
                PrefSlider(
                    label = stringResource(R.string.label_line_spacing),
                    valueLabel = stringResource(R.string.fmt_x_times, prefs.lineSpacing),
                    value = prefs.lineSpacing,
                    valueRange = 1.0f..2.4f,
                    steps = 13,
                    onChange = { v -> update { it.copy(lineSpacing = (v * 10).toInt() / 10f) } }
                )
                PrefSlider(
                    label = stringResource(R.string.label_padding_h),
                    valueLabel = "${prefs.paddingH} dp",
                    value = prefs.paddingH.toFloat(),
                    valueRange = 8f..48f,
                    steps = 39,
                    onChange = { v -> update { it.copy(paddingH = v.toInt()) } }
                )
                PrefSlider(
                    label = stringResource(R.string.label_padding_v),
                    valueLabel = "${prefs.paddingV} dp",
                    value = prefs.paddingV.toFloat(),
                    valueRange = 4f..64f,
                    steps = 59,
                    onChange = { v -> update { it.copy(paddingV = v.toInt()) } }
                )
            }

            // ---------- 主题与字体 ----------
            SectionCard(stringResource(R.string.sec_theme)) {
                PrefSwitch(
                    label = stringResource(R.string.sw_follow_dark),
                    subtitle = stringResource(R.string.sw_follow_dark_sub),
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
                        onClick = { update { it.copy(serif = !prefs.serif) } },
                        label = {
                            Text(
                                if (prefs.serif) stringResource(R.string.font_style_serif)
                                else stringResource(R.string.font_style_sans)
                            )
                        }
                    )
                }
            }

            // ---------- 阅读体验 ----------
            SectionCard(stringResource(R.string.sec_experience)) {
                PrefSwitch(
                    stringResource(R.string.sw_keep_on),
                    stringResource(R.string.sw_keep_on_sub),
                    prefs.keepScreenOn
                ) {
                    v -> update { it.copy(keepScreenOn = v) }
                }
                PrefSwitch(
                    label = stringResource(R.string.sw_secure),
                    subtitle = stringResource(R.string.sw_secure_sub),
                    checked = prefs.secureMode,
                    onChange = { v -> update { it.copy(secureMode = v) } }
                )
            }

            // ---------- 隐私空间说明 ----------
            SectionCard(stringResource(R.string.sec_private)) {
                Text(
                    stringResource(R.string.private_help),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 备份与恢复 ----------
            SectionCard(stringResource(R.string.sec_backup)) {
                Text(
                    stringResource(R.string.backup_help),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(onClick = { exportLauncher.launch(BackupManager.suggestFileName()) }) {
                        Text(stringResource(R.string.btn_export))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }) {
                        Text(stringResource(R.string.btn_import_backup))
                    }
                }
            }

            // ---------- 关于 ----------
            SectionCard(stringResource(R.string.sec_about)) {
                Text(
                    stringResource(R.string.about_text),
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
