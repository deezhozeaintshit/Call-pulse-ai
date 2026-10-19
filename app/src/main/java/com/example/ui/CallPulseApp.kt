package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CallPulseTopBar
import com.example.ui.screens.AgnesAiSuiteScreen
import com.example.ui.screens.CallForensicsScrubberScreen
import com.example.ui.screens.LiveTelephonyRadarScreen
import com.example.ui.screens.SettingsAndQueueScreen
import com.example.ui.screens.TelemetryCallLogScreen
import com.example.ui.screens.WalkthroughModal
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumGrey

data class NavigationTabItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)

@Composable
fun CallPulseApp(
    viewModel: CallPulseViewModel
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val showWalkthrough by viewModel.showWalkthrough.collectAsState()
    val walkthroughStep by viewModel.walkthroughStep.collectAsState()

    val navItems = listOf(
        NavigationTabItem("Radar", Icons.Default.Radar, "nav_tab_radar"),
        NavigationTabItem("Calls", Icons.AutoMirrored.Filled.ListAlt, "nav_tab_calls"),
        NavigationTabItem("Forensics", Icons.Default.GraphicEq, "nav_tab_forensics"),
        NavigationTabItem("Agnes AI", Icons.Default.AutoAwesome, "nav_tab_agnes"),
        NavigationTabItem("Settings", Icons.Default.Settings, "nav_tab_system")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBlack,
        topBar = {
            CallPulseTopBar(
                isRecording = isRecording,
                recordingDurationSeconds = recordingDuration,
                onOpenWalkthrough = { viewModel.restartWalkthrough() }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    windowInsets = androidx.compose.material3.NavigationBarDefaults.windowInsets
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = currentTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(index) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianBlack,
                                selectedTextColor = RadarCyan,
                                indicatorColor = RadarCyan,
                                unselectedIconColor = TitaniumGrey,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(bottom = 12.dp)
        ) {
            Crossfade(targetState = currentTab, label = "ScreenTransition") { tab ->
                when (tab) {
                    0 -> LiveTelephonyRadarScreen(viewModel = viewModel)
                    1 -> TelemetryCallLogScreen(viewModel = viewModel)
                    2 -> CallForensicsScrubberScreen(viewModel = viewModel)
                    3 -> AgnesAiSuiteScreen(viewModel = viewModel)
                    4 -> SettingsAndQueueScreen(viewModel = viewModel)
                }
            }
        }
    }

    // 5-Step First-Time Guided Walkthrough Modal
    if (showWalkthrough) {
        WalkthroughModal(
            currentStep = walkthroughStep,
            onNext = { viewModel.nextWalkthroughStep() },
            onPrev = { viewModel.prevWalkthroughStep() },
            onDismiss = { viewModel.completeWalkthrough() }
        )
    }
}
