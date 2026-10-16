package com.hazbu.xblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class AdBlockUtilsTest {

    @Test
    fun testIsAdDomainExactMatch() {
        val blocked = setOf("adservice.google.com", "doubleclick.net")
        assertTrue(AdBlockUtils.isAdDomain("adservice.google.com", blocked))
        assertTrue(AdBlockUtils.isAdDomain("doubleclick.net", blocked))
    }

    @Test
    fun testIsAdDomainSubdomainMatch() {
        val blocked = setOf("doubleclick.net", "adservice.google.com")
        assertTrue(AdBlockUtils.isAdDomain("sub.doubleclick.net", blocked))
        assertTrue(AdBlockUtils.isAdDomain("deep.sub.doubleclick.net", blocked))
        assertTrue(AdBlockUtils.isAdDomain("tracker.adservice.google.com", blocked))
    }

    @Test
    fun testIsAdDomainNoFalsePositive() {
        val blocked = setOf("doubleclick.net", "ad.com")
        // Different domain ending with same string
        assertFalse(AdBlockUtils.isAdDomain("notdoubleclick.net", blocked))
        assertFalse(AdBlockUtils.isAdDomain("mydoubleclick.net", blocked))
        // Substring inside unrelated domain
        assertFalse(AdBlockUtils.isAdDomain("broadcom.com", blocked))
        assertFalse(AdBlockUtils.isAdDomain("downloadads.com", blocked))
        // Parent domain of a blocked subdomain should NOT be blocked
        val subOnlyBlocked = setOf("ads.google.com")
        assertFalse(AdBlockUtils.isAdDomain("google.com", subOnlyBlocked))
    }

    @Test
    fun testIsAdDomainEdgeCases() {
        val blocked = setOf("doubleclick.net")
        // Case-insensitivity
        assertTrue(AdBlockUtils.isAdDomain("SUB.DoubleClick.NET", blocked))
        // Trailing dot
        assertTrue(AdBlockUtils.isAdDomain("sub.doubleclick.net.", blocked))
        // Port number
        assertTrue(AdBlockUtils.isAdDomain("sub.doubleclick.net:443", blocked))
        // Null or empty
        assertFalse(AdBlockUtils.isAdDomain("", blocked))
        assertFalse(AdBlockUtils.isAdDomain(null, blocked))
        assertFalse(AdBlockUtils.isAdDomain("sub.doubleclick.net", emptySet()))
    }

    @Test
    fun testParseAdGuardFilter() {
        val filterData = """
            ! Checksum: abcdef
            ! Title: Sample Filter
            ||adservice.google.com^
            ||sub.tracker.net^
            ||UpperCASED.com^
            ! Comment line
            /banner/ads/
            ||invalid/domain^
        """.trimIndent()

        val domains = AdBlockUtils.parseAdGuardFilter(StringReader(filterData))
        assertTrue(domains.contains("adservice.google.com"))
        assertTrue(domains.contains("sub.tracker.net"))
        assertTrue(domains.contains("uppercased.com"))
        assertFalse(domains.contains("invalid/domain"))
        assertFalse(domains.contains("/banner/ads/"))
    }

    @Test
    fun testIsAdClass() {
        val adPackages = setOf("com.google.android.gms.ads", "com.facebook.ads")
        assertTrue(AdBlockUtils.isAdClass("com.google.android.gms.ads.AdView", adPackages))
        assertTrue(AdBlockUtils.isAdClass("COM.GOOGLE.ANDROID.GMS.ADS.INTERSTITIAL", adPackages))
        assertTrue(AdBlockUtils.isAdClass("com.facebook.ads.internal.Banner", adPackages))
        assertFalse(AdBlockUtils.isAdClass("androidx.appcompat.widget.Toolbar", adPackages))
        assertFalse(AdBlockUtils.isAdClass("com.google.android.material.button.MaterialButton", adPackages))
        assertFalse(AdBlockUtils.isAdClass(null, adPackages))
        assertFalse(AdBlockUtils.isAdClass("com.example.View", emptySet()))
    }
}
