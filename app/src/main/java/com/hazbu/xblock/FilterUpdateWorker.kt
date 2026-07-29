package com.hazbu.xblock

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hazbu.xblock.Constants.KEY_DOMAINS
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_ADGUARD
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_EXODUS
import com.hazbu.xblock.Constants.KEY_LAST_UPDATE_ADGUARD
import com.hazbu.xblock.Constants.KEY_LAST_UPDATE_EXODUS
import com.hazbu.xblock.Constants.KEY_PACKAGES
import com.hazbu.xblock.Constants.PREFS_NAME
import okhttp3.OkHttpClient
import okhttp3.Request

class FilterUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val tagX = "xBlock"

    companion object {
        const val KEY_TYPE = "update_type"
        const val TYPE_ADGUARD = "adguard"
        const val TYPE_EXODUS = "exodus"
        const val KEY_JUST_UPDATED = "just_updated"
    }

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: TYPE_ADGUARD
        Log.d(tagX, "FilterUpdateWorker: Starting $type update...")

        val downloadKey = if (type == TYPE_EXODUS) KEY_IS_DOWNLOADING_EXODUS else KEY_IS_DOWNLOADING_ADGUARD

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(downloadKey, true) }

        val url = if (type == TYPE_EXODUS) AdBlockUtils.EXODUS_URL else AdBlockUtils.FILTER_URL
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(tagX, "FilterUpdateWorker: $type server error ${response.code}")
                prefs.edit { putBoolean(downloadKey, false) }
                return Result.retry()
            }

            val body = response.body
            if (body != null) {
                body.charStream().use { reader ->
                    if (type == TYPE_EXODUS) {
                        val packages = AdBlockUtils.parseExodusFilter(reader)
                        saveFilter(KEY_PACKAGES, KEY_LAST_UPDATE_EXODUS, packages)
                        Log.d(tagX, "FilterUpdateWorker: Successfully updated ${packages.size} packages")
                    } else {
                        val domains = AdBlockUtils.parseAdGuardFilter(reader)
                        saveFilter(KEY_DOMAINS, KEY_LAST_UPDATE_ADGUARD, domains)
                        Log.d(tagX, "FilterUpdateWorker: Successfully updated ${domains.size} domains")
                    }
                }

                prefs.edit {
                    putBoolean(downloadKey, false)
                    putBoolean(KEY_JUST_UPDATED, true)
                }
                Result.success()
            } else {
                Log.e(tagX, "FilterUpdateWorker: Empty response body for $type")
                prefs.edit { putBoolean(downloadKey, false) }
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(tagX, "FilterUpdateWorker: $type update failed: ${e.message}")
            prefs.edit { putBoolean(downloadKey, false) }
            Result.retry()
        }
    }

    private fun saveFilter(filterKey: String, timeKey: String, data: Set<String>) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putStringSet(filterKey, data)
            putLong(timeKey, System.currentTimeMillis())
        }
        AdBlockUtils.fixPermissions(applicationContext)
    }
}
