package com.findev.fintrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionTest {

    @Test
    fun `matches the formula the release workflow uses`() {
        // MAJOR * 10000 + MINOR * 100 + PATCH, as in .github/workflows/release.yml.
        assertEquals(10203L, versionCodeFromTag("v1.2.3"))
        assertEquals(10000L, versionCodeFromTag("v1.0.0"))
        assertEquals(20015L, versionCodeFromTag("v2.0.15"))
    }

    @Test
    fun `the v prefix is optional`() {
        assertEquals(10203L, versionCodeFromTag("1.2.3"))
    }

    @Test
    fun `ordering follows the version, not the string`() {
        val older = versionCodeFromTag("v1.9.0")!!
        val newer = versionCodeFromTag("v1.10.0")!!
        // String comparison would call 1.9.0 the newer one.
        assert(newer > older)
    }

    @Test
    fun `tags that are not three numbers are rejected`() {
        assertNull(versionCodeFromTag("v2.0"))
        assertNull(versionCodeFromTag("v1.2.3.4"))
        assertNull(versionCodeFromTag("nightly"))
        assertNull(versionCodeFromTag(""))
        assertNull(versionCodeFromTag("v1.2.x"))
        assertNull(versionCodeFromTag("v1..3"))
    }

    @Test
    fun `a component that would overflow its slot is rejected`() {
        // 1.100.0 would collide with 2.0.0 under this formula, so it is refused rather
        // than silently offered as a different version than it is.
        assertNull(versionCodeFromTag("v1.100.0"))
        assertNull(versionCodeFromTag("v1.0.100"))
    }

    @Test
    fun `a plain tag is the stable channel`() {
        val tag = parseReleaseTag("v1.2.3")!!

        assertEquals(ReleaseChannel.STABLE, tag.channel)
        assertEquals("1.2.3", tag.versionName)
        assertEquals(10203L, tag.versionCode)
    }

    @Test
    fun `a beta suffix switches the channel without changing the number`() {
        val beta = parseReleaseTag("v1.2.3-beta")!!

        assertEquals(ReleaseChannel.BETA, beta.channel)
        assertEquals("1.2.3-beta", beta.versionName)
        // Same code as the stable of that version: the channels are separate apps, so their
        // version codes never have to avoid each other.
        assertEquals(10203L, beta.versionCode)
    }

    @Test
    fun `versions keep climbing across channels`() {
        val beta = parseReleaseTag("v0.2.4-beta")!!
        val stable = parseReleaseTag("v0.2.5")!!

        assert(stable.versionCode > beta.versionCode)
    }

    @Test
    fun `an unknown suffix is refused rather than treated as stable`() {
        // Offering an alpha to a stable install would be exactly the accident the channels
        // are meant to prevent.
        assertNull(parseReleaseTag("v1.2.3-alpha"))
        assertNull(parseReleaseTag("v1.2.3-beta.1"))
        assertNull(parseReleaseTag("v1.2.3-BETA"))
    }

    @Test
    fun `release notes lose their markdown and generated noise`() {
        val raw = """
            ## What's Changed
            * **Fixed** the widget
            versionCode: 100

            **Full Changelog**: https://github.com/Sch1z0eD/FinTrack/commits/v0.1.0
        """.trimIndent()

        val formatted = formatReleaseNotes(raw)

        assertEquals("## What's Changed\n* Fixed the widget", formatted)
    }

    @Test
    fun `empty release notes stay empty`() {
        assertEquals("", formatReleaseNotes(""))
    }

    @Test
    fun `surrounding whitespace is not silently accepted`() {
        assertNull(versionCodeFromTag(" v1.2.3"))
        assertNull(versionCodeFromTag("v1.2.3 "))
    }
}
