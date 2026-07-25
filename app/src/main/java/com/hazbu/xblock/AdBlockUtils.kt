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
        return className.contains("com.google.android.gms.ads", ignoreCase = true) ||
               className.contains("com.applovin", ignoreCase = true) ||
               className.contains("com.mbridge.msdk", ignoreCase = true) || // Mintegral
               className.contains("com.facebook.ads", ignoreCase = true) ||
               className.contains("com.unity3d.ads", ignoreCase = true) ||
               className.contains("com.unity3d.services", ignoreCase = true) ||
               className.contains("com.vungle.ads", ignoreCase = true) ||
               className.contains("com.ironsource", ignoreCase = true)
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
