package com.moyue.reader.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privacyStore: DataStore<Preferences> by preferencesDataStore(name = "privacy")

/**
 * 隐私空间密码仓库：类型 + 加盐哈希存于独立 DataStore（不进 Room、不进备份）。
 */
class PrivacyRepository(private val context: Context) {

    companion object {
        const val TYPE_PIN = "pin"
        const val TYPE_PATTERN = "pattern"
    }

    private object K {
        val type = stringPreferencesKey("pwd_type")
        val hash = stringPreferencesKey("pwd_hash")
        val salt = stringPreferencesKey("pwd_salt")
        val bio = booleanPreferencesKey("biometric_enabled")
    }

    suspend fun hasPassword(): Boolean =
        context.privacyStore.data.map { it[K.hash] != null }.first()

    suspend fun passwordType(): String? =
        context.privacyStore.data.map { it[K.type] }.first()

    suspend fun setPassword(type: String, secret: String) {
        val salt = PasswordHasher.randomSalt()
        context.privacyStore.edit { p ->
            p[K.type] = type
            p[K.salt] = salt
            p[K.hash] = PasswordHasher.hash(secret, salt)
        }
    }

    suspend fun verify(type: String, secret: String): Boolean {
        val p = context.privacyStore.data.first()
        val storedHash = p[K.hash] ?: return false
        if (p[K.type] != type) return false
        val salt = p[K.salt] ?: return false
        return PasswordHasher.verify(secret, salt, storedHash)
    }

    suspend fun bioEnabled(): Boolean =
        context.privacyStore.data.map { it[K.bio] == true }.first()

    suspend fun setBioEnabled(v: Boolean) {
        context.privacyStore.edit { p -> p[K.bio] = v }
    }

    suspend fun clearPassword() {
        context.privacyStore.edit { p -> p.remove(K.bio) }
        context.privacyStore.edit { p ->
            p.remove(K.type)
            p.remove(K.salt)
            p.remove(K.hash)
        }
    }
}
