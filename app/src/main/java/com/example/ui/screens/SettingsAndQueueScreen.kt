package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CallPulseViewModel
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TitaniumGrey
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsAndQueueScreen(
    viewModel: CallPulseViewModel
) {
    val wifiOnly by viewModel.wifiOnlyUpload.collectAsState()
    val apiKey by viewModel.agnesApiKey.collectAsState()
    val backendUrl by viewModel.backendGatewayUrl.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var backendInput by remember(backendUrl) { mutableStateOf(backendUrl) }
    val isKeyConfigured = apiKey.isNotBlank() && apiKey != "YOUR_AGNES_API_KEY"

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title
        Column {
            Text(
                text = "SYSTEM CONFIGURATION",
                color = RadarCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = "Diagnostics & Offline Queue",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Agnes AI Gateway Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Agnes Gateway",
                            tint = RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AGNES AI GATEWAY CONFIG",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        color = if (isKeyConfigured) RadarEmerald.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isKeyConfigured) "KEY VERIFIED" else "PROD STANDBY",
                            color = if (isKeyConfigured) RadarEmerald else WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("AGNES_API_KEY", fontSize = 11.sp) },
                    placeholder = { Text("Enter Agnes AI API Bearer Key...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_api_key_field")
                )

                OutlinedTextField(
                    value = backendInput,
                    onValueChange = { backendInput = it },
                    label = { Text("Backend Proxy Gateway Endpoint", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_gateway_url_field")
                )

                Button(
                    onClick = {
                        viewModel.saveAgnesApiKey(keyInput)
                        viewModel.saveBackendGatewayUrl(backendInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_settings_button")
                ) {
                    Text(
                        text = "SAVE GATEWAY SETTINGS",
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Offline Queue & Wi-Fi Sync Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Wi-Fi Only Telemetry Sync",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Queue audio intelligence uploads until high-speed Wi-Fi is active",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { viewModel.toggleWifiOnly() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadarCyan,
                            checkedTrackColor = Color(0xFF132A42),
                            uncheckedThumbColor = TitaniumGrey,
                            uncheckedTrackColor = Color(0xFF1B2433)
                        ),
                        modifier = Modifier.testTag("wifi_only_toggle_switch")
                    )
                }
            }
        }

        // Foreground Service & Hardware DSP Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FOREGROUND SERVICE DIAGNOSTICS",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                DiagnosticRow("Microphone Service Type", "FOREGROUND_SERVICE_MICROPHONE", RadarEmerald)
                DiagnosticRow("Audio Capture Format", "16kHz Mono 16-Bit PCM", RadarCyan)
                DiagnosticRow("Automatic Gain Control (AGC)", "Attached (-16 LUFS)", RadarEmerald)
                DiagnosticRow("Acoustic Echo Canceler (AEC)", "Hardware Session Active", RadarEmerald)
                DiagnosticRow("High-Pass Rumble Filter", "85Hz Butterworth 2nd-Order", RadarCyan)
                DiagnosticRow("Application Memory Pool", "${telemetryState.memoryUsageMb} MB Allocated", TextSecondary)
            }
        }

        // Walkthrough Relaunch & Cache Purge Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.restartWalkthrough() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("relaunch_walkthrough_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF182A45)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Walkthrough",
                    tint = RadarCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WALKTHROUGH TOUR",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = { viewModel.purgeTelemetryCache() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("purge_cache_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DangerCoral.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Purge",
                    tint = DangerCoral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PURGE CACHE",
                    color = DangerCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
fun DiagnosticRow(
    title: String,
    value: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
