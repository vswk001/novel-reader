package com.moyue.reader.reader

import androidx.compose.ui.graphics.Color
import com.moyue.reader.data.repo.ReaderThemeKind

/** 阅读界面配色（独立于应用 Material 主题） */
data class ReaderPalette(
    val bg: Color,
    val text: Color,
    val secondary: Color,
    val menuBg: Color,
    val menuText: Color,
    val divider: Color,
    val isDark: Boolean
)

object ReaderThemes {
    fun of(kind: ReaderThemeKind): ReaderPalette = when (kind) {
        ReaderThemeKind.WHITE -> ReaderPalette(
            bg = Color(0xFFFFFFFF), text = Color(0xFF212121), secondary = Color(0xFF9E9E9E),
            menuBg = Color(0xFFFFFFFF), menuText = Color(0xFF212121),
            divider = Color(0xFFEDEDED), isDark = false
        )
        ReaderThemeKind.SEPIA -> ReaderPalette(
            bg = Color(0xFFF7F1E1), text = Color(0xFF3B3226), secondary = Color(0xFFA79B80),
            menuBg = Color(0xFFF1EBD9), menuText = Color(0xFF3B3226),
            divider = Color(0xFFE0D8C2), isDark = false
        )
        ReaderThemeKind.GREEN -> ReaderPalette(
            bg = Color(0xFFE4EFDA), text = Color(0xFF2E3B2A), secondary = Color(0xFF8FA184),
            menuBg = Color(0xFFDDE9D1), menuText = Color(0xFF2E3B2A),
            divider = Color(0xFFCBDCC0), isDark = false
        )
        ReaderThemeKind.GRAY -> ReaderPalette(
            bg = Color(0xFFE7E7E7), text = Color(0xFF333333), secondary = Color(0xFF999999),
            menuBg = Color(0xFFF0F0F0), menuText = Color(0xFF333333),
            divider = Color(0xFFDDDDDD), isDark = false
        )
        ReaderThemeKind.BLACK -> ReaderPalette(
            bg = Color(0xFF101010), text = Color(0xFFB3B3B3), secondary = Color(0xFF6E6E6E),
            menuBg = Color(0xFF1C1C1C), menuText = Color(0xFFCCCCCC),
            divider = Color(0xFF2C2C2C), isDark = true
        )
    }

    fun selectable(): List<ReaderThemeKind> = ReaderThemeKind.entries.toList()
}
