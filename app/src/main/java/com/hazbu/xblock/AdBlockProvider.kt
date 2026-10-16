package com.hazbu.xblock
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hazbu.xblock.Constants.FILE_DOMAINS
import com.hazbu.xblock.Constants.FILE_PACKAGES
import com.hazbu.xblock.Constants.KEY_DOMAINS
import com.hazbu.xblock.Constants.KEY_DOMAINS_COUNT
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_ADGUARD
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_EXODUS
import com.hazbu.xblock.Constants.KEY_PACKAGES
import com.hazbu.xblock.Constants.KEY_PACKAGES_COUNT
import com.hazbu.xblock.Constants.PREFS_NAME
import java.io.File
class AdBlockProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val fileName = when (uri.path) {
            "/domains" -> FILE_DOMAINS
            "/packages" -> FILE_PACKAGES
            else -> return null
        }
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            migrateLegacyIfPresent(context, fileName, file)
        }
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (_: Exception) {}
        }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (_: Exception) {
            null
        }
    }

    private fun migrateLegacyIfPresent(context: Context, fileName: String, targetFile: File) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyKey = if (fileName == FILE_DOMAINS) KEY_DOMAINS else KEY_PACKAGES
        val legacySet = prefs.getStringSet(legacyKey, null)
        if (!legacySet.isNullOrEmpty()) {
            try {
                targetFile.bufferedWriter().use { writer ->
                    for (item in legacySet) {
                        writer.write(item)
                        writer.newLine()
                    }
                }
                val countKey = if (fileName == FILE_DOMAINS) KEY_DOMAINS_COUNT else KEY_PACKAGES_COUNT
                prefs.edit().putInt(countKey, legacySet.size).remove(legacyKey).apply()
            } catch (_: Exception) {}
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (uri.path) {
            "/status" -> {
                val domainsCount = prefs.getInt(KEY_DOMAINS_COUNT, 0)
                val packagesCount = prefs.getInt(KEY_PACKAGES_COUNT, 0)
                val count = domainsCount + packagesCount
                val isDownloading = prefs.getBoolean(KEY_IS_DOWNLOADING_ADGUARD, false) ||
                                    prefs.getBoolean(KEY_IS_DOWNLOADING_EXODUS, false)
                val cursor = MatrixCursor(arrayOf("count", "is_downloading", "just_updated"))
                cursor.addRow(arrayOf(count, if (isDownloading) 1 else 0, 0))
                cursor
            }
            "/domains" -> {
                val file = File(context.filesDir, FILE_DOMAINS)
                if (!file.exists()) migrateLegacyIfPresent(context, FILE_DOMAINS, file)
                val cursor = MatrixCursor(arrayOf("domain"))
                if (file.exists()) {
                    file.forEachLine { line ->
                        if (line.isNotEmpty()) cursor.addRow(arrayOf(line))
                    }
                }
                cursor
            }
            "/packages" -> {
                val file = File(context.filesDir, FILE_PACKAGES)
                if (!file.exists()) migrateLegacyIfPresent(context, FILE_PACKAGES, file)
                val cursor = MatrixCursor(arrayOf("package"))
                if (file.exists()) {
                    file.forEachLine { line ->
                        if (line.isNotEmpty()) cursor.addRow(arrayOf(line))
                    }
                }
                cursor
            }
            else -> null
        }
    }
    override fun getType(uri: Uri): String? {
        return when (uri.path) {
            "/domains", "/packages" -> "text/plain"
            else -> null
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

