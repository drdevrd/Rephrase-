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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class RephraseAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var fabView: View? = null
    private var activeNode: AccessibilityNodeInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    // Many Indian mobile networks have a broken/black-holed IPv6 route to Google's API endpoints:
    // the first connection attempt silently stalls until connectTimeout, and only the fallback
    // (IPv4) attempt succeeds. Forcing IPv4-only here removes that stall entirely.
    private val ipv4OnlyDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            return if (v4.isNotEmpty()) v4 else all
        }
    }
    private val client = OkHttpClient.Builder()
        .dns(ipv4OnlyDns)
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var prefs: SharedPreferences
    @Volatile private var lastApiError: String = ""
    @Volatile private var lastCallMs: Long = 0
    @Volatile private var lastModelUsed: String = ""
    private var isDisabledForApp = false
    private var isKeyboardVisible = false

    companion object {
        var isEnabled = true
    }

    private val blockedApps = setOf(
        "com.csam.icici.bank.imobile",
        "com.axis.mobile",
        "com.sbi.lotusintouch",
        "net.one97.paytm",
        "com.hdfc.wallet",
        "com.kotak.mahindra.kotak_mahindra",
        "com.rbl.rblmobilebanking",
        "com.indusind.mobile",
        "com.idbi.mpassbook",
        "com.sc.bmw.in",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "in.amazon.mShop.android.shopping",
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.airtel.money",
        "com.jio.jiopay",
        "com.bhim.axispay",
        "com.dreamplug.androidapp",
        "in.org.npci.upiapp",
        "com.whatsapp.w4b",
        "com.sbi.SBIFreedomPlus",
        "com.yesbank",
        "com.snapwork.hdfc",
        "com.upi.axispay",
        "com.amazon.mShop.android.shopping"
    )

    private val tones = mapOf(
        "formal"    to "Give exactly 3 numbered rephrasing options in formal professional tone. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "casual"    to "Give exactly 3 numbered rephrasing options in friendly casual tone. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "medical"   to "Give exactly 3 numbered rephrasing options in clinical medical language. Keep ALL drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "simple"    to "Give exactly 3 numbered rephrasing options in very simple language. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "concise"   to "Give exactly 3 numbered rephrasing options as concisely as possible. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "natural"   to "Give exactly 3 numbered rephrasing options that sound completely natural. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "discharge" to "Give exactly 3 numbered rephrasing options in discharge summary language. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ...",
        "tamil"     to "Give exactly 3 numbered Tamil translation options. Keep drug names in English. Format:\n1. ...\n2. ...\n3. ...",
        "email"     to "Give exactly 3 numbered rephrasing options as polished professional email body. Keep drug names exactly. Format:\n1. ...\n2. ...\n3. ..."
    )

    private val grammarPrompt = "You are a grammar checker. Check the given text for grammar errors. Respond in exactly this format:\nCORRECTED: [the corrected sentence]\nEXPLANATION: [brief explanation of what was wrong, or No errors found if correct]"

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("rephrase_prefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isEnabled) return
        val pkg = event?.packageName?.toString() ?: ""
        if (blockedApps.contains(pkg)) {
            if (!isDisabledForApp) {
                isDisabledForApp = true
                dismissBubble()
                dismissFab()
            }
            return
        } else {
            if (isDisabledForApp) isDisabledForApp = false
        }
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source ?: return
                if (source.isEditable) {
                    activeNode = source
                    isKeyboardVisible = true
                    handler.postDelayed({ showFab() }, 500)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                try {
                    val root = rootInActiveWindow ?: return
                    val focusedNode = findFocusedEditableNode(root)
                    if (focusedNode != null) {
                        activeNode = focusedNode
                        if (!isKeyboardVisible) {
                            isKeyboardVisible = true
                            handler.postDelayed({ showFab() }, 500)
                        }
                    } else {
                        if (isKeyboardVisible) {
                            isKeyboardVisible = false
                            handler.postDelayed({ dismissFab() }, 300)
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    private fun getTextFromField(): String {
        activeNode?.let { node ->
            val text = node.text?.toString()
            if (!text.isNullOrEmpty()) return text
        }
        try {
            val root = rootInActiveWindow ?: return ""
            val node = findFocusedEditableNode(root)
            if (node != null) {
                activeNode = node
                val text = node.text?.toString()
                if (!text.isNullOrEmpty()) return text
            }
        } catch (e: Exception) {}
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    private fun showFab() {
        if (!isEnabled) return
        if (fabView != null) return
        val inflater = LayoutInflater.from(this)
        val fabLayout = inflater.inflate(R.layout.floating_button, null)
        fabView = fabLayout
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = prefs.getInt("fab_x", 16)
        params.y = prefs.getInt("fab_y", 300)
        val btn = fabLayout.findViewById<Button>(R.id.fab_rephrase)
        var startRawX = 0f
        var startRawY = 0f
        var startPX = 0
        var startPY = 0
        var isDragging = false
        btn.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                    startPX = params.x
                    startPY = params.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX: Float = ev.rawX - startRawX
                    val deltaY: Float = ev.rawY - startRawY
                    val absDX: Float = if (deltaX < 0f) -deltaX else deltaX
                    val absDY: Float = if (deltaY < 0f) -deltaY else deltaY
                    if (absDX > 10f || absDY > 10f) {
                        isDragging = true
                        params.x = startPX - deltaX.toInt()
                        params.y = startPY - deltaY.toInt()
                        try {
                            windowManager?.updateViewLayout(fabLayout, params)
                            prefs.edit().putInt("fab_x", params.x).putInt("fab_y", params.y).apply()
                        } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val text = getTextFromField()
                        if (text.isNotEmpty()) {
                            showBubble(text)
                        } else {
                            Toast.makeText(this, "No text found. Type something first!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        try { windowManager?.addView(fabLayout, params) } catch (e: Exception) {}
    }

    private fun dismissFab() {
        fabView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            fabView = null
        }
    }

    private fun parseOptions(response: String): List<String> {
        val lines = response.trim().split("\n")
        val options = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length > 2 && (trimmed[0] == '1' || trimmed[0] == '2' || trimmed[0] == '3') && (trimmed[1] == '.' || trimmed[1] == ')')) {
                options.add(trimmed.substring(2).trim())
            }
        }
        if (options.size < 2) {
            return response.trim().split("\n").filter { it.isNotEmpty() }.take(3)
        }
        return options.take(3)
    }

    private fun parseGrammar(response: String): Pair<String, String> {
        val corrected = Regex("CORRECTED:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)?.trim() ?: response.trim()
        val explanation = Regex("EXPLANATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)?.trim() ?: ""
        return Pair(corrected, explanation)
    }

    private fun showBubble(inputText: String) {
        if (inputText.isEmpty()) return
        dismissBubble()
        val inflater = LayoutInflater.from(this)
        val bubLayout = inflater.inflate(R.layout.floating_bubble, null)
        bubbleView = bubLayout
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100
        val statusMsg = bubLayout.findViewById<TextView>(R.id.statusMsg)
        val optionsScroll = bubLayout.findViewById<View>(R.id.optionsScroll)
        val btnOption1 = bubLayout.findViewById<Button>(R.id.btn_option1)
        val btnOption2 = bubLayout.findViewById<Button>(R.id.btn_option2)
        val btnOption3 = bubLayout.findViewById<Button>(R.id.btn_option3)
        val btnCloseTop = bubLayout.findViewById<Button>(R.id.btn_close_top)
        val customTonesRow = bubLayout.findViewById<LinearLayout>(R.id.customTonesRow)
        val btnCustom1 = bubLayout.findViewById<Button>(R.id.btn_custom1)
        val btnCustom2 = bubLayout.findViewById<Button>(R.id.btn_custom2)
        val btnCustom3 = bubLayout.findViewById<Button>(R.id.btn_custom3)
        val grammarScroll = bubLayout.findViewById<View>(R.id.grammarScroll)
        val grammarCorrected = bubLayout.findViewById<TextView>(R.id.grammarCorrected)
        val grammarExplanation = bubLayout.findViewById<TextView>(R.id.grammarExplanation)
        val btnUseGrammar = bubLayout.findViewById<Button>(R.id.btn_use_grammar)
        val askAiScroll = bubLayout.findViewById<View>(R.id.askAiScroll)
        val askAiResult = bubLayout.findViewById<TextView>(R.id.askAiResult)
        val customName1 = prefs.getString("custom_name_1", "") ?: ""
        val customPrompt1 = prefs.getString("custom_prompt_1", "") ?: ""
        val customName2 = prefs.getString("custom_name_2", "") ?: ""
        val customPrompt2 = prefs.getString("custom_prompt_2", "") ?: ""
        val customName3 = prefs.getString("custom_name_3", "") ?: ""
        val customPrompt3 = prefs.getString("custom_prompt_3", "") ?: ""
        val askAiPrompt = prefs.getString("ask_ai_prompt", "") ?: ""
        if (customName1.isNotEmpty() || customName2.isNotEmpty() || customName3.isNotEmpty()) {
            customTonesRow.visibility = View.VISIBLE
            if (customName1.isNotEmpty()) btnCustom1.text = "* $customName1" else btnCustom1.visibility = View.GONE
            if (customName2.isNotEmpty()) btnCustom2.text = "* $customName2" else btnCustom2.visibility = View.GONE
            if (customName3.isNotEmpty()) btnCustom3.text = "* $customName3" else btnCustom3.visibility = View.GONE
        }
        fun resetResults() {
            optionsScroll.visibility = View.GONE
            grammarScroll.visibility = View.GONE
            btnUseGrammar.visibility = View.GONE
            askAiScroll.visibility = View.GONE
            btnCloseTop.visibility = View.GONE
        }
        fun pasteResult(text: String) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("rephrased", text))
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = activeNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ?: false
            if (!success) {
                activeNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
            handler.post {
                Toast.makeText(this, "Copied! Long press to paste if needed.", Toast.LENGTH_SHORT).show()
            }
        }
        fun rephrase(prompt: String) {
            statusMsg.text = "Rephrasing..."
            resetResults()
            callApi(inputText, prompt, false) { result ->
                handler.post {
                    if (result != null) {
                        val options = parseOptions(result)
                        btnOption1.text = "1. ${options.getOrElse(0) { "" }}"
                        btnOption2.text = "2. ${options.getOrElse(1) { "" }}"
                        btnOption3.text = "3. ${options.getOrElse(2) { "" }}"
                        optionsScroll.visibility = View.VISIBLE
                        btnCloseTop.visibility = View.VISIBLE
                        statusMsg.text = "Tap an option to use it (${lastCallMs}ms${if (lastModelUsed.isNotEmpty()) " · " + lastModelUsed else ""})"
                    } else {
                        statusMsg.text = if (lastApiError.isNotEmpty()) lastApiError else "Failed. Check API key & provider in settings."
                        btnCloseTop.visibility = View.VISIBLE
                    }
                }
            }
        }
        fun checkGrammar() {
            statusMsg.text = "Checking grammar..."
            resetResults()
            callApi(inputText, grammarPrompt, false) { result ->
                handler.post {
                    if (result != null) {
                        val pair = parseGrammar(result)
                        val corrected = pair.first
                        val explanation = pair.second
                        grammarCorrected.text = corrected
                        grammarExplanation.text = explanation
                        grammarScroll.visibility = View.VISIBLE
                        btnUseGrammar.visibility = View.VISIBLE
                        btnCloseTop.visibility = View.VISIBLE
                        statusMsg.text = "Grammar check done (${lastCallMs}ms${if (lastModelUsed.isNotEmpty()) " · " + lastModelUsed else ""})"
                        btnUseGrammar.setOnClickListener {
                            pasteResult(corrected)
                            dismissBubble()
                        }
                    } else {
                        statusMsg.text = if (lastApiError.isNotEmpty()) lastApiError else "Failed. Check API key & provider in settings."
                        btnCloseTop.visibility = View.VISIBLE
                    }
                }
            }
        }
        fun askAi(prompt: String) {
            statusMsg.text = "Asking AI..."
            resetResults()
            callApi(inputText, prompt, true) { result ->
                handler.post {
                    if (result != null) {
                        askAiResult.text = result
                        askAiScroll.visibility = View.VISIBLE
                        btnCloseTop.visibility = View.VISIBLE
                        statusMsg.text = "AI Response (${lastCallMs}ms${if (lastModelUsed.isNotEmpty()) " · " + lastModelUsed else ""})"
                    } else {
                        statusMsg.text = if (lastApiError.isNotEmpty()) lastApiError else "Failed. Check API key & provider in settings."
                        btnCloseTop.visibility = View.VISIBLE
                    }
                }
            }
        }
        bubLayout.findViewById<Button>(R.id.btn_formal).setOnClickListener { rephrase(tones["formal"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_casual).setOnClickListener { rephrase(tones["casual"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_medical).setOnClickListener { rephrase(tones["medical"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_simple).setOnClickListener { rephrase(tones["simple"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_concise).setOnClickListener { rephrase(tones["concise"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_natural).setOnClickListener { rephrase(tones["natural"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_discharge).setOnClickListener { rephrase(tones["discharge"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_tamil).setOnClickListener { rephrase(tones["tamil"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_formal2).setOnClickListener { rephrase(tones["email"]!!) }
        bubLayout.findViewById<Button>(R.id.btn_grammar).setOnClickListener { checkGrammar() }
        bubLayout.findViewById<Button>(R.id.btn_ask_ai).setOnClickListener {
            if (askAiPrompt.isNotEmpty()) askAi(askAiPrompt)
            else {
                statusMsg.text = "Set your question in RePhrase settings first!"
                btnCloseTop.visibility = View.VISIBLE
            }
        }
        if (customPrompt1.isNotEmpty()) btnCustom1.setOnClickListener {
            rephrase("Give exactly 3 numbered rephrasing options. $customPrompt1 Format:\n1. ...\n2. ...\n3. ...")
        }
        if (customPrompt2.isNotEmpty()) btnCustom2.setOnClickListener {
            rephrase("Give exactly 3 numbered rephrasing options. $customPrompt2 Format:\n1. ...\n2. ...\n3. ...")
        }
        if (customPrompt3.isNotEmpty()) btnCustom3.setOnClickListener {
            rephrase("Give exactly 3 numbered rephrasing options. $customPrompt3 Format:\n1. ...\n2. ...\n3. ...")
        }
        btnOption1.setOnClickListener {
            var t = btnOption1.text.toString()
            if (t.startsWith("1. ")) t = t.substring(3)
            pasteResult(t); dismissBubble()
        }
        btnOption2.setOnClickListener {
            var t = btnOption2.text.toString()
            if (t.startsWith("2. ")) t = t.substring(3)
            pasteResult(t); dismissBubble()
        }
        btnOption3.setOnClickListener {
            var t = btnOption3.text.toString()
            if (t.startsWith("3. ")) t = t.substring(3)
            pasteResult(t); dismissBubble()
        }
        btnCloseTop.setOnClickListener { dismissBubble() }
        try { windowManager?.addView(bubLayout, params) } catch (e: Exception) {}
    }

    // Unified API caller — picks provider from prefs
    private fun callApi(text: String, prompt: String, isDirect: Boolean, callback: (String?) -> Unit) {
        lastApiError = ""
        lastModelUsed = ""
        val callStart = System.currentTimeMillis()
        val timedCallback: (String?) -> Unit = { result ->
            lastCallMs = System.currentTimeMillis() - callStart
            callback(result)
        }
        val provider = prefs.getString("api_provider", "gemini") ?: "gemini"
        // Per-provider key, falling back to the old shared key for installs not yet migrated
        var key = prefs.getString("api_key_$provider", "") ?: ""
        if (key.isEmpty()) key = prefs.getString("api_key", "") ?: ""
        if (key.isEmpty()) {
            handler.post { Toast.makeText(this, "No API key set for $provider! Open RePhrase settings.", Toast.LENGTH_LONG).show() }
            callback(null); return
        }
        val userContent = if (isDirect) "$prompt\n\nText: $text" else "Rephrase this exact text as instructed: [$text]"
        when (provider) {
            "claude" -> callClaude(key, prompt, userContent, isDirect, timedCallback)
            "openai" -> callOpenAI(key, prompt, userContent, isDirect, timedCallback)
            else -> callGemini(key, prompt, userContent, isDirect, timedCallback)
        }
    }

    // Shows the real failure reason (HTTP code + provider error message) instead of a generic "Failed" toast
    private fun reportApiError(provider: String, response: Response, bodyStr: String) {
        val code = response.code
        val shortMsg = try {
            val json = JSONObject(bodyStr)
            when {
                json.has("error") && json.get("error") is JSONObject -> {
                    val err = json.getJSONObject("error")
                    val base = err.optString("message", bodyStr)
                    val det = err.optJSONArray("details")?.toString() ?: ""
                    if (det.isNotEmpty()) "$base | $det" else base
                }
                json.has("error") -> json.optString("error", bodyStr)
                else -> bodyStr
            }
        } catch (e: Exception) { bodyStr }
        val trimmed = if (shortMsg.length > 400) shortMsg.substring(0, 400) + "…" else shortMsg
        lastApiError = "$provider $code: $trimmed"
        handler.post {
            Toast.makeText(this, "$provider error $code: $trimmed", Toast.LENGTH_LONG).show()
        }
    }

    private fun callClaude(key: String, prompt: String, userContent: String, isDirect: Boolean, callback: (String?) -> Unit) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", if (isDirect) userContent else "$prompt\n\n$userContent"))
        val body = JSONObject()
        body.put("model", "claude-haiku-4-5-20251001")
        body.put("max_tokens", 1000)
        if (!isDirect) body.put("system", prompt)
        body.put("messages", messages)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                lastApiError = "Claude network error: ${e.message}"
                handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) { reportApiError("Claude", response, bodyStr); callback(null); return }
                try {
                    val json = JSONObject(bodyStr)
                    callback(json.getJSONArray("content").getJSONObject(0).getString("text"))
                } catch (e: Exception) {
                    lastApiError = "Claude: unexpected response format"
                    handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                    callback(null)
                }
            }
        })
    }

    private fun callOpenAI(key: String, prompt: String, userContent: String, isDirect: Boolean, callback: (String?) -> Unit) {
        val messages = JSONArray()
        if (!isDirect) messages.put(JSONObject().put("role", "system").put("content", prompt))
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        val body = JSONObject()
        body.put("model", "gpt-4o-mini")
        body.put("max_tokens", 1000)
        body.put("messages", messages)
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                lastApiError = "OpenAI network error: ${e.message}"
                handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) { reportApiError("OpenAI", response, bodyStr); callback(null); return }
                try {
                    val json = JSONObject(bodyStr)
                    callback(json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
                } catch (e: Exception) {
                    lastApiError = "OpenAI: unexpected response format"
                    handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                    callback(null)
                }
            }
        })
    }

    private fun callGemini(key: String, prompt: String, userContent: String, isDirect: Boolean, callback: (String?) -> Unit) {
        val startedAt = System.currentTimeMillis()
        geminiAttempt(key, prompt, userContent, isDirect, callback, attempt = 1, modelIndex = 0, startedAt = startedAt)
    }

    // Fast Lite model first (auto-updating alias); full Flash as fallback if the alias 404s
    private val geminiModels = listOf("gemini-flash-lite-latest", "gemini-3.6-flash")

    // One retry on network failure/busy; on 400 resend once without generationConfig
    private fun geminiAttempt(
        key: String, prompt: String, userContent: String, isDirect: Boolean,
        callback: (String?) -> Unit, attempt: Int, modelIndex: Int, startedAt: Long,
        useGenConfig: Boolean = true
    ) {
        val model = geminiModels[modelIndex]
        lastModelUsed = model
        val maxAttempts = 2
        val fullText = if (isDirect) userContent else "$prompt\n\n$userContent"
        val parts = JSONArray().put(JSONObject().put("text", fullText))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val body = JSONObject().put("contents", contents)
        if (useGenConfig) body.put("generationConfig", JSONObject().put("maxOutputTokens", 300))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        fun elapsed() = "${(System.currentTimeMillis() - startedAt)}ms"

        fun retryLater(reason: String) {
            handler.post {
                Toast.makeText(this@RephraseAccessibilityService,
                    "Gemini $reason after ${elapsed()} — retrying…", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed({
                geminiAttempt(key, prompt, userContent, isDirect, callback, attempt + 1, modelIndex, startedAt, useGenConfig)
            }, 800L)
        }

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < maxAttempts) { retryLater("timeout"); return }
                lastApiError = "Gemini network error after ${elapsed()}: ${e.message}"
                handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val code = response.code
                    // Model rejected a config field → resend once with bare request
                    if (code == 400 && useGenConfig) {
                        geminiAttempt(key, prompt, userContent, isDirect, callback, attempt, modelIndex, startedAt, useGenConfig = false)
                        return
                    }
                    // Model rejects request or is retired → try the next model in the list
                    if ((code == 400 || code == 404) && modelIndex + 1 < geminiModels.size) {
                        geminiAttempt(key, prompt, userContent, isDirect, callback, 1, modelIndex + 1, startedAt)
                        return
                    }
                    if ((code == 429 || code == 500 || code == 503) && attempt < maxAttempts) {
                        retryLater("busy ($code)"); return
                    }
                    reportApiError("Gemini (${elapsed()})", response, bodyStr); callback(null); return
                }
                try {
                    val json = JSONObject(bodyStr)
                    val respParts = json.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                    // Skip any "thought" parts; join the actual answer text
                    val sb = StringBuilder()
                    for (i in 0 until respParts.length()) {
                        val part = respParts.getJSONObject(i)
                        if (part.optBoolean("thought", false)) continue
                        sb.append(part.optString("text", ""))
                    }
                    val text = sb.toString().trim()
                    if (text.isEmpty()) throw IllegalStateException("empty")
                    handler.post {
                        Toast.makeText(this@RephraseAccessibilityService, "Gemini ($model) — ${elapsed()}", Toast.LENGTH_SHORT).show()
                    }
                    callback(text)
                } catch (e: Exception) {
                    lastApiError = "Gemini: unexpected response format (${elapsed()})"
                    handler.post { Toast.makeText(this@RephraseAccessibilityService, lastApiError, Toast.LENGTH_LONG).show() }
                    callback(null)
                }
            }
        })
    }

    private fun dismissBubble() {
        bubbleView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            bubbleView = null
        }
    }

    override fun onInterrupt() { dismissBubble(); dismissFab() }
    override fun onDestroy() { dismissBubble(); dismissFab(); super.onDestroy() }
}
