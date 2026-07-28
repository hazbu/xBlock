package com.hazbu.xblock

import android.content.Context
import org.json.JSONObject
import java.io.File

object AdBlockUtils {
    const val FILTER_URL = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
    const val EXODUS_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
    
    const val PREFS_NAME = "ad_prefs"
    const val KEY_DOMAINS = "domains"
    const val KEY_PACKAGES = "packages"
    const val KEY_LAST_UPDATE_ADGUARD = "last_update"
    const val KEY_LAST_UPDATE_EXODUS = "last_update_exodus"
    const val KEY_IS_DOWNLOADING_ADGUARD = "is_downloading_adguard"
    const val KEY_IS_DOWNLOADING_EXODUS = "is_downloading_exodus"
    const val KEY_JUST_UPDATED = "just_updated"

    fun parseAdGuardFilter(reader: java.io.Reader): Set<String> {
        val domains = mutableSetOf<String>()
        reader.forEachLine { line ->
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

    fun parseExodusFilter(reader: java.io.Reader): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val json = reader.readText()
            val root = JSONObject(json)
            val trackers = root.optJSONObject("trackers") ?: return emptySet()
            
            val keys = trackers.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val tracker = trackers.getJSONObject(key)
                val signature = tracker.optString("code_signature", "")
                if (signature.isNotEmpty()) {
                    signature.split("|").forEach { pkg ->
                        val cleaned = pkg.trim().trimEnd('.')
                        if (cleaned.isNotEmpty()) {
                            packages.add(cleaned)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return packages
    }

    fun isAdClass(className: String, adPackages: Set<String>): Boolean {
        val lowerName = className.lowercase()
        return adPackages.any { lowerName.startsWith(it.lowercase()) }
    }

    fun fixPermissions(context: Context) {
        try {
            val dataDir = File(context.applicationInfo.dataDir)
            dataDir.setExecutable(true, false)
            
            val prefsDir = File(dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false)
                @Suppress("SetWorldReadable")
                prefsDir.setReadable(true, false)
            }
            
            val prefFile = File(prefsDir, "$PREFS_NAME.xml")
            if (prefFile.exists()) {
                @Suppress("SetWorldReadable")
                prefFile.setReadable(true, false)
            }
        } catch (_: Exception) {}
    }
}
