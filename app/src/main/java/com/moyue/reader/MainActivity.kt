package com.moyue.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.moyue.reader.di.Graph
import com.moyue.reader.ui.AppNav
import com.moyue.reader.ui.theme.MoyueTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    /**
     * 请求码 16 位适配。
     *
     * androidx 的 ActivityResultRegistry 为每个 launcher 随机分配 65536..Int.MAX 的请求码，
     * 并原样传给 Activity.startActivityForResult；而系统只接受低 16 位，否则抛
     * IllegalArgumentException: Can only use lower 16 bits for requestCode（选择文件时闪退）。
     *
     * 做法是在 Activity 这一侧做双向映射：启动时把框架码换成自分配的小整数，
     * 结果回来时再换回框架码，直接交给 androidx 注册表分发（见 onActivityResult）。
     * 注册表本身不可替换（getter 为 final）。
     */
    private val requestCodes = RequestCodeMapper()

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: android.os.Bundle?) {
        super.startActivityForResult(intent, requestCodes.toSmall(requestCode), options)
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        super.startActivityForResult(intent, requestCodes.toSmall(requestCode))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val frameworkCode = requestCodes.toFramework(requestCode)
        if (frameworkCode != null && frameworkCode != requestCode) {
            // 这是我们换过码的 launcher：直接交给 androidx 注册表分发，不能走 super。
            // FragmentActivity.onActivityResult 把「请求码高 16 位非 0」当作 Fragment 的结果，
            // 而 androidx 分配的框架码高 16 位几乎必然非 0 —— 走 super 会被它当作
            // 「未知 Fragment 的结果」记一行 Log.w 后直接 return，静默丢弃。
            activityResultRegistry.dispatchResult(frameworkCode, resultCode, data)
        } else {
            // 未经映射的码（Fragment 结果、三方库直接使用的小整数等）：保持系统原有行为
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 首次启动才处理外部打开/分享；系统回收后重建不重复导入
        if (savedInstanceState == null) handleExternalIntent(intent)
        // 进程重建：恢复请求码映射，使选择器返回的迟到结果仍能路由回原 launcher
        requestCodes.restore(savedInstanceState)

        // ActivityResult 全局一次注册（每进程仅此一次）。
        // 注意：请求码在 androidx 中是随机数（与注册次数无关），16 位问题由 requestCodes 映射解决；
        // 此处一次注册只是为了避免 Compose 组合生命周期带来的无谓注销/重注册。
        Graph.pickFilesLauncher = activityResultRegistry.register(
            "moyue_pick_files",
            androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
        ) { uris -> Graph.onFilesPicked?.invoke(uris) }
        Graph.pickFolderLauncher = activityResultRegistry.register(
            "moyue_pick_folder",
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri -> uri?.let { Graph.onFolderPicked?.invoke(it) } }
        Graph.exportBackupLauncher = activityResultRegistry.register(
            "moyue_export_backup",
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> uri?.let { Graph.onBackupExport?.invoke(it) } }
        Graph.importBackupLauncher = activityResultRegistry.register(
            "moyue_import_backup",
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let { Graph.onBackupImport?.invoke(it) } }

        // 隐私模式：FLAG_SECURE 禁止截屏与最近任务预览，随设置实时生效
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Graph.settingsRepository.prefs.collect { prefs ->
                    if (prefs.secureMode) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            MoyueTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        requestCodes.save(outState)
    }

    /** 系统「打开方式 / 分享到墨阅」：记录 Uri，书架页到达后导入普通书架 */
    private fun handleExternalIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { Graph.pendingImportUri = it }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )?.let { Graph.pendingImportUri = it }
        }
    }

    /** 音量键翻页：由阅读页注册处理器；过滤按住不放产生的按键重复，避免连跳多页 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Graph.volumeKeyHandler?.let { handler ->
                if (handler(event.keyCode)) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
