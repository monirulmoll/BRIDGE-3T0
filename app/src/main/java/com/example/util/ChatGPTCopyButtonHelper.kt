package com.example.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.example.R
import com.example.state.AutomationManager

object ChatGPTCopyButtonHelper {

    private const val TAG = "ChatGPTCopyButtonHelper"

    /**
     * Method 1: Searches the Accessibility tree for "Copy" text or ID,
     * and performs ACTION_CLICK or clicks its live coordinates.
     */
    fun clickCopyButtonMethod1(service: AccessibilityService, rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        try {
            // User snippet: val accessibilityNodes = rootInActiveWindow.findAccessibilityNodeInfosByText("Copy")
            val accessibilityNodes = rootNode.findAccessibilityNodeInfosByText("Copy")

            if (!accessibilityNodes.isNullOrEmpty()) {
                // Pick the bottom-most copy button (newest ChatGPT response)
                val copyButtonNode = accessibilityNodes.maxByOrNull {
                    val b = Rect()
                    it.getBoundsInScreen(b)
                    b.centerY()
                } ?: accessibilityNodes.last()

                // Log node properties
                AutomationManager.log("Method 1: Found newest 'Copy' node (pkg=${copyButtonNode.packageName}, class=${copyButtonNode.className})")

                // Try ACTION_CLICK
                val clickedAction = copyButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                // Get live coordinates of the button
                val bounds = Rect()
                copyButtonNode.getBoundsInScreen(bounds)

                if (bounds.width() > 0 && bounds.height() > 0) {
                    val centerX = bounds.centerX().toFloat()
                    val centerY = bounds.centerY().toFloat()
                    AutomationManager.log("Method 1: Copy button live coordinates: ($centerX, $centerY)")
                    // Direct gesture click on live coordinates
                    val gestureClicked = ChatGPTInteractionHelper.dispatchClickGesture(service, centerX, centerY)
                    if (gestureClicked || clickedAction) {
                        AutomationManager.log("Method 1: Clicked Copy button via live coordinates ($centerX, $centerY)")
                        return true
                    }
                } else if (clickedAction) {
                    AutomationManager.log("Method 1: Clicked Copy button via performAction(ACTION_CLICK)")
                    return true
                }
            }

            // Fallback scan: Search by contentDescription (e.g. "Copy code" in ChatGPT code block header)
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectNodes(rootNode, allNodes)
            val copyCandidates = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
            for (node in allNodes) {
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val text = node.text?.toString()?.lowercase().orEmpty()
                val id = node.viewIdResourceName?.lowercase().orEmpty()

                if (desc.contains("copy") || text.contains("copy") || id.contains("copy")) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        copyCandidates.add(Pair(node, bounds))
                    }
                }
            }

            // Pick the bottom-most candidate (newest response)
            val bestCandidate = copyCandidates.maxByOrNull { it.second.centerY() }
            if (bestCandidate != null) {
                val (node, bounds) = bestCandidate
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()
                val actionClicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val gestureClicked = ChatGPTInteractionHelper.dispatchClickGesture(service, centerX, centerY)
                if (actionClicked || gestureClicked) {
                    AutomationManager.log("Method 1: Clicked newest Copy node at ($centerX, $centerY)")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Method 1 error: ${e.message}")
        }

        return false
    }

    /**
     * Method 2: Visual Template Matching (equivalent to OpenCV matchTemplate TM_CCOEFF_NORMED)
     * Matches the copy icon on the screen with accuracy >= 0.85 and clicks the center coordinate.
     */
    fun clickCopyButtonMethod2(
        service: AccessibilityService,
        onComplete: (Boolean, Point?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val screenBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, true)
                            hardwareBuffer.close()

                            if (screenBitmap != null) {
                                val drawable = ContextCompat.getDrawable(service, R.drawable.ic_chatgpt_copy)
                                if (drawable != null) {
                                    val targetIconMat = TemplateMatcher.drawableToBitmap(drawable, 36, 36)
                                    val matchPoint = TemplateMatcher.findAndClickImage(screenBitmap, targetIconMat, 0.85f)

                                    if (matchPoint != null) {
                                        AutomationManager.log("Method 2: Copy icon match found at (${matchPoint.x}, ${matchPoint.y}) [Confidence >= 85%]")
                                        val clicked = ChatGPTInteractionHelper.dispatchClickGesture(
                                            service,
                                            matchPoint.x.toFloat(),
                                            matchPoint.y.toFloat()
                                        )
                                        onComplete(clicked, matchPoint)
                                        return
                                    } else {
                                        AutomationManager.log("Method 2: No copy icon matched with confidence >= 0.85")
                                    }
                                }
                            }
                            onComplete(false, null)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "Method 2 takeScreenshot failed with code $errorCode")
                            onComplete(false, null)
                        }
                    }
                )
                return
            } catch (e: Exception) {
                Log.e(TAG, "Method 2 screenshot error: ${e.message}")
            }
        }

        onComplete(false, null)
    }

    /**
     * Combined Pipeline: Tries Method 1 first. If Method 1 fails, falls back to Method 2.
     */
    fun findAndClickCopyButton(
        service: AccessibilityService,
        rootNode: AccessibilityNodeInfo?,
        onDone: (Boolean) -> Unit
    ) {
        // 1. Try Method 1 (Accessibility Scanner)
        val successMethod1 = clickCopyButtonMethod1(service, rootNode)
        if (successMethod1) {
            onDone(true)
            return
        }

        // 2. If Method 1 fails -> Method 2 (Visual Template Matching)
        AutomationManager.log("Method 1 did not find 'Copy' node. Executing Method 2: Template Matching...")
        clickCopyButtonMethod2(service) { success, _ ->
            onDone(success)
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        list.add(node)
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), list)
        }
    }
}
