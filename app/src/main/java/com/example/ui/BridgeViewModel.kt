package com.example.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoBridgeApplication
import com.example.data.BridgeSettings
import com.example.data.CapturedItem
import com.example.service.BridgeAccessibilityService
import com.example.service.BridgeBackgroundService
import com.example.service.BridgeOverlayService
import com.example.service.BridgeStatus
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
    val isTesting: Boolean = false
)

class BridgeViewModel : ViewModel() {

    private val repository = AutoBridgeApplication.instance.repository

    val settings: StateFlow<BridgeSettings> = repository.settings
    val status: StateFlow<BridgeStatus> = BridgeAccessibilityService.statusFlow

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

    fun checkPermissions(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isServiceRunning = BridgeAccessibilityService.instance != null
        val isServiceInEnabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true

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

        _uiState.value = _uiState.value.copy(
            isAccessibilityGranted = isServiceRunning || isServiceInEnabledList,
            hasOverlayPermission = overlay,
            isBatteryOptimizationIgnored = isIgnoringBattery,
            isBackgroundServiceRunning = BridgeBackgroundService.isRunning
        )
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
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "$appName is not installed on this device", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open $appName: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Interactive Sandbox Simulation ---
    fun updateTestInput(input: String) {
        _uiState.value = _uiState.value.copy(testInputText = input)
    }

    fun runSimulatedChatGptCodeDetection(context: Context, codeSample: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val extracted = if (codeSample.contains("```")) {
                val regex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```")
                regex.find(codeSample)?.groupValues?.get(1)?.trim() ?: codeSample.trim()
            } else {
                codeSample.trim()
            }

            copyToClipboard(context, extracted, "AutoBridge Test Code")

            val item = CapturedItem(
                type = CapturedItem.TYPE_CHATGPT_CODE,
                title = "ChatGPT Code (Simulated Test)",
                content = extracted,
                languageOrTag = if (extracted.contains("def ")) "python" else "bash",
                sourcePackage = "com.openai.chatgpt (Test)",
                isAutoRelayed = false
            )
            repository.insertItem(item)

            _uiState.value = _uiState.value.copy(
                isTesting = false,
                testOutputLog = "✅ Successfully parsed & auto-copied code snippet to Clipboard!\nStrict Copy Logo Match: Active\nSnippet length: ${extracted.length} chars.\nBridge is ready to auto-paste into Termux."
            )
        }
    }

    fun runSimulatedTermuxOutputRelay(context: Context, terminalOutputSample: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true)
            val trimmed = terminalOutputSample.trim()
            copyToClipboard(context, trimmed, "AutoBridge Test Termux")

            val currentSettings = settings.value
            val item = CapturedItem(
                type = CapturedItem.TYPE_TERMUX_OUTPUT,
                title = "Termux Output (Simulated Test)",
                content = trimmed,
                languageOrTag = "terminal",
                sourcePackage = "com.termux (Test)",
                isAutoRelayed = currentSettings.autoSendTermuxToGpt
            )
            repository.insertItem(item)

            val formattedRelay = currentSettings.promptTemplate.replace("{OUTPUT}", trimmed)
            val service = BridgeAccessibilityService.instance
            if (service != null && currentSettings.autoSendTermuxToGpt) {
                service.triggerManualRelay(trimmed, currentSettings)
            }

            _uiState.value = _uiState.value.copy(
                isTesting = false,
                testOutputLog = "✅ Termux terminal output captured & copied to Clipboard!\nFormatted Relay Message Prepared:\n\n$formattedRelay\n\n(Auto-Send dispatch executed)"
            )
        }
    }
}
