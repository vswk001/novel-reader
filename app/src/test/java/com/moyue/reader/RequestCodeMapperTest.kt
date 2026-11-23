package com.moyue.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 请求码映射器（纯逻辑）回归。
 * 背景：androidx 为每个 launcher 随机分配 65536..Int.MAX 的请求码，系统只接受低 16 位。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RequestCodeMapperTest {

    @Test
    fun frameworkCodes_mapInto16BitRange() {
        val m = RequestCodeMapper()
        // androidx 的取值范围两端 + 常见值
        listOf(65536, 100_000, 1_000_000, Int.MAX_VALUE).forEach { fw ->
            val small = m.toSmall(fw)
            assert(small in 1..0xFFFF) { "框架码 $fw 映射为 $small，超出 16 位" }
        }
    }

    @Test
    fun sameFrameworkCode_alwaysGetsSameSmallCode() {
        val m = RequestCodeMapper()
        val fw = 123_456_789
        assertEquals(m.toSmall(fw), m.toSmall(fw))
        assertEquals(m.toSmall(fw), m.toSmall(fw))
    }

    @Test
    fun roundTrip_returnsOriginalFrameworkCode() {
        val m = RequestCodeMapper()
        val codes = listOf(70_000, 5_000_000, Int.MAX_VALUE - 1, 65536)
        codes.forEach { fw ->
            val small = m.toSmall(fw)
            assertEquals("回程映射错误", fw, m.toFramework(small))
        }
    }

    @Test
    fun differentLaunchers_neverShareASmallCode() {
        val m = RequestCodeMapper()
        // 远超实际使用量：验证不会两个框架码撞到同一个小整数
        val fwCodes = (0 until 5000).map { 65536 + it * 97 }
        val smalls = fwCodes.map { m.toSmall(it) }
        assertEquals("小整数冲突", fwCodes.size, smalls.distinct().size)
        fwCodes.zip(smalls).forEach { (fw, small) -> assertEquals(fw, m.toFramework(small)) }
    }

    @Test
    fun alreadySmallCode_passesThroughUnchanged() {
        val m = RequestCodeMapper()
        // 三方库可能直接用小整数：不得改动，且回程可识别
        assertEquals(42, m.toSmall(42))
        assertEquals(42, m.toFramework(42))
    }

    @Test
    fun smallCodeDoesNotCollide_withPassthroughCode() {
        val m = RequestCodeMapper()
        assertEquals(65534, m.toSmall(65534))          // 直接透传并占用 65534
        val mapped = m.toSmall(500_000)                 // 自分配时必须跳过已被占用的 65534
        assertNotEquals(65534, mapped)
        assertEquals(500_000, m.toFramework(mapped))
    }

    @Test
    fun unknownSmallCode_returnsNull() {
        val m = RequestCodeMapper()
        assertNull(m.toFramework(7))
        assertNull(m.toFramework(65534))
    }

    @Test
    fun saveRestore_survivesActivityRecreation() {
        // 场景：停在选择器期间进程被回收，Activity 重建后迟到的结果仍须能路由回去
        val before = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        val state = android.os.Bundle()
        val m1 = RequestCodeMapper()
        val smallA = m1.toSmall(111_111_111)
        val smallB = m1.toSmall(222_222_222)
        m1.toSmall(77) // 透传登记的小值
        m1.save(state)

        val m2 = RequestCodeMapper()
        assertNull("恢复前不应有映射", m2.toFramework(smallA))
        m2.restore(state)
        assertEquals(111_111_111, m2.toFramework(smallA))
        assertEquals(222_222_222, m2.toFramework(smallB))
        assertEquals("透传小值须保留", 77, m2.toFramework(77))
        // 恢复后继续分配不得与已恢复的小整数冲突
        val smallC = m2.toSmall(333_333_333)
        assert(smallC != smallA && smallC != smallB && smallC != 77) { "恢复后分配冲突: $smallC" }
        assertEquals(333_333_333, m2.toFramework(smallC))
        // 同一框架码恢复后仍稳定映射到原小整数
        assertEquals(smallA, m2.toSmall(111_111_111))
        before.finish()
    }

    @Test
    fun restore_withNullOrEmptyState_isNoop() {
        val m = RequestCodeMapper()
        m.restore(null)
        m.restore(android.os.Bundle())
        assertNull(m.toFramework(1))
        assertEquals(65534, m.toSmall(65536)) // 仍从 FIRST 开始分配
    }

    @Test
    fun clear_resetsAllMappings() {
        val m = RequestCodeMapper()
        val small = m.toSmall(88_888)
        m.clear()
        assertNull(m.toFramework(small))
    }
}
