package com.tom7.openremote

import com.tom7.openremote.data.normalizeServerUrl
import com.tom7.openremote.data.parsePairingInput
import com.tom7.openremote.data.pairingPayloadFromLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    @Test
    fun pairingLinkKeepsOriginAndCode() {
        val link = parsePairingInput("http://192.168.1.20:4096/auth/connect/AbC-123_xyz")

        assertEquals(listOf("http://192.168.1.20:4096"), link?.urls)
        assertEquals("AbC-123_xyz", link?.code)
    }

    @Test
    fun pairingLinkDropsQueryFragmentAndTrailingSlash() {
        val link = parsePairingInput("https://phone.example.com:8443/auth/connect/code42?ref=qr#frag")

        assertEquals(listOf("https://phone.example.com:8443"), link?.urls)
        assertEquals("code42", link?.code)
    }

    @Test
    fun scannerPayloadUsesEmbeddedUrls() {
        val payload = """
            {"code":"Code-99","urls":["http://10.0.0.5:4096","http://10.0.0.5:4096/","http://desktop.local:4096"]}
        """.trimIndent()

        val link = parsePairingInput(payload)

        assertEquals("Code-99", link?.code)
        assertEquals(listOf("http://10.0.0.5:4096", "http://desktop.local:4096"), link?.urls)
    }

    @Test
    fun scannerPayloadWithoutUrlsIsRejected() {
        assertNull(parsePairingInput("""{"code":"Code-99","urls":[]}"""))
        assertNull(parsePairingInput("""{"urls":["http://10.0.0.5:4096"]}"""))
    }

    @Test
    fun bareCodeUsesFallbackUrl() {
        val link = parsePairingInput("fresh-code_1", "192.168.0.10:4096")

        assertEquals("fresh-code_1", link?.code)
        assertEquals(listOf("http://192.168.0.10:4096"), link?.urls)
    }

    @Test
    fun bareCodeWithoutFallbackIsRejected() {
        assertNull(parsePairingInput("fresh-code_1"))
    }

    @Test
    fun invalidInputsAreRejected() {
        assertNull(parsePairingInput(""))
        assertNull(parsePairingInput("   "))
        assertNull(parsePairingInput("http://192.168.1.20:4096/auth/connect/"))
        assertNull(parsePairingInput("http://192.168.1.20:4096/api/session/abc"))
        assertNull(parsePairingInput("not a code!"))
        assertNull(parsePairingInput("""{"code":"bad code","urls":["http://10.0.0.5:4096"]}"""))
    }

    @Test
    fun payloadRoundTripsThroughParser() {
        val original = parsePairingInput("http://127.0.0.1:4096/auth/connect/sample-code")

        val reparsed = parsePairingInput(pairingPayloadFromLink(original!!))

        assertEquals(original, reparsed)
    }

    @Test
    fun normalizesServerUrls() {
        assertEquals("http://127.0.0.1:4096", normalizeServerUrl("127.0.0.1:4096"))
        assertEquals("http://127.0.0.1:4096", normalizeServerUrl("  http://127.0.0.1:4096/  "))
        assertEquals("https://desktop.local", normalizeServerUrl("https://desktop.local/"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("ftp://desktop.local"))
        assertNull(normalizeServerUrl("http://user:pass@desktop.local:4096"))
        assertNull(normalizeServerUrl("http://desktop.local:4096/?token=abc"))
    }
}
