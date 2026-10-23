package com.moyue.reader.data.importer

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 基础 EPUB 解析器：ZIP → container.xml → OPF(manifest/spine) → NCX/nav 目录 → XHTML 正文提取。
 * 转成「章节标题 + 纯文本」后走与 TXT 相同的入库管线（不依赖 XML 库，OPF/NCX 均为结构规整的生成文件）。
 */
object EpubParser {

    data class EpubChapter(val title: String, val text: String)
    data class EpubBook(val title: String, val author: String?, val chapters: List<EpubChapter>)

    /** 跳过的大体积资源，不进入内存 */
    private val SKIP_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ttf", "otf",
        "woff", "woff2", "mp3", "mp4", "ogg", "wav", "css", "js"
    )

    private const val MAX_ENTRY_BYTES = 32_000_000

    fun parse(bytes: ByteArray): EpubBook {
        val entries = readZip(bytes)
        val container = entries["meta-inf/container.xml"]
            ?: error("不是有效的 EPUB 文件（缺少 container.xml）")
        val opfPath = Regex("full-path=\"([^\"]+)\"")
            .find(container.decodeToString())?.groupValues?.get(1)
            ?: error("EPUB container.xml 解析失败")
        val opfBytes = entries[opfPath.lowercase()]
            ?: error("EPUB 缺少 OPF 文件：$opfPath")
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
            val text = xhtmlToText(html)
            if (text.isBlank()) null else EpubChapter(
                title = tocTitles[href] ?: rawTitle.ifBlank { "第${i + 1}节" },
                text = text
            )
        }
        if (chapters.isEmpty()) error("EPUB 中没有可读取的正文内容")

        // 单文件整本（部分制作粗糙的 EPUB）：按 TXT 章节规则二次切分
        if (chapters.size == 1 && chapters[0].text.length > 100_000) {
            val big = chapters[0].text
            val parts = ChapterParser.parse(big)
            chapters = parts.map {
                EpubChapter(it.title, big.substring(it.charStart.coerceIn(0, big.length), it.charEnd.coerceIn(0, big.length)))
            }
        }
        return EpubBook(bookTitle.trim(), author?.takeIf { it.isNotBlank() }, chapters)
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

    /** XHTML → 纯文本：剔除 head/script/style、块级标签转行、去标签、解码实体 */
    fun xhtmlToText(html: String): String {
        var s = html
        s = Regex("(?is)<head[^>]*>.*?</head>").replace(s, "")
        s = Regex("(?is)<(script|style)[^>]*>.*?</\\1>").replace(s, "")
        s = Regex("(?i)<br\\s*/?>").replace(s, "\n")
        s = Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|pre)>").replace(s, "\n")
        s = Regex("(?i)<(p|div|h[1-6]|li|blockquote|pre)(\\s[^>]*)?>").replace(s, "\n")
        s = stripTags(s)
        s = decodeEntities(s)
        return s
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
