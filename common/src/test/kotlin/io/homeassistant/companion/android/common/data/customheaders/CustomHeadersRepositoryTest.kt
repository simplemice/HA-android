package io.homeassistant.companion.android.common.data.customheaders

import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomHeadersRepositoryTest {
    private val storage = FakeStorage()
    private val cipher = FakeHeaderCipher()
    private val serverDao: ServerDao = mockk()
    private val repository = CustomHeadersRepository(storage, cipher, serverDao)

    private fun server(id: Int, externalUrl: String, internalUrl: String? = null, cloudUrl: String? = null) = Server(
        id = id,
        _name = "s$id",
        connection = ServerConnectionInfo(externalUrl = externalUrl, internalUrl = internalUrl, cloudUrl = cloudUrl),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private val idHeader = CustomHeader("CF-Access-Client-Id", "id-1")
    private val secretHeader = CustomHeader("CF-Access-Client-Secret", "secret-1")

    @Test
    fun `Given headers saved when get then returned and persisted encrypted only`() = runTest {
        repository.setHeaders(1, listOf(idHeader, secretHeader))

        assertEquals(listOf(idHeader, secretHeader), repository.getHeaders(1))
        assertTrue(storage.values.values.none { it.contains("secret-1") || it.contains("CF-Access") })
    }

    @Test
    fun `Given a new repository on same storage when get then headers survive restart`() = runTest {
        repository.setHeaders(1, listOf(idHeader))

        assertEquals(listOf(idHeader), CustomHeadersRepository(storage, cipher, serverDao).getHeaders(1))
    }

    @Test
    fun `Given two servers when editing one then the other is untouched`() = runTest {
        repository.setHeaders(1, listOf(idHeader))
        repository.setHeaders(2, listOf(secretHeader))

        repository.setHeaders(1, listOf(CustomHeader("X-Other", "v")))

        assertEquals(listOf(secretHeader), repository.getHeaders(2))
    }

    @Test
    fun `Given headers when set with empty list then everything is removed`() = runTest {
        repository.setHeaders(1, listOf(idHeader))
        repository.setHeaders(1, emptyList())

        assertTrue(repository.getHeaders(1).isEmpty())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `Given a header removed from the list when get then it is gone`() = runTest {
        repository.setHeaders(1, listOf(idHeader, secretHeader))
        repository.setHeaders(1, listOf(idHeader))

        assertEquals(listOf(idHeader), repository.getHeaders(1))
    }

    @Test
    fun `Given invalid or duplicated headers when set then rejected`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.test.runTest { repository.setHeaders(1, listOf(CustomHeader("Authorization", "x"))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.test.runTest {
                repository.setHeaders(1, listOf(CustomHeader("X-A", "1"), CustomHeader("x-a", "2")))
            }
        }
    }

    @Test
    fun `Given undecryptable data when get then empty`() = runTest {
        repository.setHeaders(1, listOf(idHeader))
        cipher.failDecrypt = true

        assertTrue(repository.getHeaders(1).isEmpty())
    }

    @Test
    fun `Given servers when get for url then only the matching origin is returned`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(
            server(1, "https://a.example.com", internalUrl = "http://192.168.1.2:8123", cloudUrl = "https://x.ui.nabu.casa"),
            server(2, "https://b.example.com"),
        )
        repository.setHeaders(1, listOf(idHeader))
        repository.setHeaders(2, listOf(secretHeader))

        assertEquals(listOf(idHeader), repository.getHeadersForUrl("https://a.example.com/api/".toHttpUrl()))
        assertEquals(listOf(idHeader), repository.getHeadersForUrl("http://192.168.1.2:8123/api/".toHttpUrl()))
        assertEquals(listOf(secretHeader), repository.getHeadersForUrl("https://b.example.com/".toHttpUrl()))
    }

    @Test
    fun `Given unrelated hosts when get for url then nothing is returned`() = runTest {
        coEvery { serverDao.getAll() } returns listOf(
            server(1, "https://a.example.com", cloudUrl = "https://x.ui.nabu.casa"),
        )
        repository.setHeaders(1, listOf(idHeader))

        listOf(
            "https://x.ui.nabu.casa/",
            "https://team.cloudflareaccess.com/cdn-cgi/access/login",
            "https://a.example.com:8443/",
            "http://a.example.com/",
            "https://evil.a.example.com/",
            "https://github.com/",
        ).forEach { assertTrue(repository.getHeadersForUrl(it.toHttpUrl()).isEmpty(), it) }
        assertFalse(repository.getHeaders(1).isEmpty())
    }
}
