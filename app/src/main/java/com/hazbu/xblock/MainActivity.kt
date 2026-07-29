package com.hazbu.xblock

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adguardStatus: TextView
    private lateinit var exodusStatus: TextView
    private lateinit var btnAdGuard: Button
    private lateinit var btnExodus: Button
    private lateinit var btnDeleteAdGuard: Button
    private lateinit var btnDeleteExodus: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val titleView = findViewById<TextView>(R.id.title)
        val spannable = SpannableString("xBlock")
        val primaryColor = MaterialColors.getColor(titleView, androidx.appcompat.R.attr.colorPrimary)
        spannable.setSpan(
            ForegroundColorSpan(primaryColor),
            0, 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        titleView.text = spannable

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left + (24 * resources.displayMetrics.density).toInt(),
                top = systemBars.top,
                right = systemBars.right + (24 * resources.displayMetrics.density).toInt(),
                bottom = systemBars.bottom
            )
            insets
        }

        adguardStatus = findViewById(R.id.adguard_status)
        exodusStatus = findViewById(R.id.exodus_status)
        btnAdGuard = findViewById(R.id.btn_update_adguard)
        btnExodus = findViewById(R.id.btn_update_exodus)
        btnDeleteAdGuard = findViewById(R.id.btn_delete_adguard)
        btnDeleteExodus = findViewById(R.id.btn_delete_exodus)

        btnAdGuard.setOnClickListener { triggerUpdate(FilterUpdateWorker.TYPE_ADGUARD) }
        btnExodus.setOnClickListener { triggerUpdate(FilterUpdateWorker.TYPE_EXODUS) }

        btnDeleteAdGuard.setOnClickListener {
            deleteFilter(AdBlockUtils.KEY_DOMAINS, AdBlockUtils.KEY_LAST_UPDATE_ADGUARD)
        }
        btnDeleteExodus.setOnClickListener {
            deleteFilter(AdBlockUtils.KEY_PACKAGES, AdBlockUtils.KEY_LAST_UPDATE_EXODUS)
        }

        refreshUI()
    }

    private fun deleteFilter(vararg keys: String) {
        val prefs = getSharedPreferences(AdBlockUtils.PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            keys.forEach { remove(it) }
        }
        AdBlockUtils.fixPermissions(this)
        refreshUI()
    }

    private fun triggerUpdate(type: String) {
        val data = Data.Builder()
            .putString(FilterUpdateWorker.KEY_TYPE, type)
            .build()

        val request = OneTimeWorkRequestBuilder<FilterUpdateWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "Update_$type",
            ExistingWorkPolicy.REPLACE,
            request
        )

        val targetButton = if (type == FilterUpdateWorker.TYPE_EXODUS) btnExodus else btnAdGuard
        
        // Observe progress
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id).observe(this) { info ->
            if (info?.state == WorkInfo.State.SUCCEEDED || info?.state == WorkInfo.State.FAILED) {
                refreshUI()
                targetButton.isEnabled = true
            } else {
                targetButton.isEnabled = false
            }
        }
    }

    private fun refreshUI() {
        val prefs = getSharedPreferences(AdBlockUtils.PREFS_NAME, MODE_PRIVATE)
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        
        // AdGuard
        val isDownloadingAdGuard = prefs.getBoolean(AdBlockUtils.KEY_IS_DOWNLOADING_ADGUARD, false)
        if (isDownloadingAdGuard) {
            adguardStatus.text = "Status: Updating..."
            btnAdGuard.isEnabled = false
        } else {
            val domains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
            val lastUpdateAdGuard = prefs.getLong(AdBlockUtils.KEY_LAST_UPDATE_ADGUARD, 0L)
            val adguardDateStr = if (lastUpdateAdGuard > 0) sdf.format(Date(lastUpdateAdGuard)) else "Never"
            adguardStatus.text = "Count: ${domains.size}\nLast: $adguardDateStr"
            btnAdGuard.isEnabled = true
        }

        // Exodus
        val isDownloadingExodus = prefs.getBoolean(AdBlockUtils.KEY_IS_DOWNLOADING_EXODUS, false)
        if (isDownloadingExodus) {
            exodusStatus.text = "Status: Updating..."
            btnExodus.isEnabled = false
        } else {
            val packages = prefs.getStringSet(AdBlockUtils.KEY_PACKAGES, emptySet()) ?: emptySet()
            val lastUpdateExodus = prefs.getLong(AdBlockUtils.KEY_LAST_UPDATE_EXODUS, 0L)
            val exodusDateStr = if (lastUpdateExodus > 0) sdf.format(Date(lastUpdateExodus)) else "Never"
            exodusStatus.text = "Count: ${packages.size}\nLast: $exodusDateStr"
            btnExodus.isEnabled = true
        }
    }
}
