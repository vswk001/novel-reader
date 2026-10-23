package com.moyue.reader

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.moyue.reader.di.Graph
import com.moyue.reader.ui.AppNav
import com.moyue.reader.ui.theme.MoyueTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
