package com.moyue.reader

import com.moyue.reader.data.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val githubJson = """
        {
          "tag_name": "v1.9.6",
          "name": "墨阅 v1.9.6",
          "body": "## 修复\n若干修复",
          "html_url": "https://github.com/vswk001/novel-reader/releases/tag/v1.9.6",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://x/n.txt", "size": 10},
            {"name": "MoYue-v1.9.6-release.apk", "browser_download_url": "https://x/a.apk", "size": 2797309}
          ]
        }
    """.trimIndent()

    @Test
    fun `parse github release json`() {
        val info = UpdateChecker.parse(githubJson)
        assertEquals("v1.9.6", info.tag)
        assertEquals("墨阅 v1.9.6", info.name)
        assertEquals("https://x/a.apk", info.apkUrl)
        assertEquals(2797309L, info.apkSizeBytes)
        assertTrue(info.notes!!.contains("修复"))
    }

    @Test
    fun `parse release without assets`() {
        val info = UpdateChecker.parse("""{"tag_name":"v2.0.0","html_url":"u"}""")
        assertEquals("v2.0.0", info.tag)
        assertNull(info.apkUrl)
        assertNull(info.notes)
    }

    @Test(expected = Exception::class)
    fun `parse missing tag throws`() {
        UpdateChecker.parse("""{"name":"no tag"}""")
    }

    @Test
    fun `version comparison`() {
        assertTrue(UpdateChecker.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.9.10", "1.9.9"))  // 数字段比较，非字符串
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"))   // 段数补齐
        assertFalse(UpdateChecker.isNewer("v1.9.6", "1.9.6"))
        assertFalse(UpdateChecker.isNewer("1.9.5", "v1.9.6"))
    }

    // ---- 镜像回退链 ----

    private val M = listOf("https://ghproxy.net/", "https://ghfast.top/")

    @Test
    fun `direct feed gets mirror api candidates`() {
        val feed = "https://api.github.com/repos/o/r/releases/latest"
        org.junit.Assert.assertEquals(
            listOf(feed) + M.map { it + feed },
            UpdateChecker.apiCandidates(feed, M)
        )
    }

    @Test
    fun `proxied feed is not double wrapped`() {
        val feed = "https://my.worker.dev/https://api.github.com/repos/o/r/releases/latest"
        org.junit.Assert.assertEquals(listOf(feed), UpdateChecker.apiCandidates(feed, M))
        org.junit.Assert.assertEquals("https://my.worker.dev/", UpdateChecker.splitProxyFeed(feed).first)
    }

    @Test
    fun `download candidates follow feed proxy strategy`() {
        val apk = "https://github.com/o/r/releases/download/v1/a.apk"
        org.junit.Assert.assertEquals(
            listOf(apk) + M.map { it + apk },
            UpdateChecker.downloadCandidates("https://api.github.com/x", apk, M)
        )
        org.junit.Assert.assertEquals(
            listOf("https://my.worker.dev/" + apk),
            UpdateChecker.downloadCandidates("https://my.worker.dev/https://api.github.com/x", apk, M)
        )
    }
}
