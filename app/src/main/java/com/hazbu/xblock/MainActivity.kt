package com.hazbu.xblock

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var domainCountText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var updateButton: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        domainCountText = findViewById(R.id.domain_count_text)
        lastUpdateText = findViewById(R.id.last_update_text)
        updateButton = findViewById(R.id.update_button)

        updateButton.setOnClickListener {
            downloadFilter()
        }

        refreshUI()
    }

    private fun getSafePrefs(): SharedPreferences {
        return try {
            getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (e: Exception) {
            getSharedPreferences(AdBlockUtils.PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun refreshUI() {
        val prefs = getSafePrefs()
        val domains = prefs.getStringSet(AdBlockUtils.KEY_DOMAINS, emptySet()) ?: emptySet()
        val lastUpdate = prefs.getLong(AdBlockUtils.KEY_LAST_UPDATE, 0L)

        domainCountText.text = "Domains loaded: ${domains.size}"

        if (lastUpdate > 0) {
            val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            lastUpdateText.text = "Last update: ${sdf.format(Date(lastUpdate))}"
        }
    }

    private fun downloadFilter() {
        updateButton.isEnabled = false
        val request = Request.Builder().url(AdBlockUtils.FILTER_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateButton.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                        updateButton.isEnabled = true
                    }
                    return
                }

                response.body?.string()?.let { body ->
                    val domains = AdBlockUtils.parseAdGuardFilter(body)
                    saveDomains(domains)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Updated ${domains.size} domains", Toast.LENGTH_SHORT).show()
                        refreshUI()
                        updateButton.isEnabled = true
                    }
                }
            }
        })
    }

    private fun saveDomains(domains: Set<String>) {
        getSafePrefs().edit().apply {
            putStringSet(AdBlockUtils.KEY_DOMAINS, domains)
            putLong(AdBlockUtils.KEY_LAST_UPDATE, System.currentTimeMillis())
        }.commit()
        AdBlockUtils.fixPermissions(this)
    }
}
