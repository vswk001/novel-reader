package com.moyue.reader.data.update

import com.moyue.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查更新：GET 更新源 JSON（兼容 GitHub Releases /latest 格式）。
 * 更新源地址不写死在代码里：由构建脚本注入 BuildConfig.UPDATE_FEED_URL
 * （发布时 -PupdateFeedUrl=... 或环境变量），联网仅此一处用途。
 */
object UpdateChecker {

    data class ReleaseInfo(
        val tag: String,
        val name: String?,
        /** 更新日志（Markdown 原文） */
        val notes: String?,
        /** 发布页地址 */
        val htmlUrl: String?,
        /** APK 直链（assets 中第一个 .apk） */
        val apkUrl: String?,
        val apkSizeBytes: Long?
    )

    suspend fun fetch(feedUrl: String = BuildConfig.UPDATE_FEED_URL): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(feedUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            // GitHub API 要求 User-Agent
            conn.setRequestProperty("User-Agent", "MoYue-Reader")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val code = conn.responseCode
                when {
                    code == 404 -> error("HTTP 404: feed unreachable (private repo or wrong URL)")
                    code != 200 -> error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** 解析 GitHub Releases latest 兼容 JSON；其它源需提供相同字段 */
    fun parse(body: String): ReleaseInfo {
        val obj = Json.parseToJsonElement(body).jsonObject
        fun str(k: String) = obj[k]?.jsonPrimitive?.content
        val asset = obj["assets"]
            ?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.firstOrNull { a ->
                runCatching { a["name"]?.jsonPrimitive?.content }.getOrNull()
                    ?.endsWith(".apk", ignoreCase = true) == true
            }
        return ReleaseInfo(
            tag = str("tag_name") ?: error("tag_name missing"),
            name = str("name"),
            notes = str("body"),
            htmlUrl = str("html_url"),
            apkUrl = asset?.let { runCatching { it["browser_download_url"]?.jsonPrimitive?.content }.getOrNull() },
            apkSizeBytes = asset?.let { a ->
                runCatching { a["size"]?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull()
            }
        )
    }

    /** 比较 v1.9.4 风格版本号：remote 是否比 current 新 */
    fun isNewer(remote: String, current: String): Boolean {
        val r = nums(remote)
        val c = nums(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun nums(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { it.toIntOrNull() ?: 0 }
}
