package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianBorderGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumGrey

data class WalkthroughStepData(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
fun WalkthroughModal(
    currentStep: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = listOf(
        WalkthroughStepData(
            title = "Autonomous Telephony Capture",
            subtitle = "Native State Machine & WakeLock",
            description = "CallPulse AI registers a low-level Telephony Broadcast Receiver. When a call triggers OFFHOOK, the service wakes up with partial WakeLock and captures 16kHz mono audio in the background.",
            icon = Icons.Default.PhoneInTalk,
            badge = "STEP 1 OF 5 • TELEPHONY"
        ),
        WalkthroughStepData(
            title = "Voice Level Normalization",
            subtitle = "Automatic Gain Control & 85Hz HPF",
            description = "Dual-microphone capture equalizes remote callers and local speakers to -16 LUFS parity within 0.1 dB. Low-frequency AC rumble below 85Hz is filtered in real-time.",
            icon = Icons.Default.GraphicEq,
            badge = "STEP 2 OF 5 • DSP ENGINE"
        ),
        WalkthroughStepData(
            title = "Interactive Diarization & Aliases",
            subtitle = "Speaker Identification & Renaming",
            description = "Each speaker turn is color-coded with speech tension diagnostics. Tap any speaker pill ('Speaker 2') to persistently rename them to their real identity across the recording.",
            icon = Icons.Default.Group,
            badge = "STEP 3 OF 5 • DIARIZATION"
        ),
        WalkthroughStepData(
            title = "Verbal Contract Guard",
            subtitle = "SHA-256 Cryptographic Agreement Slips",
            description = "Agnes AI isolates verbal commitments, deliverables, and price quotes. Each clause is stamped with a SHA-256 seal and can be dispatched via 1-tap SMS or WhatsApp.",
            icon = Icons.Default.VerifiedUser,
            badge = "STEP 4 OF 5 • CONTRACT GUARD"
        ),
        WalkthroughStepData(
            title = "Autonomous Action Executor",
            subtitle = "Gmail & Google Calendar 1-Tap Sync",
            description = "Extracted commitments are pre-formatted for Android system intents. Tap to open pre-drafted recap emails in Gmail or schedule calendar invites in Google Calendar.",
            icon = Icons.Default.AssignmentTurnedIn,
            badge = "STEP 5 OF 5 • ACTIONS"
        )
    )

    val step = steps[currentStep.coerceIn(0, steps.size - 1)]

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        modifier = Modifier.testTag("walkthrough_modal_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = RadarCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RadarCyan)
                ) {
                    Text(
                        text = step.badge,
                        color = RadarCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = step.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = step.subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step Icon Emblem
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack)
                        .border(1.5.dp, ObsidianBorderGlow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = step.title,
                        tint = RadarCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = step.description,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Step Progress Indicator
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = RadarCyan,
                    trackColor = Color(0xFF16253A)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("walkthrough_next_button")
            ) {
                Text(
                    text = if (currentStep == 4) "START TELEMETRY" else "NEXT STEP",
                    color = ObsidianBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep > 0) {
                    TextButton(onClick = onPrev) {
                        Text("BACK", color = TitaniumGrey, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("walkthrough_dismiss_button")
                ) {
                    Text(
                        text = if (currentStep == 0) "SKIP TOUR" else "CLOSE",
                        color = TitaniumGrey,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    )
}
