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
enum class PageAnim { SLIDE, COVER, FADE, NONE, CURL }

@Serializable
enum class SortKind { RECENT, TITLE, ADDED }

/** 阅读模式：翻页 / 上下滚动 */
@Serializable
enum class ReadMode { PAGE, SCROLL }

/** 点击翻页区域：左右 / 上下 / 关闭 */
@Serializable
enum class ClickZone { LEFT_RIGHT, TOP_BOTTOM, OFF }

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
    val tapZone: ClickZone = ClickZone.LEFT_RIGHT,
    val readMode: ReadMode = ReadMode.PAGE,
    val keepScreenOn: Boolean = true,
    val brightnessFollowSystem: Boolean = true,
    /** 0.02..1.0，仅作用于本应用窗口 */
    val brightness: Float = 0.5f,
    val secureMode: Boolean = false,
    val sortBy: SortKind = SortKind.RECENT,
    val bookshelfGrid: Boolean = true,
    /** TTS 朗读语速 0.5..2.0 */
    val ttsRate: Float = 1.0f,
    /** 自动滚动速度 dp/s */
    val autoScrollSpeed: Int = 140,
    /** 每日阅读目标（分钟） */
    val dailyGoalMin: Int = 30,
    /** 自定义更新源 URL；空 = 使用内置默认源 */
    val updateFeedUrl: String = ""
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
        val tapZone = stringPreferencesKey("tap_zone")
        val readMode = stringPreferencesKey("read_mode")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val brightnessFollowSystem = booleanPreferencesKey("brightness_follow_system")
        val brightness = floatPreferencesKey("brightness")
        val secureMode = booleanPreferencesKey("secure_mode")
        val sortBy = stringPreferencesKey("sort_by")
        val bookshelfGrid = booleanPreferencesKey("bookshelf_grid")
        val ttsRate = floatPreferencesKey("tts_rate")
        val autoScrollSpeed = intPreferencesKey("auto_scroll_speed")
        val dailyGoalMin = intPreferencesKey("daily_goal_min")
        val updateFeedUrl = stringPreferencesKey("update_feed_url")
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
            tapZone = enumOrDefault(p[Keys.tapZone], ClickZone.LEFT_RIGHT),
            readMode = enumOrDefault(p[Keys.readMode], ReadMode.PAGE),
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
            brightnessFollowSystem = p[Keys.brightnessFollowSystem] ?: true,
            brightness = p[Keys.brightness] ?: 0.5f,
            secureMode = p[Keys.secureMode] ?: false,
            sortBy = enumOrDefault(p[Keys.sortBy], SortKind.RECENT),
            bookshelfGrid = p[Keys.bookshelfGrid] ?: true,
            ttsRate = p[Keys.ttsRate] ?: 1.0f,
            autoScrollSpeed = p[Keys.autoScrollSpeed] ?: 140,
            dailyGoalMin = p[Keys.dailyGoalMin] ?: 30,
            updateFeedUrl = p[Keys.updateFeedUrl] ?: ""
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
            p[Keys.tapZone] = new.tapZone.name
            p[Keys.readMode] = new.readMode.name
            p[Keys.keepScreenOn] = new.keepScreenOn
            p[Keys.brightnessFollowSystem] = new.brightnessFollowSystem
            p[Keys.brightness] = new.brightness
            p[Keys.secureMode] = new.secureMode
            p[Keys.sortBy] = new.sortBy.name
            p[Keys.bookshelfGrid] = new.bookshelfGrid
            p[Keys.ttsRate] = new.ttsRate
            p[Keys.autoScrollSpeed] = new.autoScrollSpeed
            p[Keys.dailyGoalMin] = new.dailyGoalMin
            p[Keys.updateFeedUrl] = new.updateFeedUrl
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
        tapZone = enumOrDefault(p[Keys.tapZone], ClickZone.LEFT_RIGHT),
        readMode = enumOrDefault(p[Keys.readMode], ReadMode.PAGE),
        keepScreenOn = p[Keys.keepScreenOn] ?: true,
        brightnessFollowSystem = p[Keys.brightnessFollowSystem] ?: true,
        brightness = p[Keys.brightness] ?: 0.5f,
        secureMode = p[Keys.secureMode] ?: false,
        sortBy = enumOrDefault(p[Keys.sortBy], SortKind.RECENT),
        bookshelfGrid = p[Keys.bookshelfGrid] ?: true,
        ttsRate = p[Keys.ttsRate] ?: 1.0f,
        autoScrollSpeed = p[Keys.autoScrollSpeed] ?: 140
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
