package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.AudioCleanupEngine
import com.example.audio.AudioEngineDiagnostics
import com.example.data.CallRepository
import com.example.db.CallDatabase
import com.example.db.CallRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Foreground Call Recording Service.
 * Captures 16kHz mono audio (MIC-first source selection) with -16 LUFS leveling.
 *
 * Two modes:
 * 1. MONITOR mode (persistent): started ONCE from the app UI. Shows an ongoing
 *    "Call monitoring active" notification and watches phone state from inside
 *    the already-foreground service. When a call goes off-hook, the allowlist is
 *    checked and capture starts directly — no background foreground-service
 *    start, which Android 14+ forbids for the microphone type (while-in-use
 *    permission restriction; the battery exemption does not bypass it).
 * 2. MANUAL recording: started from the UI while the app is in the foreground
 *    (test captures, dialer recordings).
 *
 * Auto-record rule: a call is recorded ONLY when its number is on the user's
 * auto-record allowlist (digit-normalized, last-10 suffix match). Applies to
 * both incoming and outgoing calls.
 */
class CallRecordingService : Service() {

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "callpulse_recording_channel"

        const val ACTION_START_RECORDING = "com.example.callpulse.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.callpulse.STOP_RECORDING"
        const val ACTION_START_MONITORING = "com.example.callpulse.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.callpulse.STOP_MONITORING"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"

        private const val PREFS = "callpulse_prefs"
        private const val PREF_MONITORING_ENABLED = "monitoring_enabled"
        private const val PREF_ALLOWLIST = "auto_record_numbers"

        // Live Telemetry Observables for UI
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _isMonitoring = MutableStateFlow(false)
        val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

        private val _recordingDurationSeconds = MutableStateFlow(0)
        val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

        private val _currentPhoneNumber = MutableStateFlow("")
        val currentPhoneNumber: StateFlow<String> = _currentPhoneNumber.asStateFlow()

        private val _currentCallType = MutableStateFlow("TEST_CAPTURE")
        val currentCallType: StateFlow<String> = _currentCallType.asStateFlow()

        private val _audioDiagnostics = MutableStateFlow(AudioEngineDiagnostics())
        val audioDiagnostics: StateFlow<AudioEngineDiagnostics> = _audioDiagnostics.asStateFlow()

        private val _liveRmsAmplitude = MutableStateFlow(0.0f)
        val liveRmsAmplitude: StateFlow<Float> = _liveRmsAmplitude.asStateFlow()

        /**
         * Manual recording start from the UI (app in foreground). Safe: the
         * foreground-service start happens while the app is in use.
         */
        fun startRecording(
            context: Context,
            phoneNumber: String = "Unknown Caller",
            contactName: String = "Unknown Contact",
            callType: String = "INCOMING"
        ) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording service: ${e.message}")
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }

        /**
         * Starts persistent call monitoring. MUST be called from a foreground
         * (user-visible) context — the Settings UI. That is the only moment
         * Android allows a microphone-type foreground service to start.
         */
        fun startMonitoring(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var captureWakeLock: PowerManager.WakeLock? = null
    private var sessionSetupJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioCleanupEngine: AudioCleanupEngine? = null

    private var rawOutputFile: File? = null
    private var cleanedOutputFile: File? = null
    private var rawFileOutputStream: FileOutputStream? = null
    private var cleanedFileOutputStream: FileOutputStream? = null

    private var isCapturing = false
    private var isMonitoring = false
    private var captureThread: Thread? = null
    private var startTimeMillis: Long = 0
    private var timerJob: Job? = null

    private var activeCallId: String = ""
    private var activePhoneNumber: String = ""
    private var activeContactName: String = ""
    private var activeCallType: String = "TEST_CAPTURE"

    // Phone-state tracking for the in-service monitor (replaces the old manifest receiver)
    private var phoneStateReceiver: BroadcastReceiver? = null
    private var lastTelState = TelephonyManager.EXTRA_STATE_IDLE
    private var savedNumber: String? = null
    private var isIncomingCall = false

    private var sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerPhoneStateReceiver()
        // Sync the UI observable with the persisted monitoring flag (e.g. after rotation).
        _isMonitoring.value = prefs.getBoolean(PREF_MONITORING_ENABLED, false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // Sticky restart (system killed us): resume monitoring only if the user left it on.
        if (action == null) {
            if (prefs.getBoolean(PREF_MONITORING_ENABLED, false)) {
                Log.i(TAG, "Sticky restart — resuming call monitoring.")
                enterMonitoringMode()
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START_MONITORING -> enterMonitoringMode()

            ACTION_STOP_MONITORING -> exitMonitoringMode()

            ACTION_START_RECORDING -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown Caller"
                val contact = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Unknown Contact"
                val type = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "INCOMING"

                if (!isCapturing) {
                    startForegroundWith(buildRecordingNotification(0, -16.0))
                    startCaptureSession(phone, contact, type)
                }
            }

            ACTION_STOP_RECORDING -> {
                if (isCapturing) {
                    stopCaptureSession()
                }
                returnToIdleState()
            }
        }

        return if (isMonitoring || prefs.getBoolean(PREF_MONITORING_ENABLED, false)) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    // -------------------------------------------------------------------------
    // Monitor mode
    // -------------------------------------------------------------------------

    private fun enterMonitoringMode() {
        isMonitoring = true
        _isMonitoring.value = true
        prefs.edit().putBoolean(PREF_MONITORING_ENABLED, true).apply()
        startForegroundWith(buildMonitorNotification())
        Log.i(TAG, "Call monitoring ACTIVE — auto-record armed for allowlist numbers.")
    }

    private fun exitMonitoringMode() {
        if (isCapturing) {
            stopCaptureSession()
        }
        isMonitoring = false
        _isMonitoring.value = false
        prefs.edit().putBoolean(PREF_MONITORING_ENABLED, false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Call monitoring stopped by user.")
    }

    /** After a capture ends: back to the monitor notification, or shut down when not monitoring. */
    private fun returnToIdleState() {
        if (isMonitoring) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildMonitorNotification())
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // -------------------------------------------------------------------------
    // In-service phone-state machine (dynamic receiver — lives as long as the service)
    // -------------------------------------------------------------------------

    private fun registerPhoneStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Intent.ACTION_NEW_OUTGOING_CALL)
        }
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handlePhoneState(intent)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(phoneStateReceiver, filter)
            }
            Log.d(TAG, "Phone-state monitor receiver registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register phone-state receiver: ${e.message}")
        }
    }

    private fun handlePhoneState(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                isIncomingCall = false
                Log.d(TAG, "Outgoing call initiated to: $savedNumber")
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: savedNumber
                if (!number.isNullOrBlank()) {
                    savedNumber = number
                }

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        isIncomingCall = true
                        Log.i(TAG, "Incoming call ringing from $savedNumber")
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        if (lastTelState != TelephonyManager.EXTRA_STATE_OFFHOOK && !isCapturing) {
                            val num = savedNumber ?: "Unknown Caller"
                            val callType = if (isIncomingCall) "INCOMING" else "OUTGOING"
                            if (shouldAutoRecord(num)) {
                                Log.i(TAG, "Allowlist match ($num) — starting capture.")
                                val contact = resolveContactName(num)
                                // Already foregrounded as the monitor: just swap the
                                // notification and start capturing. No background start.
                                startForegroundWith(buildRecordingNotification(0, -16.0))
                                startCaptureSession(num, contact, callType)
                            } else {
                                Log.i(TAG, "Skipping auto-record: $num is not on the allowlist.")
                            }
                        }
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (lastTelState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                            if (isCapturing) {
                                Log.i(TAG, "Call ended — finalizing capture.")
                                stopCaptureSession()
                                returnToIdleState()
                            }
                        }
                        isIncomingCall = false
                        savedNumber = null
                    }
                }
                lastTelState = state
            }
        }
    }

    /**
     * Allowlist-only rule for BOTH directions: record iff the number matches an
     * entry in the user's auto-record list. Digit-normalized with last-10
     * suffix fallback so "+1 (555) 123-4567" matches "5551234567".
     */
    private fun shouldAutoRecord(number: String?): Boolean {
        val allowed = prefs.getStringSet(PREF_ALLOWLIST, emptySet()) ?: emptySet()
        if (allowed.isEmpty()) return false
        val incoming = number?.filter { it.isDigit() } ?: ""
        if (incoming.isEmpty()) return false
        return allowed.any { candidate ->
            val norm = candidate.filter { it.isDigit() }
            norm.isNotEmpty() && (norm == incoming || norm.takeLast(10) == incoming.takeLast(10))
        }
    }

    private fun resolveContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank() || phoneNumber == "Unknown Caller") return "Unknown Contact"
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: phoneNumber else phoneNumber
            } ?: phoneNumber
        } catch (e: Exception) {
            phoneNumber
        }
    }

    // -------------------------------------------------------------------------
    // Foreground plumbing
    // -------------------------------------------------------------------------

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // -------------------------------------------------------------------------
    // Audio capture (unchanged)
    // -------------------------------------------------------------------------

    private fun startCaptureSession(phoneNumber: String, contactName: String, callType: String) {
        // Cancel any half-started session, then set up off the main thread:
        // source probing blocks ~2.5s and must never ANR the UI/receiver thread.
        sessionSetupJob?.cancel()
        if (isCapturing) return

        _currentPhoneNumber.value = phoneNumber
        _currentCallType.value = callType

        sessionSetupJob = serviceScope.launch {
            val probe = selectWorkingAudioSource()
            if (probe == null || !isActive) {
                probe?.record?.release()
                Log.e(TAG, "No working audio source — aborting capture")
                if (isActive) returnToIdleState()
                return@launch
            }

            sampleRate = probe.sampleRate
            audioRecord = probe.record
            val bufferSize = probe.bufferSize

            // Capture-scoped wake lock: held for the whole call, however long.
            // (The old 10-minute onCreate lock could lapse mid-call and cause gaps.)
            try {
                captureWakeLock =
                    (getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallPulseAI::CaptureWakeLock")
                        .apply { acquire() }
            } catch (e: Exception) {
                Log.w(TAG, "Capture wake lock failed: ${e.message}")
            }

            activeCallId = "CALL-${System.currentTimeMillis().toString(36).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"
            activePhoneNumber = phoneNumber
            activeContactName = contactName
            activeCallType = callType
            startTimeMillis = System.currentTimeMillis()

            audioCleanupEngine = AudioCleanupEngine(sampleRate)
            probe.record.audioSessionId.let { sessionId ->
                audioCleanupEngine?.attachToAudioSession(sessionId)
            }

            // Prepare raw and cleaned WAV output files
            val callsDir = File(filesDir, "recordings").apply { mkdirs() }
            rawOutputFile = File(callsDir, "${activeCallId}_raw.wav")
            cleanedOutputFile = File(callsDir, "${activeCallId}_cleaned.wav")

            try {
                rawFileOutputStream = FileOutputStream(rawOutputFile)
                cleanedFileOutputStream = FileOutputStream(cleanedOutputFile)

                // Write 44-byte empty placeholder header (patched at finalization,
                // and every 30s during capture so a killed recording stays playable)
                writeWavHeader(rawFileOutputStream!!, 0, sampleRate, 1, 16)
                writeWavHeader(cleanedFileOutputStream!!, 0, sampleRate, 1, 16)

                // Flush the probe's lead bytes: no call audio is lost to validation.
                rawFileOutputStream?.write(probe.leadBytes)
                val cleanedLead = audioCleanupEngine?.processPcm(probe.leadBytes, probe.leadBytes.size)
                    ?: probe.leadBytes
                cleanedFileOutputStream?.write(cleanedLead, 0, probe.leadBytes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening recording output files: ${e.message}")
                stopCaptureSession()
                returnToIdleState()
                return@launch
            }

            isCapturing = true
            _isRecording.value = true
            _recordingDurationSeconds.value = 0
            // NOTE: probe.record is already started.

            // Launch worker thread for continuous audio buffer read & DSP cleanup
            captureThread = Thread({
                val audioBuffer = ByteArray(bufferSize)
                var consecutiveErrors = 0

                while (isCapturing) {
                    val readBytes = try {
                        audioRecord?.read(audioBuffer, 0, bufferSize) ?: -1
                    } catch (e: Exception) {
                        -1
                    }
                    if (readBytes > 0) {
                        consecutiveErrors = 0

                        // 1. Write raw unaltered PCM
                        try {
                            rawFileOutputStream?.write(audioBuffer, 0, readBytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Raw write failed (storage full?): ${e.message}")
                            break
                        }

                        // 2. Process through DSP Engine (-16 LUFS voice leveling & 85Hz highpass)
                        val cleanedBuffer = audioCleanupEngine?.processPcm(audioBuffer, readBytes) ?: audioBuffer
                        try {
                            cleanedFileOutputStream?.write(cleanedBuffer, 0, readBytes)
                        } catch (e: Exception) {
                            Log.e(TAG, "Cleaned write failed (storage full?): ${e.message}")
                            break
                        }

                        // Update live telemetry
                        val diagnostics = audioCleanupEngine?.getDiagnostics() ?: AudioEngineDiagnostics()
                        _audioDiagnostics.value = diagnostics
                        _liveRmsAmplitude.value = diagnostics.currentRms
                    } else {
                        // Persistent read errors (dead audio HAL, revoked capture):
                        // stop cleanly instead of writing an endless silent file.
                        consecutiveErrors++
                        if (consecutiveErrors > 60) {
                            Log.e(TAG, "AudioRecord.read failing persistently — aborting capture")
                            break
                        }
                        try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                    }
                }
                if (isCapturing) {
                    serviceScope.launch {
                        stopCaptureSession()
                        returnToIdleState()
                    }
                }
            }, "CallPulse-AudioCapture").apply { start() }

            // Launch duration & notification timer job
            timerJob = serviceScope.launch {
                while (isActive && isCapturing) {
                    delay(1000)
                    val elapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                    _recordingDurationSeconds.value = elapsed
                    val lufs = _audioDiagnostics.value.currentLufs
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, buildRecordingNotification(elapsed, lufs))
                    // Keep the WAV headers honest so a process kill mid-call
                    // still leaves playable files.
                    if (elapsed > 0 && elapsed % 30 == 0) {
                        patchWavHeaders()
                    }
                }
            }
        }
    }

    /**
     * Audio source selection, best-first.
     *
     * VOICE_CALL would be ideal (uplink + downlink mix) but it requires the
     * system-only CAPTURE_AUDIO_OUTPUT permission: on most phones a third-party
     * app's VOICE_CALL recorder *initializes* yet captures digital silence.
     * So MIC goes first (what every non-root call recorder uses), then
     * VOICE_COMMUNICATION, with VOICE_CALL kept only as a last resort.
     *
     * Note: with MIC, the far side of the call is only audible via speakerphone
     * (or a Bluetooth speaker) — that is a hardware limit for non-system apps,
     * not something software can bypass.
     */
    /**
     * Audio source selection with live validation, best-first.
     *
     * VOICE_CALL would be ideal (uplink + downlink mix) but it requires the
     * system-only CAPTURE_AUDIO_OUTPUT permission: on most phones a third-party
     * app's VOICE_CALL recorder *initializes* yet captures digital silence.
     * Order: MIC -> VOICE_COMMUNICATION -> VOICE_CALL (last resort).
     *
     * Each candidate is started and probed for ~2.5s: a source that delivers
     * bit-for-bit zero the whole time is a blocked source (even a quiet room
     * flips LSBs), so it is released and the next candidate is tried. Probe
     * bytes are kept and written to the output files, so no call audio is lost.
     * Sample rates fall back 16kHz -> 44.1kHz -> 8kHz when unsupported.
     *
     * Source order follows the community-settled best practice (Cube ACR FAQ):
     * VOICE_RECOGNITION first, then VOICE_COMMUNICATION, then MIC, with
     * VOICE_CALL last (needs a system-only permission; usually silent anyway).
     * Note Android 10+ can silently deprioritize a third-party MIC stream during
     * an active call — the probe catches that and moves on automatically.
     */
    private data class SourceProbe(
        val record: AudioRecord,
        val name: String,
        val sampleRate: Int,
        val bufferSize: Int,
        val leadBytes: ByteArray
    )

    private fun selectWorkingAudioSource(): SourceProbe? {
        val rates = listOf(16000, 44100, 8000)
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL"
        )
        for (rate in rates) {
            val minBuf = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
            if (minBuf <= 0) {
                Log.w(TAG, "Sample rate ${rate}Hz not supported, trying next")
                continue
            }
            val bufferSize = minBuf.coerceAtLeast(4096)
            for ((source, name) in sources) {
                val record = try {
                    AudioRecord(source, rate, channelConfig, audioFormat, bufferSize)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource.$name@${rate}Hz failed (${e.message})")
                    null
                } ?: continue
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioSource.$name@${rate}Hz did not initialize")
                    record.release()
                    continue
                }
                try {
                    record.startRecording()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource.$name@${rate}Hz startRecording threw (${e.message})")
                    record.release()
                    continue
                }
                val leadBytes = probeSource(record, bufferSize)
                if (leadBytes == null) {
                    Log.w(TAG, "AudioSource.$name@${rate}Hz captured digital silence — trying next")
                    try { record.stop() } catch (_: Exception) { }
                    record.release()
                    continue
                }
                Log.i(TAG, "Selected audio source: $name @ ${rate}Hz (${leadBytes.size} lead bytes kept)")
                return SourceProbe(record, name, rate, bufferSize, leadBytes)
            }
        }
        Log.e(TAG, "No working audio source found")
        return null
    }

    /**
     * Reads up to ~2.5s from a started recorder. Returns the bytes captured,
     * or null when every sample was bit-for-bit zero (the signature of a
     * blocked source — real silence still has LSB noise) or reads kept failing.
     */
    private fun probeSource(record: AudioRecord, bufferSize: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val probe = ByteArray(bufferSize)
        val deadline = System.currentTimeMillis() + 2500
        var maxAbs = 0
        var errorStreak = 0
        while (System.currentTimeMillis() < deadline && out.size() < 16000 * 2 * 3) {
            val n = try {
                record.read(probe, 0, bufferSize)
            } catch (e: Exception) {
                -1
            }
            if (n > 0) {
                errorStreak = 0
                out.write(probe, 0, n)
                var i = 0
                while (i < n && maxAbs == 0) {
                    val low = probe[i].toInt() and 0xFF
                    val high = probe[i + 1].toInt()
                    val s = (high shl 8) or low
                    val abs = if (s == Short.MIN_VALUE.toInt()) 32767 else kotlin.math.abs(s)
                    if (abs > maxAbs) maxAbs = abs
                    i += 2
                }
            } else {
                errorStreak++
                if (errorStreak > 20) return null
                try { Thread.sleep(25) } catch (_: InterruptedException) { return null }
            }
        }
        return if (maxAbs > 0) out.toByteArray() else null
    }

    private fun stopCaptureSession() {
        isCapturing = false
        timerJob?.cancel()
        sessionSetupJob?.cancel()
        sessionSetupJob = null

        try {
            if (captureWakeLock?.isHeld == true) {
                captureWakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing capture wakeLock: ${e.message}")
        }
        captureWakeLock = null

        // No session ever started (e.g. monitor stopped while idle) — nothing to finalize.
        if (activeCallId.isEmpty()) {
            return
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        audioCleanupEngine?.release()
        audioCleanupEngine = null

        captureThread?.interrupt()
        captureThread = null

        try {
            rawFileOutputStream?.flush()
            rawFileOutputStream?.close()
            cleanedFileOutputStream?.flush()
            cleanedFileOutputStream?.close()

            // Patch WAV headers with finalized data lengths
            rawOutputFile?.let { finalizeWavFile(it) }
            cleanedOutputFile?.let { finalizeWavFile(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing WAV files: ${e.message}")
        }

        val duration = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
        val finalCallId = activeCallId
        val finalPhone = activePhoneNumber
        val finalContact = activeContactName
        val finalType = activeCallType
        val finalCleanedPath = cleanedOutputFile?.absolutePath ?: ""
        val finalRawPath = rawOutputFile?.absolutePath ?: ""
        val diagnostics = _audioDiagnostics.value

        _isRecording.value = false

        // Insert CallRecordEntity into Room Database and dispatch to Agnes AI
        serviceScope.launch {
            val database = CallDatabase.getDatabase(applicationContext)
            val repository = CallRepository(database.callDao(), applicationContext)

            val newRecord = CallRecordEntity(
                id = finalCallId,
                phoneNumber = finalPhone,
                contactName = finalContact,
                callType = finalType,
                timestamp = startTimeMillis,
                durationSeconds = duration.coerceAtLeast(1),
                audioFilePath = finalCleanedPath,
                cleanedAudioFilePath = finalCleanedPath,
                rawAudioFilePath = finalRawPath,
                syncStatus = "PENDING",
                integratedLufs = diagnostics.currentLufs,
                truePeakDb = diagnostics.truePeakDb,
                parityDeltaDb = diagnostics.parityDeltaDb
            )

            repository.insertCall(newRecord)
            Log.i(TAG, "Inserted call record $finalCallId into database. Dispatching to Agnes AI...")

            // Trigger Agnes AI Speech Intelligence Analysis Pipeline
            repository.analyzeCallWithAgnes(finalCallId, File(finalCleanedPath))
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = totalAudioLen + 36
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 bytes for fmt chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    private fun finalizeWavFile(wavFile: File) {
        if (!wavFile.exists()) return
        val fileLength = wavFile.length()
        val audioDataLength = fileLength - 44
        val totalDataLength = fileLength - 8

        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.seek(4)
            raf.write(
                byteArrayOf(
                    (totalDataLength and 0xff).toByte(),
                    ((totalDataLength shr 8) and 0xff).toByte(),
                    ((totalDataLength shr 16) and 0xff).toByte(),
                    ((totalDataLength shr 24) and 0xff).toByte()
                )
            )
            raf.seek(40)
            raf.write(
                byteArrayOf(
                    (audioDataLength and 0xff).toByte(),
                    ((audioDataLength shr 8) and 0xff).toByte(),
                    ((audioDataLength shr 16) and 0xff).toByte(),
                    ((audioDataLength shr 24) and 0xff).toByte()
                )
            )
        }
    }

    /**
     * Re-patches the WAV headers with current file lengths. Called every 30s
     * during capture so a process kill mid-call still leaves playable files,
     * and once more at finalization.
     */
    private fun patchWavHeaders() {
        try {
            rawFileOutputStream?.flush()
            cleanedFileOutputStream?.flush()
            rawOutputFile?.let { finalizeWavFile(it) }
            cleanedOutputFile?.let { finalizeWavFile(it) }
        } catch (e: Exception) {
            Log.w(TAG, "WAV header patch failed: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------------

    private fun openAppPendingIntent(): PendingIntent {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMonitorNotification(): Notification {
        val stopIntent = Intent(this, CallRecordingService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallPulse AI: Call monitoring active")
            .setContentText("Auto-recording calls from your allowlist — even with the app closed.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop monitoring",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun buildRecordingNotification(durationSeconds: Int, lufs: Double): Notification {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val timerFormatted = String.format("%02d:%02d", minutes, seconds)

        val stopIntent = Intent(this, CallRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallPulse AI: Active Telephony Capture")
            .setContentText("Recording $timerFormatted • DSP Normalized: ${String.format("%.1f", lufs)} LUFS")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop & Analyze",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CallPulse AI Audio Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows call monitoring status and live recording telemetry."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            phoneStateReceiver?.let { unregisterReceiver(it) }
            phoneStateReceiver = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering phone-state receiver: ${e.message}")
        }
        stopCaptureSession()
        super.onDestroy()
    }
}
