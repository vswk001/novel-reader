package com.moyue.reader

import com.moyue.reader.privacy.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPasswordTest {

    @Test
    fun `pin hash roundtrip`() {
        val salt = PasswordHasher.randomSalt()
        val hash = PasswordHasher.hash("123456", salt)
        assertTrue(PasswordHasher.verify("123456", salt, hash))
    }

    @Test
    fun `pattern hash roundtrip`() {
        val salt = PasswordHasher.randomSalt()
        val pattern = "0-4-8-5-2"
        val hash = PasswordHasher.hash(pattern, salt)
        assertTrue(PasswordHasher.verify(pattern, salt, hash))
    }

    @Test
    fun `wrong password fails`() {
        val salt = PasswordHasher.randomSalt()
        val hash = PasswordHasher.hash("123456", salt)
        assertFalse(PasswordHasher.verify("123457", salt, hash))
        assertFalse(PasswordHasher.verify("", salt, hash))
    }

    @Test
    fun `different salts give different hashes`() {
        val a = PasswordHasher.hash("secret", "0011223344556677")
        val b = PasswordHasher.hash("secret", "8899aabbccddeeff")
        assertNotEquals(a, b)
    }

    @Test
    fun `salt and hash are hex strings`() {
        val salt = PasswordHasher.randomSalt()
        assertEquals(32, salt.length)
        assertTrue(salt.all { it in "0123456789abcdef" })
        assertEquals(64, PasswordHasher.hash("x", salt).length)
    }
}
