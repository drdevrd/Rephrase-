package com.hshospital.rephrase

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RephraseAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var bubbleView: android.view.View? = null
    private var selectedText: String = ""
    private var activeNode: AccessibilityNodeInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private lateinit var prefs: SharedPreferences

    private val tones = mapOf(
        "formal"    to "Rephrase formally and professionally. Return only the rephrased text, nothing else.",
        "casual"    to "Rephrase in a friendly, casual conversational tone. Return only the rephrased text, nothing else.",
        "medical"   to "Rephrase in precise clinical medical language. Return only the rephrased text, nothing else.",
        "simple"    to "Rephrase in very simple language a patient can understand. No jargon. Return only the rephrased text, nothing else.",
        "empathy"   to "Rephrase in a warm, empathetic tone for a doctor speaking to a worried parent. Return only the rephrased text, nothing else.",
        "concise"   to "Make this as short as possible without losing meaning. Return only the rephrased text, nothing else.",
        "email"     to "Rephrase as a polished professional email body. Return only the rephrased text, nothing else.",
        "discharge" to "Rephrase in structured clinical discharge summary language. Return only the rephrased text, nothing else.",
        "tamil"     to "Translate to simple Tamil a patient in Tamil Nadu can understand. Return only the Tamil text, nothing else.",
        "broadcast" to "Rephrase as a friendly WhatsApp broadcast from a pediatric doctor to parents. Return only the rephrased text, nothing else."
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("rephrase_prefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val source = event.source ?: return
            val text = source.text?.toString() ?: return
            val start = source.textSelectionStart
            val end = source.textSelectionEnd

            if (start >= 0 && end > start && end <= text.length) {
                selectedText = text.substring(start, end)
                activeNode = source
                handler.postDelayed({ showBubble() }, 300)
            }
        }
    }

    private fun showBubble() {
        if (selectedText.isEmpty()) return
        dismissBubble()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_bubble, null)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 200

        val buttonMap = mapOf(
            R.id.btn_formal    to "formal",
            R.id.btn_casual    to "casual",
            R.id.btn_medical   to "medical",
            R.id.btn_simple    to "simple",
            R.id.btn_empathy   to "empathy",
            R.id.btn_concise   to "concise",
            R.id.btn_email     to "email",
            R.id.btn_discharge to "discharge",
            R.id.btn_tamil     to "tamil",
            R.id.btn_broadcast to "broadcast"
        )

        val statusMsg = view.findViewById<TextView>(R.id.statusMsg)

        buttonMap.forEach { (btnId, toneKey) ->
            view.findViewById<Button>(btnId).setOnClickListener {
                statusMsg.text = "⏳ Rephrasing..."
                callClaudeApi(selectedText, tones[toneKey]!!) { rephrased ->
                    handler.post {
                        if (rephrased != null) {
                            pasteText(rephrased)
                            statusMsg.text = "✅ Done!"
                            handler.postDelayed({ dismissBubble() }, 1500)
                        } else {
                            statusMsg.text = "⚠ Failed. Try again."
                        }
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dismissBubble()
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pasteText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("rephrased", text))
        activeNode?.let { node ->
            val args = android.os.Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun callClaudeApi(text: String, prompt: String, callback: (String?) -> Unit) {
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isEmpty()) { callback(null); return }

        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 500)
            put("system", prompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
