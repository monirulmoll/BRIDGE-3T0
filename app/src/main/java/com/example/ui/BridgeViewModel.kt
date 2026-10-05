package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoBridgeApplication
import com.example.bridge.TermuxBridgeClient
import com.example.data.BridgeSettings
import com.example.data.CapturedItem
import com.example.service.BridgeAccessibilityService
import com.example.service.BridgeBackgroundService
import com.example.service.BridgeOverlayService
import com.example.service.BridgeStatus
import com.example.state.AutomationManager
import com.example.state.AutomationState
import com.example.state.DebugMetrics
import com.example.util.AccessibilityPermissionHelper
import com.example.util.ChatGPTInteractionHelper
import com.example.util.CodeExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BridgeUiState(
    val isAccessibilityGranted: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "CHATGPT_CODE", "TERMUX_OUTPUT"
    val testInputText: String = "",
    val testOutputLog: String = "",
    val isTesting: Boolean = false,
    val detectedGptPackage: String = "com.openai.chatgpt"
)

class BridgeViewModel : ViewModel() {

    private val repository = AutoBridgeApplication.instance.repository
    private val bridgeClient = TermuxBridgeClient()

    val settings: StateFlow<BridgeSettings> = repository.settings
    val status: StateFlow<BridgeStatus> = BridgeAccessibilityService.statusFlow
    val debugMetrics: StateFlow<DebugMetrics> = AutomationManager.debugMetrics

    val gptCodeCount: StateFlow<Int> = repository.gptCodeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val termuxOutputCount: StateFlow<Int> = repository.termuxOutputCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(BridgeUiState())
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    val capturedItems: StateFlow<List<CapturedItem>> = combine(
        repository.allItems,
        _uiState
    ) { items, state ->
        items.filter { item ->
            val matchesFilter = when (state.selectedFilter) {
                "CHATGPT_CODE" -> item.type == CapturedItem.TYPE_CHATGPT_CODE
                "TERMUX_OUTPUT" -> item.type == CapturedItem.TYPE_TERMUX_OUTPUT
                else -> true
            }
            val matchesSearch = state.searchQuery.isBlank() ||
                    item.content.contains(state.searchQuery, ignoreCase = true) ||
                    item.title.contains(state.searchQuery, ignoreCase = true) ||
                    item.languageOrTag.contains(state.searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkBridgeHealth()
    }

    fun checkPermissions(context: Context) {
        val isGranted = AccessibilityPermissionHelper.isAccessibilityPermissionGranted(context)
        AutomationManager.markAccessibility(isGranted)

        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }

        val gptPackage = ChatGPTInteractionHelper.detectChatGptPackage(context)

        _uiState.value = _uiState.value.copy(
            isAccessibilityGranted = isGranted,
            hasOverlayPermission = overlay,
            isBatteryOptimizationIgnored = isIgnoringBattery,
            isBackgroundServiceRunning = BridgeBackgroundService.isRunning,
            detectedGptPackage = gptPackage
        )

        // Ping local bridge in background
        checkBridgeHealth()
    }

    fun checkBridgeHealth() {
        viewModelScope.launch {
            val isConnected = bridgeClient.pingBridge()
            AutomationManager.markBridgeConnected(isConnected)
        }
    }

    /**
     * Requirement 6: Startup behaviour:
     * Check permissions, open ChatGPT, and initiate the BRIDGE START automated sequence
     */
    fun startBridgeAutomation(context: Context) {
        checkPermissions(context)
        if (!_uiState.value.isAccessibilityGranted) {
            Toast.makeText(context, "Please enable Accessibility Permission first!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
            return
        }

        AutomationManager.log("Launching ChatGPT and initiating BRIDGE START automation...")
        AutomationManager.setState(AutomationState.CHATGPT_READY)

        val gptPkg = _uiState.value.detectedGptPackage
        launchApp(context, gptPkg, "ChatGPT")
    }

    fun setFilter(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateSettings(newSettings: BridgeSettings) {
        repository.updateSettings(newSettings)
    }

    fun toggleBridge(enabled: Boolean, context: Context) {
        val current = settings.value
        val updated = current.copy(isBridgeEnabled = enabled)
        repository.updateSettings(updated)
        if (enabled && updated.workInBackground) {
            BridgeBackgroundService.start(context)
        } else if (!enabled) {
            BridgeBackgroundService.stop(context)
        }
    }

    fun toggleBackgroundService(context: Context, enabled: Boolean) {
        val current = settings.value
        repository.updateSettings(current.copy(workInBackground = enabled))
        if (enabled) {
            BridgeBackgroundService.start(context)
        } else {
            BridgeBackgroundService.stop(context)
        }
        checkPermissions(context)
    }

    fun toggleScreenOffExecution(enabled: Boolean) {
        val current = settings.value
        repository.updateSettings(current.copy(screenOffExecution = enabled))
        val service = BridgeAccessibilityService.instance
        if (enabled) {
            service?.acquireWakeLock()
        } else {
            service?.releaseWakeLock()
        }
    }

    fun toggleStrictCodeButtonOnly(enabled: Boolean) {
        val current = settings.value
        repository.updateSettings(current.copy(strictCodeButtonOnly = enabled))
    }

    fun toggleFloatingOverlay(context: Context, enabled: Boolean) {
        val current = settings.value
        repository.updateSettings(current.copy(floatingOverlayEnabled = enabled))
        if (enabled) {
            BridgeOverlayService.start(context)
        } else {
            BridgeOverlayService.stop(context)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "AutoBridge") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        cm?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show()
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open Overlay Permission settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchApp(context: Context, packageName: String, appName: String) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "$appName is not installed on this device", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open $appName: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Sandbox Simulation & Direct Bridge Test ---
    fun updateTestInput(input: String) {
        _uiState.value = _uiState.value.copy(testInputText = input)
    }

    fun runSimulatedBridgeCommand(context: Context, command: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            AutomationManager.log("Testing Bridge POST http://127.0.0.1:8765/run with: $command")
            val cleanCmd = CodeExtractor.cleanCodeSnippet(command)

            val result = bridgeClient.executeCommand(cleanCmd)
            result.onSuccess { output ->
                copyToClipboard(context, output, "AutoBridge Output")
                val outputHash = CodeExtractor.computeHash(output)
                AutomationManager.markOutputReceived(output, outputHash)
                AutomationManager.markOutputCopied(true)

                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testOutputLog = "✅ Bridge Connected & Executed!\n\nOUTPUT:\n$output\n\n(Output copied to clipboard & tracked to prevent feedback loop)"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    testOutputLog = "❌ Bridge Error (http://127.0.0.1:8765/run):\n${err.message}\n\nPlease ensure Termux local bridge server is running on port 8765."
                )
            }
        }
    }

    fun runSimulatedChatGptCodeDetection(context: Context, sampleText: String) {
        val extracted = CodeExtractor.extractExecutableCode(sampleText)
        if (extracted != null) {
            copyToClipboard(context, extracted, "AutoBridge Code")
            val hash = CodeExtractor.computeHash(extracted)
            AutomationManager.markCodeDetected(extracted, hash)
            _uiState.value = _uiState.value.copy(
                testOutputLog = "✅ Code Extracted Successfully!\n\nCODE:\n$extracted\n\nLength: ${extracted.length} chars (Markdown fences & explanations excluded)"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                testOutputLog = "⚠️ No valid code block detected in sample text."
            )
        }
    }
}
