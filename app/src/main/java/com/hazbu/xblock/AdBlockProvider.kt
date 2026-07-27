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
        if (uri.path == "/domains") {
            val context = context ?: return null
            val prefs = context.getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)
            val domains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
            
            val cursor = MatrixCursor(arrayOf("domain"))
            for (domain in domains) {
                cursor.addRow(arrayOf(domain))
            }
            return cursor
        }
        return null
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
