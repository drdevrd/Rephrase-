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
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private lateinit var prefs: SharedPreferences
    private var isDisabledForApp = false
    private var isKeyboardVisible = false
    private var lastErrorMsg = ""

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
        "formal"    to "Give exactly 10 distinct numbered rephrasing options in formal professional tone. Keep drug names exactly. Each option must be meaningfully different from the others. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "casual"    to "Give exactly 10 distinct numbered rephrasing options in friendly casual tone. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "medical"   to "Give exactly 10 distinct numbered rephrasing options in clinical medical language. Keep ALL drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "simple"    to "Give exactly 10 distinct numbered rephrasing options in very simple language. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "concise"   to "Give exactly 10 distinct numbered rephrasing options as concisely as possible. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "natural"   to "Give exactly 10 distinct numbered rephrasing options that sound completely natural. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "discharge" to "Give exactly 10 distinct numbered rephrasing options in discharge summary language. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "tamil"     to "Give exactly 10 distinct numbered Tamil translation options. Keep drug names in English. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ...",
        "email"     to "Give exactly 10 distinct numbered rephrasing options as polished professional email body. Keep drug names exactly. Each option must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ..."
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
        // Match any number prefix (1., 2., ..., 10., 15., etc.)
        val numberedPattern = Regex("^\\s*(\\d{1,2})[.)]\\s*(.+)$")
        val options = mutableListOf<String>()
        for (line in response.trim().split("\n")) {
            val m = numberedPattern.find(line)
            if (m != null) {
                options.add(m.groupValues[2].trim())
            }
        }
        if (options.size < 2) {
            return response.trim().split("\n").filter { it.isNotEmpty() }
        }
        return options
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
        val optionsContainer = bubLayout.findViewById<LinearLayout>(R.id.optionsContainer)
        val optionsSelectable = bubLayout.findViewById<android.widget.EditText>(R.id.optionsSelectable)
        val btnCopySelectedOptions = bubLayout.findViewById<Button>(R.id.btn_copy_selected_options)
        val btnToggleSelect = bubLayout.findViewById<Button>(R.id.btn_toggle_select)
        val btnCloseX = bubLayout.findViewById<Button>(R.id.btn_close_x)
        val customTonesRow = bubLayout.findViewById<LinearLayout>(R.id.customTonesRow)
        val btnCustom1 = bubLayout.findViewById<Button>(R.id.btn_custom1)
        val btnCustom2 = bubLayout.findViewById<Button>(R.id.btn_custom2)
        val btnCustom3 = bubLayout.findViewById<Button>(R.id.btn_custom3)
        val grammarScroll = bubLayout.findViewById<View>(R.id.grammarScroll)
        val grammarCorrected = bubLayout.findViewById<TextView>(R.id.grammarCorrected)
        val grammarExplanation = bubLayout.findViewById<TextView>(R.id.grammarExplanation)
        val btnUseGrammar = bubLayout.findViewById<Button>(R.id.btn_use_grammar)
        val askAiScroll = bubLayout.findViewById<View>(R.id.askAiScroll)
        val askAiResult = bubLayout.findViewById<android.widget.EditText>(R.id.askAiResult)
        val askAiCopyRow = bubLayout.findViewById<LinearLayout>(R.id.askAiCopyRow)
        val btnCopyAskAi = bubLayout.findViewById<Button>(R.id.btn_copy_ask_ai)
        val btnCopySelectedAskAi = bubLayout.findViewById<Button>(R.id.btn_copy_selected_ask_ai)
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
            optionsContainer.removeAllViews()
            optionsSelectable.visibility = View.GONE
            btnToggleSelect.visibility = View.GONE
            btnToggleSelect.text = "📝 Select partial text"
            grammarScroll.visibility = View.GONE
            btnUseGrammar.visibility = View.GONE
            askAiScroll.visibility = View.GONE
            askAiCopyRow.visibility = View.GONE
            btnCopyAskAi.visibility = View.GONE
            btnCopySelectedOptions.visibility = View.GONE
        }
        // Build a single option row: [big "N. text" USE button | 56dp Copy button]
        fun buildOptionRow(index: Int, text: String, pasteFn: (String) -> Unit) {
            val row = LinearLayout(this@RephraseAccessibilityService)
            row.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.bottomMargin = 10
            row.layoutParams = rowParams

            val useBtn = Button(this@RephraseAccessibilityService)
            val useParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            useParams.marginEnd = 10
            useBtn.layoutParams = useParams
            useBtn.text = "${index + 1}. $text"
            useBtn.textSize = 12f
            useBtn.isAllCaps = false
            useBtn.setBackgroundColor(android.graphics.Color.parseColor("#238636"))
            useBtn.setTextColor(android.graphics.Color.WHITE)
            useBtn.setPadding(24, 20, 16, 20)
            useBtn.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            useBtn.setOnClickListener { pasteFn(text); dismissBubble() }

            val copyBtn = Button(this@RephraseAccessibilityService)
            copyBtn.layoutParams = LinearLayout.LayoutParams(140, LinearLayout.LayoutParams.WRAP_CONTENT)
            copyBtn.text = "Copy"
            copyBtn.textSize = 11f
            copyBtn.isAllCaps = false
            copyBtn.setBackgroundColor(android.graphics.Color.parseColor("#0e7490"))
            copyBtn.setTextColor(android.graphics.Color.WHITE)
            copyBtn.setPadding(8, 20, 8, 20)
            copyBtn.setOnClickListener {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("rephrased", text))
                Toast.makeText(this@RephraseAccessibilityService, "Option ${index + 1} copied!", Toast.LENGTH_SHORT).show()
            }

            row.addView(useBtn)
            row.addView(copyBtn)
            optionsContainer.addView(row)
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
            lastErrorMsg = ""
            callApi(inputText, prompt, false) { result ->
                handler.post {
                    if (result != null) {
                        val options = parseOptions(result)
                        // Dynamically build a row per option (handles 1 to 15+)
                        for ((i, opt) in options.withIndex()) {
                            buildOptionRow(i, opt) { t -> pasteResult(t) }
                        }
                        // Build the full selectable list for partial-text copy mode
                        val numbered = options.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
                        optionsSelectable.setText(numbered)
                        optionsScroll.visibility = View.VISIBLE
                        btnToggleSelect.visibility = View.VISIBLE
                        statusMsg.text = "${options.size} options · tap to USE · Copy to copy whole · 📝 for partial"
                    } else {
                        val errDetail = if (lastErrorMsg.isNotEmpty()) lastErrorMsg else "Check API key & provider in settings."
                        statusMsg.text = "Failed: $errDetail"
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
                        statusMsg.text = "Grammar check done"
                        btnUseGrammar.setOnClickListener {
                            pasteResult(corrected)
                            dismissBubble()
                        }
                    } else {
                        statusMsg.text = "Failed. Check API key & provider in settings."
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
                        askAiResult.setText(result)
                        askAiScroll.visibility = View.VISIBLE
                        askAiCopyRow.visibility = View.VISIBLE
                        btnCopyAskAi.visibility = View.VISIBLE
                        statusMsg.text = "AI Response · select text or tap Copy Answer"
                    } else {
                        val errDetail = if (lastErrorMsg.isNotEmpty()) lastErrorMsg else "Check API key & provider in settings."
                        statusMsg.text = "Failed: $errDetail"
                    }
                }
            }
        }
        btnCopyAskAi.setOnClickListener {
            val t = askAiResult.text.toString()
            if (t.isNotEmpty()) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("ai_answer", t))
                Toast.makeText(this, "All answer copied!", Toast.LENGTH_SHORT).show()
            }
        }
        // Copy Selected (AI answer) — reads the current selection range directly from the EditText
        btnCopySelectedAskAi.setOnClickListener {
            val full = askAiResult.text.toString()
            val start = askAiResult.selectionStart.coerceAtLeast(0)
            val end = askAiResult.selectionEnd.coerceAtLeast(0)
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            val picked = if (lo in 0..full.length && hi in 0..full.length && hi > lo)
                full.substring(lo, hi) else full
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("ai_selected", picked))
            val label = if (hi > lo) "Selected text copied (${picked.length} chars)" else "No selection — copied all"
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }
        // Copy Selected (rephrase options) — same logic for the big selectable EditText
        btnCopySelectedOptions.setOnClickListener {
            val full = optionsSelectable.text.toString()
            val start = optionsSelectable.selectionStart.coerceAtLeast(0)
            val end = optionsSelectable.selectionEnd.coerceAtLeast(0)
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            val picked = if (lo in 0..full.length && hi in 0..full.length && hi > lo)
                full.substring(lo, hi) else full
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("rephrase_selected", picked))
            val label = if (hi > lo) "Selected text copied (${picked.length} chars)" else "No selection — copied all"
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }
        // Focus-friendly flag combinations:
        //  - notFocusableFlags : default — overlay doesn't steal keyboard focus from the text field below
        //  - focusableFlags    : for selection mode — allows selection menu, uses NOT_TOUCH_MODAL so outside taps still work
        val focusableFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val notFocusableFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        // Tap the AI answer area → enable selection on it
        askAiResult.setOnClickListener {
            params.flags = focusableFlags
            try { windowManager?.updateViewLayout(bubLayout, params) } catch (e: Exception) {}
            askAiResult.requestFocus()
            askAiResult.setSelection(0)
            Toast.makeText(this, "Long-press to select text", Toast.LENGTH_SHORT).show()
        }
        // Toggle between per-option buttons and the full selectable EditText (all options)
        btnToggleSelect.setOnClickListener {
            if (optionsSelectable.visibility == View.VISIBLE) {
                optionsSelectable.visibility = View.GONE
                btnCopySelectedOptions.visibility = View.GONE
                optionsScroll.visibility = View.VISIBLE
                btnToggleSelect.text = "📝 Select partial text"
                params.flags = notFocusableFlags
                try { windowManager?.updateViewLayout(bubLayout, params) } catch (e: Exception) {}
            } else {
                optionsScroll.visibility = View.GONE
                optionsSelectable.visibility = View.VISIBLE
                btnCopySelectedOptions.visibility = View.VISIBLE
                btnToggleSelect.text = "⬅ Back to buttons"
                params.flags = focusableFlags
                try { windowManager?.updateViewLayout(bubLayout, params) } catch (e: Exception) {}
                optionsSelectable.requestFocus()
                optionsSelectable.setSelection(0)
                Toast.makeText(this, "Long-press to highlight → tap 📋 Copy Selected", Toast.LENGTH_LONG).show()
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
            }
        }
        // For custom tones, the user's own prompt controls the count (e.g. "give 10 variations")
        fun buildCustomPrompt(userPrompt: String): String {
            val mentionsCount = Regex("\\b\\d+\\b").containsMatchIn(userPrompt)
            return if (mentionsCount) {
                "$userPrompt\n\nFormat each as a numbered list: 1. ... 2. ... 3. ... etc. Each option must be meaningfully different."
            } else {
                "Give exactly 10 distinct numbered rephrasing options. $userPrompt. Each must be meaningfully different. Format as:\n1. ...\n2. ...\n...\n10. ..."
            }
        }
        if (customPrompt1.isNotEmpty()) btnCustom1.setOnClickListener {
            rephrase(buildCustomPrompt(customPrompt1))
        }
        if (customPrompt2.isNotEmpty()) btnCustom2.setOnClickListener {
            rephrase(buildCustomPrompt(customPrompt2))
        }
        if (customPrompt3.isNotEmpty()) btnCustom3.setOnClickListener {
            rephrase(buildCustomPrompt(customPrompt3))
        }
        // Always-visible ✕ dismisses the bubble immediately — safe against accidental taps
        btnCloseX.setOnClickListener { dismissBubble() }
        // Start in non-focusable mode so the text field below keeps focus
        params.flags = notFocusableFlags
        try { windowManager?.addView(bubLayout, params) } catch (e: Exception) {}
    }

    // Search all common prefs locations until we find a non-empty value for one of the given keys.
    // Returns Triple(value, prefsFileName, keyName) or Triple("", "", "") if nothing found.
    private fun findPrefValue(keyNames: List<String>): Triple<String, String, String> {
        val possibleFiles = listOf(
            "rephrase_prefs",
            "${packageName}_preferences",   // PreferenceManager default
            "settings",
            "prefs",
            "RephrasePrefs",
            "RePhrasePrefs"
        )
        for (fileName in possibleFiles) {
            val p = try { getSharedPreferences(fileName, Context.MODE_PRIVATE) } catch (e: Exception) { continue }
            for (keyName in keyNames) {
                val v = try { p.getString(keyName, "") ?: "" } catch (e: Exception) { "" }
                if (v.isNotEmpty()) return Triple(v, fileName, keyName)
            }
        }
        return Triple("", "", "")
    }

    // Unified API caller — picks provider from prefs (searches all common prefs locations)
    private fun callApi(text: String, prompt: String, isDirect: Boolean, callback: (String?) -> Unit) {
        // Provider first
        val providerNames = listOf("api_provider", "apiProvider", "provider", "ai_provider")
        val (providerRaw, _, _) = findPrefValue(providerNames)
        val provider = (if (providerRaw.isNotEmpty()) providerRaw else "gemini").lowercase()
        // Key is stored per-provider: api_key_gemini, api_key_claude, api_key_openai
        val keyNames = listOf(
            "api_key_$provider",
            "api_key",
            "apiKey",
            "api-key",
            "key",
            "${provider}_key",
            "${provider}_api_key"
        )
        val (key, keyFile, keyFound) = findPrefValue(keyNames)
        // DEBUG: show what we actually found and where
        handler.post {
            val keyPreview = if (key.isEmpty()) "EMPTY!" else "${key.take(6)}... (${key.length} chars)"
            val src = if (keyFile.isEmpty()) "none" else "$keyFile/$keyFound"
            Toast.makeText(this, "Provider:$provider | Key:$keyPreview | Src:$src", Toast.LENGTH_SHORT).show()
        }
        if (key.isEmpty()) {
            // Scan actual shared_prefs directory on disk to see what files SettingsActivity really creates
            val diskReport = StringBuilder()
            try {
                val prefsDir = java.io.File(applicationInfo.dataDir, "shared_prefs")
                if (prefsDir.exists()) {
                    val files = prefsDir.listFiles() ?: emptyArray()
                    if (files.isEmpty()) {
                        diskReport.append("shared_prefs dir EMPTY — nothing ever saved!")
                    } else {
                        diskReport.append("Files on disk: ")
                        for (f in files) {
                            diskReport.append(f.name).append(" (")
                            // Read first few key names from the XML
                            try {
                                val content = f.readText()
                                val keyMatches = Regex("name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()
                                diskReport.append(keyMatches.joinToString(",").take(80))
                            } catch (e: Exception) { diskReport.append("?") }
                            diskReport.append(") ")
                        }
                    }
                } else {
                    diskReport.append("shared_prefs dir does NOT exist")
                }
            } catch (e: Exception) {
                diskReport.append("Scan error: ${e.message}")
            }
            lastErrorMsg = diskReport.toString()
            handler.post { Toast.makeText(this, diskReport.toString(), Toast.LENGTH_LONG).show() }
            callback(null); return
        }
        val userContent = if (isDirect) "$prompt\n\nText: $text" else "Rephrase this exact text as instructed: [$text]"

        // Build fallback chain: primary provider first, then others that have keys saved
        val allProviders = listOf("gemini", "claude", "openai")
        val chain = (listOf(provider) + allProviders.filter { it != provider }).distinct()
        val chainWithKeys = mutableListOf<Pair<String, String>>()
        for (p in chain) {
            val k = if (p == provider) key else {
                val (k2, _, _) = findPrefValue(listOf("api_key_$p", "${p}_key", "${p}_api_key"))
                k2
            }
            if (k.isNotEmpty()) chainWithKeys.add(p to k)
        }
        tryChain(chainWithKeys, prompt, userContent, isDirect, 0, callback)
    }

    // Recursive fallback: try each provider in order; on failure, move to the next
    private fun tryChain(chain: List<Pair<String, String>>, prompt: String, userContent: String, isDirect: Boolean, index: Int, callback: (String?) -> Unit) {
        if (index >= chain.size) {
            // All providers failed
            callback(null); return
        }
        val (provider, key) = chain[index]
        handler.post {
            Toast.makeText(this, "Trying $provider (${index + 1}/${chain.size})...", Toast.LENGTH_SHORT).show()
        }
        val wrapped: (String?) -> Unit = { result ->
            if (result != null) {
                callback(result)
            } else {
                // Fall through to next provider
                handler.post {
                    if (index + 1 < chain.size) {
                        Toast.makeText(this, "$provider failed → trying ${chain[index + 1].first}", Toast.LENGTH_SHORT).show()
                    }
                }
                tryChain(chain, prompt, userContent, isDirect, index + 1, callback)
            }
        }
        when (provider) {
            "claude", "anthropic" -> callClaude(key, prompt, userContent, isDirect, wrapped)
            "openai", "gpt" -> callOpenAI(key, prompt, userContent, isDirect, wrapped)
            else -> callGemini(key, prompt, userContent, isDirect, wrapped)
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
                lastErrorMsg = "Claude network: ${e.message}"
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseStr)
                    if (!response.isSuccessful) {
                        val errMsg = json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        lastErrorMsg = "Claude ${response.code}: $errMsg"
                        callback(null); return
                    }
                    callback(json.getJSONArray("content").getJSONObject(0).getString("text"))
                } catch (e: Exception) {
                    lastErrorMsg = "Claude parse: ${responseStr.take(80)}"
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
                lastErrorMsg = "OpenAI network: ${e.message}"
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseStr)
                    if (!response.isSuccessful) {
                        val errMsg = json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        lastErrorMsg = "OpenAI ${response.code}: $errMsg"
                        callback(null); return
                    }
                    callback(json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
                } catch (e: Exception) {
                    lastErrorMsg = "OpenAI parse: ${responseStr.take(80)}"
                    callback(null)
                }
            }
        })
    }

    private fun callGemini(key: String, prompt: String, userContent: String, isDirect: Boolean, callback: (String?) -> Unit) {
        val fullText = if (isDirect) userContent else "$prompt\n\n$userContent"
        val parts = JSONArray()
        parts.put(JSONObject().put("text", fullText))
        val contentObj = JSONObject().put("parts", parts)
        val contents = JSONArray()
        contents.put(contentObj)
        val body = JSONObject().put("contents", contents)
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent")
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                lastErrorMsg = "Network: ${e.message}"
                handler.post { Toast.makeText(this@RephraseAccessibilityService, "Network error: ${e.message}", Toast.LENGTH_LONG).show() }
                callback(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseStr)
                    if (!response.isSuccessful) {
                        val errMsg = json.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                        lastErrorMsg = "HTTP ${response.code}: $errMsg"
                        handler.post { Toast.makeText(this@RephraseAccessibilityService, "Gemini: $errMsg", Toast.LENGTH_LONG).show() }
                        callback(null); return
                    }
                    callback(json.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text"))
                } catch (e: Exception) {
                    lastErrorMsg = "Parse: ${responseStr.take(80)}"
                    handler.post { Toast.makeText(this@RephraseAccessibilityService, "Parse error: ${responseStr.take(120)}", Toast.LENGTH_LONG).show() }
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
