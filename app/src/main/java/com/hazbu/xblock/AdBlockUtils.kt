package com.hazbu.xblock
import android.content.Context
import com.hazbu.xblock.Constants.PREFS_NAME
import org.json.JSONObject
import java.io.File
object AdBlockUtils {
    const val FILTER_URL = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
    const val EXODUS_URL = "https://reports.exodus-privacy.eu.org/api/trackers"
    fun parseAdGuardFilter(reader: java.io.Reader): Set<String> {
        val domains = mutableSetOf<String>()
        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("||")) {
                val caretIndex = trimmed.indexOf("^")
                if (caretIndex != -1) {
                    val domain = trimmed.substring(2, caretIndex).lowercase().trimEnd('.')
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
                        val cleaned = pkg.trim().trimEnd('.').lowercase()
                        if (cleaned.isNotEmpty()) {
                            packages.add(cleaned)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return packages
    }
    fun isAdDomain(host: String?, blockedDomains: Set<String>): Boolean {
        if (host.isNullOrEmpty() || blockedDomains.isEmpty()) return false
        var current = host.lowercase().trim().trimEnd('.')
        val colonIndex = current.indexOf(':')
        if (colonIndex != -1) {
            current = current.substring(0, colonIndex)
        }
        while (current.isNotEmpty()) {
            if (blockedDomains.contains(current)) {
                return true
            }
            val nextDot = current.indexOf('.')
            if (nextDot == -1) break
            current = current.substring(nextDot + 1)
        }
        return false
    }
    fun isAdClass(className: String?, adPackages: Set<String>): Boolean {
        if (className.isNullOrEmpty() || adPackages.isEmpty()) return false
        val lowerName = className.lowercase()
        return adPackages.any { lowerName.startsWith(it) }
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

