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
        val apkSizeBytes: Long?,
        /** APK 备用下载镜像（直链失败时依次回退） */
        val apkMirrors: List<String> = emptyList()
    )

    /** 镜像前缀列表（构建期注入，逗号分隔） */
    fun mirrorPrefixes(): List<String> =
        BuildConfig.UPDATE_MIRRORS.split(',')
            .map { it.trim().trimEnd('/') + "/" }
            .filter { it.startsWith("http") }

    /**
     * feed 形如「代理前缀 + https://真实地址」时拆出前缀与真实地址；否则前缀为 null。
     * 兼容 ghproxy 类反代与 Cloudflare Worker 路径式代理。
     */
    fun splitProxyFeed(feed: String): Pair<String?, String> {
        val idx = feed.indexOf("https://", 8)
        return if (idx > 0) feed.substring(0, idx) to feed.substring(idx) else null to feed
    }

    /** API 候选链：原始 feed 优先，其后依次套镜像前缀（feed 自带代理前缀则不再套） */
    fun apiCandidates(feed: String, prefixes: List<String> = mirrorPrefixes()): List<String> {
        val (prefix, real) = splitProxyFeed(feed)
        return if (prefix != null) listOf(feed)
        else listOf(feed) + prefixes.map { it + real }
    }

    /** APK 下载候选链：与 feed 相同的代理/镜像策略（重定向直链同样被反代覆盖） */
    fun downloadCandidates(
        feed: String,
        apkUrl: String,
        prefixes: List<String> = mirrorPrefixes()
    ): List<String> {
        val (prefix, _) = splitProxyFeed(feed)
        return if (prefix != null) listOf(prefix + apkUrl)
        else listOf(apkUrl) + prefixes.map { it + apkUrl }
    }

    /**
     * 检查更新（带镜像回退）：依次尝试 API 候选链，
     * 成功后为 APK 直链生成同策略的下载候选链（见 UpdateDownloader 的失败续传逻辑）。
     */
    suspend fun fetchWithMirrors(feed: String = BuildConfig.UPDATE_FEED_URL): Result<ReleaseInfo> {
        var last: Result<ReleaseInfo>? = null
        for (candidate in apiCandidates(feed)) {
            val r = fetch(candidate)
            if (r.isSuccess) {
                return r.map { info ->
                    val urls = info.apkUrl?.let { downloadCandidates(feed, it) }.orEmpty()
                    info.copy(apkUrl = urls.firstOrNull(), apkMirrors = urls.drop(1))
                }
            }
            last = r
        }
        return last ?: Result.failure(IllegalStateException("no update candidates"))
    }

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
