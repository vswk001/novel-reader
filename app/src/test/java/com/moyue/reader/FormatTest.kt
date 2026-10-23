package com.moyue.reader

import com.moyue.reader.ui.common.PRIVACY_MASK_TITLE
import com.moyue.reader.ui.common.maskTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `maskTitle hides title in privacy mode`() {
        assertEquals(PRIVACY_MASK_TITLE, maskTitle("斗破苍穹", privacyMode = true))
    }

    @Test
    fun `maskTitle keeps title when privacy off`() {
        assertEquals("斗破苍穹", maskTitle("斗破苍穹", privacyMode = false))
    }

    @Test
    fun `mask is constant regardless of input`() {
        val a = maskTitle("书甲", true)
        val b = maskTitle("书乙标题很长很长很长", true)
        assertEquals(a, b)
    }
}
