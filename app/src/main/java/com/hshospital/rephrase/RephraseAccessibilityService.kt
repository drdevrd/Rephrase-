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
    private val client = OkHttpClient()
    private lateinit var prefs: SharedPreferences
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
        // Try active node first
        activeNode?.let { node ->
            val text = node.text?.toString()
            if (!text.isNullOrEmpty()) return text
        }
        // Try root window scan
        try {
            val root = rootInActiveWindow ?: return ""
            val node = findFocusedEditableNode(root)
            if (node != null) {
                activeNode = node
                val text = node.text?.toString()
                if (!text.isNullOrEmpty()) return text
            }
        } catch (e: Exception) {}
        // Fallback to clipboard
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    private fun selectAllAndCopy() {
        // Perform select all on active node
        activeNode?.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        // Small delay then copy
        handler.postDelayed({
            activeNode?.performAction(AccessibilityNodeInfo.ACTION_COPY)
        }, 200)
    }

    private fun pasteFromClipboard() {
        // Paste into active field
        activeNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        // If paste didn't work, try set text
        handler.postDelayed({
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: return@postDelayed
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            activeNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }, 300)
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
                        // Get text from field
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
            // Put in clipboard
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("rephrased", text))
            // Try to paste directly
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = activeNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) ?: false
            if (!success) {
                // Try paste action
                activeNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
            handler.post {
                Toast.makeText(this, "Copied! Long press to paste if needed.", Toast.LENGTH_SHORT).show()
            }
        }

        fun rephrase(prompt: String) {
            statusMsg.text = "Rephrasing..."
            resetResults()
            callApiRephrase(inputText, prompt) { result ->
                handler.post {
                    if (result != null) {
                        val options = parseOptions(result)
                        btnOption1.text = "1. ${options.getOrElse(0) { "" }}"
                        btnOption2.text = "2. ${options.getOrElse(1) { "" }}"
                        btnOption3.text = "3. ${options.getOrElse(2) { "" }}"
                        optionsScroll.visibility = View.VISIBLE
                        btnCloseTop.visibility = View.VISIBLE
                        statusMsg.text = "Tap an option to use it"
                    } else {
                        statusMsg.text = "Failed. Try again."
                        btnCloseTop.visibility = View.VISIBLE
                    }
                }
            }
        }

        fun checkGrammar() {
            statusMsg.text = "Checking grammar..."
            resetResults()
            callApiRephrase(inputText, grammarPrompt) { result ->
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
                        statusMsg.text = "Grammar check done"
                        btnUseGrammar.setOnClickListener {
                            pasteResult(corrected)
                            dismissBubble()
                        }
                    } else {
                        statusMsg.text = "Failed. Try again."
                        btnCloseTop.visibility = View.VISIBLE
                    }
                }
            }
        }

        fun askAi(prompt: String) {
            statusMsg.text = "Asking AI..."
            resetResults()
            callApiDirect(inputText, prompt) { result ->
                handler.post {
                    if (result != null) {
                        askAiResult.text = result
                        askAiScroll.visibility = View.VISIBLE
                        btnCloseTop.visibility = View.VISIBLE
                        statusMsg.text = "AI Response"
                    } else {
                        statusMsg.text = "Failed. Try again."
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

    private fun callApiRephrase(text: String, prompt: String, callback: (String?) -> Unit) {
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isEmpty()) { callback(null); return }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", prompt))
        messages.put(JSONObject().put("role", "user").put("content", "Rephrase this exact text as instructed: [$text]"))
        val body = JSONObject()
        body.put("model", "gpt-4o-mini")
        body.put("max_tokens", 1000)
        body.put("messages", messages)
        makeRequest(body, callback)
    }

    private fun callApiDirect(text: String, prompt: String, callback: (String?) -> Unit) {
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isEmpty()) { callback(null); return }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", "You are a helpful assistant. Answer the user question about the given text clearly and concisely."))
        messages.put(JSONObject().put("role", "user").put("content", "$prompt\n\nText: $text"))
        val body = JSONObject()
        body.put("model", "gpt-4o-mini")
        body.put("max_tokens", 1000)
        body.put("messages", messages)
        makeRequest(body, callback)
    }

    private fun makeRequest(body: JSONObject, callback: (String?) -> Unit) {
        val key = prefs.getString("api_key", "") ?: ""
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = JSONObject(response.body?.string() ?: "")
                    callback(json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content"))
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

    override fun onInterrupt() { dismissBubble(); dismissFab() }
    override fun onDestroy() { dismissBubble(); dismissFab(); super.onDestroy() }
}
