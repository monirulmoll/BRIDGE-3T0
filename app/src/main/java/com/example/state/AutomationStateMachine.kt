package com.example.state

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AutomationState {
    IDLE,
    CHATGPT_READY,
    WAITING_FOR_RESPONSE,
    CODE_DETECTED,
    SEND_TO_TERMUX,
    WAITING_FOR_OUTPUT,
    OUTPUT_RECEIVED,
    PASTE_TO_CHATGPT,
    WAITING_FOR_NEXT_RESPONSE
}

data class DebugMetrics(
    val accessibilityEnabled: Boolean = false,
    val backgroundMode: Boolean = false,
    val controllerUrl: String = "http://127.0.0.1:8765",
    val controllerConnected: Boolean = false,
    val commandSent: Boolean = false,
    val outputReceived: Boolean = false,
    val keyboardOpen: Boolean = false,
    val lastClickCoordinates: String = "",
    val sendButtonFound: Boolean = false,
    val sendActionSuccess: Boolean = false,
    val lastSendButtonDetails: String = "",
    val chatGptDetected: Boolean = false,
    val chatGptResponseDetected: Boolean = false,
    val codeBlockDetected: Boolean = false,
    val codeLength: Int = 0,
    val bridgeConnected: Boolean = false,
    val outputCopied: Boolean = false,
    val chatGptInputFound: Boolean = false,
    val messageSent: Boolean = false,
    val currentState: AutomationState = AutomationState.IDLE,
    val startupMessageSent: Boolean = false,
    val lastCommandText: String = "",
    val lastOutputText: String = "",
    val logs: List<String> = emptyList()
)

object AutomationManager {

    private const val TAG = "AutomationManager"
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _debugMetrics = MutableStateFlow(DebugMetrics())
    val debugMetrics: StateFlow<DebugMetrics> = _debugMetrics.asStateFlow()

    // Sets of hashed commands and outputs to prevent feedback loops
    private val executedCommandHashes = LinkedHashSet<String>()
    private val processedOutputHashes = LinkedHashSet<String>()

    var startupMessageAlreadySent: Boolean = false
        private set

    fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        val logLine = "[$timestamp] $message"
        try {
            Log.d(TAG, logLine)
        } catch (_: Throwable) {
            println("$TAG: $logLine")
        }
        val currentLogs = _debugMetrics.value.logs.takeLast(49)
        _debugMetrics.value = _debugMetrics.value.copy(
            logs = currentLogs + logLine
        )
    }

    fun updateBackgroundMode(enabled: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(backgroundMode = enabled)
        if (enabled) {
            log("BACKGROUND MODE: ON")
            log("CHATGPT FOREGROUND LAUNCH: DISABLED")
            log("ACCESSIBILITY: ENABLED")
            log("AUTOMATION: RUNNING")
        } else {
            log("BACKGROUND MODE: OFF")
            log("CHATGPT FOREGROUND LAUNCH: ENABLED")
        }
    }

    fun logBackgroundActionUnavailable(reason: String) {
        log("BACKGROUND ACTION NOT AVAILABLE: $reason")
    }

    fun setState(newState: AutomationState) {
        val oldState = _debugMetrics.value.currentState
        if (oldState != newState) {
            log("State: $oldState ➜ $newState")
            _debugMetrics.value = _debugMetrics.value.copy(currentState = newState)
        }
    }

    fun getCurrentState(): AutomationState = _debugMetrics.value.currentState

    fun markAccessibility(enabled: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(accessibilityEnabled = enabled)
    }

    fun markControllerStatus(connected: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(
            controllerConnected = connected,
            bridgeConnected = connected
        )
    }

    fun markSendButtonStatus(found: Boolean, details: String = "") {
        _debugMetrics.value = _debugMetrics.value.copy(
            sendButtonFound = found,
            lastSendButtonDetails = details
        )
    }

    fun markSendActionResult(success: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(
            sendActionSuccess = success,
            messageSent = success
        )
    }

    fun markKeyboardStatus(open: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(keyboardOpen = open)
    }

    fun markClickCoordinates(coords: String) {
        _debugMetrics.value = _debugMetrics.value.copy(lastClickCoordinates = coords)
        log("Send Button Target Coordinates: $coords")
    }

    fun markChatGptDetected(detected: Boolean) {
        if (_debugMetrics.value.chatGptDetected != detected) {
            _debugMetrics.value = _debugMetrics.value.copy(chatGptDetected = detected)
            if (detected && _debugMetrics.value.currentState == AutomationState.IDLE) {
                setState(AutomationState.CHATGPT_READY)
            }
        }
    }

    fun markChatGptResponseDetected(detected: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(chatGptResponseDetected = detected)
    }

    fun markCodeDetected(code: String, codeHash: String): Boolean {
        if (processedOutputHashes.contains(codeHash) || executedCommandHashes.contains(codeHash)) {
            return false
        }

        val state = _debugMetrics.value.currentState
        val canAcceptCode = state == AutomationState.WAITING_FOR_RESPONSE ||
                state == AutomationState.WAITING_FOR_NEXT_RESPONSE ||
                state == AutomationState.CHATGPT_READY

        if (!canAcceptCode) {
            return false
        }

        _debugMetrics.value = _debugMetrics.value.copy(
            codeBlockDetected = true,
            codeLength = code.length,
            lastCommandText = code
        )
        log("Code Block Detected (Length: ${code.length} chars)")
        setState(AutomationState.CODE_DETECTED)
        return true
    }

    fun markSendingToTermux(code: String, codeHash: String) {
        executedCommandHashes.add(codeHash)
        if (executedCommandHashes.size > 200) {
            val first = executedCommandHashes.iterator().next()
            executedCommandHashes.remove(first)
        }
        setState(AutomationState.SEND_TO_TERMUX)
        _debugMetrics.value = _debugMetrics.value.copy(
            commandSent = true,
            lastCommandText = code
        )
        log("Sending Command to Termux Bridge (http://127.0.0.1:8765/run): ${code.take(40)}...")
        setState(AutomationState.WAITING_FOR_OUTPUT)
    }

    fun markBridgeConnected(connected: Boolean) {
        markControllerStatus(connected)
    }

    fun markOutputReceived(output: String, outputHash: String) {
        processedOutputHashes.add(outputHash)
        if (processedOutputHashes.size > 200) {
            val first = processedOutputHashes.iterator().next()
            processedOutputHashes.remove(first)
        }

        _debugMetrics.value = _debugMetrics.value.copy(
            outputReceived = true,
            lastOutputText = output
        )
        log("Termux Output Received (${output.lines().size} lines, ${output.length} chars)")
        setState(AutomationState.OUTPUT_RECEIVED)
    }

    fun markOutputCopied(copied: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(outputCopied = copied)
        if (copied) {
            log("Termux Output copied to Clipboard")
            setState(AutomationState.PASTE_TO_CHATGPT)
        }
    }

    fun markChatGptInputFound(found: Boolean) {
        _debugMetrics.value = _debugMetrics.value.copy(chatGptInputFound = found)
    }

    fun markMessageSent(sent: Boolean) {
        markSendActionResult(sent)
        if (sent) {
            log("Message sent to ChatGPT successfully")
            setState(AutomationState.WAITING_FOR_NEXT_RESPONSE)
        }
    }

    fun markStartupMessageSent() {
        startupMessageAlreadySent = true
        _debugMetrics.value = _debugMetrics.value.copy(
            startupMessageSent = true,
            messageSent = true,
            sendActionSuccess = true
        )
        log("Initial STARTUP MESSAGE ('BRIDGE START') sent successfully")
        setState(AutomationState.WAITING_FOR_RESPONSE)
    }

    fun resetStartupState() {
        startupMessageAlreadySent = false
        _debugMetrics.value = _debugMetrics.value.copy(startupMessageSent = false)
    }

    fun isOutputHashKnown(hash: String): Boolean = processedOutputHashes.contains(hash)
}
