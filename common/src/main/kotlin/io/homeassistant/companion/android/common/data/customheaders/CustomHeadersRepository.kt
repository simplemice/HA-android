package io.homeassistant.companion.android.common.data.customheaders

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.di.qualifiers.NamedCustomHeadersStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import timber.log.Timber

private const val KEY_PREFIX = "headers_"

/**
 * Single source of truth for the custom HTTP headers configured per Home Assistant server.
 *
 * Headers are encrypted with [HeaderCipher] and stored in a storage excluded from backups.
 */
@Singleton
class CustomHeadersRepository @Inject internal constructor(
    @NamedCustomHeadersStorage private val storage: LocalStorage,
    private val cipher: HeaderCipher,
    private val serverDao: ServerDao,
) {
    /** Returns the headers configured for [serverId], empty if none or if they can't be read. */
    suspend fun getHeaders(serverId: Int): List<CustomHeader> {
        val json = storage.getString(key(serverId))?.let { cipher.decrypt(it) } ?: return emptyList()
        return try {
            kotlinJsonMapper.decodeFromString<List<CustomHeader>>(json)
        } catch (e: SerializationException) {
            Timber.e(e, "Unable to decode the custom headers of server $serverId")
            emptyList()
        }
    }

    /**
     * Replaces the headers of [serverId]. An empty list removes them.
     *
     * @throws IllegalArgumentException if a header is invalid or a name is used twice.
     */
    suspend fun setHeaders(serverId: Int, headers: List<CustomHeader>) {
        if (headers.isEmpty()) {
            removeHeaders(serverId)
            return
        }
        require(headers.all { it.validate() == CustomHeader.Validation.Valid }) { "Invalid custom header" }
        require(headers.map { it.name.lowercase() }.toSet().size == headers.size) { "Duplicated custom header name" }
        storage.putString(key(serverId), cipher.encrypt(kotlinJsonMapper.encodeToString(headers)))
    }

    /** Deletes every header of [serverId], to call when the server is removed. */
    suspend fun removeHeaders(serverId: Int) {
        storage.remove(key(serverId))
    }

    /**
     * Returns the headers to attach to a request to [url].
     *
     * Only a server whose external or internal URL has the same origin as [url] matches. Cloud URLs and any other host
     * never match so credentials are never sent to them.
     */
    suspend fun getHeadersForUrl(url: HttpUrl): List<CustomHeader> {
        val server = serverDao.getAll().firstOrNull { it.connection.isCustomHeadersUrl(url) } ?: return emptyList()
        return getHeaders(server.id)
    }

    private fun key(serverId: Int) = "$KEY_PREFIX$serverId"
}
