package com.hazbu.xblock

import android.content.Context
import java.io.File

object AdBlockUtils {
    const val FILTER_URL = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
    const val PREFS_NAME = "ad_prefs"
    const val KEY_DOMAINS = "domains"
    const val KEY_LAST_UPDATE = "last_update"

    fun parseAdGuardFilter(content: String): Set<String> {
        val domains = mutableSetOf<String>()
        val lines = content.split("\n")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("||")) {
                val caretIndex = trimmed.indexOf("^")
                if (caretIndex != -1) {
                    val domain = trimmed.substring(2, caretIndex)
                    if (domain.isNotEmpty() && !domain.contains("/")) {
                        domains.add(domain)
                    }
                }
            }
        }
        return domains
    }

    fun isAdView(className: String): Boolean {
        val lowerName = className.lowercase()
        return lowerName.contains("com.google.android.gms.ads") ||
               lowerName.contains("com.google.unity.ads") ||
               lowerName.contains("com.applovin") ||
               lowerName.contains("com.mbridge.msdk") ||
               lowerName.contains("com.facebook.ads") ||
               lowerName.contains("com.unity3d.ads") ||
               lowerName.contains("com.unity3d.services") ||
               lowerName.contains("com.vungle.ads") ||
               lowerName.contains("com.ironsource") ||
               lowerName.contains("com.adcolony") ||
               lowerName.contains("com.chartboost")
    }

    fun fixPermissions(context: Context) {
        try {
            val dataDir = File(context.applicationInfo.dataDir)
            dataDir.setExecutable(true, false)
            
            val prefsDir = File(dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false)
                prefsDir.setReadable(true, false)
            }
            
            val prefFile = File(prefsDir, "${PREFS_NAME}.xml")
            if (prefFile.exists()) {
                prefFile.setReadable(true, false)
            }
        } catch (ignored: Exception) {
        }
    }
}
