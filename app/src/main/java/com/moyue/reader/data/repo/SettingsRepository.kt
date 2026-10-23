package com.moyue.reader.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
enum class ReaderThemeKind { WHITE, SEPIA, GREEN, GRAY, BLACK }

@Serializable
enum class PageAnim { SLIDE, COVER, FADE, NONE }

@Serializable
enum class SortKind { RECENT, TITLE, ADDED }

/** 全局阅读与应用偏好，UI 与阅读器共用 */
@Serializable
data class ReaderPrefs(
    val fontSp: Int = 20,
    /** 行距倍数 1.0..2.4 */
    val lineSpacing: Float = 1.5f,
    /** 正文水平边距 dp */
    val paddingH: Int = 16,
    /** 正文上下边距 dp */
    val paddingV: Int = 12,
    val theme: ReaderThemeKind = ReaderThemeKind.SEPIA,
    val themeFollowSystem: Boolean = false,
    val serif: Boolean = false,
    val pageAnim: PageAnim = PageAnim.SLIDE,
    val volumeKeyTurn: Boolean = true,
    val tapTurn: Boolean = true,
    val keepScreenOn: Boolean = true,
    val brightnessFollowSystem: Boolean = true,
    /** 0.02..1.0，仅作用于本应用窗口 */
    val brightness: Float = 0.5f,
    val secureMode: Boolean = false,
    val sortBy: SortKind = SortKind.RECENT,
    val bookshelfGrid: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSp = intPreferencesKey("font_sp")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val paddingH = intPreferencesKey("padding_h")
        val paddingV = intPreferencesKey("padding_v")
        val theme = stringPreferencesKey("reader_theme")
        val themeFollowSystem = booleanPreferencesKey("theme_follow_system")
        val serif = booleanPreferencesKey("serif")
        val pageAnim = stringPreferencesKey("page_anim")
        val volumeKeyTurn = booleanPreferencesKey("volume_key_turn")
        val tapTurn = booleanPreferencesKey("tap_turn")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val brightnessFollowSystem = booleanPreferencesKey("brightness_follow_system")
        val brightness = floatPreferencesKey("brightness")
        val secureMode = booleanPreferencesKey("secure_mode")
        val sortBy = stringPreferencesKey("sort_by")
        val bookshelfGrid = booleanPreferencesKey("bookshelf_grid")
    }

    val prefs: Flow<ReaderPrefs> = context.dataStore.data.map { p ->
        ReaderPrefs(
            fontSp = p[Keys.fontSp] ?: 20,
            lineSpacing = p[Keys.lineSpacing] ?: 1.5f,
            paddingH = p[Keys.paddingH] ?: 16,
            paddingV = p[Keys.paddingV] ?: 12,
            theme = enumOrDefault(p[Keys.theme], ReaderThemeKind.SEPIA),
            themeFollowSystem = p[Keys.themeFollowSystem] ?: false,
            serif = p[Keys.serif] ?: false,
            pageAnim = enumOrDefault(p[Keys.pageAnim], PageAnim.SLIDE),
            volumeKeyTurn = p[Keys.volumeKeyTurn] ?: true,
            tapTurn = p[Keys.tapTurn] ?: true,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            brightnessFollowSystem = p[Keys.brightnessFollowSystem] ?: true,
            brightness = p[Keys.brightness] ?: 0.5f,
            secureMode = p[Keys.secureMode] ?: false,
            sortBy = enumOrDefault(p[Keys.sortBy], SortKind.RECENT),
            bookshelfGrid = p[Keys.bookshelfGrid] ?: true
        )
    }

    suspend fun current(): ReaderPrefs = prefs.first()

    suspend fun update(transform: (ReaderPrefs) -> ReaderPrefs) {
        context.dataStore.edit { p ->
            val old = prefsFrom(p)
            val new = transform(old)
            p[Keys.fontSp] = new.fontSp
            p[Keys.lineSpacing] = new.lineSpacing
            p[Keys.paddingH] = new.paddingH
            p[Keys.paddingV] = new.paddingV
            p[Keys.theme] = new.theme.name
            p[Keys.themeFollowSystem] = new.themeFollowSystem
            p[Keys.serif] = new.serif
            p[Keys.pageAnim] = new.pageAnim.name
            p[Keys.volumeKeyTurn] = new.volumeKeyTurn
            p[Keys.tapTurn] = new.tapTurn
            p[Keys.keepScreenOn] = new.keepScreenOn
            p[Keys.brightnessFollowSystem] = new.brightnessFollowSystem
            p[Keys.brightness] = new.brightness
            p[Keys.secureMode] = new.secureMode
            p[Keys.sortBy] = new.sortBy.name
            p[Keys.bookshelfGrid] = new.bookshelfGrid
        }
    }

    private fun prefsFrom(p: Preferences): ReaderPrefs = ReaderPrefs(
        fontSp = p[Keys.fontSp] ?: 20,
        lineSpacing = p[Keys.lineSpacing] ?: 1.5f,
        paddingH = p[Keys.paddingH] ?: 16,
        paddingV = p[Keys.paddingV] ?: 12,
        theme = enumOrDefault(p[Keys.theme], ReaderThemeKind.SEPIA),
        themeFollowSystem = p[Keys.themeFollowSystem] ?: false,
        serif = p[Keys.serif] ?: false,
        pageAnim = enumOrDefault(p[Keys.pageAnim], PageAnim.SLIDE),
        volumeKeyTurn = p[Keys.volumeKeyTurn] ?: true,
        tapTurn = p[Keys.tapTurn] ?: true,
        keepScreenOn = p[Keys.keepScreenOn] ?: true,
        brightnessFollowSystem = p[Keys.brightnessFollowSystem] ?: true,
        brightness = p[Keys.brightness] ?: 0.5f,
        secureMode = p[Keys.secureMode] ?: false,
        sortBy = enumOrDefault(p[Keys.sortBy], SortKind.RECENT),
        bookshelfGrid = p[Keys.bookshelfGrid] ?: true
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
