package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
 * Foreground Call Recording Service
 * Captures 16kHz mono audio with dual audio fallback (VOICE_CALL fallback to MIC).
 * Employs AudioCleanupEngine for -16 LUFS voice normalization and 85Hz rumble filtering.
 */
class CallRecordingService : Service() {

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "callpulse_recording_channel"

        const val ACTION_START_RECORDING = "com.example.callpulse.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.callpulse.STOP_RECORDING"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"

        // Live Telemetry Observables for UI
        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

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
                // Android 12+ throws ForegroundServiceStartNotAllowedException when the app
                // is in the background without the battery-optimization exemption.
                Log.e(TAG, "Failed to start recording service from background: ${e.message}")
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecord: AudioRecord? = null
    private var audioCleanupEngine: AudioCleanupEngine? = null

    private var rawOutputFile: File? = null
    private var cleanedOutputFile: File? = null
    private var rawFileOutputStream: FileOutputStream? = null
    private var cleanedFileOutputStream: FileOutputStream? = null

    private var isCapturing = false
    private var captureThread: Thread? = null
    private var startTimeMillis: Long = 0
    private var timerJob: Job? = null

    private var activeCallId: String = ""
    private var activePhoneNumber: String = ""
    private var activeContactName: String = ""
    private var activeCallType: String = "TEST_CAPTURE"

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_RECORDING -> {
                val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown Caller"
                val contact = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Unknown Contact"
                val type = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "INCOMING"

                if (!isCapturing) {
                    startForegroundWithMicrophone()
                    startCaptureSession(phone, contact, type)
                }
            }

            ACTION_STOP_RECORDING -> {
                if (isCapturing) {
                    stopCaptureSession()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CallPulseAI::RecordingWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L /* 10 minutes */)
            }
            Log.d(TAG, "Acquired partial WakeLock")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun startForegroundWithMicrophone() {
        val notification = buildRecordingNotification(0, -16.0)
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

    private fun startCaptureSession(phoneNumber: String, contactName: String, callType: String) {
        activeCallId = "CALL-${System.currentTimeMillis().toString(36).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"
        activePhoneNumber = phoneNumber
        activeContactName = contactName
        activeCallType = callType
        startTimeMillis = System.currentTimeMillis()

        _isRecording.value = true
        _recordingDurationSeconds.value = 0
        _currentPhoneNumber.value = phoneNumber
        _currentCallType.value = callType

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

        // Dual Audio Fallback: VOICE_CALL fallback to MIC
        audioRecord = createAudioRecordWithFallback(bufferSize)

        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord with either VOICE_CALL or MIC fallback.")
            stopCaptureSession()
            return
        }

        audioCleanupEngine = AudioCleanupEngine(sampleRate)
        audioRecord?.audioSessionId?.let { sessionId ->
            audioCleanupEngine?.attachToAudioSession(sessionId)
        }

        // Prepare raw and cleaned WAV output files
        val callsDir = File(filesDir, "recordings").apply { mkdirs() }
        rawOutputFile = File(callsDir, "${activeCallId}_raw.wav")
        cleanedOutputFile = File(callsDir, "${activeCallId}_cleaned.wav")

        try {
            rawFileOutputStream = FileOutputStream(rawOutputFile)
            cleanedFileOutputStream = FileOutputStream(cleanedOutputFile)

            // Write 44-byte empty placeholder header (will patch with exact sizes at finalization)
            writeWavHeader(rawFileOutputStream!!, 0, sampleRate, 1, 16)
            writeWavHeader(cleanedFileOutputStream!!, 0, sampleRate, 1, 16)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening recording output files: ${e.message}")
            stopCaptureSession()
            return
        }

        isCapturing = true
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}")
            stopCaptureSession()
            return
        }

        // Launch worker thread for continuous audio buffer read & DSP cleanup
        captureThread = Thread({
            val audioBuffer = ByteArray(bufferSize)
            var totalBytesRead = 0L

            while (isCapturing) {
                val readBytes = audioRecord?.read(audioBuffer, 0, bufferSize) ?: -1
                if (readBytes > 0) {
                    totalBytesRead += readBytes

                    // 1. Write raw unaltered PCM
                    rawFileOutputStream?.write(audioBuffer, 0, readBytes)

                    // 2. Process through DSP Engine (-16 LUFS voice leveling & 85Hz highpass)
                    val cleanedBuffer = audioCleanupEngine?.processPcm(audioBuffer, readBytes) ?: audioBuffer
                    cleanedFileOutputStream?.write(cleanedBuffer, 0, readBytes)

                    // Update live telemetry
                    val diagnostics = audioCleanupEngine?.getDiagnostics() ?: AudioEngineDiagnostics()
                    _audioDiagnostics.value = diagnostics
                    _liveRmsAmplitude.value = diagnostics.currentRms
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
            }
        }
    }

    private fun createAudioRecordWithFallback(bufferSize: Int): AudioRecord? {
        // Try AudioSource.VOICE_CALL first
        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "AudioRecord successfully initialized with AudioSource.VOICE_CALL")
                return record
            } else {
                record.release()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "AudioSource.VOICE_CALL not permitted, falling back to AudioSource.MIC: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "AudioSource.VOICE_CALL failed (${e.message}), falling back to AudioSource.MIC")
        }

        // Fallback to AudioSource.MIC
        try {
            val micRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (micRecord.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "AudioRecord fallback successfully initialized with AudioSource.MIC")
                return micRecord
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioSource.MIC initialization failed: ${e.message}")
        }

        return null
    }

    private fun stopCaptureSession() {
        isCapturing = false
        timerJob?.cancel()

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

    private fun buildRecordingNotification(durationSeconds: Int, lufs: Double): Notification {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val timerFormatted = String.format("%02d:%02d", minutes, seconds)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            .setContentIntent(contentPendingIntent)
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
                description = "Shows live call recording telemetry, LUFS leveling, and stop action."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCaptureSession()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock: ${e.message}")
        }
    }
}
