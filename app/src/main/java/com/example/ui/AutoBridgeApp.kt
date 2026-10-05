package com.example.ui

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BridgeSettings
import com.example.data.CapturedItem
import com.example.state.AutomationState
import com.example.state.DebugMetrics
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.TerminalGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBridgeApp(
    viewModel: BridgeViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val debugMetrics by viewModel.debugMetrics.collectAsStateWithLifecycle()
    val capturedItems by viewModel.capturedItems.collectAsStateWithLifecycle()
    val gptCount by viewModel.gptCodeCount.collectAsStateWithLifecycle()
    val termuxCount by viewModel.termuxOutputCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
        if (settings.isBridgeEnabled && settings.workInBackground) {
            viewModel.toggleBackgroundService(context, true)
        }
    }

    Scaffold(
        topBar = {
            AutoBridgeTopBar(
                isBridgeEnabled = settings.isBridgeEnabled,
                isAccessibilityGranted = uiState.isAccessibilityGranted,
                currentState = debugMetrics.currentState,
                onToggleBridge = { viewModel.toggleBridge(it, context) },
                onRefresh = { viewModel.checkPermissions(context) }
            )
        },
        bottomBar = {
            AutoBridgeBottomNav(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MonitorScreen(
                    uiState = uiState,
                    settings = settings,
                    status = status,
                    debugMetrics = debugMetrics,
                    gptCount = gptCount,
                    termuxCount = termuxCount,
                    totalCount = totalCount,
                    recentItems = capturedItems.take(5),
                    onStartAutomation = { viewModel.startBridgeAutomation(context) },
                    onToggleBackgroundMode = { viewModel.toggleBackgroundMode(it) },
                    onTestController = { viewModel.testControllerConnection(context) },
                    onEnableAccessibility = { viewModel.openAccessibilitySettings(context) },
                    onRequestBatteryExemption = { viewModel.requestIgnoreBatteryOptimizations(context) },
                    onLaunchGpt = { viewModel.launchApp(context, uiState.detectedGptPackage, "ChatGPT") },
                    onLaunchTermux = { viewModel.launchApp(context, settings.termuxPackage, "Termux") },
                    onCopyItem = { item -> viewModel.copyToClipboard(context, item.content, item.title) },
                    onNavigateToTab = { selectedTab = it }
                )
                1 -> DebugScreen(
                    debugMetrics = debugMetrics,
                    onRefresh = { viewModel.checkPermissions(context) },
                    onOpenAccessibility = { viewModel.openAccessibilitySettings(context) }
                )
                2 -> SandboxScreen(
                    uiState = uiState,
                    settings = settings,
                    onUpdateInput = { viewModel.updateTestInput(it) },
                    onTestChatGptCode = { sample ->
                        viewModel.runSimulatedChatGptCodeDetection(context, sample)
                    },
                    onTestBridgeCommand = { command ->
                        viewModel.runSimulatedBridgeCommand(context, command)
                    },
                    onCopyLog = { log ->
                        viewModel.copyToClipboard(context, log, "AutoBridge Sandbox Log")
                    }
                )
                3 -> HistoryScreen(
                    capturedItems = capturedItems,
                    searchQuery = uiState.searchQuery,
                    selectedFilter = uiState.selectedFilter,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onCopyItem = { item -> viewModel.copyToClipboard(context, item.content, item.title) },
                    onDeleteItem = { id -> viewModel.deleteItem(id) },
                    onClearHistory = { viewModel.clearHistory() }
                )
                4 -> SettingsScreen(
                    settings = settings,
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    isAccessibilityGranted = uiState.isAccessibilityGranted,
                    isBatteryIgnored = uiState.isBatteryOptimizationIgnored,
                    detectedGptPackage = uiState.detectedGptPackage,
                    onUpdateSettings = { viewModel.updateSettings(it) },
                    onToggleBackgroundMode = { viewModel.toggleBackgroundMode(it) },
                    onToggleBackground = { viewModel.toggleBackgroundService(context, it) },
                    onToggleScreenOff = { viewModel.toggleScreenOffExecution(it) },
                    onToggleStrictCode = { viewModel.toggleStrictCodeButtonOnly(it) },
                    onToggleOverlay = { viewModel.toggleFloatingOverlay(context, it) },
                    onRequestBatteryOptimization = { viewModel.requestIgnoreBatteryOptimizations(context) },
                    onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings(context) },
                    onOpenOverlaySettings = { viewModel.openOverlaySettings(context) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBridgeTopBar(
    isBridgeEnabled: Boolean,
    isAccessibilityGranted: Boolean,
    currentState: AutomationState,
    onToggleBridge: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Slate900
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !isBridgeEnabled -> Slate700
                                isAccessibilityGranted -> NeonEmerald.copy(alpha = pulseAlpha)
                                else -> AmberAlert.copy(alpha = pulseAlpha)
                            }
                        )
                )

                Text(
                    text = "AutoBridge",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = currentState.name,
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = Slate400
                    )
                }

                Switch(
                    checked = isBridgeEnabled,
                    onCheckedChange = onToggleBridge,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonEmerald,
                        uncheckedThumbColor = Slate400,
                        uncheckedTrackColor = Slate800
                    ),
                    modifier = Modifier.testTag("master_bridge_switch")
                )
            }
        }
    )
}

@Composable
fun AutoBridgeBottomNav(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Slate900,
        contentColor = Slate200,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val items = listOf(
            Triple("Monitor", Icons.Default.PlayArrow, "monitor_tab"),
            Triple("Debug", Icons.Default.BugReport, "debug_tab"),
            Triple("Sandbox", Icons.Default.Science, "sandbox_tab"),
            Triple("History", Icons.Default.History, "history_tab"),
            Triple("Settings", Icons.Default.Settings, "settings_tab")
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(item.second, contentDescription = item.first) },
                label = { Text(item.first, fontSize = 10.sp) },
                selected = selectedTab == index,
                onClick = { onSelectTab(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = CyberCyan,
                    indicatorColor = CyberCyan,
                    unselectedIconColor = Slate400,
                    unselectedTextColor = Slate400
                ),
                modifier = Modifier.testTag(item.third)
            )
        }
    }
}

// -------------------------------------------------------------
// 1. MONITOR SCREEN
// -------------------------------------------------------------
@Composable
fun MonitorScreen(
    uiState: BridgeUiState,
    settings: BridgeSettings,
    status: com.example.service.BridgeStatus,
    debugMetrics: DebugMetrics,
    gptCount: Int,
    termuxCount: Int,
    totalCount: Int,
    recentItems: List<CapturedItem>,
    onStartAutomation: () -> Unit,
    onToggleBackgroundMode: (Boolean) -> Unit,
    onTestController: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onLaunchGpt: () -> Unit,
    onLaunchTermux: () -> Unit,
    onCopyItem: (CapturedItem) -> Unit,
    onNavigateToTab: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ACCESSIBILITY PERMISSION STATUS CARD
        item {
            if (uiState.isAccessibilityGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NeonEmerald)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility: ENABLED", fontWeight = FontWeight.Bold, color = NeonEmerald, fontSize = 14.sp)
                            Text("Service is active and monitoring ChatGPT & Termux.", color = Slate400, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberAlert.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberAlert)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AmberAlert, modifier = Modifier.size(24.dp))
                            Text("Accessibility: DISABLED (Action Required)", fontWeight = FontWeight.Bold, color = AmberAlert, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please turn ON 'AutoBridge Code Relay' in Android Settings. As soon as you enable it, the app will instantly reflect 'ENABLED'.",
                            fontSize = 12.sp,
                            color = Slate200,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onEnableAccessibility,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAlert, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_accessibility_button")
                        ) {
                            Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // BACKGROUND MODE TOGGLE CARD (User Request: "where is background mode button")
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.backgroundMode) Slate850 else Slate900
                ),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (settings.backgroundMode) CyberCyan else Slate800
                    )
                ),
                modifier = Modifier.fillMaxWidth().testTag("home_background_mode_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Background Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (settings.backgroundMode) CyberCyan.copy(alpha = 0.2f) else Slate800)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (settings.backgroundMode) "ON" else "OFF",
                                    color = if (settings.backgroundMode) CyberCyan else Slate400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (settings.backgroundMode)
                                "ON: ChatGPT will NOT open! Automation runs automatically in the background."
                            else
                                "OFF: Normal visible automation (opens ChatGPT screen).",
                            color = if (settings.backgroundMode) NeonEmerald else Slate400,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = settings.backgroundMode,
                        onCheckedChange = onToggleBackgroundMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyberCyan,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        ),
                        modifier = Modifier.testTag("home_background_mode_switch")
                    )
                }
            }
        }

        // TERMUX CONTROLLER CONNECTION CARD (Requirement: Test Controller Button)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (debugMetrics.controllerConnected) NeonEmerald else AmberAlert
                    )
                ),
                modifier = Modifier.fillMaxWidth().testTag("home_controller_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TERMUX CONTROLLER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)
                            Text("http://127.0.0.1:8765/run", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (debugMetrics.controllerConnected) NeonEmerald.copy(alpha = 0.15f) else AmberAlert.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (debugMetrics.controllerConnected) "CONTROLLER: CONNECTED" else "CONTROLLER: DISCONNECTED",
                                color = if (debugMetrics.controllerConnected) NeonEmerald else AmberAlert,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onTestController,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (debugMetrics.controllerConnected) Slate800 else AmberAlert,
                            contentColor = if (debugMetrics.controllerConnected) CyberCyan else Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_controller_button")
                    ) {
                        Text("Test Controller (echo BRIDGE_CONNECTION_OK)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // PRIMARY AUTOMATION TRIGGER BUTTON
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AUTOMATION PIPELINE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan, letterSpacing = 1.sp)
                        StatusBadge(
                            text = if (settings.backgroundMode) "Mode: BACKGROUND" else "Mode: NORMAL",
                            color = if (settings.backgroundMode) CyberCyan else NeonEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Current State: ${debugMetrics.currentState.name}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (settings.backgroundMode)
                            "Background Mode ON: ChatGPT will NOT be opened. Listens to accessibility and sends commands to http://127.0.0.1:8765/run."
                        else
                            "Normal Mode: Opens ChatGPT, types 'BRIDGE START', detects code responses, and relays outputs via Termux bridge.",
                        fontSize = 12.sp,
                        color = Slate400,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onStartAutomation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (settings.backgroundMode) TerminalGreen else CyberCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("start_bridge_automation_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (settings.backgroundMode) "START AUTOMATION (BACKGROUND MODE)" else "START AUTOMATION (OPEN CHATGPT)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Live Service Metrics Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = status.lastAction,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusBadge(
                            text = if (settings.backgroundMode) "Background Mode: ON" else "Background Mode: OFF",
                            color = if (settings.backgroundMode) CyberCyan else Slate400
                        )
                        if (settings.screenOffExecution) {
                            StatusBadge(text = "Screen-Off Awake: ON", color = NeonEmerald)
                        }
                        if (settings.strictCodeButtonOnly) {
                            StatusBadge(text = "Code Filter: Active", color = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Slate800)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "GPT Code Copied", value = gptCount.toString(), color = CyberCyan)
                        StatItem(label = "Termux Relayed", value = termuxCount.toString(), color = TerminalGreen)
                        StatItem(label = "Total Ops", value = totalCount.toString(), color = Slate200)
                    }
                }
            }
        }

        // Quick Launch Shortcuts
        item {
            Text("QUICK LAUNCH APPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onLaunchGpt,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.5f))),
                    modifier = Modifier.weight(1f).testTag("launch_chatgpt_button")
                ) {
                    Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open ChatGPT", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onLaunchTermux,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalGreen),
                    border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalGreen.copy(alpha = 0.5f))),
                    modifier = Modifier.weight(1f).testTag("launch_termux_button")
                ) {
                    Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Termux", fontSize = 12.sp)
                }
            }
        }

        // Recent Activity Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECENT CAPTURES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)
                Text(
                    text = "View all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberCyan,
                    modifier = Modifier.clickable { onNavigateToTab(3) }.padding(4.dp)
                )
            }
        }

        if (recentItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, tint = Slate700, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No items captured yet", color = Slate400, fontSize = 13.sp)
                        Text("Tap 'START AUTOMATION' or test in the Sandbox tab", color = Slate700, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(recentItems, key = { it.id }) { item ->
                CapturedItemCard(item = item, onCopy = { onCopyItem(item) }, onDelete = null)
            }
        }
    }
}

// -------------------------------------------------------------
// 2. DEBUG MODE SCREEN (Requirement 9)
// -------------------------------------------------------------
@Composable
fun DebugScreen(
    debugMetrics: DebugMetrics,
    onRefresh: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AUTOMATION DEBUG MODE", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text("Real-time pipeline diagnostics & telemetry", fontSize = 12.sp, color = Slate400)
            }
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
            }
        }

        // Live Diagnostic Metrics Table (Exact Format from Requirement)
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DebugLine(label = "Controller URL", value = debugMetrics.controllerUrl, isPositive = true)
                DebugLine(label = "Connection", value = if (debugMetrics.controllerConnected) "CONNECTED" else "DISCONNECTED", isPositive = debugMetrics.controllerConnected)
                DebugLine(label = "Command sent", value = if (debugMetrics.commandSent) "YES" else "NO", isPositive = debugMetrics.commandSent)
                DebugLine(label = "Output received", value = if (debugMetrics.outputReceived) "YES" else "NO", isPositive = debugMetrics.outputReceived)
                DebugLine(label = "Send button", value = if (debugMetrics.sendButtonFound) "FOUND" else "NOT FOUND", isPositive = debugMetrics.sendButtonFound)
                DebugLine(label = "Send action", value = if (debugMetrics.sendActionSuccess) "SUCCESS" else "FAILED", isPositive = debugMetrics.sendActionSuccess)
                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 6.dp))
                DebugLine(label = "BACKGROUND MODE", value = if (debugMetrics.backgroundMode) "ON" else "OFF", isPositive = debugMetrics.backgroundMode)
                DebugLine(label = "CHATGPT FOREGROUND LAUNCH", value = if (debugMetrics.backgroundMode) "DISABLED" else "ENABLED", isPositive = !debugMetrics.backgroundMode)
                DebugLine(label = "ACCESSIBILITY", value = if (debugMetrics.accessibilityEnabled) "ENABLED" else "DISABLED", isPositive = debugMetrics.accessibilityEnabled)
                DebugLine(label = "AUTOMATION", value = "RUNNING", isPositive = true)
                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 6.dp))
                DebugLine(label = "ChatGPT detected", value = if (debugMetrics.chatGptDetected) "YES" else "NO", isPositive = debugMetrics.chatGptDetected)
                DebugLine(label = "ChatGPT response detected", value = if (debugMetrics.chatGptResponseDetected) "YES" else "NO", isPositive = debugMetrics.chatGptResponseDetected)
                DebugLine(label = "Code block detected", value = if (debugMetrics.codeBlockDetected) "YES" else "NO", isPositive = debugMetrics.codeBlockDetected)
                DebugLine(label = "Code length", value = "${debugMetrics.codeLength} chars", isPositive = debugMetrics.codeLength > 0)
                DebugLine(label = "Output copied", value = if (debugMetrics.outputCopied) "YES" else "NO", isPositive = debugMetrics.outputCopied)
                DebugLine(label = "ChatGPT input found", value = if (debugMetrics.chatGptInputFound) "YES" else "NO", isPositive = debugMetrics.chatGptInputFound)
                DebugLine(label = "Startup 'BRIDGE START' sent", value = if (debugMetrics.startupMessageSent) "YES" else "NO", isPositive = debugMetrics.startupMessageSent)
                DebugLine(label = "Current State", value = debugMetrics.currentState.name, isPositive = true)
            }
        }

        // Send Button Node Details (If found)
        if (debugMetrics.lastSendButtonDetails.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CodeBlockBg),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("SEND BUTTON ACCESSIBILITY NODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = debugMetrics.lastSendButtonDetails,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Slate200,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Live Event Logs
        Card(
            colors = CardDefaults.cardColors(containerColor = CodeBlockBg),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("EVENT LOG STREAM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TerminalGreen)
                Spacer(modifier = Modifier.height(8.dp))

                if (debugMetrics.logs.isEmpty()) {
                    Text("No events recorded yet. Automation is listening.", color = Slate700, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                } else {
                    debugMetrics.logs.takeLast(15).forEach { logLine ->
                        Text(
                            text = logLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Slate200,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugLine(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label:", fontSize = 13.sp, color = Slate400)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) NeonEmerald else AmberAlert,
            fontFamily = FontFamily.Monospace
        )
    }
}

// -------------------------------------------------------------
// 3. SANDBOX TESTING SCREEN
// -------------------------------------------------------------
@Composable
fun SandboxScreen(
    uiState: BridgeUiState,
    settings: BridgeSettings,
    onUpdateInput: (String) -> Unit,
    onTestChatGptCode: (String) -> Unit,
    onTestBridgeCommand: (String) -> Unit,
    onCopyLog: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bridge & Parser Sandbox", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Test local bridge (POST http://127.0.0.1:8765/run) and code block extraction directly.", fontSize = 12.sp, color = Slate400)

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.testInputText,
                    onValueChange = onUpdateInput,
                    placeholder = {
                        Text("Enter command (e.g. echo 'Hello Termux') or sample markdown with ```bash code```...", fontSize = 12.sp, color = Slate700)
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("test_input_field"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CodeBlockBg,
                        unfocusedContainerColor = CodeBlockBg,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Slate200,
                        unfocusedTextColor = Slate200
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateInput("Here is the script to run:\n```bash\necho 'AutoBridge connected!'\nuname -a\n```\nRun this in Termux.")
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample Markdown", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onUpdateInput("echo 'Bridge execution test 123'") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample Command", fontSize = 11.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onTestChatGptCode(uiState.testInputText) },
                        enabled = uiState.testInputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("test_gpt_code_button")
                    ) {
                        Text("Test Code Parser", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onTestBridgeCommand(uiState.testInputText) },
                        enabled = uiState.testInputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("test_termux_relay_button")
                    ) {
                        Text("Run on Port 8765", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (uiState.testOutputLog.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CodeBlockBg),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RESULT OUTPUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                        IconButton(onClick = { onCopyLog(uiState.testOutputLog) }, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy log", tint = Slate400, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.testOutputLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Slate200,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. HISTORY SCREEN
// -------------------------------------------------------------
@Composable
fun HistoryScreen(
    capturedItems: List<CapturedItem>,
    searchQuery: String,
    selectedFilter: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onCopyItem: (CapturedItem) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search captured code or outputs...", fontSize = 13.sp, color = Slate400) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_history_field"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate900,
                unfocusedContainerColor = Slate900,
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Slate800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                listOf(
                    Pair("ALL", "All (${capturedItems.size})"),
                    Pair("CHATGPT_CODE", "GPT Code"),
                    Pair("TERMUX_OUTPUT", "Termux Logs")
                ).forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter.first,
                        onClick = { onFilterChange(filter.first) },
                        label = { Text(filter.second, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = Slate900,
                            labelColor = Slate400
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Slate800,
                            selectedBorderColor = CyberCyan,
                            enabled = true,
                            selected = selectedFilter == filter.first
                        )
                    )
                }
            }

            if (capturedItems.isNotEmpty()) {
                IconButton(onClick = onClearHistory, modifier = Modifier.testTag("clear_history_button")) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear all history", tint = Slate400)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (capturedItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Slate700, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No captured history found", color = Slate400, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(capturedItems, key = { it.id }) { item ->
                    CapturedItemCard(
                        item = item,
                        onCopy = { onCopyItem(item) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CapturedItemCard(
    item: CapturedItem,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val isGpt = item.type == CapturedItem.TYPE_CHATGPT_CODE
    val accentColor = if (isGpt) CyberCyan else TerminalGreen
    val relativeTime = DateUtils.getRelativeTimeSpanString(item.timestamp).toString()

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
        modifier = Modifier.fillMaxWidth().testTag("captured_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = if (isGpt) "CHATGPT CODE" else "TERMUX OUTPUT", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    if (item.languageOrTag.isNotBlank()) {
                        Text(text = item.languageOrTag, fontSize = 11.sp, color = Slate400, fontFamily = FontFamily.Monospace)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = relativeTime, fontSize = 11.sp, color = Slate400)
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy code", tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete item", tint = Slate700, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CodeBlockBg).padding(12.dp)
            ) {
                Text(
                    text = item.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isGpt) Slate200 else TerminalGreen,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 5. SETTINGS SCREEN
// -------------------------------------------------------------
@Composable
fun SettingsScreen(
    settings: BridgeSettings,
    hasOverlayPermission: Boolean,
    isAccessibilityGranted: Boolean,
    isBatteryIgnored: Boolean,
    detectedGptPackage: String,
    onUpdateSettings: (BridgeSettings) -> Unit,
    onToggleBackgroundMode: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onToggleStrictCode: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AUTOMATION SECTION (Background Mode Toggle)
        Text("AUTOMATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Background Mode",
                    subtitle = if (settings.backgroundMode) {
                        "ON = ChatGPT ko foreground mein unnecessarily open nahi karega"
                    } else {
                        "OFF = Normal visible automation"
                    },
                    checked = settings.backgroundMode,
                    onCheckedChange = onToggleBackgroundMode,
                    testTag = "toggle_background_mode"
                )
            }
        }

        Text("TERMUX LOCAL BRIDGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Endpoint: http://127.0.0.1:8765/run", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Controller communicates with your existing local bridge running in Termux via POST /run with JSON {\"command\":\"...\"}.", fontSize = 12.sp, color = Slate400)
            }
        }

        Text("BACKGROUND & SCREEN-OFF SUPPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Work in Background (Foreground Service)",
                    subtitle = "Keeps persistent notification alive so automation is never killed by Android.",
                    checked = settings.workInBackground,
                    onCheckedChange = onToggleBackground,
                    testTag = "toggle_work_in_background"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Work when Screen is Off (WakeLock)",
                    subtitle = "Maintains CPU awake state when phone screen is turned off.",
                    checked = settings.screenOffExecution,
                    onCheckedChange = onToggleScreenOff,
                    testTag = "toggle_screen_off_execution"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Disable Battery Optimization", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                        Text(
                            text = if (isBatteryIgnored) "Unrestricted background access active." else "Recommended: Whitelist app from battery saver.",
                            fontSize = 12.sp,
                            color = if (isBatteryIgnored) NeonEmerald else Slate400
                        )
                    }

                    if (!isBatteryIgnored) {
                        Button(onClick = onRequestBatteryOptimization, colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black), shape = RoundedCornerShape(8.dp)) {
                            Text("Whitelist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(NeonEmerald.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("Active", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text("CODE EXTRACTION & RELAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 1.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Pure Code Only (Strip Fences & Chat)",
                    subtitle = "Strips markdown ```, headings, explanations. Never sends chat text to Termux.",
                    checked = settings.strictCodeButtonOnly,
                    onCheckedChange = onToggleStrictCode,
                    testTag = "toggle_strict_code"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Haptic Vibration Feedback",
                    subtitle = "Vibrate briefly whenever code is captured, executed, or relayed.",
                    checked = settings.vibrationFeedback,
                    onCheckedChange = { onUpdateSettings(settings.copy(vibrationFeedback = it)) },
                    testTag = "toggle_vibration"
                )
            }
        }

        // Accessibility Deep Link
        Button(
            onClick = onOpenAccessibilitySettings,
            colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = CyberCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open System Accessibility Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 11.sp, color = Slate400)
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
            Text(text = subtitle, fontSize = 12.sp, color = Slate400, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = NeonEmerald,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
