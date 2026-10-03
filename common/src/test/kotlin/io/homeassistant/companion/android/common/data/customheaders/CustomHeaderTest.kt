package io.homeassistant.companion.android.common.data.customheaders

import io.homeassistant.companion.android.common.data.customheaders.CustomHeader.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CustomHeaderTest {
    @Test
    fun `Given a regular header when validate then valid`() {
        assertEquals(Validation.Valid, CustomHeader("CF-Access-Client-Id", "abc").validate())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Bad Name", "Bad:Name", "Naïve"])
    fun `Given an invalid name when validate then InvalidName`(name: String) {
        assertEquals(Validation.InvalidName, CustomHeader(name, "v").validate())
    }

    @ParameterizedTest
    @ValueSource(strings = ["authorization", "Host", "COOKIE", "User-Agent", "Sec-WebSocket-Key", "Proxy-Authorization"])
    fun `Given a reserved name when validate then ReservedName`(name: String) {
        assertEquals(Validation.ReservedName, CustomHeader(name, "v").validate())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "a\nb", "a\r\nInjected: 1"])
    fun `Given an invalid value when validate then InvalidValue`(value: String) {
        assertEquals(Validation.InvalidValue, CustomHeader("X-Test", value).validate())
    }

    @Test
    fun `Given a header when toString then the value is not printed`() {
        assertFalse(CustomHeader("X-Test", "super-secret").toString().contains("super-secret"))
    }
}
