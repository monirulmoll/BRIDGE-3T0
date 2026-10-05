package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ChatGPTInteractionHelper {

    private const val TAG = "ChatGPTInteractionHelper"
    const val STARTUP_MESSAGE = "BRIDGE START"

    /**
     * Dynamically verifies the installed ChatGPT package on the device
     */
    fun detectChatGptPackage(context: Context): String {
        val pm = context.packageManager
        // 1. Direct check for official OpenAI ChatGPT
        try {
            pm.getPackageInfo("com.openai.chatgpt", 0)
            return "com.openai.chatgpt"
        } catch (_: Exception) {}

        // 2. Query all installed apps for any package containing chatgpt or openai
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val pkg = app.packageName.lowercase()
                if (pkg.contains("chatgpt") || pkg.contains("openai")) {
                    Log.i(TAG, "Dynamically detected ChatGPT package: ${app.packageName}")
                    return app.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying packages: ${e.message}")
        }

        return "com.openai.chatgpt"
    }

    fun isChatGptPackage(packageName: String, preferredPackage: String): Boolean {
        if (packageName.equals(preferredPackage, ignoreCase = true)) return true
        val lower = packageName.lowercase()
        return lower.contains("chatgpt") || lower.contains("openai") ||
                lower in listOf(
                    "com.android.chrome",
                    "org.mozilla.firefox",
                    "com.brave.browser",
                    "com.kiwibrowser.browser",
                    "com.microsoft.emmx"
                )
    }

    /**
     * Robustly locates ChatGPT's prompt input field without assuming hardcoded resource IDs
     */
    fun findInputField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var fallbackEditable: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val isEditText = node.className?.toString() == "android.widget.EditText"
            if (node.isEditable || isEditText) {
                if (fallbackEditable == null) {
                    fallbackEditable = node
                }

                val hint = node.hintText?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val text = node.text?.toString()?.lowercase().orEmpty()
                val viewId = node.viewIdResourceName?.lowercase().orEmpty()

                // Check standard keywords used across ChatGPT app versions & languages
                val matchesKeyword = hint.contains("message") || hint.contains("ask") ||
                        hint.contains("prompt") || hint.contains("chat") ||
                        desc.contains("message") || desc.contains("prompt") ||
                        viewId.contains("input") || viewId.contains("query") ||
                        viewId.contains("prompt") || viewId.contains("chat")

                if (matchesKeyword) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return fallbackEditable
    }

    /**
     * Robustly locates ChatGPT's send button without hardcoded coordinates
     */
    fun findSendButton(root: AccessibilityNodeInfo?, inputField: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var candidateButton: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isClickable) {
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val text = node.text?.toString()?.lowercase().orEmpty()
                val viewId = node.viewIdResourceName?.lowercase().orEmpty()

                val isSendAction = desc.contains("send") || desc.contains("submit") ||
                        text.contains("send") || viewId.contains("send") ||
                        desc.contains("bhejo")

                if (isSendAction) {
                    return node
                }

                // If node is an ImageButton or ImageView right next to inputField
                val className = node.className?.toString().orEmpty()
                if (className.contains("Button") || className.contains("ImageView")) {
                    if (viewId.contains("send") || viewId.contains("arrow") || viewId.contains("action")) {
                        candidateButton = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // If not found in general traversal, search siblings of inputField
        if (inputField != null) {
            val parent = inputField.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != null && sibling != inputField && sibling.isClickable) {
                        return sibling
                    }
                }
            }
        }

        return candidateButton
    }

    /**
     * Sets text into the located input field
     */
    fun setText(inputNode: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting text: ${e.message}")
            false
        }
    }

    /**
     * Collects all text nodes that could contain ChatGPT responses or code blocks
     */
    fun collectTextNodes(node: AccessibilityNodeInfo?, outList: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length >= 3) {
            outList.add(text)
        }
        for (i in 0 until node.childCount) {
            collectTextNodes(node.getChild(i), outList)
        }
    }
}
