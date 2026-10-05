package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.example.bridge.TermuxBridgeClient
import com.example.data.CapturedItem
import com.example.state.AutomationManager
import com.example.state.AutomationState
import com.example.util.ChatGPTInteractionHelper
import com.example.util.CodeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val bridgeClient = TermuxBridgeClient()

    private var detectedChatGptPackage: String = "com.openai.chatgpt"
    private var isSendingBridgeCommand: Boolean = false
    private var lastInspectionTime: Long = 0

    // Pending text to paste into ChatGPT input field
    @Volatile
    private var pendingInputText: String? = null
    private var pendingInputAttempts: Int = 0

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
        detectedChatGptPackage = ChatGPTInteractionHelper.detectChatGptPackage(this)

        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        AutomationManager.markAccessibility(true)
        AutomationManager.updateBackgroundMode(settings.backgroundMode)
        AutomationManager.log("AccessibilityService Connected & Active (Package: $detectedChatGptPackage)")

        if (settings.screenOffExecution) {
            acquireWakeLock()
        }
        if (settings.workInBackground) {
            BridgeBackgroundService.start(this)
        }

        _statusFlow.value = _statusFlow.value.copy(
            isConnected = true,
            lastAction = "AutoBridge Accessibility Connected & Ready"
        )
        Log.i(TAG, "BridgeAccessibilityService connected successfully")
    }

    fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
                Log.d(TAG, "Accessibility WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error: ${e.message}")
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

        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        if (!settings.isBridgeEnabled) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return

        _statusFlow.value = _statusFlow.value.copy(currentPackage = packageName)

        val isChatGpt = ChatGPTInteractionHelper.isChatGptPackage(packageName, detectedChatGptPackage)
        AutomationManager.markChatGptDetected(isChatGpt)

        if (isChatGpt) {
            val rootNode = rootInActiveWindow ?: findChatGptRootNode()
            if (rootNode != null) {
                handleChatGptWindow(rootNode, settings)
            }
        } else if (settings.backgroundMode) {
            // In background mode, if there is pending input to ChatGPT, try finding interactive ChatGPT window
            if (!pendingInputText.isNullOrBlank()) {
                val gptRoot = findChatGptRootNode()
                if (gptRoot != null) {
                    attemptPasteAndSendToChatGpt(gptRoot)
                }
            }
        }
    }

    private fun handleChatGptWindow(rootNode: AccessibilityNodeInfo, settings: com.example.data.BridgeSettings) {
        val now = System.currentTimeMillis()
        if (now - lastInspectionTime < 400) return
        lastInspectionTime = now

        // Check if there is pending text waiting to be pasted into ChatGPT
        if (!pendingInputText.isNullOrBlank()) {
            attemptPasteAndSendToChatGpt(rootNode)
            return
        }

        // Check for Initial Startup Message: "BRIDGE START"
        if (!AutomationManager.startupMessageAlreadySent) {
            attemptSendStartupMessage(rootNode)
            return
        }

        // Process response code if waiting for response or ready
        val currentState = AutomationManager.getCurrentState()
        val canInspectForCode = currentState == AutomationState.WAITING_FOR_RESPONSE ||
                currentState == AutomationState.WAITING_FOR_NEXT_RESPONSE ||
                currentState == AutomationState.CHATGPT_READY

        if (canInspectForCode && !isSendingBridgeCommand) {
            inspectChatGPTForCode(rootNode)
        }
    }

    private var isSendingMessage: Boolean = false

    /**
     * Requirement: Sends the exact startup message "BRIDGE START" on first app launch
     * Finds the actual Send button matching the screenshot (blue circle with upward arrow),
     * clicks the actionable node, verifies message sent, and prevents retyping.
     */
    private fun attemptSendStartupMessage(rootNode: AccessibilityNodeInfo) {
        if (isSendingMessage) return
        val inputField = ChatGPTInteractionHelper.findInputField(rootNode) ?: return
        AutomationManager.markChatGptInputFound(true)

        isSendingMessage = true
        val success = ChatGPTInteractionHelper.setText(inputField, ChatGPTInteractionHelper.STARTUP_MESSAGE)
        if (success) {
            mainHandler.postDelayed({
                val updatedRoot = rootInActiveWindow ?: rootNode
                val currentInput = ChatGPTInteractionHelper.findInputField(updatedRoot)
                val candidate = ChatGPTInteractionHelper.findSendButtonCandidate(updatedRoot, currentInput)

                // Execute Send via Direct Coordinate Click (matching keyboard state) + Action
                val clicked = ChatGPTInteractionHelper.executeSendAction(this, currentInput, candidate)

                mainHandler.postDelayed({
                    val verifyRoot = rootInActiveWindow ?: updatedRoot
                    val verifyInput = ChatGPTInteractionHelper.findInputField(verifyRoot)
                    val remainingText = verifyInput?.text?.toString()?.trim().orEmpty()
                    if (remainingText.isBlank() || remainingText != ChatGPTInteractionHelper.STARTUP_MESSAGE) {
                        AutomationManager.markStartupMessageSent()
                        AutomationManager.markSendActionResult(true)
                        triggerVibration()
                        Log.i(TAG, "BRIDGE START verified sent successfully!")
                    } else {
                        AutomationManager.log("Send attempted at coordinates. Halting retry to prevent typing loop.")
                        AutomationManager.markStartupMessageSent()
                    }
                    isSendingMessage = false
                }, 400)
            }, 350)
        } else {
            isSendingMessage = false
        }
    }

    /**
     * Inspects ChatGPT response nodes, extracts pure code, and triggers Termux bridge
     */
    private fun inspectChatGPTForCode(rootNode: AccessibilityNodeInfo) {
        val textBlocks = mutableListOf<String>()
        ChatGPTInteractionHelper.collectTextNodes(rootNode, textBlocks)
        if (textBlocks.isEmpty()) return

        AutomationManager.markChatGptResponseDetected(true)

        for (text in textBlocks) {
            val extractedCode = CodeExtractor.extractExecutableCode(text)
            if (!extractedCode.isNullOrBlank()) {
                val codeHash = CodeExtractor.computeHash(extractedCode)

                if (AutomationManager.isOutputHashKnown(codeHash)) {
                    continue
                }

                val accepted = AutomationManager.markCodeDetected(extractedCode, codeHash)
                if (accepted) {
                    processExtractedCode(extractedCode, codeHash)
                    return
                }
            }
        }
    }

    private fun processExtractedCode(code: String, codeHash: String) {
        copyToClipboard(code, "AutoBridge Code")
        triggerVibration()

        serviceScope.launch {
            val item = CapturedItem(
                type = CapturedItem.TYPE_CHATGPT_CODE,
                title = "ChatGPT Code (${code.length} chars)",
                content = code,
                languageOrTag = if (code.contains("pkg") || code.contains("apt")) "bash" else "code",
                sourcePackage = detectedChatGptPackage,
                isAutoRelayed = false
            )
            AutoBridgeApplication.instance.repository.insertItem(item)
        }

        isSendingBridgeCommand = true
        AutomationManager.markSendingToTermux(code, codeHash)

        serviceScope.launch {
            try {
                val result = bridgeClient.executeCommand(code)
                isSendingBridgeCommand = false

                result.onSuccess { output ->
                    AutomationManager.markBridgeConnected(true)
                    val outputHash = CodeExtractor.computeHash(output)
                    AutomationManager.markOutputReceived(output, outputHash)

                    copyToClipboard(output, "AutoBridge Termux Output")
                    AutomationManager.markOutputCopied(true)
                    triggerVibration()

                    val outputItem = CapturedItem(
                        type = CapturedItem.TYPE_TERMUX_OUTPUT,
                        title = "Termux Output (${output.lines().size} lines)",
                        content = output,
                        languageOrTag = "terminal",
                        sourcePackage = "com.termux",
                        isAutoRelayed = true
                    )
                    AutoBridgeApplication.instance.repository.insertItem(outputItem)

                    scheduleRelayToChatGpt(output)
                }.onFailure { error ->
                    AutomationManager.log("Bridge /run error: ${error.message}")
                    AutomationManager.markBridgeConnected(false)
                    AutomationManager.setState(AutomationState.WAITING_FOR_RESPONSE)
                }
            } catch (e: Exception) {
                isSendingBridgeCommand = false
                Log.e(TAG, "Exception during bridge execution: ${e.message}")
                AutomationManager.log("Execution error: ${e.message}")
                AutomationManager.setState(AutomationState.WAITING_FOR_RESPONSE)
            }
        }
    }

    fun triggerManualRelay(textToSend: String) {
        scheduleRelayToChatGpt(textToSend)
    }

    /**
     * Handles forwarding output to ChatGPT with Background Mode separation
     */
    private fun scheduleRelayToChatGpt(output: String) {
        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        val formattedText = if (settings.promptTemplate.contains("{OUTPUT}")) {
            settings.promptTemplate.replace("{OUTPUT}", output)
        } else {
            output
        }

        pendingInputText = formattedText
        pendingInputAttempts = 0

        if (settings.backgroundMode) {
            // Background Mode ON: Do NOT intentionally bring ChatGPT to foreground!
            AutomationManager.log("BACKGROUND MODE: ON (ChatGPT foreground launch suppressed)")

            val chatGptRoot = findChatGptRootNode()
            if (chatGptRoot != null) {
                attemptPasteAndSendToChatGpt(chatGptRoot)
            } else {
                AutomationManager.logBackgroundActionUnavailable(
                    "ChatGPT window is not interactive in background. Android requires app window to be interactive to inspect nodes without foreground launch."
                )
            }
        } else {
            // Background Mode OFF: Normal visible mode — bring ChatGPT to front if needed
            if (_statusFlow.value.currentPackage != detectedChatGptPackage) {
                bringChatGptToFront()
            }
        }
    }

    private fun attemptPasteAndSendToChatGpt(rootNode: AccessibilityNodeInfo) {
        val textToPaste = pendingInputText ?: return
        if (isSendingMessage) return

        val inputField = ChatGPTInteractionHelper.findInputField(rootNode)
        if (inputField != null) {
            AutomationManager.markChatGptInputFound(true)
            isSendingMessage = true
            val success = ChatGPTInteractionHelper.setText(inputField, textToPaste)
            if (success) {
                mainHandler.postDelayed({
                    val updatedRoot = rootInActiveWindow ?: rootNode
                    val currentInput = ChatGPTInteractionHelper.findInputField(updatedRoot)
                    val candidate = ChatGPTInteractionHelper.findSendButtonCandidate(updatedRoot, currentInput)

                    // Execute Send via Direct Coordinate Click (matching keyboard state) + Action
                    val clicked = ChatGPTInteractionHelper.executeSendAction(this, currentInput, candidate)
                    mainHandler.postDelayed({
                        pendingInputText = null
                        isSendingMessage = false
                        AutomationManager.markMessageSent(clicked)
                        triggerVibration()
                    }, 400)
                }, 350)
            } else {
                isSendingMessage = false
                pendingInputText = null // Do not repeatedly type!
            }
        }
    }

    fun findChatGptRootNode(): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && ChatGPTInteractionHelper.isChatGptPackage(activeRoot.packageName?.toString().orEmpty(), detectedChatGptPackage)) {
            return activeRoot
        }
        try {
            val allWindows = windows
            if (allWindows != null) {
                for (window in allWindows) {
                    val root = window.root
                    if (root != null && ChatGPTInteractionHelper.isChatGptPackage(root.packageName?.toString().orEmpty(), detectedChatGptPackage)) {
                        return root
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun bringChatGptToFront() {
        val settings = AutoBridgeApplication.instance.repository.currentSettings()
        if (settings.backgroundMode) {
            // Suppressed in Background Mode
            return
        }
        try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(detectedChatGptPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing ChatGPT to front: ${e.message}")
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        try {
            val clip = ClipData.newPlainText(label, text)
            clipboardManager?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard copy failed: ${e.message}")
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        Log.w(TAG, "BridgeAccessibilityService onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        releaseWakeLock()
        AutomationManager.markAccessibility(false)
        AutomationManager.log("AccessibilityService Destroyed")
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
