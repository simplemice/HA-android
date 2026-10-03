package io.homeassistant.companion.android.common.data.customheaders

import kotlinx.serialization.Serializable

/**
 * A custom HTTP header the user wants to attach to every request sent to one of their Home Assistant
 * servers, for example to pass a reverse proxy or API gateway in front of Home Assistant.
 *
 * The value is a credential: [toString] never prints it so it cannot end up in logs or exceptions.
 *
 * @property name Header name, must be a valid HTTP token and not one of [RESERVED_NAMES].
 * @property value Header value, must not contain control characters.
 * @property isSensitive When `true` the UI masks the value and never shows it again once saved.
 */
@Serializable
data class CustomHeader(val name: String, val value: String, val isSensitive: Boolean = true) {

    /**
     * Result of [validate]. Using a sealed type instead of a boolean tells the UI which rule failed.
     */
    sealed interface Validation {
        data object Valid : Validation
        data object InvalidName : Validation
        data object ReservedName : Validation
        data object InvalidValue : Validation
    }

    /** Checks that the header can be safely sent by OkHttp and does not interfere with the app's own headers. */
    fun validate(): Validation = when {
        !NAME_REGEX.matches(name) -> Validation.InvalidName
        RESERVED_NAMES.any { it.equals(name, ignoreCase = true) } ||
            RESERVED_PREFIXES.any { name.startsWith(it, ignoreCase = true) } -> Validation.ReservedName
        value.isEmpty() || value.any { it.isISOControl() && it != '\t' } -> Validation.InvalidValue
        else -> Validation.Valid
    }

    override fun toString(): String = "CustomHeader(name=$name, value=HIDDEN, isSensitive=$isSensitive)"

    companion object {
        // RFC 9110 token
        private val NAME_REGEX = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

        /**
         * Headers owned by the app or the HTTP stack. Authorization is reserved because the app sends the Home Assistant
         * bearer token with it and a proxy value would either be ignored or break the app's authentication.
         */
        val RESERVED_NAMES = setOf(
            "Host",
            "Authorization",
            "Content-Length",
            "Content-Type",
            "Content-Encoding",
            "Transfer-Encoding",
            "Connection",
            "Upgrade",
            "User-Agent",
            "Cookie",
        )
        private val RESERVED_PREFIXES = listOf("Sec-WebSocket-", "Proxy-")
    }
}
