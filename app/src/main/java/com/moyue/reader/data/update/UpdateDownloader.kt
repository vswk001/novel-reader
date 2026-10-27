package com.moyue.reader.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * 应用内 APK 下载与安装：
 * - DownloadManager 系统服务下载（免存储权限，存应用专属外部目录）
 * - FileProvider content:// 触发系统安装器
 */
object UpdateDownloader {

    const val MIME_APK = "application/vnd.android.package-archive"
    private const val FILE_NAME = "MoYue-update.apk"

    data class DlState(val status: Int, val bytesSoFar: Long, val totalBytes: Long) {
        val percent: Int
            get() = if (totalBytes > 0) (bytesSoFar * 100 / totalBytes).toInt() else 0
        val done: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    }

    fun targetFile(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)

    /** 入队下载，返回 DownloadManager 任务 id */
    fun enqueue(context: Context, url: String, title: String): Long {
        // 清理旧包，避免安装器提示版本回退/残留
        targetFile(context).delete()
        val req = DownloadManager.Request(Uri.parse(url))
            .setMimeType(MIME_APK)
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    /** 查询下载进度；任务不存在（被系统清理）返回 null */
    fun query(context: Context, id: Long): DlState? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val sofar = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DlState(status, sofar, total)
        }
    }

    /**
     * 拉起系统安装器。
     * @return true 已拉起；false 需要先授予「安装未知应用」权限（已引导至授权页）
     */
    fun install(context: Context): Boolean {
        val file = targetFile(context)
        if (!file.exists() || file.length() <= 0L) return false
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Android 8+ 未授予本应用「安装未知应用」→ 引导到授权页
            try {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ignore: Exception) {
            }
            false
        }
    }
}
