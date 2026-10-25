package com.moyue.reader.data.importer

import androidx.annotation.StringRes
import com.moyue.reader.R
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 基础 EPUB 解析器：ZIP → container.xml → OPF(manifest/spine) → NCX/nav 目录 → XHTML 正文提取。
 * 转成「章节标题 + 纯文本」后走与 TXT 相同的入库管线（不依赖 XML 库，OPF/NCX 均为结构规整的生成文件）。
 */
/** 携带字符串资源 ID 的解析失败：由调用方用 Context 解析为用户语言 */
class EpubParseException(@StringRes val resId: Int, val detail: String? = null) : RuntimeException()

object EpubParser {

    data class EpubChapter(val title: String, val text: String, val imageHrefs: List<String> = emptyList())
    data class EpubBook(
        val title: String,
        val author: String?,
        val chapters: List<EpubChapter>,
        /** zip 内 href → 图片字节（导入器落盘用） */
        val images: Map<String, ByteArray> = emptyMap()
    )

    /** 跳过的大体积资源，不进入内存（图片保留以支持内联显示） */
    private val SKIP_EXT = setOf(
        "ttf", "otf", "woff", "woff2", "mp3", "mp4", "ogg", "wav", "css", "js"
    )

    private val IMG_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

    private const val MAX_ENTRY_BYTES = 32_000_000

    /** 图片总量上限（字节），超出后跳过后续图片 */
    private const val MAX_IMAGE_TOTAL = 96L * 1024 * 1024

    /** 图片占位标记字符（渲染端由 ImageSpan 覆盖） */
    const val MARKER = '￼'

    /**
     * 章节文本内全部图片标记 → 全书拼接文本偏移。
     * 全书偏移 = 章起点 + 标题长度 + 换行 + normalized 内标记位置。
     * （标题长度必须计入：v1.9.2 之前漏掉导致 EPUB 图片整体错位、渲染校验失败不显示）
     */
    fun imageCharOffsets(chapterStart: Int, title: String, normalizedText: String): IntArray {
        val base = chapterStart + title.length + 1
        val out = mutableListOf<Int>()
        var from = 0
        while (true) {
            val p = normalizedText.indexOf(MARKER, from)
            if (p < 0) break
            out += base + p
            from = p + 1
        }
        return out.toIntArray()
    }

    fun parse(bytes: ByteArray): EpubBook {
        val entries = readZip(bytes)
        val container = entries["meta-inf/container.xml"]
            ?: throw EpubParseException(R.string.err_epub_container)
        val opfPath = Regex("full-path=\"([^\"]+)\"")
            .find(container.decodeToString())?.groupValues?.get(1)
            ?: throw EpubParseException(R.string.err_epub_container_parse)
        val opfBytes = entries[opfPath.lowercase()]
            ?: throw EpubParseException(R.string.err_epub_opf, opfPath)
        val opf = opfBytes.decodeToString()
        val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val bookTitle = Regex("(?is)<dc:title[^>]*>\\s*(.*?)\\s*</dc:title>")
            .find(opf)?.groupValues?.get(1).orEmpty()
        val author = Regex("(?is)<dc:creator[^>]*>\\s*(.*?)\\s*</dc:creator>")
            .find(opf)?.groupValues?.get(1)?.trim()

        // ---- manifest：id → 解析后的路径(lower) ----
        val items = mutableMapOf<String, String>()
        val mediaTypes = mutableMapOf<String, String>()
        Regex("(?is)<item\\s[^>]*/?>").findAll(opf).forEach { m ->
            val tag = m.value
            val id = attr(tag, "id") ?: return@forEach
            val href = attr(tag, "href") ?: return@forEach
            items[id] = resolve(opfDir, href)
            mediaTypes[id] = (attr(tag, "media-type") ?: "").lowercase()
        }

        // ---- spine：阅读顺序 ----
        val docHrefs = Regex("(?is)<itemref\\s[^>]*/?>").findAll(opf)
            .mapNotNull { attr(it.value, "idref") }
            .mapNotNull { id ->
                if ((mediaTypes[id] ?: "").contains("html")) items[id] else null
            }
            .distinct()
            .toList()

        // ---- 目录标题（EPUB3 nav 优先，EPUB2 ncx 兜底） ----
        val tocTitles = mutableMapOf<String, String>()
        val navHref = Regex("(?is)<item\\s[^>]*properties=\"[^\"]*nav[^\"]*\"[^>]*/?>")
            .findAll(opf).mapNotNull { attr(it.value, "id") }
            .mapNotNull { items[it] }.firstOrNull()
        if (navHref != null) {
            entries[navHref]?.decodeToString()?.let { nav ->
                val navDir = navHref.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                Regex("(?is)<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").findAll(nav).forEach { m ->
                    val t = stripTags(m.groupValues[2]).trim()
                    if (t.isNotEmpty()) tocTitles[resolve(navDir, m.groupValues[1])] = t
                }
            }
        }
        if (tocTitles.isEmpty()) {
            val ncxHref = items.entries
                .firstOrNull { mediaTypes[it.key] == "application/x-dtbncx+xml" }?.value
            if (ncxHref != null) {
                entries[ncxHref]?.decodeToString()?.let { ncx ->
                    val ncxDir = ncxHref.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                    Regex("(?is)<navPoint.*?</navPoint>").findAll(ncx).forEach { np ->
                        val src = Regex("(?is)<content[^>]*src=\"([^\"]+)\"")
                            .find(np.value)?.groupValues?.get(1)
                        val t = Regex("(?is)<text[^>]*>(.*?)</text>")
                            .find(np.value)?.groupValues?.get(1)?.trim()
                        if (src != null && !t.isNullOrBlank()) {
                            tocTitles[resolve(ncxDir, src)] = t
                        }
                    }
                }
            }
        }

        // ---- 正文 ----
        var chapters = docHrefs.mapIndexedNotNull { i, href ->
            val html = entries[href]?.decodeToString() ?: return@mapIndexedNotNull null
            val rawTitle = Regex("(?is)<title[^>]*>\\s*(.*?)\\s*</title>")
                .find(html)?.groupValues?.get(1).orEmpty()
            val dir = href.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            val xt = xhtmlToTextFull(html, dir)
            if (xt.text.isBlank()) null else EpubChapter(
                title = tocTitles[href] ?: rawTitle.ifBlank { "第${i + 1}节" },
                text = xt.text,
                imageHrefs = xt.imageHrefs
            )
        }
        if (chapters.isEmpty()) throw EpubParseException(R.string.err_epub_no_content)

        // 引用到的图片：受总量上限保护
        val usedHrefs = chapters.flatMap { it.imageHrefs }.toSet()
        val images = mutableMapOf<String, ByteArray>()
        var imgTotal = 0L
        for (h in usedHrefs) {
            val data = entries[h] ?: continue
            if (imgTotal + data.size > MAX_IMAGE_TOTAL) break
            images[h] = data
            imgTotal += data.size
        }

        // 单文件整本（部分制作粗糙的 EPUB）：按 TXT 章节规则二次切分（标记随文本切分）
        if (chapters.size == 1 && chapters[0].text.length > 100_000) {
            val big = chapters[0]
            val parts = ChapterParser.parse(big.text)
            val hrefQueue = ArrayDeque(big.imageHrefs)
            chapters = parts.map { p ->
                val seg = big.text.substring(
                    p.charStart.coerceIn(0, big.text.length),
                    p.charEnd.coerceIn(0, big.text.length)
                )
                val marks = seg.count { it == MARKER }
                val segHrefs = mutableListOf<String>()
                repeat(marks) { hrefQueue.removeFirstOrNull()?.let { segHrefs.add(it) } }
                EpubChapter(p.title, seg, segHrefs)
            }
        }
        return EpubBook(bookTitle.trim(), author?.takeIf { it.isNotBlank() }, chapters, images)
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.removePrefix("./")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in SKIP_EXT) {
                        val data = zis.readBytes()
                        if (data.size <= MAX_ENTRY_BYTES) out[name.lowercase()] = data
                    }
                }
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun attr(tag: String, name: String): String? =
        Regex("(?i)$name\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    /** href 相对路径 → zip 内绝对路径（去锚点、小写） */
    private fun resolve(baseDir: String, href: String): String {
        val h = href.substringBefore('#')
        return if (h.startsWith("/")) h.removePrefix("/").lowercase()
        else (baseDir + h).lowercase()
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), "")

    /** XHTML 转文本结果：图片以标记字符占位，href 依出现顺序返回 */
    data class XhtmlText(val text: String, val imageHrefs: List<String>)

    /** XHTML → 纯文本：剔除 head/script/style、块级标签转行、去标签、解码实体 */
    fun xhtmlToText(html: String): String = xhtmlToTextFull(html, "").text

    fun xhtmlToTextFull(html: String, baseDir: String): XhtmlText {
        var s = html
        s = Regex("(?is)<head[^>]*>.*?</head>").replace(s, "")
        s = Regex("(?is)<(script|style)[^>]*>.*?</\\1>").replace(s, "")
        // 图片（<img src> / SVG <image xlink:href>）→ 独立行占位标记，收集 href
        val hrefs = mutableListOf<String>()
        s = Regex("(?is)<img\\s[^>]*/?>").replace(s) { m ->
            val src = attr(m.value, "src") ?: return@replace ""
            hrefs += resolve(baseDir, src)
            "\n$MARKER\n"
        }
        s = Regex("(?is)<image\\s[^>]*/?>").replace(s) { m ->
            val src = attr(m.value, "xlink:href") ?: attr(m.value, "href") ?: return@replace ""
            hrefs += resolve(baseDir, src)
            "\n$MARKER\n"
        }
        s = Regex("(?i)<br\\s*/?>").replace(s, "\n")
        s = Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|pre)>").replace(s, "\n")
        s = Regex("(?i)<(p|div|h[1-6]|li|blockquote|pre)(\\s[^>]*)?>").replace(s, "\n")
        s = stripTags(s)
        s = decodeEntities(s)
        return XhtmlText(s, hrefs)
    }

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">",
        "quot" to "\"", "apos" to "'", "ldquo" to "“", "rdquo" to "”",
        "lsquo" to "‘", "rsquo" to "’", "mdash" to "—", "ndash" to "–",
        "hellip" to "…", "middot" to "·", "bull" to "•", "copy" to "©"
    )

    fun decodeEntities(s: String): String {
        var r = s
        for ((k, v) in NAMED_ENTITIES) {
            r = r.replace("&$k;", v)
        }
        r = Regex("&#(\\d+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        r = Regex("(?i)&#x([0-9a-f]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return r
    }
}
