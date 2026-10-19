package com.example.bridge

import android.content.Context
import android.os.Process
import android.webkit.JavascriptInterface
import com.example.audio.AudioEngineDiagnostics
import com.example.data.CallRepository
import com.example.service.CallRecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Bridge exposing telephony triggers, local Room queries,
 * and hardware diagnostics via @JavascriptInterface and direct native invocation.
 */
class CallPulseNativeBridge(
    private val context: Context,
    private val repository: CallRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    @JavascriptInterface
    fun startRecording(phoneNumber: String, contactName: String, callType: String) {
        CallRecordingService.startRecording(
            context = context,
            phoneNumber = phoneNumber.ifBlank { "+1 (555) 019-2834" },
            contactName = contactName.ifBlank { "Executive Stakeholder" },
            callType = callType.ifBlank { "TEST_CAPTURE" }
        )
    }

    @JavascriptInterface
    fun stopRecording() {
        CallRecordingService.stopRecording(context)
    }

    @JavascriptInterface
    fun isRecording(): Boolean {
        return CallRecordingService.isRecording.value
    }

    @JavascriptInterface
    fun getRecordingDurationSeconds(): Int {
        return CallRecordingService.recordingDurationSeconds.value
    }

    @JavascriptInterface
    fun getHardwareDiagnostics(): String {
        val diag = CallRecordingService.audioDiagnostics.value
        val obj = JSONObject().apply {
            put("pid", Process.myPid())
            put("uid", Process.myUid())
            put("threadId", Thread.currentThread().id)
            put("agcAvailable", diag.agcAvailable)
            put("agcEnabled", diag.agcEnabled)
            put("noiseSuppressorAvailable", diag.noiseSuppressorAvailable)
            put("noiseSuppressorEnabled", diag.noiseSuppressorEnabled)
            put("echoCancelerAvailable", diag.echoCancelerAvailable)
            put("echoCancelerEnabled", diag.echoCancelerEnabled)
            put("highPassFilterActive", diag.highPassFilterActive)
            put("targetLufs", diag.targetLufs)
            put("currentLufs", diag.currentLufs)
            put("truePeakDb", diag.truePeakDb)
            put("parityDeltaDb", diag.parityDeltaDb)
            put("sampleRate", diag.sampleRate)
            put("channelCount", diag.channelCount)
            put("liveRmsAmplitude", CallRecordingService.liveRmsAmplitude.value)
        }
        return obj.toString()
    }

    @JavascriptInterface
    fun getCallRecords(): String {
        return runBlocking {
            val calls = repository.allCalls.firstOrNull() ?: emptyList()
            val arr = JSONArray()
            calls.forEach { call ->
                val obj = JSONObject().apply {
                    put("id", call.id)
                    put("phoneNumber", call.phoneNumber)
                    put("contactName", call.contactName)
                    put("callType", call.callType)
                    put("timestamp", call.timestamp)
                    put("durationSeconds", call.durationSeconds)
                    put("syncStatus", call.syncStatus)
                    put("overallSentiment", call.overallSentiment)
                    put("vocalTensionState", call.vocalTensionState)
                    put("confidencePercentage", call.confidencePercentage)
                    put("executiveSummary", JSONArray(call.executiveSummaryJson))
                    put("integratedLufs", call.integratedLufs)
                    put("truePeakDb", call.truePeakDb)
                    put("parityDeltaDb", call.parityDeltaDb)
                }
                arr.put(obj)
            }
            arr.toString()
        }
    }

    @JavascriptInterface
    fun renameSpeaker(callId: String, oldSpeaker: String, newAlias: String) {
        scope.launch {
            repository.renameSpeakerAlias(callId, oldSpeaker, newAlias)
        }
    }
}
