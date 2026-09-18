package com.hshospital.rephrase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("rephrase_prefs", Context.MODE_PRIVATE)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val saveApiKeyBtn = findViewById<Button>(R.id.saveApiKeyBtn)
        val rbGemini = findViewById<RadioButton>(R.id.rbGemini)
        val rbClaude = findViewById<RadioButton>(R.id.rbClaude)
        val rbOpenAI = findViewById<RadioButton>(R.id.rbOpenAI)
        val saveProviderBtn = findViewById<Button>(R.id.saveProviderBtn)
        val accessibilityBtn = findViewById<Button>(R.id.accessibilityBtn)
        val askAiPromptInput = findViewById<EditText>(R.id.askAiPromptInput)
        val saveAskAiBtn = findViewById<Button>(R.id.saveAskAiBtn)
        val customName1 = findViewById<EditText>(R.id.customName1)
        val customPrompt1 = findViewById<EditText>(R.id.customPrompt1)
        val customName2 = findViewById<EditText>(R.id.customName2)
        val customPrompt2 = findViewById<EditText>(R.id.customPrompt2)
        val customName3 = findViewById<EditText>(R.id.customName3)
        val customPrompt3 = findViewById<EditText>(R.id.customPrompt3)

        // Load saved values
        askAiPromptInput.setText(prefs.getString("ask_ai_prompt", ""))
        customName1.setText(prefs.getString("custom_name_1", ""))
        customPrompt1.setText(prefs.getString("custom_prompt_1", ""))
        customName2.setText(prefs.getString("custom_name_2", ""))
        customPrompt2.setText(prefs.getString("custom_prompt_2", ""))
        customName3.setText(prefs.getString("custom_name_3", ""))
        customPrompt3.setText(prefs.getString("custom_prompt_3", ""))

        // Load saved provider
        val savedProvider = prefs.getString("api_provider", "gemini") ?: "gemini"
        when (savedProvider) {
            "claude" -> rbClaude.isChecked = true
            "openai" -> rbOpenAI.isChecked = true
            else -> rbGemini.isChecked = true
        }

        // Per-provider key helpers
        fun keyPrefName(provider: String) = "api_key_$provider"
        fun currentProvider(): String = when {
            rbClaude.isChecked -> "claude"
            rbOpenAI.isChecked -> "openai"
            else -> "gemini"
        }
        fun loadKeyFor(provider: String) {
            // Migrate the old single shared key into whichever provider was active at upgrade time
            val legacy = prefs.getString("api_key", "") ?: ""
            if (legacy.isNotEmpty() && provider == savedProvider &&
                (prefs.getString(keyPrefName(provider), "") ?: "").isEmpty()) {
                prefs.edit().putString(keyPrefName(provider), legacy).remove("api_key").apply()
            }
            apiKeyInput.setText(prefs.getString(keyPrefName(provider), ""))
        }

        loadKeyFor(savedProvider)

        // Swap the visible key field whenever the provider selection changes
        rbGemini.setOnCheckedChangeListener { _, checked -> if (checked) loadKeyFor("gemini") }
        rbClaude.setOnCheckedChangeListener { _, checked -> if (checked) loadKeyFor("claude") }
        rbOpenAI.setOnCheckedChangeListener { _, checked -> if (checked) loadKeyFor("openai") }

        saveApiKeyBtn.setOnClickListener {
            val provider = currentProvider()
            val key = apiKeyInput.text.toString().trim()
            prefs.edit().putString(keyPrefName(provider), key).apply()
            Toast.makeText(this, "API key saved for $provider", Toast.LENGTH_SHORT).show()
        }

        saveProviderBtn.setOnClickListener {
            val provider = currentProvider()
            prefs.edit().putString("api_provider", provider).apply()
            Toast.makeText(this, "Provider saved: $provider", Toast.LENGTH_SHORT).show()
        }

        accessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        saveAskAiBtn.setOnClickListener {
            prefs.edit().putString("ask_ai_prompt", askAiPromptInput.text.toString().trim()).apply()
            Toast.makeText(this, "Question saved!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.saveCustom1).setOnClickListener {
            prefs.edit()
                .putString("custom_name_1", customName1.text.toString().trim())
                .putString("custom_prompt_1", customPrompt1.text.toString().trim())
                .apply()
            Toast.makeText(this, "Custom tone 1 saved!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.deleteCustom1).setOnClickListener {
            prefs.edit().putString("custom_name_1", "").putString("custom_prompt_1", "").apply()
            customName1.setText(""); customPrompt1.setText("")
            Toast.makeText(this, "Custom tone 1 deleted!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.saveCustom2).setOnClickListener {
            prefs.edit()
                .putString("custom_name_2", customName2.text.toString().trim())
                .putString("custom_prompt_2", customPrompt2.text.toString().trim())
                .apply()
            Toast.makeText(this, "Custom tone 2 saved!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.deleteCustom2).setOnClickListener {
            prefs.edit().putString("custom_name_2", "").putString("custom_prompt_2", "").apply()
            customName2.setText(""); customPrompt2.setText("")
            Toast.makeText(this, "Custom tone 2 deleted!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.saveCustom3).setOnClickListener {
            prefs.edit()
                .putString("custom_name_3", customName3.text.toString().trim())
                .putString("custom_prompt_3", customPrompt3.text.toString().trim())
                .apply()
            Toast.makeText(this, "Custom tone 3 saved!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.deleteCustom3).setOnClickListener {
            prefs.edit().putString("custom_name_3", "").putString("custom_prompt_3", "").apply()
            customName3.setText(""); customPrompt3.setText("")
            Toast.makeText(this, "Custom tone 3 deleted!", Toast.LENGTH_SHORT).show()
        }
    }
}
