package com.moyue.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.moyue.reader.di.Graph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回归：选择文件后"没有导入、也没有报错"。
 *
 * 忠实复现真机时序：点「选择文件」→ 系统选择器全屏覆盖，我们的 Activity 进入 STOP
 * → 用户选完返回，结果回传 → Activity 回到 START/RESUME。
 * 必须在这整条时序走完后，导入回调才被触发。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickerResultDeliveryTest {

    private lateinit var controller: ActivityController<MainActivity>

    @Before
    fun setUp() {
        Graph.resetForTest()
        Graph.onFilesPicked = null
        Graph.onFolderPicked = null
    }

    @After
    fun tearDown() {
        runCatching { controller.close() }
        Graph.resetForTest()
    }

    private fun filesResultIntent(vararg uris: Uri): Intent = Intent().apply {
        data = uris.firstOrNull()
        if (uris.size > 1) {
            clipData = android.content.ClipData.newRawUri("docs", uris[0]).apply {
                uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
            }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun resultDelivered_whenActivityWasStoppedWhilePickerWasOpen() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val picked = mutableListOf<List<Uri>>()
        Graph.onFilesPicked = { picked += it }

        // 1) 点击「选择文件」
        Graph.pickFilesLauncher!!.launch(arrayOf("text/*"))
        val started = shadowOf(activity).nextStartedActivityForResult!!
        assertTrue(started.requestCode in 0..0xFFFF)

        // 2) 系统选择器覆盖 → 我们的 Activity 进入 STOP
        controller.pause().stop()

        // 3) 用户选完文件，系统回传结果（真实设备上此刻 Activity 仍是 stopped）
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABooks%2Fnovel.txt")
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, filesResultIntent(uri))

        // 4) 返回应用：START → RESUME
        controller.start().resume()
        assertEquals(
            "选择器关闭、应用回到前台后，选中的文件必须送达导入回调",
            listOf(listOf(uri)), picked
        )
    }

    @Test
    fun resultDelivered_whenResultArrivesAfterRestart() {
        // 另一种设备时序：先 onStart，随后才 onActivityResult
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val picked = mutableListOf<List<Uri>>()
        Graph.onFilesPicked = { picked += it }

        Graph.pickFilesLauncher!!.launch(arrayOf("text/*"))
        val started = shadowOf(activity).nextStartedActivityForResult!!
        controller.pause().stop()

        controller.start()
        val uri = Uri.parse("content://probe/doc/9")
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, filesResultIntent(uri))
        controller.resume()

        assertEquals(listOf(listOf(uri)), picked)
    }

    @Test
    fun folderResultDelivered_whenActivityWasStoppedWhilePickerWasOpen() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val folder = mutableListOf<Uri>()
        Graph.onFolderPicked = { folder += it }

        Graph.pickFolderLauncher!!.launch(null)
        val started = shadowOf(activity).nextStartedActivityForResult!!
        controller.pause().stop()

        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABooks")
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, filesResultIntent(tree))
        controller.start().resume()

        assertEquals(listOf(tree), folder)
    }

    @Test
    fun backupLaunchers_deliverResultsToo() {
        // 备份导出 / 导入与导入图书走同一条请求码映射路径，须一并覆盖
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val shadow = shadowOf(activity)

        val exported = mutableListOf<Uri>()
        val imported = mutableListOf<Uri>()
        Graph.onBackupExport = { exported += it }
        Graph.onBackupImport = { imported += it }

        Graph.exportBackupLauncher!!.launch("moyue-backup.json")
        val exportReq = shadow.nextStartedActivityForResult!!
        Graph.importBackupLauncher!!.launch(arrayOf("application/json"))
        val importReq = shadow.nextStartedActivityForResult!!
        assertTrue(exportReq.requestCode in 0..0xFFFF)
        assertTrue(importReq.requestCode in 0..0xFFFF)
        assertNotEquals("两个 launcher 不能共用同一个请求码", exportReq.requestCode, importReq.requestCode)

        val out = Uri.parse("content://probe/downloads/moyue-backup.json")
        val inn = Uri.parse("content://probe/downloads/old-backup.json")
        shadow.receiveResult(exportReq.intent, Activity.RESULT_OK, filesResultIntent(out))
        shadow.receiveResult(importReq.intent, Activity.RESULT_OK, filesResultIntent(inn))

        assertEquals(listOf(out), exported)
        assertEquals(listOf(inn), imported)
    }
}
