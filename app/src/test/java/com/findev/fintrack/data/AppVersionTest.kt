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
    fun `surrounding whitespace is not silently accepted`() {
        assertNull(versionCodeFromTag(" v1.2.3"))
        assertNull(versionCodeFromTag("v1.2.3 "))
    }
}
