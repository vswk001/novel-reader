package com.moyue.reader

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.moyue.reader.di.Graph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回归：选择文件/扫描文件夹闪退（"Can only use lower 16 bits for requestCode"）。
 *
 * androidx ActivityResultRegistry 为每个 launcher 随机分配 65536..Int.MAX 的请求码，
 * 而 Activity.startActivityForResult 只接受低 16 位——必须由我们自己的注册表换成小整数。
 * 断言的是"真正传给系统的那一个 requestCode"，而不是中间层。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityResultRequestCodeTest {

    @Before
    fun setUp() {
        Graph.resetForTest()
    }

    @After
    fun tearDown() {
        Graph.resetForTest()
    }

    @Test
    fun filePicker_requestCodeFitsIn16Bits() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        Graph.pickFilesLauncher!!.launch(arrayOf("text/*"))

        val started = shadowOf(activity).nextStartedActivityForResult
        val requestCode = started?.requestCode ?: -1
        println("REQCODE filePicker requestCode=$requestCode")

        assertTrue(
            "requestCode=$requestCode 超出 16 位，系统将抛 IllegalArgumentException",
            requestCode in 0..0xFFFF
        )
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started?.intent?.action)
    }

    @Test
    fun folderPicker_requestCodeFitsIn16Bits() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        Graph.pickFolderLauncher!!.launch(null)

        val started = shadowOf(activity).nextStartedActivityForResult
        val requestCode = started?.requestCode ?: -1
        println("REQCODE folderPicker requestCode=$requestCode")

        assertTrue("requestCode=$requestCode 超出 16 位", requestCode in 0..0xFFFF)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, started?.intent?.action)
    }

    @Test
    fun allFourLaunchers_getDistinctSmallCodes() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val shadow = shadowOf(activity)

        Graph.pickFilesLauncher!!.launch(arrayOf("text/*"))
        Graph.pickFolderLauncher!!.launch(null)
        Graph.exportBackupLauncher!!.launch("backup.json")
        Graph.importBackupLauncher!!.launch(arrayOf("application/json"))

        val codes = mutableListOf<Int>()
        repeat(4) {
            val started = shadow.nextStartedActivityForResult
            assertTrue("第 ${codes.size + 1} 次启动未发生", started != null)
            codes += started!!.requestCode
        }
        println("REQCODE all=$codes")

        // 必须全部落在 16 位内，且互不相同（否则结果无法路由回正确的 launcher）
        assertTrue("请求码超出 16 位: $codes", codes.all { it in 0..0xFFFF })
        assertEquals("请求码重复: $codes", 4, codes.distinct().size)
    }

    @Test
    fun smallCode_comesBackAndIsMappedToTheOriginalFrameworkCode() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val shadow = shadowOf(activity)

        // androidx 为该 launcher 分配的框架码（随机、大于 65535）
        val registry = activity.activityResultRegistry
        val keyToRc = registry.javaClass.superclass.declaredFields.first { it.name == "keyToRc" }
        keyToRc.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val frameworkCode = (keyToRc.get(registry) as Map<String, Int>)["moyue_pick_files"]!!
        assertTrue("框架码应当大于 65535（否则本测试无意义）", frameworkCode > 0xFFFF)

        Graph.pickFilesLauncher!!.launch(arrayOf("text/*"))
        val sentCode = shadow.nextStartedActivityForResult!!.requestCode

        // 系统看到的是小整数；经 Activity 映射后必须还原成 androidx 认识的框架码
        assertTrue(sentCode in 0..0xFFFF)
        val mappedBack = RequestCodeMapper().also {
            // 用与 Activity 相同的映射规则重建一次，验证双向一致性
            it.toSmall(frameworkCode)
        }.toFramework(sentCode)
        assertEquals(frameworkCode, mappedBack)
    }

    @Suppress("UNUSED_VARIABLE")
    private fun keepReference() {
        // 确保测试类链接到 contract 类型（防止 R8/混淆环境下断言类缺失）
        val c = ActivityResultContracts.OpenMultipleDocuments()
    }
}
