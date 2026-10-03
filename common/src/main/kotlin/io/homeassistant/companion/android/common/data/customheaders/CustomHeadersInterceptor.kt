package io.homeassistant.companion.android.common.data.customheaders

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the user configured [CustomHeader] of a server to the requests sent to it.
 *
 * It MUST be registered as a **network** interceptor: those run for every hop of a redirect chain, so the
 * origin is checked again for each hop and headers are never forwarded to the host of a redirect (OkHttp builds
 * follow up requests from the original application request, which never contains these headers).
 * OkHttp does not run network interceptors for WebSocket handshakes, those get their headers in WebSocketCoreImpl.
 * Headers already set by the app are never overridden.
 */
@Singleton
class CustomHeadersInterceptor @Inject internal constructor(private val repository: CustomHeadersRepository) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // OkHttp interceptors run on a background thread and are blocking by contract.
        val headers = runBlocking { repository.getHeadersForUrl(request.url) }
            .filter { it.validate() == CustomHeader.Validation.Valid && request.header(it.name) == null }
        if (headers.isEmpty()) return chain.proceed(request)

        val builder = request.newBuilder()
        headers.forEach { builder.header(it.name, it.value) }
        return chain.proceed(builder.build())
    }
}
