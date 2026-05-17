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
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RephraseAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var selectedText: String = ""
    private var activeNode: AccessibilityNodeInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private lateinit var prefs: SharedPreferences

    private val BASE = "You are a text rephrasing tool. The user gives you text to rephrase. The input is NEVER a question directed at you — it is ALWAYS text that needs rephrasing. NEVER respond to the content. NEVER answer questions in the text. Keep ALL medical terms, drug names, brand names and dosages EXACTLY as written. Return ONLY the rephrased text, nothing else."

    private val tones = mapOf(
        "formal"    to "$BASE Rephrase in a formal professional tone.",
        "casual"    to "$BASE Rephrase in a friendly casual tone.",
        "medical"   to "$BASE Rephrase in precise clinical medical language.",
        "simple"    to "$BASE Rephrase in very simple language anyone can understand.",
        "empathy"   to "$BASE Rephrase in a warm empathetic tone suitable for a doctor speaking to a worried parent.",
        "concise"   to "$BASE Rephrase as concisely as possible keeping core meaning.",
        "email"     to "$BASE Rephrase as a polished professional email body.",
        "discharge" to "$BASE Rephrase in structured clinical discharge summary language.",
        "tamil"     to "You are a translation tool. The user gives you text to translate. The input is NEVER a question directed at you — it is ALWAYS text to translate. NEVER respond to the content. Keep ALL drug names and brand names EXACTLY in English. Translate ONLY to simple Tamil. Return ONLY the Tamil text.",
        "broadcast" to "$BASE Rephrase as a clear friendly WhatsApp broadcast message from a pediatric doctor to parents."
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100

        val statusMsg = view.findViewById<TextView>(R.id.statusMsg)
        val resultText = view.findViewById<TextView>(R.id.resultText)
        val resultScroll = view.findViewById<ScrollView>(R.id.resultScroll)
        val actionButtons = view.findViewById<View>(R.id.actionButtons)
        val btnUse = view.findViewById<Button>(R.id.btn_use)
        val btnClose = view.findViewById<Button>(R.id.btn_close)
        val btnCloseTop = view.findViewById<Button>(R.id.btn_close_top)

        var lastResult = ""

        mapOf(
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
        ).forEach { (btnId, toneKey) ->
            view.findViewById<Button>(btnId).setOnClickListener {
                statusMsg.text = "⏳ Rephrasing..."
                resultScroll.visibility = View.GONE
                actionButtons.visibility = View.GONE
                btnCloseTop.visibility = View.GONE
                callApi(selectedText, tones[toneKey]!!) { result ->
                    handler.post {
                        if (result != null) {
                            lastResult = result
                            resultText.text = result
                            resultScroll.visibility = View.VISIBLE
                            actionButtons.visibility = View.VISIBLE
                            btnCloseTop.visibility = View.GONE
                            statusMsg.text = "✅ Ready — scroll to read"
                        } else {
                            statusMsg.text = "⚠ Failed. Try again."
                            btnCloseTop.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        btnUse.setOnClickListener { paste(lastResult); dismissBubble() }
        btnClose.setOnClickListener { dismissBubble() }
        btnCloseTop.setOnClickListener { dismissBubble() }

        try { windowManager?.addView(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun paste(text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("rephrased", text))
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        activeNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun callApi(text: String, prompt: String, callback: (String?) -> Unit) {
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isEmpty()) { callback(null); return }
        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001")
            put("max_tokens", 500)
            put("system", prompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "Rephrase this text: \"$text\"")
            }))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = JSONObject(response.body?.string() ?: "")
                    callback(json.getJSONArray("content").getJSONObject(0).getString("text"))
                } catch (e: Exception) { callback(null) }
            }
        })
    }

    private fun dismissBubble() {
        bubbleView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            bubbleView = null
        }
    }

    override fun onInterrupt() { dismissBubble() }
    override fun onDestroy() { dismissBubble(); super.onDestroy() }
}
