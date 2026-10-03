package io.homeassistant.companion.android.common.data.customheaders

import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CustomHeadersInterceptorTest {
    private val ha1 = MockWebServer()
    private val ha2 = MockWebServer()
    private val other = MockWebServer()
    private val storage = FakeStorage()
    private val serverDao: ServerDao = mockk()
    private lateinit var repository: CustomHeadersRepository
    private lateinit var client: OkHttpClient

    private fun server(id: Int, url: String) = Server(
        id = id,
        _name = "s$id",
        connection = ServerConnectionInfo(externalUrl = url),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @BeforeEach
    fun setUp() {
        listOf(ha1, ha2, other).forEach { it.start() }
        coEvery { serverDao.getAll() } returns listOf(
            server(1, ha1.url("/").toString()),
            server(2, ha2.url("/").toString()),
        )
        repository = CustomHeadersRepository(storage, FakeHeaderCipher(), serverDao)
        client = OkHttpClient.Builder()
            .addNetworkInterceptor(CustomHeadersInterceptor(repository))
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @AfterEach
    fun tearDown() {
        listOf(ha1, ha2, other).forEach { it.close() }
    }

    private fun get(url: okhttp3.HttpUrl, block: Request.Builder.() -> Unit = {}) {
        client.newCall(Request.Builder().url(url).apply(block).build()).execute().close()
    }

    private fun setHeaders(serverId: Int, vararg headers: CustomHeader) = runBlocking { repository.setHeaders(serverId, headers.toList()) }

    @Test
    fun `Given headers when request to configured server then headers are sent`() {
        setHeaders(1, CustomHeader("CF-Access-Client-Id", "id"), CustomHeader("CF-Access-Client-Secret", "secret"))
        ha1.enqueue(MockResponse.Builder().build())

        get(ha1.url("/api/"))

        val recorded = ha1.takeRequest()
        assertEquals("id", recorded.headers["CF-Access-Client-Id"])
        assertEquals("secret", recorded.headers["CF-Access-Client-Secret"])
    }

    @Test
    fun `Given headers when request to unrelated host then no header is sent`() {
        setHeaders(1, CustomHeader("X-Secret", "secret"))
        other.enqueue(MockResponse.Builder().build())

        get(other.url("/"))

        assertNull(other.takeRequest().headers["X-Secret"])
    }

    @Test
    fun `Given two servers with headers when requests then each only gets its own`() {
        setHeaders(1, CustomHeader("X-Secret", "one"))
        setHeaders(2, CustomHeader("X-Secret", "two"))
        ha1.enqueue(MockResponse.Builder().build())
        ha2.enqueue(MockResponse.Builder().build())

        get(ha1.url("/"))
        get(ha2.url("/"))

        assertEquals("one", ha1.takeRequest().headers["X-Secret"])
        assertEquals("two", ha2.takeRequest().headers["X-Secret"])
    }

    @Test
    fun `Given a redirect to another host when request then headers are not leaked`() {
        setHeaders(1, CustomHeader("X-Secret", "secret"))
        ha1.enqueue(MockResponse.Builder().code(302).addHeader("Location", other.url("/login").toString()).build())
        other.enqueue(MockResponse.Builder().build())

        get(ha1.url("/api/"))

        val rec = ha1.takeRequest()
        assertEquals("secret", rec.headers["X-Secret"])
        assertNull(other.takeRequest().headers["X-Secret"])
    }

    @Test
    fun `Given a redirect from unrelated host to configured server when request then headers are sent on that hop`() {
        setHeaders(1, CustomHeader("X-Secret", "secret"))
        other.enqueue(MockResponse.Builder().code(302).addHeader("Location", ha1.url("/").toString()).build())
        ha1.enqueue(MockResponse.Builder().build())

        get(other.url("/"))

        assertNull(other.takeRequest().headers["X-Secret"])
        val rec = ha1.takeRequest()
        assertEquals("secret", rec.headers["X-Secret"])
    }

    @Test
    fun `Given no headers configured when request then request is unchanged`() {
        ha1.enqueue(MockResponse.Builder().build())

        get(ha1.url("/")) { header("Authorization", "Bearer token") }

        val recorded = ha1.takeRequest()
        assertEquals("Bearer token", recorded.headers["Authorization"])
        assertEquals(setOf("authorization", "host", "connection", "accept-encoding", "user-agent"), recorded.headers.names().map { it.lowercase() }.toSet())
    }

    @Test
    fun `Given a header removed when request then it is no longer sent`() {
        setHeaders(1, CustomHeader("X-A", "a"), CustomHeader("X-B", "b"))
        setHeaders(1, CustomHeader("X-A", "a"))
        ha1.enqueue(MockResponse.Builder().build())

        get(ha1.url("/"))

        val recorded = ha1.takeRequest()
        assertEquals("a", recorded.headers["X-A"])
        assertNull(recorded.headers["X-B"])
    }

    @Test
    fun `Given a header already set by the app when request then app value is kept`() {
        setHeaders(1, CustomHeader("X-A", "custom"))
        ha1.enqueue(MockResponse.Builder().build())

        get(ha1.url("/")) { header("X-A", "app") }

        assertEquals("app", ha1.takeRequest().headers["X-A"])
    }

    @Test
    fun `Given headers when WebSocket handshake then headers are sent`() {
        ha1.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {}).build())
        val opened = java.util.concurrent.CountDownLatch(1)
        val (wsClient, request) = applyCustomHeaders(
            client,
            Request.Builder().url(ha1.url("/api/websocket")).build(),
            listOf(CustomHeader("X-Secret", "secret")),
        )

        wsClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.countDown()
                    webSocket.close(1000, null)
                }
            },
        )

        assertEquals("secret", ha1.takeRequest().headers["X-Secret"])
        assertTrue(opened.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `Given headers when WebSocket handshake is redirected to another host then headers are not leaked`() {
        ha1.enqueue(MockResponse.Builder().code(302).addHeader("Location", other.url("/login").toString()).build())
        val failed = java.util.concurrent.CountDownLatch(1)
        val (wsClient, request) = applyCustomHeaders(
            client,
            Request.Builder().url(ha1.url("/api/websocket")).build(),
            listOf(CustomHeader("X-Secret", "secret")),
        )

        wsClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failed.countDown()
                }
            },
        )

        assertTrue(failed.await(5, TimeUnit.SECONDS))
        assertEquals(0, other.requestCount)
    }

    @Test
    fun `Given no headers when applyCustomHeaders then client and request are unchanged`() {
        val request = Request.Builder().url(ha1.url("/api/websocket")).build()

        val (wsClient, result) = applyCustomHeaders(client, request, emptyList())

        assertSame(client, wsClient)
        assertSame(request, result)
    }

    @Test
    fun `Given a debug logging interceptor when request then the custom header value is not logged`() {
        setHeaders(1, CustomHeader("X-Secret", "very-secret"))
        ha1.enqueue(MockResponse.Builder().build())
        val logs = mutableListOf<String>()
        val loggingClient = client.newBuilder()
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor { logs.add(it) }.apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
                },
            )
            .build()

        loggingClient.newCall(Request.Builder().url(ha1.url("/")).build()).execute().close()

        assertEquals("very-secret", ha1.takeRequest().headers["X-Secret"])
        assertEquals(false, logs.any { it.contains("very-secret") })
    }
}
