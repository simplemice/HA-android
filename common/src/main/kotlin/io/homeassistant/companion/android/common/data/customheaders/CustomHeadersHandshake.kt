package io.homeassistant.companion.android.common.data.customheaders

import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Applies [headers] to a WebSocket handshake [request], returning the client to send it with and the request.
 *
 * OkHttp does not run network interceptors for WebSocket handshakes, so the headers are added here. Since they would
 * be copied to the target of a redirect (for example the login page of a proxy on another host), redirects are not
 * followed when at least one valid header applies. Without headers the given client and request are returned as is.
 */
internal fun applyCustomHeaders(
    client: OkHttpClient,
    request: Request,
    headers: List<CustomHeader>,
): Pair<OkHttpClient, Request> {
    val valid = headers.filter { it.validate() == CustomHeader.Validation.Valid && request.header(it.name) == null }
    if (valid.isEmpty()) return client to request

    val requestBuilder = request.newBuilder()
    valid.forEach { requestBuilder.header(it.name, it.value) }
    val noRedirectClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    return noRedirectClient to requestBuilder.build()
}
