package com.moyue.reader.settings

import android.content.Context
import com.moyue.reader.R
import com.moyue.reader.data.update.UpdateDownloader

/** 安装引导：含「安装未知应用」授权缺失时的提示回调 */
object UpdateInstallerHelper {
    /**
     * @return 成功拉起安装器返回 null；需要授权时返回提示文案
     */
    fun install(context: Context): String? {
        val ok = UpdateDownloader.install(context)
        return if (ok) null else context.getString(R.string.update_need_install_permission)
    }
}
