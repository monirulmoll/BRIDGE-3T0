package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BridgeSettings(
    val isBridgeEnabled: Boolean = true,
    val backgroundMode: Boolean = false, // Background Mode: ON = Do not bring ChatGPT to foreground; OFF = Normal visible automation
    val autoCopyChatGptCode: Boolean = true,
    val strictCodeButtonOnly: Boolean = true, // Strictly only copy code attached to ChatGPT copy button/logo
    val autoSendTermuxToGpt: Boolean = true,
    val autoSwitchApp: Boolean = true,
    val workInBackground: Boolean = true, // Persistent background foreground service
    val screenOffExecution: Boolean = true, // Hold partial wake lock so CPU stays awake when screen is off
    val promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    val vibrationFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val floatingOverlayEnabled: Boolean = false,
    val chatGptPackage: String = "com.openai.chatgpt",
    val termuxPackage: String = "com.termux",
    val autoCaptureDelayMs: Long = 1200L
) {
    companion object {
        const val DEFAULT_PROMPT_TEMPLATE = "Here is the output from Termux terminal:\n```\n{OUTPUT}\n```\nPlease analyze the output and provide the next command or code block."
    }
}

class BridgePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("autobridge_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<BridgeSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): BridgeSettings {
        return BridgeSettings(
            isBridgeEnabled = prefs.getBoolean("isBridgeEnabled", true),
            backgroundMode = prefs.getBoolean("backgroundMode", false),
            autoCopyChatGptCode = prefs.getBoolean("autoCopyChatGptCode", true),
            strictCodeButtonOnly = prefs.getBoolean("strictCodeButtonOnly", true),
            autoSendTermuxToGpt = prefs.getBoolean("autoSendTermuxToGpt", true),
            autoSwitchApp = prefs.getBoolean("autoSwitchApp", true),
            workInBackground = prefs.getBoolean("workInBackground", true),
            screenOffExecution = prefs.getBoolean("screenOffExecution", true),
            promptTemplate = prefs.getString("promptTemplate", BridgeSettings.DEFAULT_PROMPT_TEMPLATE)
                ?: BridgeSettings.DEFAULT_PROMPT_TEMPLATE,
            vibrationFeedback = prefs.getBoolean("vibrationFeedback", true),
            soundFeedback = prefs.getBoolean("soundFeedback", false),
            floatingOverlayEnabled = prefs.getBoolean("floatingOverlayEnabled", false),
            chatGptPackage = prefs.getString("chatGptPackage", "com.openai.chatgpt") ?: "com.openai.chatgpt",
            termuxPackage = prefs.getString("termuxPackage", "com.termux") ?: "com.termux",
            autoCaptureDelayMs = prefs.getLong("autoCaptureDelayMs", 1200L)
        )
    }

    fun updateSettings(newSettings: BridgeSettings) {
        prefs.edit()
            .putBoolean("isBridgeEnabled", newSettings.isBridgeEnabled)
            .putBoolean("backgroundMode", newSettings.backgroundMode)
            .putBoolean("autoCopyChatGptCode", newSettings.autoCopyChatGptCode)
            .putBoolean("strictCodeButtonOnly", newSettings.strictCodeButtonOnly)
            .putBoolean("autoSendTermuxToGpt", newSettings.autoSendTermuxToGpt)
            .putBoolean("autoSwitchApp", newSettings.autoSwitchApp)
            .putBoolean("workInBackground", newSettings.workInBackground)
            .putBoolean("screenOffExecution", newSettings.screenOffExecution)
            .putString("promptTemplate", newSettings.promptTemplate)
            .putBoolean("vibrationFeedback", newSettings.vibrationFeedback)
            .putBoolean("soundFeedback", newSettings.soundFeedback)
            .putBoolean("floatingOverlayEnabled", newSettings.floatingOverlayEnabled)
            .putString("chatGptPackage", newSettings.chatGptPackage)
            .putString("termuxPackage", newSettings.termuxPackage)
            .putLong("autoCaptureDelayMs", newSettings.autoCaptureDelayMs)
            .apply()
        _settingsFlow.value = newSettings
    }

    fun currentSettings(): BridgeSettings = _settingsFlow.value
}
