package com.example.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.example.service.BridgeAccessibilityService

object AccessibilityPermissionHelper {

    fun isAccessibilityPermissionGranted(context: Context): Boolean {
        // 1. Direct active instance check
        if (BridgeAccessibilityService.instance != null) {
            return true
        }

        // 2. Check system Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (accessibilityEnabled == 1) {
                val servicesSetting = Settings.Secure.getString(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (!servicesSetting.isNullOrBlank()) {
                    val colonSplitter = TextUtils.SimpleStringSplitter(':')
                    colonSplitter.setString(servicesSetting)
                    val targetPackage = context.packageName
                    val targetSimpleName = BridgeAccessibilityService::class.java.simpleName
                    val targetCanonical = BridgeAccessibilityService::class.java.name

                    while (colonSplitter.hasNext()) {
                        val componentString = colonSplitter.next()
                        val component = ComponentName.unflattenFromString(componentString)
                        if (component != null && component.packageName.equals(targetPackage, ignoreCase = true)) {
                            if (component.className.contains(targetSimpleName) || component.className == targetCanonical) {
                                return true
                            }
                        } else if (componentString.contains(targetPackage) && componentString.contains(targetSimpleName)) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Check AccessibilityManager enabled service list
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    val sInfo = service.resolveInfo.serviceInfo
                    if (sInfo.packageName.equals(context.packageName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }
}
