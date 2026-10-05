package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.AutoBridgeApplication
import com.example.data.BridgeSettings
import com.example.data.CapturedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class BridgeStatus(
    val isConnected: Boolean = false,
    val currentPackage: String = "",
    val lastAction: String = "Service idle. Ready to monitor.",
    val lastActionTime: Long = System.currentTimeMillis()
)

class BridgeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var clipboardManager: ClipboardManager? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Debounce and hash caches to prevent duplicate rapid triggers
    private val copiedCodeHashes = LinkedHashSet<String>()
    private var lastCopiedTime: Long = 0
    private var lastCapturedTermuxText: String = ""
    private var lastCapturedTermuxTime: Long = 0

    // Pending relay to ChatGPT
    private var pendingRelayText: String? = null
    private var pendingRelayTimestamp: Long = 0
    private var relayAttemptCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoBridge::AccessibilityWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        if (settings.screenOffExecution) {
            acquireWakeLock()
        }

        // Start background service if configured
        if (settings.workInBackground) {
            BridgeBackgroundService.start(this)
        }

        _statusFlow.value = _statusFlow.value.copy(
            isConnected = true,
            lastAction = "AutoBridge Accessibility Service Connected & Active"
        )
        Log.d(TAG, "BridgeAccessibilityService connected successfully")
    }

    fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
                Log.d(TAG, "Accessibility WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed acquiring wake lock: ${e.message}")
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Accessibility WakeLock released")
            }
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val app = AutoBridgeApplication.instance
        val settings = app.repository.currentSettings()
        if (!settings.isBridgeEnabled) return

        val packageName = event.packageName?.toString() ?: return

        _statusFlow.value = _statusFlow.value.copy(
            currentPackage = packageName
        )

        // 1. ChatGPT Monitoring & Auto-Copying Code
        if (isChatGptApp(packageName, settings)) {
            handleChatGptWindow(settings, packageName)
        }

        // 2. Termux Monitoring & Output Capture
        if (isTermuxApp(packageName, settings)) {
            handleTermuxWindow(settings, packageName)
        }
    }

    private fun isChatGptApp(packageName: String, settings: BridgeSettings): Boolean {
        if (packageName.equals(settings.chatGptPackage, ignoreCase = true)) return true
        if (packageName.contains("chatgpt", ignoreCase = true)) return true
        return packageName in listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.kiwibrowser.browser",
            "com.microsoft.emmx"
        )
    }

    private fun isTermuxApp(packageName: String, settings: BridgeSettings): Boolean {
        return packageName.equals(settings.termuxPackage, ignoreCase = true) ||
                packageName.contains("termux", ignoreCase = true)
    }

    private fun handleChatGptWindow(settings: BridgeSettings, packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        // If there's a pending relay text waiting to be sent to ChatGPT:
        if (!pendingRelayText.isNullOrBlank()) {
            attemptRelayToChatGpt(rootNode)
            return
        }

        // Auto-Copy ChatGPT Code
        if (!settings.autoCopyChatGptCode) return

        val now = System.currentTimeMillis()
        if (now - lastCopiedTime < 800) return // Throttle checks

        // Target: ONLY generated code having a "Copy code" button or copy icon logo
        val copyButtons = findCopyCodeButtons(rootNode)
        for (btn in copyButtons) {
            val codeContent = extractCodeFromCodeContainer(btn, rootNode)
            if (!codeContent.isNullOrBlank() && looksLikeCode(codeContent)) {
                val hash = sha256(codeContent)
                if (!copiedCodeHashes.contains(hash)) {
                    processNewChatGPTCode(codeContent, packageName, settings, hash)
                    // Trigger click on the native copy button
                    try {
                        btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } catch (_: Exception) {}
                    return
                }
            }
        }

        // Fallback only if strict mode is disabled
        if (!settings.strictCodeButtonOnly) {
            val extractedBlocks = scanForCodeBlocks(rootNode)
            for (code in extractedBlocks) {
                val hash = sha256(code)
                if (!copiedCodeHashes.contains(hash)) {
                    processNewChatGPTCode(code, packageName, settings, hash)
                    return
                }
            }
        }
    }

    private fun processNewChatGPTCode(
        code: String,
        packageName: String,
        settings: BridgeSettings,
        hash: String
    ) {
        lastCopiedTime = System.currentTimeMillis()
        copiedCodeHashes.add(hash)
        if (copiedCodeHashes.size > 200) {
            val first = copiedCodeHashes.iterator().next()
            copiedCodeHashes.remove(first)
        }

        // Copy directly to system clipboard
        copyToClipboard(code, "AutoBridge ChatGPT Code")

        // Haptic feedback
        if (settings.vibrationFeedback) {
            triggerVibration()
        }

        val language = detectLanguage(code)
        val summary = code.lines().firstOrNull { it.isNotBlank() }?.take(40) ?: "Code block"
        val item = CapturedItem(
            type = CapturedItem.TYPE_CHATGPT_CODE,
            title = "ChatGPT Code ($language)",
            content = code,
            languageOrTag = language,
            sourcePackage = packageName,
            isAutoRelayed = false
        )

        serviceScope.launch {
            AutoBridgeApplication.instance.repository.insertItem(item)
        }

        _statusFlow.value = _statusFlow.value.copy(
            lastAction = "Auto-copied generated code ($language) to clipboard",
            lastActionTime = System.currentTimeMillis()
        )
        Log.i(TAG, "Successfully auto-copied code snippet from ChatGPT: $summary")
    }

    private fun handleTermuxWindow(settings: BridgeSettings, packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()
        if (now - lastCapturedTermuxTime < settings.autoCaptureDelayMs) return

        val terminalText = extractTerminalText(rootNode) ?: return
        if (terminalText.length < 5) return

        val cleaned = cleanTerminalOutput(terminalText)
        if (cleaned.isNotBlank() && cleaned != lastCapturedTermuxText) {
            val hash = sha256(cleaned)
            if (!copiedCodeHashes.contains(hash)) {
                lastCapturedTermuxTime = now
                lastCapturedTermuxText = cleaned
                copiedCodeHashes.add(hash)

                // Copy to system clipboard
                copyToClipboard(cleaned, "AutoBridge Termux Output")

                if (settings.vibrationFeedback) {
                    triggerVibration()
                }

                val item = CapturedItem(
                    type = CapturedItem.TYPE_TERMUX_OUTPUT,
                    title = "Termux Output (${cleaned.lines().size} lines)",
                    content = cleaned,
                    languageOrTag = "terminal",
                    sourcePackage = packageName,
                    isAutoRelayed = settings.autoSendTermuxToGpt
                )

                serviceScope.launch {
                    AutoBridgeApplication.instance.repository.insertItem(item)
                }

                _statusFlow.value = _statusFlow.value.copy(
                    lastAction = "Captured Termux terminal output (${cleaned.lines().size} lines)",
                    lastActionTime = System.currentTimeMillis()
                )

                // If Auto-Send to ChatGPT is enabled, trigger relay
                if (settings.autoSendTermuxToGpt) {
                    val prompt = settings.promptTemplate.replace("{OUTPUT}", cleaned)
                    triggerRelayToChatGpt(prompt, settings)
                }
            }
        }
    }

    fun triggerManualRelay(textToSend: String, settings: BridgeSettings) {
        val prompt = if (textToSend.contains("{OUTPUT}")) {
            textToSend
        } else {
            settings.promptTemplate.replace("{OUTPUT}", textToSend)
        }
        triggerRelayToChatGpt(prompt, settings)
    }

    private fun triggerRelayToChatGpt(prompt: String, settings: BridgeSettings) {
        pendingRelayText = prompt
        pendingRelayTimestamp = System.currentTimeMillis()
        relayAttemptCount = 0

        _statusFlow.value = _statusFlow.value.copy(
            lastAction = "Preparing relay to ChatGPT...",
            lastActionTime = System.currentTimeMillis()
        )

        // Switch to ChatGPT if autoSwitchApp is enabled
        if (settings.autoSwitchApp) {
            mainHandler.postDelayed({
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(settings.chatGptPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching ChatGPT app: ${e.message}")
                }
            }, 300)
        }
    }

    private fun attemptRelayToChatGpt(rootNode: AccessibilityNodeInfo) {
        val textToSend = pendingRelayText ?: return
        val now = System.currentTimeMillis()
        if (now - pendingRelayTimestamp > 15000) {
            pendingRelayText = null
            return
        }

        relayAttemptCount++
        if (relayAttemptCount > 20) {
            pendingRelayText = null
            return
        }

        val inputNode = findChatGptInputNode(rootNode)
        if (inputNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    textToSend
                )
            }
            val setResult = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (setResult) {
                mainHandler.postDelayed({
                    val updatedRoot = rootInActiveWindow ?: rootNode
                    val sendButton = findSendButton(updatedRoot)
                    if (sendButton != null) {
                        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        _statusFlow.value = _statusFlow.value.copy(
                            lastAction = "Successfully relayed Termux output to ChatGPT!",
                            lastActionTime = System.currentTimeMillis()
                        )
                        Log.i(TAG, "Relayed message to ChatGPT successfully")
                    } else {
                        _statusFlow.value = _statusFlow.value.copy(
                            lastAction = "Inserted Termux output into ChatGPT input box",
                            lastActionTime = System.currentTimeMillis()
                        )
                    }
                    pendingRelayText = null
                }, 400)
            }
        }
    }

    private fun findChatGptInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var fallbackEditable: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isEditable) {
                fallbackEditable = node
                val hint = node.hintText?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""

                if (hint.contains("message") || hint.contains("prompt") || hint.contains("chat") ||
                    desc.contains("message") || viewId.contains("input") || viewId.contains("query")
                ) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallbackEditable
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isClickable) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""

                if (desc.contains("send") || desc.contains("bhejo") || text.contains("send") ||
                    viewId.contains("send") || desc.contains("submit")
                ) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Finds specifically "Copy code" buttons or copy icons associated with code blocks.
     * In ChatGPT, code blocks display a header with language name and a "Copy code" button/logo.
     */
    private fun findCopyCodeButtons(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Specific check for code copy button/logo
            val isExplicitCodeCopy = text.contains("copy code") || desc.contains("copy code") ||
                    (desc.contains("copy") && (viewId.contains("code") || text.contains("code"))) ||
                    (node.isClickable && (text == "copy" || desc == "copy") && isInsideCodeBlock(node))

            if (isExplicitCodeCopy && node.isClickable) {
                results.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return results
    }

    private fun isInsideCodeBlock(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            val textBlocks = mutableListOf<String>()
            collectTextNodes(parent, textBlocks)
            if (textBlocks.any { looksLikeCode(it) }) {
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    /**
     * Extracts only the code snippet contained in the code block container near the copy button
     */
    private fun extractCodeFromCodeContainer(button: AccessibilityNodeInfo, root: AccessibilityNodeInfo): String? {
        var parent = button.parent
        var depth = 0
        while (parent != null && depth < 4) {
            val textBlocks = mutableListOf<String>()
            collectTextNodes(parent, textBlocks)
            // Filter out the button text itself (e.g. "Copy code", language labels)
            val codeCandidates = textBlocks.filter { candidate ->
                candidate != "Copy code" && candidate != "Copy" &&
                        !isLanguageHeaderOnly(candidate) && looksLikeCode(candidate)
            }
            if (codeCandidates.isNotEmpty()) {
                // Return candidate with highest code score/length
                return codeCandidates.maxByOrNull { it.length }
            }
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun isLanguageHeaderOnly(text: String): Boolean {
        val t = text.trim().lowercase()
        return t in listOf("python", "bash", "sh", "javascript", "js", "kotlin", "java", "json", "c", "cpp", "sql", "html", "css")
    }

    private fun scanForCodeBlocks(root: AccessibilityNodeInfo): List<String> {
        val codeBlocks = mutableListOf<String>()
        val textList = mutableListOf<String>()
        collectTextNodes(root, textList)

        for (text in textList) {
            if (text.contains("```")) {
                val extracted = extractBacktickCode(text)
                if (extracted.isNotBlank() && looksLikeCode(extracted)) {
                    codeBlocks.add(extracted)
                }
            } else if (looksLikeCode(text) && text.lines().size >= 2) {
                codeBlocks.add(text)
            }
        }

        return codeBlocks
    }

    private fun extractTerminalText(root: AccessibilityNodeInfo): String? {
        val textList = mutableListOf<String>()
        collectTextNodes(root, textList)
        if (textList.isEmpty()) return null

        return textList.joinToString("\n")
    }

    private fun cleanTerminalOutput(text: String): String {
        val lines = text.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return ""

        return lines.takeLast(40).joinToString("\n")
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo, outList: MutableList<String>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.length > 3) {
            outList.add(text.trim())
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTextNodes(it, outList) }
        }
    }

    private fun looksLikeCode(text: String): Boolean {
        if (text.length < 15) return false
        val codeIndicators = listOf(
            "import ", "def ", "class ", "function", "var ", "val ", "const ",
            "fun ", "return ", "if (", "while (", "for (", "#!/", "echo ",
            "apt ", "pkg ", "curl ", "npm ", "git ", "pip ", "python ",
            "sudo ", "chmod ", "cd ", "ls -", "grep ", "mkdir ", "{", "}",
            "=>", "==", "!=", "println", "console.log"
        )
        var score = 0
        for (indicator in codeIndicators) {
            if (text.contains(indicator)) {
                score++
                if (score >= 2) return true
            }
        }
        return score >= 1 && (text.contains(";") || text.contains("\n") || text.contains("$"))
    }

    private fun extractBacktickCode(text: String): String {
        val regex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```")
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text
    }

    private fun detectLanguage(code: String): String {
        val firstLine = code.lines().firstOrNull()?.trim() ?: ""
        return when {
            firstLine.startsWith("#!/bin/bash") || firstLine.startsWith("#!/bin/sh") ||
                    code.contains("pkg install") || code.contains("apt install") -> "bash"
            code.contains("def ") || code.contains("import numpy") || code.contains("print(") -> "python"
            code.contains("fun ") || code.contains("val ") || code.contains("var ") -> "kotlin"
            code.contains("const ") || code.contains("console.log") || code.contains("function ") -> "javascript"
            code.contains("#include <") -> "c/c++"
            code.contains("public class") -> "java"
            code.contains("SELECT ") && code.contains("FROM ") -> "sql"
            else -> "code"
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager?.setPrimaryClip(clip)
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onInterrupt() {
        Log.w(TAG, "BridgeAccessibilityService onInterrupt called")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        releaseWakeLock()
        _statusFlow.value = _statusFlow.value.copy(
            isConnected = false,
            lastAction = "Service stopped"
        )
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AutoBridgeService"

        var instance: BridgeAccessibilityService? = null
            private set

        private val _statusFlow = MutableStateFlow(BridgeStatus())
        val statusFlow: StateFlow<BridgeStatus> = _statusFlow.asStateFlow()
    }
}
