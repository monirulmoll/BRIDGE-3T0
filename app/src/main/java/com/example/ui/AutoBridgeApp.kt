package com.example.ui

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
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
    val capturedItems by viewModel.capturedItems.collectAsStateWithLifecycle()
    val gptCount by viewModel.gptCodeCount.collectAsStateWithLifecycle()
    val termuxCount by viewModel.termuxOutputCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()

    // Periodically re-check permissions and background state
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
                    gptCount = gptCount,
                    termuxCount = termuxCount,
                    totalCount = totalCount,
                    recentItems = capturedItems.take(5),
                    onEnableAccessibility = { viewModel.openAccessibilitySettings(context) },
                    onRequestBatteryExemption = { viewModel.requestIgnoreBatteryOptimizations(context) },
                    onLaunchGpt = { viewModel.launchApp(context, settings.chatGptPackage, "ChatGPT") },
                    onLaunchTermux = { viewModel.launchApp(context, settings.termuxPackage, "Termux") },
                    onCopyItem = { item -> viewModel.copyToClipboard(context, item.content, item.title) },
                    onNavigateToTab = { selectedTab = it }
                )
                1 -> SandboxScreen(
                    uiState = uiState,
                    settings = settings,
                    onUpdateInput = { viewModel.updateTestInput(it) },
                    onTestChatGptCode = { sample ->
                        viewModel.runSimulatedChatGptCodeDetection(context, sample)
                    },
                    onTestTermuxOutput = { output ->
                        viewModel.runSimulatedTermuxOutputRelay(context, output)
                    },
                    onCopyLog = { log ->
                        viewModel.copyToClipboard(context, log, "AutoBridge Sandbox Log")
                    }
                )
                2 -> HistoryScreen(
                    capturedItems = capturedItems,
                    searchQuery = uiState.searchQuery,
                    selectedFilter = uiState.selectedFilter,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onCopyItem = { item -> viewModel.copyToClipboard(context, item.content, item.title) },
                    onDeleteItem = { id -> viewModel.deleteItem(id) },
                    onClearHistory = { viewModel.clearHistory() }
                )
                3 -> SettingsScreen(
                    settings = settings,
                    hasOverlayPermission = uiState.hasOverlayPermission,
                    isAccessibilityGranted = uiState.isAccessibilityGranted,
                    isBatteryIgnored = uiState.isBatteryOptimizationIgnored,
                    onUpdateSettings = { viewModel.updateSettings(it) },
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
                    fontSize = 19.sp,
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "GPT ↔ Termux",
                        color = CyberCyan,
                        fontSize = 11.sp,
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
            Triple("Sandbox", Icons.Default.Science, "sandbox_tab"),
            Triple("History", Icons.Default.History, "history_tab"),
            Triple("Settings", Icons.Default.Settings, "settings_tab")
        )

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(item.second, contentDescription = item.first) },
                label = { Text(item.first, fontSize = 11.sp) },
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
    gptCount: Int,
    termuxCount: Int,
    totalCount: Int,
    recentItems: List<CapturedItem>,
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
        // Accessibility Permission Notice Banner
        if (!uiState.isAccessibilityGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = AmberAlert.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberAlert)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Accessibility permission needed",
                                tint = AmberAlert,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Accessibility Service Not Active",
                                fontWeight = FontWeight.Bold,
                                color = AmberAlert,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To automatically copy ChatGPT code blocks and capture Termux outputs, please enable the AutoBridge service in Android Settings.",
                            fontSize = 13.sp,
                            color = Slate200,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onEnableAccessibility,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAlert, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_accessibility_button")
                        ) {
                            Text("Open Accessibility Settings", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Screen Off & Battery Optimization Reminder Banner
        if (!uiState.isBatteryOptimizationIgnored && settings.screenOffExecution) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyberCyan.copy(alpha = 0.4f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.BatteryAlert, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                Text("Screen-Off Execution Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text("Allow app to run unrestricted so it keeps working when screen turns off.", color = Slate400, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestBatteryExemption,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Live Service Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BRIDGE STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400,
                            letterSpacing = 1.sp
                        )

                        val statusColor = if (status.isConnected && settings.isBridgeEnabled) NeonEmerald else Slate400
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = if (status.isConnected && settings.isBridgeEnabled) "LISTENING" else "STANDBY",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = status.lastAction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badges for Active Modes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (settings.screenOffExecution) {
                            StatusBadge(text = "Screen-Off CPU Awake: ON", color = NeonEmerald)
                        }
                        if (settings.strictCodeButtonOnly) {
                            StatusBadge(text = "Copy Logo Only: Active", color = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Slate800)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "GPT Code Copied", value = gptCount.toString(), color = CyberCyan)
                        StatItem(label = "Termux Relayed", value = termuxCount.toString(), color = TerminalGreen)
                        StatItem(label = "Total Operations", value = totalCount.toString(), color = Slate200)
                    }
                }
            }
        }

        // Quick Launch App Shortcuts
        item {
            Text(
                text = "QUICK LAUNCH & TARGETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                letterSpacing = 1.sp
            )
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
                    modifier = Modifier
                        .weight(1f)
                        .testTag("launch_chatgpt_button")
                ) {
                    Icon(imageVector = Icons.Default.Launch, contentDescription = "Launch ChatGPT", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch ChatGPT")
                }

                OutlinedButton(
                    onClick = onLaunchTermux,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalGreen),
                    border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalGreen.copy(alpha = 0.5f))),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("launch_termux_button")
                ) {
                    Icon(imageVector = Icons.Default.Terminal, contentDescription = "Launch Termux", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Termux")
                }
            }
        }

        // Bridge Workflow Explanation Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Automated Loop Workflow",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    WorkflowStep(number = "1", title = "Strict ChatGPT Code Copy", desc = "Accessibility scans ChatGPT response, targeting only generated code blocks that have the Copy code button/logo, and puts code in clipboard.")
                    WorkflowStep(number = "2", title = "Termux Auto-Paste (Your Bridge)", desc = "Your existing bridge controller automatically pastes the clipboard code into Termux.")
                    WorkflowStep(number = "3", title = "Termux Output Auto-Capture", desc = "AutoBridge grabs terminal stdout/stderr, formats prompt template, and sends back to ChatGPT even in background/screen-off.")
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
                Text(
                    text = "RECENT CAPTURES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "View all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberCyan,
                    modifier = Modifier
                        .clickable { onNavigateToTab(2) }
                        .padding(4.dp)
                )
            }
        }

        if (recentItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, tint = Slate700, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No items captured yet", color = Slate400, fontSize = 13.sp)
                        Text("Switch to ChatGPT or test in the Sandbox tab", color = Slate700, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(recentItems, key = { it.id }) { item ->
                CapturedItemCard(
                    item = item,
                    onCopy = { onCopyItem(item) },
                    onDelete = null
                )
            }
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
fun WorkflowStep(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(CyberCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = desc, fontSize = 12.sp, color = Slate400, lineHeight = 16.sp)
        }
    }
}

// -------------------------------------------------------------
// 2. SANDBOX TESTING SCREEN
// -------------------------------------------------------------
@Composable
fun SandboxScreen(
    uiState: BridgeUiState,
    settings: BridgeSettings,
    onUpdateInput: (String) -> Unit,
    onTestChatGptCode: (String) -> Unit,
    onTestTermuxOutput: (String) -> Unit,
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
                Text(
                    text = "Bridge Test Sandbox",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Test the exact parsing, code detection, and relay logic without needing to switch apps right now.",
                    fontSize = 12.sp,
                    color = Slate400
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = uiState.testInputText,
                    onValueChange = onUpdateInput,
                    placeholder = {
                        Text(
                            text = "Paste sample ChatGPT response with code or Termux terminal output...",
                            fontSize = 12.sp,
                            color = Slate700
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("test_input_field"),
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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateInput(
                                "Sure! Here is the script:\n```bash\npkg update -y && pkg upgrade -y\npkg install python git -y\necho 'Setup Complete!'\n```\nRun this in Termux."
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample GPT Code", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            onUpdateInput(
                                "Reading package lists... Done\nBuilding dependency tree... Done\nAll packages are up to date.\n~ $ python -V\nPython 3.11.8\n~ $ "
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sample Termux Log", fontSize = 11.sp, maxLines = 1)
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_gpt_code_button")
                    ) {
                        Text("Test GPT Code Copy", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onTestTermuxOutput(uiState.testInputText) },
                        enabled = uiState.testInputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_termux_relay_button")
                    ) {
                        Text("Test Termux Relay", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Test Output Console Card
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
                        Text(
                            text = "SIMULATION OUTPUT / CLIPBOARD LOG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )

                        IconButton(
                            onClick = { onCopyLog(uiState.testOutputLog) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy log", tint = Slate400, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
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
// 3. HISTORY SCREEN
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

        // Search Bar & Filter Chips
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search captured code or outputs...", fontSize = 13.sp, color = Slate400) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_history_field"),
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
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear all history", tint = Slate400)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (capturedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Slate700,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No captured history found", color = Slate400, fontSize = 14.sp)
                    Text("Code copied from ChatGPT will automatically appear here", color = Slate700, fontSize = 12.sp)
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("captured_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isGpt) "CHATGPT CODE" else "TERMUX OUTPUT",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (item.languageOrTag.isNotBlank()) {
                        Text(
                            text = item.languageOrTag,
                            fontSize = 11.sp,
                            color = Slate400,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = relativeTime,
                        fontSize = 11.sp,
                        color = Slate400
                    )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CodeBlockBg)
                    .padding(12.dp)
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
// 4. SETTINGS SCREEN
// -------------------------------------------------------------
@Composable
fun SettingsScreen(
    settings: BridgeSettings,
    hasOverlayPermission: Boolean,
    isAccessibilityGranted: Boolean,
    isBatteryIgnored: Boolean,
    onUpdateSettings: (BridgeSettings) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onToggleStrictCode: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    var promptTemplateText by remember(settings.promptTemplate) {
        mutableStateOf(settings.promptTemplate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // BACKGROUND & SCREEN-OFF EXECUTION SECTION
        Text(
            text = "BACKGROUND & SCREEN-OFF SUPPORT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            letterSpacing = 1.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Work in Background (Foreground Service)",
                    subtitle = "Maintains persistent notification so Android doesn't kill the app in background.",
                    checked = settings.workInBackground,
                    onCheckedChange = onToggleBackground,
                    testTag = "toggle_work_in_background"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Work when Screen is Off (WakeLock)",
                    subtitle = "Keeps CPU awake so code copying and Termux relay continue even when the phone display is turned off.",
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
                        Text(
                            text = "Disable Battery Optimization",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isBatteryIgnored) "Unrestricted background access granted." else "Recommended: Whitelist app from battery saver so Android won't freeze it in sleep mode.",
                            fontSize = 12.sp,
                            color = if (isBatteryIgnored) NeonEmerald else Slate400,
                            lineHeight = 16.sp
                        )
                    }

                    if (!isBatteryIgnored) {
                        Button(
                            onClick = onRequestBatteryOptimization,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Whitelist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonEmerald.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Active", color = NeonEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CHATGPT CODE EXTRACTION FILTERING
        Text(
            text = "CHATGPT CODE FILTERING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            letterSpacing = 1.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Only Copy Generated Code (Copy Logo/Button)",
                    subtitle = "Strictly targets ChatGPT code blocks with the 'Copy code' button or logo. Normal text/messages are never copied.",
                    checked = settings.strictCodeButtonOnly,
                    onCheckedChange = onToggleStrictCode,
                    testTag = "toggle_strict_code_only"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Auto-Copy ChatGPT Code",
                    subtitle = "Automatically copies generated code directly into the system clipboard.",
                    checked = settings.autoCopyChatGptCode,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoCopyChatGptCode = it)) },
                    testTag = "toggle_auto_copy_gpt"
                )
            }
        }

        // AUTOMATION SETTINGS
        Text(
            text = "TERMUX AUTOMATION SETTINGS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            letterSpacing = 1.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Auto-Send Termux Output to ChatGPT",
                    subtitle = "Captures stdout/stderr from Termux and prepares/sends prompt back to ChatGPT.",
                    checked = settings.autoSendTermuxToGpt,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoSendTermuxToGpt = it)) },
                    testTag = "toggle_auto_send_termux"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Auto-Switch to ChatGPT",
                    subtitle = "Automatically brings ChatGPT to front when Termux output is captured.",
                    checked = settings.autoSwitchApp,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoSwitchApp = it)) },
                    testTag = "toggle_auto_switch_app"
                )

                HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "Haptic Vibration Feedback",
                    subtitle = "Vibrate briefly whenever code is copied or sent.",
                    checked = settings.vibrationFeedback,
                    onCheckedChange = { onUpdateSettings(settings.copy(vibrationFeedback = it)) },
                    testTag = "toggle_vibration"
                )
            }
        }

        // Custom Prompt Template
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Termux Output Prompt Template",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "Customize the message sent to ChatGPT when forwarding Termux output. Use {OUTPUT} placeholder.",
                    fontSize = 12.sp,
                    color = Slate400
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = promptTemplateText,
                    onValueChange = {
                        promptTemplateText = it
                        onUpdateSettings(settings.copy(promptTemplate = it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CodeBlockBg,
                        unfocusedContainerColor = CodeBlockBg,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Slate200,
                        unfocusedTextColor = Slate200
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        promptTemplateText = BridgeSettings.DEFAULT_PROMPT_TEMPLATE
                        onUpdateSettings(settings.copy(promptTemplate = BridgeSettings.DEFAULT_PROMPT_TEMPLATE))
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset to Default Template", fontSize = 11.sp)
                }
            }
        }

        // Floating Overlay Control
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Quick-Action Pill",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Display a draggable floating button on top of Termux to 1-tap capture & relay output.",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }

                    Switch(
                        checked = settings.floatingOverlayEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasOverlayPermission) {
                                onOpenOverlaySettings()
                            } else {
                                onToggleOverlay(enabled)
                            }
                        }
                    )
                }

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Requires 'Display over other apps' permission",
                        fontSize = 11.sp,
                        color = AmberAlert
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onOpenOverlaySettings,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant Overlay Permission", fontSize = 11.sp)
                    }
                }
            }
        }

        // Accessibility Service Deep Link
        Button(
            onClick = onOpenAccessibilitySettings,
            colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = CyberCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("System Accessibility Settings", fontWeight = FontWeight.SemiBold)
        }
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
