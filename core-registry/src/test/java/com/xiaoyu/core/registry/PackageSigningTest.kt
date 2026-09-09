package com.xiaoyu.core.registry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageSigningTest {
    @Test
    fun matches_normalizesColonsAndCase() {
        assertTrue(PackageSigning.matches("AA:BB:CC", "aabbcc"))
        assertTrue(PackageSigning.matches("AABBCC", "AA:BB:CC"))
    }

    @Test
    fun matches_blankExpectedAcceptsAny() {
        assertTrue(PackageSigning.matches(null, "AABBCC"))
        assertTrue(PackageSigning.matches("", "AABBCC"))
    }

    @Test
    fun matches_rejectsMismatch() {
        assertFalse(PackageSigning.matches("AABBCC", "DDEEFF"))
        assertFalse(PackageSigning.matches("AABBCC", null))
    }
}
