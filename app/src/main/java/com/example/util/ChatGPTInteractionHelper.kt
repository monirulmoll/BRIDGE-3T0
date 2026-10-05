package com.example.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.state.AutomationManager

data class SendButtonCandidate(
    val targetNode: AccessibilityNodeInfo,
    val actionableNode: AccessibilityNodeInfo,
    val bounds: Rect,
    val details: String
)

object ChatGPTInteractionHelper {

    private const val TAG = "ChatGPTInteractionHelper"
    const val STARTUP_MESSAGE = "BRIDGE START"

    /**
     * Dynamically verifies the installed ChatGPT package on the device
     */
    fun detectChatGptPackage(context: Context): String {
        val pm = context.packageManager
        try {
            pm.getPackageInfo("com.openai.chatgpt", 0)
            return "com.openai.chatgpt"
        } catch (_: Exception) {}

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
                val viewId = node.viewIdResourceName?.lowercase().orEmpty()

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
     * Finds the Send button corresponding to the screenshot:
     * A circular blue button with an upward arrow (↑) on the right side of the prompt input pill.
     * Logs: package name, class name, text, contentDescription, viewIdResourceName, clickable, enabled, available actions
     */
    fun findSendButtonCandidate(root: AccessibilityNodeInfo?, inputField: AccessibilityNodeInfo?): SendButtonCandidate? {
        if (root == null) return null

        val inputBounds = Rect()
        if (inputField != null) {
            inputField.getBoundsInScreen(inputBounds)
        }

        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = -1

        for (node in allNodes) {
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val className = node.className?.toString().orEmpty()

            val nodeBounds = Rect()
            node.getBoundsInScreen(nodeBounds)

            var score = 0

            // 1. Text or ContentDescription matching "send", "submit", "arrow", "up"
            if (desc.contains("send") || text.contains("send") || viewId.contains("send") ||
                desc.contains("bhejo") || desc.contains("submit") || text.contains("↑") ||
                desc.contains("arrow") || desc.contains("up") || viewId.contains("submit")) {
                score += 100
            }

            // 2. Positional alignment with the input field (as seen in the screenshot)
            if (inputBounds.width() > 0) {
                // Should be horizontally to the right of the input field or near right side of screen
                val isRightAligned = nodeBounds.centerX() >= inputBounds.centerX()
                val isVerticallyAligned = Math.abs(nodeBounds.centerY() - inputBounds.centerY()) < 120

                if (isRightAligned && isVerticallyAligned) {
                    score += 50
                    if (nodeBounds.right >= inputBounds.right - 100) {
                        score += 30
                    }
                }
            }

            // 3. Clickability or button/image characteristics
            if (node.isClickable) score += 20
            if (className.contains("Button") || className.contains("ImageView")) score += 10

            if (score > bestScore && score >= 50) {
                bestScore = score
                bestNode = node
            }
        }

        // If not found by traversal, check sibling nodes of inputField
        if (bestNode == null && inputField != null) {
            var parent = inputField.parent
            while (parent != null && bestNode == null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != null && sibling != inputField) {
                        val bounds = Rect()
                        sibling.getBoundsInScreen(bounds)
                        if (bounds.centerX() > inputBounds.centerX()) {
                            bestNode = sibling
                            break
                        }
                    }
                }
                parent = parent.parent
            }
        }

        val target = bestNode ?: return null

        // Inspect parent or children to find the actual clickable/actionable node
        val actionable = findActionableNode(target)
        val bounds = Rect()
        actionable.getBoundsInScreen(bounds)

        // Log detailed node metrics as explicitly requested in item 3
        val details = buildString {
            append("package=${actionable.packageName}, ")
            append("class=${actionable.className}, ")
            append("text='${actionable.text}', ")
            append("contentDescription='${actionable.contentDescription}', ")
            append("viewIdResourceName='${actionable.viewIdResourceName}', ")
            append("clickable=${actionable.isClickable}, ")
            append("enabled=${actionable.isEnabled}, ")
            append("actions=[${actionable.actionList?.joinToString { it.id.toString() }}], ")
            append("bounds=[${bounds.left},${bounds.top} to ${bounds.right},${bounds.bottom}]")
        }

        Log.i(TAG, "Send button candidate located: $details")
        AutomationManager.log("Send Button Details: $details")
        AutomationManager.markSendButtonStatus(true, details)

        return SendButtonCandidate(
            targetNode = target,
            actionableNode = actionable,
            bounds = bounds,
            details = details
        )
    }

    /**
     * Climbs up parents or inspects children to find the node with clickable/enabled state
     */
    private fun findActionableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable && node.isEnabled) {
            return node
        }

        // Check parents
        var curr: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (curr != null && depth < 3) {
            if (curr.isClickable && curr.isEnabled) {
                return curr
            }
            curr = curr.parent
            depth++
        }

        // Check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && child.isClickable && child.isEnabled) {
                return child
            }
        }

        return node
    }

    /**
     * Executes Send Action via ACTION_CLICK and fallback coordinate touch gesture
     */
    fun executeSendAction(service: AccessibilityService, candidate: SendButtonCandidate): Boolean {
        // 1. Try standard Accessibility ACTION_CLICK
        var clicked = candidate.actionableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        if (!clicked && candidate.targetNode != candidate.actionableNode) {
            clicked = candidate.targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // 2. If ACTION_CLICK was refused or not handled (e.g. React Native / Compose custom touch interception),
        // dispatch direct gesture click at the center coordinates of the blue button
        if (!clicked || candidate.bounds.width() > 0) {
            val centerX = candidate.bounds.centerX().toFloat()
            val centerY = candidate.bounds.centerY().toFloat()
            if (centerX > 0 && centerY > 0) {
                val gestureClicked = dispatchClickGesture(service, centerX, centerY)
                if (gestureClicked) {
                    clicked = true
                }
            }
        }

        return clicked
    }

    private fun dispatchClickGesture(service: AccessibilityService, x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val clickPath = Path().apply {
                    moveTo(x, y)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 80))
                    .build()
                return service.dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Gesture click failed: ${e.message}")
            }
        }
        return false
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            collectAllNodes(node.getChild(i), list)
        }
    }

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
