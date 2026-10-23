package com.moyue.reader.data.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset

/**
 * TXT 编码检测：BOM 优先，其次严格 UTF-8 校验，GB18030 兜底。
 * 覆盖中文 TXT 的绝大多数场景（UTF-8 / GBK / GB18030 / UTF-16）。
 */
object CharsetDetector {

    const val UTF_8 = "UTF-8"
    const val UTF_16LE = "UTF-16LE"
    const val UTF_16BE = "UTF-16BE"
    const val GB18030 = "GB18030"

    /** BOM 字节数（0 表示无 BOM） */
    fun bomLength(bytes: ByteArray): Int = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> 2 // UTF-16LE
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> 2 // UTF-16BE
        else -> 0
    }

    /**
     * 返回用于解码的字符集名称。
     * @param sampleSize 参与探测的字节数（默认取前 1MB，大文件避免整段扫描）
     */
    fun detect(bytes: ByteArray, sampleSize: Int = 1 shl 20): String {
        val bom = bomLength(bytes)
        if (bom == 3) return UTF_8
        if (bom == 2) {
            return if (bytes[0] == 0xFF.toByte()) UTF_16LE else UTF_16BE
        }
        val n = minOf(bytes.size, sampleSize)
        if (n == 0) return UTF_8
        if (isStrictUtf8(bytes, n)) return UTF_8
        // GBK 双字节序列几乎不可能是合法 UTF-8，因此 UTF-8 校验失败基本就是 GB 系
        return if (isStrictCharset(bytes, n, GB18030)) GB18030 else UTF_8
    }

    /** 严格 UTF-8 校验（含过长编码/代理区/越界检查），采样尾部的截断序列容忍 */
    fun isStrictUtf8(b: ByteArray, n: Int): Boolean {
        var i = 0
        while (i < n) {
            val c = b[i].toInt() and 0xFF
            val len = when {
                c < 0x80 -> 1
                c in 0xC2..0xDF -> 2
                c in 0xE0..0xEF -> 3
                c in 0xF0..0xF4 -> 4
                else -> return false
            }
            if (i + len > n) return true // 截断在采样边界，视为合法
            for (k in 1 until len) {
                if (b[i + k].toInt() and 0xC0 != 0x80) return false
            }
            // 防过长与代理区/越界
            when {
                len == 3 && c == 0xE0 && (b[i + 1].toInt() and 0xFF) < 0xA0 -> return false
                len == 3 && c == 0xED && (b[i + 1].toInt() and 0xFF) > 0x9F -> return false
                len == 4 && c == 0xF0 && (b[i + 1].toInt() and 0xFF) < 0x90 -> return false
                len == 4 && c == 0xF4 && (b[i + 1].toInt() and 0xFF) > 0x8F -> return false
            }
            i += len
        }
        return true
    }

    private fun isStrictCharset(b: ByteArray, n: Int, name: String): Boolean = try {
        Charset.forName(name).newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b, 0, n))
        true
    } catch (e: CharacterCodingException) {
        false
    }
}
