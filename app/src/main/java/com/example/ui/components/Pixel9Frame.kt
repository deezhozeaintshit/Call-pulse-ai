package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumFrameBorder
import com.example.ui.theme.TitaniumMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pixel 9 Pro Titanium Frame Container
 * Features center camera hole cutout, dynamic VoLTE/5G status bar,
 * top telemetry header with walkthrough quick-launch, and bottom gesture bar.
 */
@Composable
fun Pixel9ProFrame(
    isRecording: Boolean,
    onOpenWalkthrough: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentTime = SimpleDateFormat("h:mm", Locale.US).format(Date())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, TitaniumFrameBorder, RoundedCornerShape(24.dp))
                .background(ObsidianBlack)
        ) {
            // Dynamic VoLTE/5G Status Bar with Center Camera Punchhole
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(ObsidianSurface)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Left: Clock & Recording Indicator
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTime,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3366))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "REC",
                            color = Color(0xFFFF3366),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Center: Pixel 9 Pro Camera Cutout
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF030508))
                        .border(1.dp, Color(0xFF1B2433), CircleShape)
                )

                // Right: 5G / VoLTE / Battery / Telemetry Help Trigger
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "5G",
                        color = RadarCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = Icons.Default.NetworkCell,
                        contentDescription = "Cellular",
                        tint = RadarCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wi-Fi",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    IconButton(
                        onClick = onOpenWalkthrough,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("walkthrough_top_bar_trigger")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tour & Architecture",
                            tint = RadarCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Main View Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }

            // Bottom Navigation Gesture Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(ObsidianBlack),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TitaniumMuted)
                )
            }
        }
    }
}
