package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CallPulseViewModel
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianBorderGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarCyanDim
import com.example.ui.theme.RadarEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TitaniumGrey
import com.example.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiveTelephonyRadarScreen(
    viewModel: CallPulseViewModel
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val durationSeconds by viewModel.recordingDuration.collectAsState()
    val audioDiag by viewModel.audioDiagnostics.collectAsState()
    val liveRms by viewModel.liveRms.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()

    val dialerNumber by viewModel.dialerNumber.collectAsState()
    val dialerContact by viewModel.dialerContact.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TELEPHONY RADAR",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Live Telephony Capture",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Surface(
                color = if (isRecording) DangerCoral.copy(alpha = 0.15f) else RadarEmerald.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRecording) DangerCoral else RadarEmerald
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) DangerCoral else RadarEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "RECORDING" else "IDLE (STANDBY)",
                        color = if (isRecording) DangerCoral else RadarEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Circular Tactical Radar Sweep
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0C1929),
                            Color(0xFF070E18),
                            ObsidianBlack
                        )
                    )
                )
                .border(1.5.dp, ObsidianBorderGlow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            TacticalRadarCanvas(isRecording = isRecording, liveRms = liveRms)

            // Center Telemetry Label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    val mins = durationSeconds / 60
                    val secs = durationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "-16 LUFS • AGC ACTIVE",
                        color = RadarCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text(
                        text = "MONITOR ACTIVE",
                        color = RadarCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Auto-records on call",
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Production Real Cellular Dialer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "Dialer",
                            tint = RadarEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CELLULAR TELEPHONY DIALER",
                            color = RadarEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (dialerNumber.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            color = DangerCoral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.clearDialer() }
                        )
                    }
                }

                // Number Display Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = dialerNumber.ifEmpty { "Type phone number to dial..." },
                            color = if (dialerNumber.isEmpty()) TextTertiary else TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (dialerContact.isNotEmpty()) {
                            Text(
                                text = "Contact: $dialerContact",
                                color = RadarEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (dialerNumber.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.deleteDialerDigit() },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = TitaniumGrey,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Quick Speed-Dial Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("611" to "Carrier Care", "+18005550199" to "Toll-Free Test").forEach { (num, lbl) ->
                        Surface(
                            color = ObsidianBlack,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .clickable {
                                    viewModel.clearDialer()
                                    num.forEach { ch ->
                                        if (ch.isDigit() || ch == '+' || ch == '*' || ch == '#') {
                                            viewModel.appendDialerDigit(ch.toString())
                                        }
                                    }
                                }
                        ) {
                            Text(
                                text = "$lbl: $num",
                                color = RadarCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Keypad Matrix
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                for (row in keypadRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (digit in row) {
                            KeypadButton(
                                label = digit,
                                onClick = { viewModel.appendDialerDigit(digit) }
                            )
                        }
                    }
                }

                // Primary Action Button: PLACE ACTUAL PHONE CALL
                Button(
                    onClick = {
                        viewModel.placeActualPhoneCall(dialerNumber)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialer_place_actual_call_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Place Phone Call",
                        tint = ObsidianBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (dialerNumber.isNotBlank()) "CALL $dialerNumber" else "OPEN PHONE DIALER",
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "Initiates a real phone call via Android Telephony. The background service automatically detects when the call is answered and records at -16 LUFS.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Live Mic Test Audio Capture Card
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
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (isRecording) DangerCoral else RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LOCAL MICROPHONE TEST",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "16kHz PCM",
                        color = TitaniumGrey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Live Audio Level Visualizer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianBlack)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val barCount = 32
                    for (i in 0 until barCount) {
                        val threshold = i.toFloat() / barCount.toFloat()
                        val active = isRecording && (liveRms >= threshold || i < 2)
                        val barColor = when {
                            i > 24 -> DangerCoral
                            i > 16 -> WarningAmber
                            else -> RadarCyan
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (active) (8 + (i % 5) * 4).dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (active) barColor else Color(0xFF162030))
                        )
                    }
                }

                // In-App Capture Toggle
                Button(
                    onClick = {
                        if (isRecording) {
                            viewModel.stopCallRecording()
                        } else {
                            viewModel.startLocalTestCapture()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("local_mic_test_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) DangerCoral else RadarCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Test Local Mic",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecording) "STOP RECORDING & PROCESS" else "RECORD LOCAL AUDIO TEST",
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Daemon PID & Hardware DSP Telemetry Grid
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
                Text(
                    text = "HARDWARE AUDIO ENGINE DIAGNOSTICS",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryMetricBadge("PID", "${telemetryState.pid}", RadarCyan)
                    TelemetryMetricBadge(
                        "WAKELOCK",
                        if (telemetryState.wakeLockHeld) "HELD" else "STANDBY",
                        if (telemetryState.wakeLockHeld) RadarEmerald else TitaniumGrey
                    )
                    TelemetryMetricBadge(
                        "TARGET LUFS",
                        "${telemetryState.targetLufs} dB",
                        WarningAmber
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryMetricBadge(
                        "AGC PARITY",
                        "Δ ${telemetryState.parityDeltaDb} dB",
                        RadarEmerald
                    )
                    TelemetryMetricBadge(
                        "85Hz FILTER",
                        if (audioDiag.highPassFilterActive) "ENABLED" else "BYPASS",
                        RadarCyan
                    )
                    TelemetryMetricBadge(
                        "AEC / NS",
                        if (audioDiag.echoCancelerEnabled || audioDiag.echoCancelerAvailable) "ATTACHED" else "READY",
                        RadarCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
fun TacticalRadarCanvas(
    isRecording: Boolean,
    liveRms: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.width / 2 - 4

        // Concentric Tactical Circles
        val rings = 4
        for (i in 1..rings) {
            val r = maxRadius * (i.toFloat() / rings)
            drawCircle(
                color = Color(0xFF172942),
                radius = r,
                center = center,
                style = Stroke(width = 1f)
            )
        }

        // Crosshairs
        drawLine(
            color = Color(0xFF132238),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1f
        )
        drawLine(
            color = Color(0xFF132238),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1f
        )

        // Radar Sweep Line
        val rad = Math.toRadians(sweepAngle.toDouble())
        val sweepEnd = Offset(
            (center.x + maxRadius * cos(rad)).toFloat(),
            (center.y + maxRadius * sin(rad)).toFloat()
        )

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    RadarCyanDim,
                    if (isRecording) DangerCoral else RadarCyan
                ),
                start = center,
                end = sweepEnd
            ),
            start = center,
            end = sweepEnd,
            strokeWidth = 2.5f
        )

        // Pulsing Acoustic Blips
        if (isRecording && liveRms > 0.05f) {
            val blipR = maxRadius * 0.65f
            val blipRad = Math.toRadians((sweepAngle - 40).toDouble())
            val blipPos = Offset(
                (center.x + blipR * cos(blipRad)).toFloat(),
                (center.y + blipR * sin(blipRad)).toFloat()
            )
            drawCircle(
                color = DangerCoral.copy(alpha = 0.8f),
                radius = 6f + liveRms * 8f,
                center = blipPos
            )
        }
    }
}

@Composable
fun TelemetryMetricBadge(
    title: String,
    value: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianBlack)
            .border(1.dp, Color(0xFF162338), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun KeypadButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(ObsidianSurfaceElevated)
            .border(1.dp, ObsidianBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
