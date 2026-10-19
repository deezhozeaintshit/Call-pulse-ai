package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.CallRecordEntity
import com.example.ui.CallPulseViewModel
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianBorderGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TitaniumGrey
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryCallLogScreen(
    viewModel: CallPulseViewModel
) {
    val calls by viewModel.callRecords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCallType by viewModel.selectedCallType.collectAsState()
    val selectedSentiment by viewModel.selectedSentiment.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val selectedCallId by viewModel.selectedCallId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FORENSIC CALL LOGS",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Telemetry Archive (${calls.size})",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Multi-Field Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            placeholder = {
                Text(
                    "Search contact, phone, transcript, summary...",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = RadarCyan,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TitaniumGrey,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("call_log_search_field"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = RadarCyan,
                unfocusedBorderColor = ObsidianBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Call Type Filter Chips
            listOf("ALL", "INCOMING", "OUTGOING", "TEST_CAPTURE").forEach { type ->
                FilterChip(
                    selected = selectedCallType == type,
                    onClick = { viewModel.selectedCallType.value = type },
                    label = {
                        Text(
                            text = type.replace("_", " "),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RadarCyan.copy(alpha = 0.2f),
                        selectedLabelColor = RadarCyan,
                        containerColor = ObsidianSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCallType == type,
                        borderColor = ObsidianBorder,
                        selectedBorderColor = RadarCyan
                    )
                )
            }

            // Sentiment Filter Chips
            listOf("ALL", "POSITIVE", "NEUTRAL", "NEGATIVE").forEach { sent ->
                FilterChip(
                    selected = selectedSentiment == sent,
                    onClick = { viewModel.selectedSentiment.value = sent },
                    label = {
                        Text(
                            text = sent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RadarEmerald.copy(alpha = 0.2f),
                        selectedLabelColor = RadarEmerald,
                        containerColor = ObsidianSurface,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedSentiment == sent,
                        borderColor = ObsidianBorder,
                        selectedBorderColor = RadarEmerald
                    )
                )
            }
        }

        // Call List or Empty State Card
        if (calls.isEmpty()) {
            PristineEmptyStateCard(
                searchQuery = searchQuery,
                onClearSearch = { viewModel.searchQuery.value = "" }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("call_records_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    CallLogCard(
                        call = call,
                        isSelected = call.id == selectedCallId,
                        summaryBullets = viewModel.parseSummary(call.executiveSummaryJson),
                        onSelect = {
                            viewModel.selectCall(call.id)
                            viewModel.selectTab(2) // Jump to Call Forensics
                        },
                        onCall = {
                            viewModel.placeActualPhoneCall(call.phoneNumber)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogCard(
    call: CallRecordEntity,
    isSelected: Boolean,
    summaryBullets: List<String>,
    onSelect: () -> Unit,
    onCall: () -> Unit
) {
    val dateFormatted = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(call.timestamp))
    val minutes = call.durationSeconds / 60
    val seconds = call.durationSeconds % 60
    val durationFormatted = String.format("%02d:%02d", minutes, seconds)

    val sentimentColor = when (call.overallSentiment.uppercase()) {
        "POSITIVE" -> RadarEmerald
        "NEGATIVE" -> DangerCoral
        else -> RadarCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("call_card_${call.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF101B2E) else ObsidianSurface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) RadarCyan else ObsidianBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Caller Name + Call Type Icon + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ObsidianBlack)
                            .border(1.dp, ObsidianBorderGlow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (call.callType) {
                                "INCOMING" -> Icons.Default.CallReceived
                                "OUTGOING" -> Icons.Default.CallMade
                                else -> Icons.Default.Call
                            },
                            contentDescription = call.callType,
                            tint = RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = call.contactName,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = call.phoneNumber,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = durationFormatted,
                            color = RadarCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = dateFormatted,
                            color = TextTertiary,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RadarEmerald.copy(alpha = 0.2f))
                            .border(1.dp, RadarEmerald, CircleShape)
                            .testTag("call_back_button_${call.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call ${call.contactName}",
                            tint = RadarEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Tag badges row: Sentiment, LUFS, Verbal Guard Seal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sentiment Badge
                Surface(
                    color = sentimentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = call.overallSentiment,
                        color = sentimentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // LUFS Badge
                Surface(
                    color = Color(0xFF132236),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "DSP",
                            tint = RadarCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.1f", call.integratedLufs)} LUFS",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Agnes Guard Badge
                Surface(
                    color = RadarEmerald.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Contract Guard",
                            tint = RadarEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "AGNES AI VERIFIED",
                            color = RadarEmerald,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Executive Summary Preview (1 or 2 bullets)
            if (summaryBullets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBlack)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    summaryBullets.take(2).forEach { bullet ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "• ",
                                color = RadarCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = bullet,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PristineEmptyStateCard(
    searchQuery: String,
    onClearSearch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    imageVector = Icons.Default.Search,
                    contentDescription = "No Records",
                    tint = RadarCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = if (searchQuery.isNotEmpty()) "NO MATCHING CALL ARCHIVES" else "TELEMETRY ARCHIVE EMPTY",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "No recordings matched query '$searchQuery'. Try adjusting filters or search terms."
                } else {
                    "No telephony captures recorded yet. Use the Radar Tab to initiate a live capture or trigger incoming/outgoing calls."
                },
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (searchQuery.isNotEmpty()) {
                Surface(
                    color = RadarCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onClearSearch() }
                ) {
                    Text(
                        text = "CLEAR FILTERS",
                        color = RadarCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
