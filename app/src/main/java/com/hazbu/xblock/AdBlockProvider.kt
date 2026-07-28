package com.hazbu.xblock

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class AdBlockProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        val prefs = context.getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)

        return when (uri.path) {
            "/domains" -> {
                val domains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
                val cursor = MatrixCursor(arrayOf("domain"))
                for (domain in domains) {
                    cursor.addRow(arrayOf(domain))
                }
                cursor
            }
            "/packages" -> {
                val packages = prefs.getStringSet(AdBlockUtils.KEY_PACKAGES, emptySet()) ?: emptySet()
                val cursor = MatrixCursor(arrayOf("package"))
                for (pkg in packages) {
                    cursor.addRow(arrayOf(pkg))
                }
                cursor
            }
            "/status" -> {
                val domains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
                val packages = prefs.getStringSet(AdBlockUtils.KEY_PACKAGES, emptySet()) ?: emptySet()
                val count = domains.size + packages.size
                val isDownloading = prefs.getBoolean(AdBlockUtils.KEY_IS_DOWNLOADING_ADGUARD, false) || 
                                    prefs.getBoolean(AdBlockUtils.KEY_IS_DOWNLOADING_EXODUS, false)

                val cursor = MatrixCursor(arrayOf("count", "is_downloading", "just_updated"))
                cursor.addRow(arrayOf(count, if (isDownloading) 1 else 0, 0)) // just_updated always 0
                cursor
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
