package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.db.CallDao
import com.example.db.CallRecordEntity
import com.example.network.ActionItemModel
import com.example.network.AgnesAiClient
import com.example.network.AgnesAnalysisResult
import com.example.network.DiarizedTurnModel
import com.example.network.HesitationSpikeModel
import com.example.network.VerbalContractModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CallRepository(
    private val callDao: CallDao,
    private val context: Context
) {
    private val tag = "CallRepository"
    private val agnesClient = AgnesAiClient()

    val allCalls: Flow<List<CallRecordEntity>> = callDao.getAllCalls()

    fun getCallById(id: String): Flow<CallRecordEntity?> = callDao.getCallById(id)

    fun searchCalls(query: String): Flow<List<CallRecordEntity>> = callDao.searchCalls(query)

    suspend fun insertCall(call: CallRecordEntity) = withContext(Dispatchers.IO) {
        callDao.insertCall(call)
    }

    suspend fun updateCall(call: CallRecordEntity) = withContext(Dispatchers.IO) {
        callDao.updateCall(call)
    }

    suspend fun deleteCall(id: String) = withContext(Dispatchers.IO) {
        callDao.deleteCall(id)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        callDao.deleteAllCalls()
    }

    /**
     * Extracts real PCM audio amplitudes from the recorded WAV file.
     * Computes real RMS envelope across the file for waveform visualization.
     */
    fun extractWaveformAmplitudes(filePath: String, barCount: Int = 56): FloatArray {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 44) {
            return FloatArray(barCount) { 0.05f }
        }
        return try {
            val bytes = file.readBytes()
            val dataBytes = bytes.size - 44
            if (dataBytes <= 0) return FloatArray(barCount) { 0.05f }
            val sampleCount = dataBytes / 2
            val samplesPerBar = (sampleCount / barCount).coerceAtLeast(1)
            val result = FloatArray(barCount)
            var maxAmp = 0.001f

            for (b in 0 until barCount) {
                val startSample = b * samplesPerBar
                val endSample = (startSample + samplesPerBar).coerceAtMost(sampleCount)
                var sumSquares = 0.0
                var count = 0
                for (s in startSample until endSample) {
                    val byteIndex = 44 + s * 2
                    if (byteIndex + 1 < bytes.size) {
                        val low = bytes[byteIndex].toInt() and 0xFF
                        val high = bytes[byteIndex + 1].toInt()
                        val pcm = (high shl 8) or low
                        sumSquares += (pcm.toDouble() * pcm.toDouble())
                        count++
                    }
                }
                val rms = if (count > 0) Math.sqrt(sumSquares / count).toFloat() else 0f
                result[b] = rms
                if (rms > maxAmp) maxAmp = rms
            }

            for (b in 0 until barCount) {
                result[b] = (result[b] / maxAmp).coerceIn(0.08f, 0.95f)
            }
            result
        } catch (e: Exception) {
            Log.w(tag, "Failed to extract waveform amplitudes: ${e.message}")
            FloatArray(barCount) { 0.05f }
        }
    }

    /**
     * Dispatches audio to Agnes AI pipeline, parses structured response, and updates Room record.
     */
    suspend fun analyzeCallWithAgnes(callId: String, audioFile: File, apiKey: String = ""): AgnesAnalysisResult =
        withContext(Dispatchers.IO) {
            val existing = callDao.getCallByIdSync(callId)
            val phone = existing?.phoneNumber ?: "+1 (555) 019-2834"
            val contact = existing?.contactName ?: "Marcus Vance"
            val type = existing?.callType ?: "INCOMING"
            val duration = existing?.durationSeconds ?: 184

            val result = agnesClient.analyzeAudioFile(
                callId = callId,
                audioFile = audioFile,
                phoneNumber = phone,
                contactName = contact,
                callType = type,
                durationSeconds = duration,
                apiKey = apiKey
            )

            // Convert lists to JSON
            val summaryJson = JSONArray(result.executiveSummary).toString()
            val actionsJson = serializeActions(result.actions)
            val contractsJson = serializeContracts(result.verbalContracts)
            val transcriptJson = serializeTranscript(result.diarizedTranscript)
            val spikesJson = serializeSpikes(result.hesitationSpikes)

            val updatedRecord = (existing ?: CallRecordEntity(id = callId, phoneNumber = phone, contactName = contact, callType = type)).copy(
                syncStatus = "SYNCED",
                overallSentiment = result.overallSentiment,
                vocalTensionState = result.vocalTensionState,
                confidencePercentage = result.confidencePercentage,
                executiveSummaryJson = summaryJson,
                actionItemsJson = actionsJson,
                verbalContractsJson = contractsJson,
                transcriptJson = transcriptJson,
                hesitationSpikesJson = spikesJson,
                integratedLufs = result.integratedLufs,
                truePeakDb = result.truePeakDb,
                parityDeltaDb = result.parityDeltaDb
            )

            callDao.insertCall(updatedRecord)
            Log.i(tag, "Successfully updated call record $callId with Agnes AI intelligence.")
            result
        }

    /**
     * Renames a speaker label across the diarized transcript in the recording and persists to Room.
     */
    suspend fun renameSpeakerAlias(callId: String, oldSpeaker: String, newAlias: String) = withContext(Dispatchers.IO) {
        val call = callDao.getCallByIdSync(callId) ?: return@withContext
        val turns = parseTranscript(call.transcriptJson).toMutableList()

        val updatedTurns = turns.map { turn ->
            if (turn.speaker.equals(oldSpeaker, ignoreCase = true)) {
                turn.copy(speaker = newAlias)
            } else {
                turn
            }
        }

        val updatedJson = serializeTranscript(updatedTurns)
        callDao.updateTranscript(callId, updatedJson)
        Log.i(tag, "Renamed speaker '$oldSpeaker' -> '$newAlias' in call $callId")
    }

    /**
     * Toggles an action item's completion state
     */
    suspend fun toggleActionCompletion(callId: String, actionId: String) = withContext(Dispatchers.IO) {
        val call = callDao.getCallByIdSync(callId) ?: return@withContext
        val actions = parseActions(call.actionItemsJson).toMutableList()

        val updated = actions.map { action ->
            if (action.id == actionId) {
                action.copy(completed = !action.completed)
            } else {
                action
            }
        }

        callDao.updateActionItems(callId, serializeActions(updated))
    }

    /**
     * Opens Android System Calendar Intent with pre-filled event details
     */
    fun launchCalendarIntent(action: ActionItemModel) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, action.calendarTitle)
                putExtra(CalendarContract.Events.DESCRIPTION, action.calendarDescription)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.calendarBeginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.calendarEndMillis)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(tag, "Could not launch calendar intent: ${e.message}")
        }
    }

    /**
     * Opens pre-drafted recap email in Gmail
     */
    fun launchGmailIntent(action: ActionItemModel) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(action.emailRecipient))
                putExtra(Intent.EXTRA_SUBJECT, action.emailSubject)
                putExtra(Intent.EXTRA_TEXT, action.emailBody)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(tag, "Could not launch Gmail intent: ${e.message}")
        }
    }

    /**
     * Dispatches Verbal Agreement Slip via SMS or WhatsApp / system share sheet
     */
    fun dispatchAgreementSlip(contract: VerbalContractModel) {
        try {
            val shareText = """
                CallPulse AI • Cryptographic Verbal Agreement Slip
                ═══════════════════════════════════════════
                CLAUSE: ${contract.clauseTitle}
                TERMS: ${contract.terms}
                QUOTE: ${contract.priceQuote}
                RISK RATING: ${contract.disputeRisk}
                
                SHA-256 VERIFICATION HASH:
                ${contract.sha256VerificationHash}
                ═══════════════════════════════════════════
                Verified by CallPulse Agnes AI Security Engine
            """.trimIndent()

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val shareIntent = Intent.createChooser(sendIntent, "Dispatch Verified Agreement Slip").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Log.w(tag, "Failed to dispatch agreement slip: ${e.message}")
        }
    }

    // JSON Serializers & Deserializers
    fun serializeActions(actions: List<ActionItemModel>): String {
        val arr = JSONArray()
        actions.forEach { a ->
            val obj = JSONObject().apply {
                put("id", a.id)
                put("task", a.task)
                put("assignee", a.assignee)
                put("dueDate", a.dueDate)
                put("calendarTitle", a.calendarTitle)
                put("calendarDescription", a.calendarDescription)
                put("calendarBeginMillis", a.calendarBeginMillis)
                put("calendarEndMillis", a.calendarEndMillis)
                put("emailRecipient", a.emailRecipient)
                put("emailSubject", a.emailSubject)
                put("emailBody", a.emailBody)
                put("completed", a.completed)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseActions(json: String): List<ActionItemModel> {
        val list = mutableListOf<ActionItemModel>()
        if (json.isBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ActionItemModel(
                        id = o.optString("id", "ACT-$i"),
                        task = o.optString("task", ""),
                        assignee = o.optString("assignee", ""),
                        dueDate = o.optString("dueDate", ""),
                        calendarTitle = o.optString("calendarTitle", ""),
                        calendarDescription = o.optString("calendarDescription", ""),
                        calendarBeginMillis = o.optLong("calendarBeginMillis", System.currentTimeMillis()),
                        calendarEndMillis = o.optLong("calendarEndMillis", System.currentTimeMillis() + 3600000),
                        emailRecipient = o.optString("emailRecipient", ""),
                        emailSubject = o.optString("emailSubject", ""),
                        emailBody = o.optString("emailBody", ""),
                        completed = o.optBoolean("completed", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing actions JSON: ${e.message}")
        }
        return list
    }

    fun serializeContracts(contracts: List<VerbalContractModel>): String {
        val arr = JSONArray()
        contracts.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("clauseTitle", c.clauseTitle)
                put("terms", c.terms)
                put("priceQuote", c.priceQuote)
                put("deliverables", JSONArray(c.deliverables))
                put("disputeRisk", c.disputeRisk)
                put("riskReasoning", c.riskReasoning)
                put("parties", JSONArray(c.parties))
                put("sha256VerificationHash", c.sha256VerificationHash)
                put("agreementSlip", c.agreementSlip)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseContracts(json: String): List<VerbalContractModel> {
        val list = mutableListOf<VerbalContractModel>()
        if (json.isBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val dList = mutableListOf<String>()
                val dArr = o.optJSONArray("deliverables")
                if (dArr != null) {
                    for (j in 0 until dArr.length()) dList.add(dArr.getString(j))
                }
                val pList = mutableListOf<String>()
                val pArr = o.optJSONArray("parties")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) pList.add(pArr.getString(j))
                }

                list.add(
                    VerbalContractModel(
                        id = o.optString("id", "CTR-$i"),
                        clauseTitle = o.optString("clauseTitle", ""),
                        terms = o.optString("terms", ""),
                        priceQuote = o.optString("priceQuote", ""),
                        deliverables = dList,
                        disputeRisk = o.optString("disputeRisk", "Low"),
                        riskReasoning = o.optString("riskReasoning", ""),
                        parties = pList,
                        sha256VerificationHash = o.optString("sha256VerificationHash", ""),
                        agreementSlip = o.optString("agreementSlip", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing contracts JSON: ${e.message}")
        }
        return list
    }

    fun serializeTranscript(transcript: List<DiarizedTurnModel>): String {
        val arr = JSONArray()
        transcript.forEach { t ->
            val obj = JSONObject().apply {
                put("speaker", t.speaker)
                put("role", t.role)
                put("timestamp", t.timestamp)
                put("seconds", t.seconds)
                put("text", t.text)
                put("stressLevel", t.stressLevel)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseTranscript(json: String): List<DiarizedTurnModel> {
        val list = mutableListOf<DiarizedTurnModel>()
        if (json.isBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    DiarizedTurnModel(
                        speaker = o.optString("speaker", "Speaker"),
                        role = o.optString("role", ""),
                        timestamp = o.optString("timestamp", "00:00"),
                        seconds = o.optInt("seconds", 0),
                        text = o.optString("text", ""),
                        stressLevel = o.optString("stressLevel", "Calm")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing transcript JSON: ${e.message}")
        }
        return list
    }

    fun serializeSpikes(spikes: List<HesitationSpikeModel>): String {
        val arr = JSONArray()
        spikes.forEach { s ->
            val obj = JSONObject().apply {
                put("timestamp", s.timestamp)
                put("seconds", s.seconds)
                put("level", s.level)
                put("note", s.note)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseSpikes(json: String): List<HesitationSpikeModel> {
        val list = mutableListOf<HesitationSpikeModel>()
        if (json.isBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    HesitationSpikeModel(
                        timestamp = o.optString("timestamp", "00:00"),
                        seconds = o.optInt("seconds", 0),
                        level = o.optInt("level", 50),
                        note = o.optString("note", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing spikes JSON: ${e.message}")
        }
        return list
    }

    fun parseExecutiveSummary(json: String): List<String> {
        val list = mutableListOf<String>()
        if (json.isBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing summary JSON: ${e.message}")
        }
        return list
    }
}



