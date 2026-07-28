package com.hazbu.xblock

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request

class FilterUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val TAG = "xBlock"

    companion object {
        const val KEY_TYPE = "update_type"
        const val TYPE_ADGUARD = "adguard"
        const val TYPE_EXODUS = "exodus"
    }

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE) ?: TYPE_ADGUARD
        Log.d(TAG, "FilterUpdateWorker: Starting $type update...")
        
        val downloadKey = if (type == TYPE_EXODUS) AdBlockUtils.KEY_IS_DOWNLOADING_EXODUS else AdBlockUtils.KEY_IS_DOWNLOADING_ADGUARD
        
        val prefs = applicationContext.getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(downloadKey, true) }
        
        val url = if (type == TYPE_EXODUS) AdBlockUtils.EXODUS_URL else AdBlockUtils.FILTER_URL
        val request = Request.Builder().url(url).build()
        
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "FilterUpdateWorker: $type server error ${response.code}")
                prefs.edit { putBoolean(downloadKey, false) }
                return Result.retry()
            }

            val body = response.body
            if (body != null) {
                body.charStream().use { reader ->
                    if (type == TYPE_EXODUS) {
                        val packages = AdBlockUtils.parseExodusFilter(reader)
                        savePackages(packages)
                        Log.d(TAG, "FilterUpdateWorker: Successfully updated ${packages.size} packages")
                    } else {
                        val domains = AdBlockUtils.parseAdGuardFilter(reader)
                        saveDomains(domains)
                        Log.d(TAG, "FilterUpdateWorker: Successfully updated ${domains.size} domains")
                    }
                }
                
                prefs.edit { 
                    putBoolean(downloadKey, false)
                    putBoolean(AdBlockUtils.KEY_JUST_UPDATED, true)
                }
                Result.success()
            } else {
                Log.e(TAG, "FilterUpdateWorker: Empty response body for $type")
                prefs.edit { putBoolean(downloadKey, false) }
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "FilterUpdateWorker: $type update failed: ${e.message}")
            prefs.edit { putBoolean(downloadKey, false) }
            Result.retry()
        }
    }

    private fun saveDomains(domains: Set<String>) {
        val prefs = applicationContext.getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putStringSet(AdBlockUtils.KEY_DOMAINS, domains)
            putLong(AdBlockUtils.KEY_LAST_UPDATE_ADGUARD, System.currentTimeMillis())
        }
        AdBlockUtils.fixPermissions(applicationContext)
    }

    private fun savePackages(packages: Set<String>) {
        val prefs = applicationContext.getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putStringSet(AdBlockUtils.KEY_PACKAGES, packages)
            putLong(AdBlockUtils.KEY_LAST_UPDATE_EXODUS, System.currentTimeMillis())
        }
        AdBlockUtils.fixPermissions(applicationContext)
    }
}
