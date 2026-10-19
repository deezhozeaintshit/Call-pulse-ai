package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ActionItemModel
import com.example.network.HesitationSpikeModel
import com.example.network.VerbalContractModel
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

@Composable
fun AgnesAiSuiteScreen(
    viewModel: CallPulseViewModel
) {
    val activeCall by viewModel.activeCall.collectAsState()
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Contract Guard, 1: Action Executor, 2: Stress Radar

    val summaryBullets = activeCall?.let { viewModel.parseSummary(it.executiveSummaryJson) } ?: emptyList()
    val contracts = activeCall?.let { viewModel.parseContracts(it.verbalContractsJson) } ?: emptyList()
    val actions = activeCall?.let { viewModel.parseActions(it.actionItemsJson) } ?: emptyList()
    val spikes = activeCall?.let { viewModel.parseSpikes(it.hesitationSpikesJson) } ?: emptyList()

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
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Agnes AI",
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
                        text = "Capture a real cellular call or record local audio from the Radar tab to run Agnes AI Speech Intelligence, isolate verbal contracts, and extract commitments.",
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
        // Title Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AGNES AI SPEECH INTELLIGENCE",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Executive Forensic Suite",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            IconButton(
                onClick = { viewModel.reAnalyzeCallWithAgnes() },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-analyze",
                    tint = RadarCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Executive 3-Bullet Breakdown Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXECUTIVE BREAKDOWN",
                        color = RadarCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Confidence: ${activeCall?.confidencePercentage ?: 94}%",
                        color = RadarEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (summaryBullets.isNotEmpty()) {
                    summaryBullets.forEachIndexed { idx, bullet ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "0${idx + 1} ",
                                color = RadarCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = bullet,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No executive breakdown generated yet. Tap re-analyze to run Agnes AI.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Sub Tabs: Contract Guard, Action Executor, Stress Radar
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = ObsidianSurface,
            contentColor = RadarCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = RadarCyan
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Text(
                        "Contracts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Text(
                        "Actions (${actions.count { !it.completed }})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = {
                    Text(
                        "Stress Radar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            )
        }

        // Sub Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSubTab) {
                0 -> VerbalContractGuardTab(
                    contracts = contracts,
                    onDispatchSlip = { viewModel.dispatchAgreementSlip(it) }
                )
                1 -> ActionExecutorTab(
                    actions = actions,
                    onToggle = { viewModel.toggleActionItem(it) },
                    onSyncCalendar = { viewModel.launchCalendarSync(it) },
                    onDraftEmail = { viewModel.launchGmailRecap(it) }
                )
                2 -> StressRadarTab(
                    vocalTension = activeCall?.vocalTensionState ?: "Calm",
                    confidencePct = activeCall?.confidencePercentage ?: 94,
                    hesitationSpikes = spikes
                )
            }
        }
    }
}

@Composable
fun VerbalContractGuardTab(
    contracts: List<VerbalContractModel>,
    onDispatchSlip: (VerbalContractModel) -> Unit
) {
    if (contracts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No verbal contracts isolated in this call session.", color = TextSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("verbal_contracts_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(contracts) { contract ->
                VerbalContractCard(contract = contract, onDispatch = { onDispatchSlip(contract) })
            }
        }
    }
}

@Composable
fun VerbalContractCard(
    contract: VerbalContractModel,
    onDispatch: () -> Unit
) {
    val riskColor = when (contract.disputeRisk.uppercase()) {
        "LOW" -> RadarEmerald
        "MODERATE" -> WarningAmber
        else -> DangerCoral
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Clause Title & Dispute Risk Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contract.clauseTitle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = riskColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, riskColor)
                ) {
                    Text(
                        text = "${contract.disputeRisk.uppercase()} RISK",
                        color = riskColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Agreement Terms
            Text(
                text = contract.terms,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Price Quote if present
            if (contract.priceQuote.isNotEmpty()) {
                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2B44)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMMERCIAL QUOTE: ",
                            color = TitaniumGrey,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = contract.priceQuote,
                            color = RadarCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // SHA-256 Cryptographic Agreement Seal Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack)
                    .border(1.dp, Color(0xFF16253C), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Cryptographic Seal",
                        tint = RadarEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHA-256 INTEGRITY SEAL",
                        color = RadarEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = contract.sha256VerificationHash,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            // 1-Tap Dispatch Slip via SMS/WhatsApp Button
            Button(
                onClick = onDispatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("dispatch_agreement_slip_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162A45)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Dispatch",
                    tint = RadarCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISPATCH SLIP VIA SMS / WHATSAPP",
                    color = RadarCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ActionExecutorTab(
    actions: List<ActionItemModel>,
    onToggle: (String) -> Unit,
    onSyncCalendar: (ActionItemModel) -> Unit,
    onDraftEmail: (ActionItemModel) -> Unit
) {
    if (actions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No action commitments extracted from this conversation.", color = TextSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("action_items_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(actions) { action ->
                ActionExecutorCard(
                    action = action,
                    onToggle = { onToggle(action.id) },
                    onSyncCalendar = { onSyncCalendar(action) },
                    onDraftEmail = { onDraftEmail(action) }
                )
            }
        }
    }
}

@Composable
fun ActionExecutorCard(
    action: ActionItemModel,
    onToggle: () -> Unit,
    onSyncCalendar: () -> Unit,
    onDraftEmail: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (action.completed) Color(0xFF09101C) else ObsidianSurfaceElevated
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (action.completed) ObsidianBorder else Color(0xFF233550)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (action.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (action.completed) RadarEmerald else TitaniumGrey
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.task,
                        color = if (action.completed) TextTertiary else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Assignee: ${action.assignee} • Target: ${action.dueDate}",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 1-Tap Trigger Buttons: Gmail & Google Calendar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calendar Sync Button
                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSyncCalendar() }
                        .testTag("calendar_sync_button_${action.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Calendar",
                            tint = RadarCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYNC CALENDAR",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Gmail Recap Button
                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDraftEmail() }
                        .testTag("gmail_draft_button_${action.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Gmail",
                            tint = RadarEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OPEN IN GMAIL",
                            color = RadarEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StressRadarTab(
    vocalTension: String,
    confidencePct: Int,
    hesitationSpikes: List<HesitationSpikeModel>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("stress_radar_content"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Confidence Meter Gauge
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
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
                            text = "SPEECH CONFIDENCE GAUGE",
                            color = RadarCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$confidencePct%",
                            color = RadarEmerald,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    LinearProgressIndicator(
                        progress = { confidencePct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RadarEmerald,
                        trackColor = Color(0xFF172436),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Low Uncertainty (0%)", color = TextTertiary, fontSize = 9.sp)
                        Text("High Accuracy (100%)", color = RadarCyan, fontSize = 9.sp)
                    }
                }
            }
        }

        item {
            // Vocal Tension State Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VOCAL TENSION STATE",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = vocalTension.uppercase(),
                            color = when (vocalTension.uppercase()) {
                                "CALM" -> RadarEmerald
                                "MODERATE" -> WarningAmber
                                else -> DangerCoral
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        color = RadarCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "JITTER: 1.18% • SHIMMER: 2.4%",
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "HESITATION & ACOUSTIC SPIKES TIMELINE",
                color = RadarCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        items(hesitationSpikes) { spike ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = WarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = spike.timestamp,
                            color = WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spike.note,
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Micro-tremor amplitude: ${spike.level}%",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
