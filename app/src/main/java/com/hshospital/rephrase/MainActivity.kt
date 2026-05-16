package com.hshospital.rephrase

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("rephrase_prefs", MODE_PRIVATE)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val saveKeyBtn = findViewById<Button>(R.id.saveKeyBtn)
        val statusText = findViewById<TextView>(R.id.statusText)
        val openAccessibilityBtn = findViewById<Button>(R.id.openAccessibilityBtn)

        val savedKey = prefs.getString("api_key", "") ?: ""
        if (savedKey.isNotEmpty()) {
            apiKeyInput.setText(savedKey)
            statusText.text = "✓ API key saved"
        }

        saveKeyBtn.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isNotEmpty()) {
                prefs.edit().putString("api_key", key).apply()
                statusText.text = "✓ API key saved successfully!"
            } else {
                statusText.text = "⚠ Please enter your API key"
                statusText.setTextColor(getColor(android.R.color.holo_red_light))
            }
        }

        openAccessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
