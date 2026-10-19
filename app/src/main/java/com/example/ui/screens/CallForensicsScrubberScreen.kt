package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.DiarizedTurnModel
import com.example.ui.CallPulseViewModel
import kotlin.math.abs
import kotlin.math.sin
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

@Composable
fun CallForensicsScrubberScreen(
    viewModel: CallPulseViewModel
) {
    val activeCall by viewModel.activeCall.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val isCleanedActive by viewModel.isCleanedAudioActive.collectAsState()
    val waveformAmplitudes by viewModel.currentWaveformAmplitudes.collectAsState()

    val showRenameModal by viewModel.showRenameModal.collectAsState()
    val speakerToRename by viewModel.speakerToRename.collectAsState()
    val renameInput by viewModel.renameInput.collectAsState()

    val transcript = activeCall?.let { viewModel.parseTranscript(it.transcriptJson) } ?: emptyList()
    val totalSeconds = activeCall?.durationSeconds ?: 0
    val currentSeconds = (progress * totalSeconds).toInt()
    val timeFormatted = String.format("%02d:%02d / %02d:%02d", currentSeconds / 60, currentSeconds % 60, totalSeconds / 60, totalSeconds % 60)

    if (activeCall == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack)
                            .border(1.dp, ObsidianBorderGlow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "No Session",
                            tint = RadarCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "NO TELEMETRY SESSION SELECTED",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = "Capture a real cellular call or record local audio from the Radar tab to scrub dual-track waveforms and inspect diarized speech turns.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Button(
                        onClick = { viewModel.selectTab(0) },
                        colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("OPEN TELEPHONY RADAR", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ACOUSTIC FORENSICS & WAVEFORM",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = activeCall?.contactName ?: "Recorded Session",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Surface(
                color = if (isCleanedActive) RadarEmerald.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isCleanedActive) "-16 LUFS LEVELED" else "RAW AUDIO",
                    color = if (isCleanedActive) RadarEmerald else WarningAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Dual-Track Waveform Scrubber Card
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
                    Text(
                        text = "DUAL-TRACK FORENSIC SCRUBBER",
                        color = RadarCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = timeFormatted,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Dual Waveform Canvas
                DualWaveformCanvas(
                    progress = progress,
                    isCleaned = isCleanedActive,
                    amplitudes = waveformAmplitudes,
                    onSeek = { viewModel.seekPlayback(it) }
                )

                // Scrubber Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Playback Speed Button
                    Surface(
                        color = ObsidianBlack,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier
                            .clickable { viewModel.cyclePlaybackSpeed() }
                            .testTag("playback_speed_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = RadarCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${speed}x",
                                color = RadarCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Transport Center: Rewind, Play/Pause, Fast-Forward
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.seekPlayback((progress - 0.05f).coerceAtLeast(0f)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = TitaniumGrey
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RadarCyan)
                                .clickable { viewModel.togglePlayback() }
                                .testTag("waveform_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.seekPlayback((progress + 0.05f).coerceAtMost(1f)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = TitaniumGrey
                            )
                        }
                    }

                    // A/B Toggle Switch Button
                    Surface(
                        color = if (isCleanedActive) RadarEmerald.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCleanedActive) RadarEmerald else WarningAmber
                        ),
                        modifier = Modifier
                            .clickable { viewModel.toggleAudioCleaningAB() }
                            .testTag("audio_ab_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "A/B",
                                tint = if (isCleanedActive) RadarEmerald else WarningAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCleanedActive) "A: CLEANED" else "B: RAW",
                                color = if (isCleanedActive) RadarEmerald else WarningAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Diarized Transcript Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INTERACTIVE DIARIZATION TRANSCRIPT",
                color = RadarCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Tap speaker pill to rename",
                color = TextTertiary,
                fontSize = 10.sp
            )
        }

        // Diarized Speech Bubbles List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("diarized_transcript_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(transcript) { turn ->
                DiarizedSpeechBubble(
                    turn = turn,
                    onRenameSpeaker = { viewModel.openRenameModal(turn.speaker) }
                )
            }
        }
    }

    // Speaker Renaming Modal
    if (showRenameModal) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameModal() },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Rename Speaker Alias",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assign a persistent contact alias for '$speakerToRename' across this entire recording session.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { viewModel.renameInput.value = it },
                        label = { Text("Speaker Alias (e.g. Lead Counsel, Marcus)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("speaker_rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSpeakerRename() },
                    colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("speaker_rename_save_button")
                ) {
                    Text("SAVE ALIAS", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameModal() }) {
                    Text("CANCEL", color = TitaniumGrey)
                }
            }
        )
    }
}

@Composable
fun DualWaveformCanvas(
    progress: Float,
    isCleaned: Boolean,
    amplitudes: FloatArray,
    onSeek: (Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianBlack)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val barCount = 56
        val barWidth = width / (barCount * 1.5f)
        val spacing = barWidth * 0.5f

        // Real PCM audio waveform representation
        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount
            val x = i * (barWidth + spacing) + spacing

            val baseAmp = if (i < amplitudes.size) amplitudes[i] else 0.08f
            val amplitude = if (isCleaned) {
                baseAmp.coerceIn(0.15f, 0.95f)
            } else {
                (baseAmp * 0.85f).coerceIn(0.08f, 0.95f)
            }

            val barHeight = height * amplitude.coerceIn(0.12f, 0.9f)
            val top = (height - barHeight) / 2

            val isPlayed = normalizedX <= progress
            val barColor = if (isPlayed) {
                if (isCleaned) RadarCyan else WarningAmber
            } else {
                Color(0xFF1E2D45)
            }

            drawRect(
                color = barColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight)
            )
        }

        // Playback Position Scrubber Line
        val scrubberX = width * progress
        drawLine(
            color = Color.White,
            start = Offset(scrubberX, 0f),
            end = Offset(scrubberX, height),
            strokeWidth = 2.5f
        )
        drawCircle(
            color = RadarCyan,
            radius = 5.dp.toPx(),
            center = Offset(scrubberX, height / 2)
        )
    }
}

@Composable
fun DiarizedSpeechBubble(
    turn: DiarizedTurnModel,
    onRenameSpeaker: () -> Unit
) {
    val isLocalHost = turn.speaker.contains("1") || turn.role.contains("Host", ignoreCase = true)
    val speakerAccent = if (isLocalHost) RadarCyan else RadarEmerald

    val tensionColor = when (turn.stressLevel) {
        "Elevated" -> DangerCoral
        "Moderate" -> WarningAmber
        else -> RadarEmerald
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Speaker Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker Tag Pill (Clickable to Rename)
                Surface(
                    color = speakerAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, speakerAccent),
                    modifier = Modifier.clickable { onRenameSpeaker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = turn.speaker,
                            color = speakerAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = speakerAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tension State Tag
                    Surface(
                        color = tensionColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${turn.stressLevel.uppercase()} TENSION",
                            color = tensionColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Timestamp
                    Text(
                        text = turn.timestamp,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Dialogue Text
            Text(
                text = turn.text,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
