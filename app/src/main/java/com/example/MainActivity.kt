package com.example

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.AutoBridgeApp
import com.example.ui.BridgeViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BridgeViewModel by viewModels()
    private var accessibilityManager: AccessibilityManager? = null
    private val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener {
        viewModel.checkPermissions(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityManager?.addAccessibilityStateChangeListener(accessibilityListener)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AutoBridgeApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        accessibilityManager?.removeAccessibilityStateChangeListener(accessibilityListener)
    }
}
