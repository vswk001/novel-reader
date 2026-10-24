package com.moyue.reader.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ImageSpan
import android.text.style.UnderlineSpan
import com.moyue.reader.data.db.BookImageEntity
import com.moyue.reader.data.db.HighlightEntity
import java.io.File

/**
 * 分页与渲染共用的 Span 构建：高亮背景、选区背景、内联图片（\uFFFC 标记）。
 * 分页引擎与 StaticPage 必须用同一函数、同一参数构建文本，
 * 保证含图片行的行高在测量与渲染两侧完全一致（所排即所见）。
 */
object PageSpans {

    /** EPUB 内联图片占位字符（ImageSpan 覆盖其显示） */
    const val IMG_CHAR = '￼'

    /** 高亮调色板（ARGB，渲染时降低透明度） */
    val HIGHLIGHT_COLORS = intArrayOf(
        0xFFFBC02D.toInt(), // 黄
        0xFF81C784.toInt(), // 绿
        0xFF64B5F6.toInt(), // 蓝
        0xFFF06292.toInt()  // 粉
    )

    /** 选区临时背景 */
    private const val SELECTION_COLOR = 0x44337AB7

    fun highlightColor(index: Int, alpha: Float = 0.38f): Int {
        val c = HIGHLIGHT_COLORS[index.coerceIn(0, HIGHLIGHT_COLORS.size - 1)]
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (c and 0x00FFFFFF)
    }

    /**
     * @param text       待排版文本（页面切片或整章）
     * @param base       text[0] 对应的章节内偏移（页面切片时为 page.start，整章为 0）
     * @param highlights 章节内绝对区间的高亮列表
     * @param selection  章节内绝对选区 [start, end)，null 无
     * @param images     章节内图片列表（charOffset 指向 \uFFFC 字符）
     * @param imgSpan    图片 → 放入 ImageSpan 的 Drawable（bounds 已按页面尺寸算好）
     */
    fun apply(
        text: CharSequence,
        base: Int,
        highlights: List<HighlightEntity>,
        selection: IntRange?,
        images: List<BookImageEntity>,
        imgSpan: (BookImageEntity) -> Drawable?
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        val localStart = 0
        val localEnd = text.length
        // 高亮背景（笔记非空的加下划线标识）
        for (h in highlights) {
            val s = (h.start - base).coerceAtLeast(localStart)
            val e = (h.end - base).coerceAtMost(localEnd)
            if (e > s) {
                val color = highlightColor(h.color)
                sb.setSpan(BackgroundColorSpan(color), s, e, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (h.note.isNotBlank()) {
                    sb.setSpan(UnderlineSpan(), s, e, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        // 选区背景
        if (selection != null) {
            val s = (selection.first - base).coerceAtLeast(localStart)
            val e = (selection.last + 1 - base).coerceAtMost(localEnd)
            if (e > s) {
                sb.setSpan(
                    BackgroundColorSpan(SELECTION_COLOR), s, e,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        // 内联图片：标记字符偏移必须落在切片内
        for (img in images) {
            val pos = img.charOffset - base
            if (pos in localStart until localEnd && text[pos] == IMG_CHAR) {
                imgSpan(img)?.let { d ->
                    sb.setSpan(
                        ImageSpan(d, ImageSpan.ALIGN_BOTTOM), pos, pos + 1,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return sb
    }

    /** 长按位置向两侧扩展出"词"：字母数字连续段；CJK/标点则扩展到句读边界 */
    fun wordAt(text: String, offset: Int): IntRange {
        if (text.isEmpty()) return 0..0
        val off = offset.coerceIn(0, text.length - 1)
        val isWordChar = { c: Char -> Character.isLetterOrDigit(c) }
        var s = off
        var e = off + 1
        while (s > 0 && isWordChar(text[s - 1])) s--
        while (e < text.length && isWordChar(text[e])) e++
        if (e - s < 2) {
            val delims = "，。！？；、,.!?;:…—\n 　“”‘’（）()《》\"'"
            while (s > 0 && text[s - 1] !in delims) s--
            while (e < text.length && text[e] !in delims) e++
        }
        return s..(e - 1)
    }
}

/**
 * 惰性图片 Drawable：构造只算边界（分页测量用，廉价），
 * 首次 draw 才解码位图（按显示尺寸 2x 采样，控制内存）。
 */
class PageImageDrawable(
    private val file: File,
    srcW: Int,
    srcH: Int,
    maxW: Int,
    maxH: Int
) : Drawable() {

    private val drawW: Int
    private val drawH: Int

    init {
        val w = srcW.coerceAtLeast(1)
        val h = srcH.coerceAtLeast(1)
        val scale = minOf(maxW.toFloat() / w, maxH.toFloat() / h, 1f)
        drawW = (w * scale).toInt().coerceAtLeast(1)
        drawH = (h * scale).toInt().coerceAtLeast(1)
        setBounds(0, 0, drawW, drawH)
    }

    private var bitmap: Bitmap? = null

    private fun ensureBitmap(): Bitmap? {
        bitmap?.let { return it }
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample() }
        bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
        return bitmap
    }

    /** 显示尺寸远小于解码尺寸时降采样，控制内存 */
    private fun sample(): Int = when {
        drawW <= 256 -> 4
        drawW <= 640 -> 2
        else -> 1
    }

    override fun draw(canvas: Canvas) {
        val bmp = ensureBitmap() ?: return
        canvas.drawBitmap(
            bmp, null,
            android.graphics.Rect(0, 0, drawW, drawH),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = drawW
    override fun getIntrinsicHeight(): Int = drawH
}
