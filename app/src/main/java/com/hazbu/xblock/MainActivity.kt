package com.hazbu.xblock

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.work.*
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.hazbu.xblock.Constants.KEY_DOMAINS
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_ADGUARD
import com.hazbu.xblock.Constants.KEY_IS_DOWNLOADING_EXODUS
import com.hazbu.xblock.Constants.KEY_LAST_UPDATE_ADGUARD
import com.hazbu.xblock.Constants.KEY_LAST_UPDATE_EXODUS
import com.hazbu.xblock.Constants.KEY_PACKAGES
import com.hazbu.xblock.Constants.PREFS_NAME
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var adguardStatus: TextView
    private lateinit var exodusStatus: TextView
    private lateinit var btnAdGuard: Button
    private lateinit var btnExodus: Button
    private lateinit var btnDeleteAdGuard: Button
    private lateinit var btnDeleteExodus: Button
    private lateinit var tvTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        setupUI()
        refreshUI()
    }

    private fun setupWindowInsets() {
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
    }

    private fun setupUI() {
        adguardStatus = findViewById(R.id.adguard_status)
        exodusStatus = findViewById(R.id.exodus_status)
        btnAdGuard = findViewById(R.id.btn_update_adguard)
        btnExodus = findViewById(R.id.btn_update_exodus)
        btnDeleteAdGuard = findViewById(R.id.btn_delete_adguard)
        btnDeleteExodus = findViewById(R.id.btn_delete_exodus)
        tvTitle = findViewById(R.id.title)

        setupTitleSpannable()

        btnAdGuard.setOnClickListener { triggerUpdate(FilterUpdateWorker.TYPE_ADGUARD) }
        btnExodus.setOnClickListener { triggerUpdate(FilterUpdateWorker.TYPE_EXODUS) }

        btnDeleteAdGuard.setOnClickListener {
            deleteFilter(KEY_DOMAINS, KEY_LAST_UPDATE_ADGUARD)
        }
        btnDeleteExodus.setOnClickListener {
            deleteFilter(KEY_PACKAGES, KEY_LAST_UPDATE_EXODUS)
        }
    }

    private fun setupTitleSpannable() {
        val titleText = tvTitle.text.toString()
        val spannable = SpannableStringBuilder(titleText)
        
        val primaryColor = MaterialColors.getColor(
            this, 
            androidx.appcompat.R.attr.colorPrimary,
            "#FF6200EE".toColorInt()
        )

        spannable.setSpan(
            ForegroundColorSpan(primaryColor),
            0, 1, // index of 'x'
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvTitle.text = spannable
    }

    private fun deleteFilter(vararg keys: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            keys.forEach { remove(it) }
        }.apply()
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

        // AdGuard
        val isDownloadingAdGuard = prefs.getBoolean(KEY_IS_DOWNLOADING_ADGUARD, false)
        if (isDownloadingAdGuard) {
            adguardStatus.text = getString(R.string.status_updating)
            btnAdGuard.isEnabled = false
        } else {
            val domains = prefs.getStringSet(KEY_DOMAINS, emptySet()) ?: emptySet()
            val lastUpdateAdGuard = prefs.getLong(KEY_LAST_UPDATE_ADGUARD, 0L)
            val adguardDateStr = if (lastUpdateAdGuard > 0) sdf.format(Date(lastUpdateAdGuard)) else getString(R.string.never)
            adguardStatus.text = getString(R.string.count_label, domains.size) + "\n" + getString(R.string.last_label, adguardDateStr)
            btnAdGuard.isEnabled = true
        }

        // Exodus
        val isDownloadingExodus = prefs.getBoolean(KEY_IS_DOWNLOADING_EXODUS, false)
        if (isDownloadingExodus) {
            exodusStatus.text = getString(R.string.status_updating)
            btnExodus.isEnabled = false
        } else {
            val packages = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
            val lastUpdateExodus = prefs.getLong(KEY_LAST_UPDATE_EXODUS, 0L)
            val exodusDateStr = if (lastUpdateExodus > 0) sdf.format(Date(lastUpdateExodus)) else getString(R.string.never)
            exodusStatus.text = getString(R.string.count_label, packages.size) + "\n" + getString(R.string.last_label, exodusDateStr)
            btnExodus.isEnabled = true
        }
    }
}
