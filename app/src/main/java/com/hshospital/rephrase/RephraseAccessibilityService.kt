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

    private val tones = mapOf(
        "formal"    to "Give exactly 3 numbered rephrasing options in formal professional tone. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "casual"    to "Give exactly 3 numbered rephrasing options in friendly casual tone. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "medical"   to "Give exactly 3 numbered rephrasing options in clinical medical language. Keep ALL drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "simple"    to "Give exactly 3 numbered rephrasing options in very simple language. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "empathy"   to "Give exactly 3 numbered rephrasing options in warm empathetic tone for a doctor speaking to a worried parent. Format:\n1. ...\n2. ...\n3. ...",
        "concise"   to "Give exactly 3 numbered rephrasing options as concisely as possible. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "natural"   to "Give exactly 3 numbered rephrasing options that sound completely natural and conversational. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "discharge" to "Give exactly 3 numbered rephrasing options in discharge summary language. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "tamil"     to "Give exactly 3 numbered Tamil translation options. Keep drug names in English. Format:\n1. ...\n2. ...\n3. ...",
        "broadcast" to "Give exactly 3 numbered rephrasing options as WhatsApp broadcast from pediatric doctor to parents. Format:\n1. ...\n2. ...\n3. ..."
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
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ showBubble() }, 300)
            }
        }
    }

    private fun parseOptions(response: String): List<String> {
        val lines = response.trim().split("\n")
        val options = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^[123][\\.\\)].*"))) {
                options.add(trimmed.substring(2).trim())
            }
        }
        // fallback — split by newline if parsing fails
        if (options.size < 2) {
            return response.trim().split("\n").filter { it.isNotEmpty() }.take(3)
        }
        return options.take(3)
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
        val optionsLayout = view.findViewById<View>(R.id.optionsLayout)
        val btnOption1 = view.findViewById<Button>(R.id.btn_option1)
        val btnOption2 = view.findViewById<Button>(R.id.btn_option2)
        val btnOption3 = view.findViewById<Button>(R.id.btn_option3)
        val btnCloseTop = view.findViewById<Button>(R.id.btn_close_top)

        mapOf(
            R.id.btn_formal    to "formal",
            R.id.btn_casual    to "casual",
            R.id.btn_medical   to "medical",
            R.id.btn_simple    to "simple",
            R.id.btn_empathy   to "empathy",
            R.id.btn_concise   to "concise",
            R.id.btn_natural   to "natural",
            R.id.btn_discharge to "discharge",
            R.id.btn_tamil     to "tamil",
            R.id.btn_broadcast to "broadcast"
        ).forEach { (btnId, toneKey) ->
            view.findViewById<Button>(btnId).setOnClickListener {
                statusMsg.text = "⏳ Rephrasing..."
                view.findViewById<View>(R.id.optionsScroll).visibility = View.GONE
                btnCloseTop.visibility = View.GONE
                callApi(selectedText, tones[toneKey]!!) { result ->
                    handler.post {
                        if (result != null) {
                            val options = parseOptions(result)
                            btnOption1.text = "1. ${options.getOrElse(0) { "" }}"
                            btnOption2.text = "2. ${options.getOrElse(1) { "" }}"
                            btnOption3.text = "3. ${options.getOrElse(2) { "" }}"
                            view.findViewById<View>(R.id.optionsScroll).visibility = View.VISIBLE
                            btnCloseTop.visibility = View.VISIBLE
                            statusMsg.text = "✅ Tap an option to use it"
                        } else {
                            statusMsg.text = "⚠ Failed. Try again."
                            btnCloseTop.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        listOf(btnOption1, btnOption2, btnOption3).forEach { btn ->
            btn.setOnClickListener {
                val text = btn.text.toString()
                    .removePrefix("1. ")
                    .removePrefix("2. ")
                    .removePrefix("3. ")
                paste(text)
                dismissBubble()
            }
        }

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
            put("model", "claude-sonnet-4-5-20250929")
            put("max_tokens", 1000)
            put("system", prompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", "$text Rephrase this sentence.")
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
