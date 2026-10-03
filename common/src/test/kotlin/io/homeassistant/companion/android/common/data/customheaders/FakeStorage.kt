package io.homeassistant.companion.android.common.data.customheaders

import io.homeassistant.companion.android.common.data.LocalStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** In-memory [LocalStorage] keeping what was written so tests can inspect the persisted values. */
internal class FakeStorage : LocalStorage {
    val values = mutableMapOf<String, String>()

    override suspend fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override suspend fun getString(key: String): String? = values[key]
    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override fun observeChanges(vararg keys: String): Flow<String> = emptyFlow()
    override suspend fun <T> observeChanges(vararg keys: String, mapper: suspend () -> T): Flow<T> = emptyFlow()
    override suspend fun putLong(key: String, value: Long?) = error("unused")
    override suspend fun getLong(key: String): Long? = error("unused")
    override suspend fun putInt(key: String, value: Int?) = error("unused")
    override suspend fun getInt(key: String): Int? = error("unused")
    override suspend fun putBoolean(key: String, value: Boolean) = error("unused")
    override suspend fun getBoolean(key: String): Boolean = error("unused")
    override suspend fun getBooleanOrNull(key: String): Boolean? = error("unused")
    override suspend fun putStringSet(key: String, value: Set<String>) = error("unused")
    override suspend fun getStringSet(key: String): Set<String>? = error("unused")
}

/** Reversible fake cipher, the Android Keystore is not available in unit tests. */
internal class FakeHeaderCipher : HeaderCipher {
    var failDecrypt = false
    override fun encrypt(plainText: String) = "enc:" + plainText.reversed()
    override fun decrypt(cipherText: String) = if (failDecrypt) null else cipherText.removePrefix("enc:").reversed()
}
