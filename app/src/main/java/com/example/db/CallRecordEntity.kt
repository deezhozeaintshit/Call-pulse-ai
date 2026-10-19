package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey
    val id: String,
    val phoneNumber: String,
    val contactName: String,
    val callType: String, // INCOMING, OUTGOING, TEST_CAPTURE
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val audioFilePath: String = "",
    val cleanedAudioFilePath: String = "",
    val rawAudioFilePath: String = "",
    val syncStatus: String = "SYNCED", // SYNCED, PENDING, FAILED, OFFLINE_QUEUED
    val overallSentiment: String = "POSITIVE", // POSITIVE, NEUTRAL, NEGATIVE
    val vocalTensionState: String = "Calm", // Calm, Moderate, Elevated
    val confidencePercentage: Int = 94,
    val executiveSummaryJson: String = "[]",
    val actionItemsJson: String = "[]",
    val verbalContractsJson: String = "[]",
    val transcriptJson: String = "[]",
    val hesitationSpikesJson: String = "[]",
    val integratedLufs: Double = -16.0,
    val truePeakDb: Double = -1.0,
    val parityDeltaDb: Double = 0.08,
    val notes: String = ""
)
