package com.moyue.reader.privacy

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 隐私空间密码哈希：PBKDF2WithHmacSHA1 + 随机盐。
 * 数字密码与图案密码都以字符串形式（图案为 "0-4-8" 点序）进入同一哈希管线。
 */
object PasswordHasher {

    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(secret: String, saltHex: String, iterations: Int = ITERATIONS): String {
        val spec = PBEKeySpec(
            secret.toCharArray(),
            saltHex.hexToBytes(),
            iterations,
            KEY_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded.toHex()
    }

    /** 常量时间比较，避免时序侧信道 */
    fun verify(secret: String, saltHex: String, expectedHex: String): Boolean =
        try {
            MessageDigest.isEqual(
                hash(secret, saltHex).toByteArray(Charsets.US_ASCII),
                expectedHex.toByteArray(Charsets.US_ASCII)
            )
        } catch (e: Exception) {
            false
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray {
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val hi = this[i * 2].digitToIntOrNull(16) ?: 0
            val lo = this[i * 2 + 1].digitToIntOrNull(16) ?: 0
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
