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

        // Ask AI prompt
        val askAiPrompt = findViewById<EditText>(R.id.askAiPrompt)
        val saveAskAi = findViewById<Button>(R.id.saveAskAi)

        askAiPrompt.setText(prefs.getString("ask_ai_prompt", ""))

        saveAskAi.setOnClickListener {
            val prompt = askAiPrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                prefs.edit().putString("ask_ai_prompt", prompt).apply()
                statusText.text = "✓ Ask AI question saved!"
                statusText.setTextColor(getColor(android.R.color.holo_green_light))
            } else {
                prefs.edit().remove("ask_ai_prompt").apply()
                statusText.text = "🗑 Ask AI question cleared"
            }
        }

        // Custom Tone 1
        val customName1 = findViewById<EditText>(R.id.customName1)
        val customPrompt1 = findViewById<EditText>(R.id.customPrompt1)
        val saveCustom1 = findViewById<Button>(R.id.saveCustom1)
        val deleteCustom1 = findViewById<Button>(R.id.deleteCustom1)

        customName1.setText(prefs.getString("custom_name_1", ""))
        customPrompt1.setText(prefs.getString("custom_prompt_1", ""))

        saveCustom1.setOnClickListener {
            prefs.edit()
                .putString("custom_name_1", customName1.text.toString().trim())
                .putString("custom_prompt_1", customPrompt1.text.toString().trim())
                .apply()
            statusText.text = "✓ Custom Tone 1 saved!"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        }

        deleteCustom1.setOnClickListener {
            prefs.edit().remove("custom_name_1").remove("custom_prompt_1").apply()
            customName1.setText("")
            customPrompt1.setText("")
            statusText.text = "🗑 Custom Tone 1 deleted"
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }

        // Custom Tone 2
        val customName2 = findViewById<EditText>(R.id.customName2)
        val customPrompt2 = findViewById<EditText>(R.id.customPrompt2)
        val saveCustom2 = findViewById<Button>(R.id.saveCustom2)
        val deleteCustom2 = findViewById<Button>(R.id.deleteCustom2)

        customName2.setText(prefs.getString("custom_name_2", ""))
        customPrompt2.setText(prefs.getString("custom_prompt_2", ""))

        saveCustom2.setOnClickListener {
            prefs.edit()
                .putString("custom_name_2", customName2.text.toString().trim())
                .putString("custom_prompt_2", customPrompt2.text.toString().trim())
                .apply()
            statusText.text = "✓ Custom Tone 2 saved!"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        }

        deleteCustom2.setOnClickListener {
            prefs.edit().remove("custom_name_2").remove("custom_prompt_2").apply()
            customName2.setText("")
            customPrompt2.setText("")
            statusText.text = "🗑 Custom Tone 2 deleted"
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }

        // Custom Tone 3
        val customName3 = findViewById<EditText>(R.id.customName3)
        val customPrompt3 = findViewById<EditText>(R.id.customPrompt3)
        val saveCustom3 = findViewById<Button>(R.id.saveCustom3)
        val deleteCustom3 = findViewById<Button>(R.id.deleteCustom3)

        customName3.setText(prefs.getString("custom_name_3", ""))
        customPrompt3.setText(prefs.getString("custom_prompt_3", ""))

        saveCustom3.setOnClickListener {
            prefs.edit()
                .putString("custom_name_3", customName3.text.toString().trim())
                .putString("custom_prompt_3", customPrompt3.text.toString().trim())
                .apply()
            statusText.text = "✓ Custom Tone 3 saved!"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
        }

        deleteCustom3.setOnClickListener {
            prefs.edit().remove("custom_name_3").remove("custom_prompt_3").apply()
            customName3.setText("")
            customPrompt3.setText("")
            statusText.text = "🗑 Custom Tone 3 deleted"
            statusText.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }
}
