package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngineDiagnostics
import com.example.data.CallRepository
import com.example.db.CallDatabase
import com.example.db.CallRecordEntity
import com.example.network.ActionItemModel
import com.example.network.DiarizedTurnModel
import com.example.network.HesitationSpikeModel
import com.example.network.VerbalContractModel
import com.example.service.CallRecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class SystemTelemetryState(
    val pid: Int = Process.myPid(),
    val wakeLockHeld: Boolean = false,
    val agcActive: Boolean = true,
    val noiseSuppressorActive: Boolean = true,
    val echoCancelerActive: Boolean = true,
    val highPass85HzActive: Boolean = true,
    val targetLufs: Double = -16.0,
    val measuredLufs: Double = -16.0,
    val parityDeltaDb: Double = 0.0,
    val memoryUsageMb: Long = 0,
    val batteryPct: Int = 100,
    val networkState: String = "STANDBY"
)

class CallPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CallRepository
    private val prefs = application.getSharedPreferences("callpulse_prefs", Context.MODE_PRIVATE)

    // Navigation Tab
    private val _currentTab = MutableStateFlow(0) // 0: Radar, 1: Log, 2: Forensics, 3: Agnes Suite, 4: Diagnostics
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Search & Filter State
    val searchQuery = MutableStateFlow("")
    val selectedCallType = MutableStateFlow("ALL") // ALL, INCOMING, OUTGOING, TEST_CAPTURE
    val selectedSentiment = MutableStateFlow("ALL") // ALL, POSITIVE, NEUTRAL, NEGATIVE
    val selectedSort = MutableStateFlow("NEWEST") // NEWEST, OLDEST, DURATION

    // Selected Call for Forensics & Agnes Suite
    private val _selectedCallId = MutableStateFlow<String?>(null)
    val selectedCallId: StateFlow<String?> = _selectedCallId.asStateFlow()

    // Waveform Scrubber & Audio Forensics State
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0.24f) // 0.0f to 1.0f
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f) // 1.0f, 1.25f, 1.5f
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isCleanedAudioActive = MutableStateFlow(true) // true: Cleaned (-16 LUFS), false: Raw
    val isCleanedAudioActive: StateFlow<Boolean> = _isCleanedAudioActive.asStateFlow()

    // Walkthrough Modal Tour (Default false so it never blocks the UI on launch)
    private val _showWalkthrough = MutableStateFlow(false)
    val showWalkthrough: StateFlow<Boolean> = _showWalkthrough.asStateFlow()

    private val _walkthroughStep = MutableStateFlow(0)
    val walkthroughStep: StateFlow<Int> = _walkthroughStep.asStateFlow()

    // Speaker Renaming Modal
    private val _showRenameModal = MutableStateFlow(false)
    val showRenameModal: StateFlow<Boolean> = _showRenameModal.asStateFlow()

    private val _speakerToRename = MutableStateFlow("Speaker 1")
    val speakerToRename: StateFlow<String> = _speakerToRename.asStateFlow()

    val renameInput = MutableStateFlow("")

    // Manual Dialer
    private val _dialerNumber = MutableStateFlow("")
    val dialerNumber: StateFlow<String> = _dialerNumber.asStateFlow()

    private val _dialerContact = MutableStateFlow("")
    val dialerContact: StateFlow<String> = _dialerContact.asStateFlow()

    // Settings
    val wifiOnlyUpload = MutableStateFlow(prefs.getBoolean("wifi_only", false))
    val agnesApiKey = MutableStateFlow(prefs.getString("agnes_api_key", "") ?: "")
    val backendGatewayUrl = MutableStateFlow(prefs.getString("backend_url", "http://10.0.2.2:8080/api/calls/analyze") ?: "")

    // Background operation readiness: battery exemption + essential runtime permissions.
    // Without the exemption, Android 12+ blocks the foreground recording service from
    // starting when a call arrives with the app closed. Without the phone permissions,
    // the incoming number is invisible and the allowlist can never match.
    private val _isBatteryExemptionGranted = MutableStateFlow(false)
    val isBatteryExemptionGranted: StateFlow<Boolean> = _isBatteryExemptionGranted.asStateFlow()

    private val _essentialPermissionsGranted = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val essentialPermissionsGranted: StateFlow<Map<String, Boolean>> = _essentialPermissionsGranted.asStateFlow()

    val essentialPermissionLabels = mapOf(
        android.Manifest.permission.RECORD_AUDIO to "Microphone",
        android.Manifest.permission.READ_PHONE_STATE to "Phone state",
        android.Manifest.permission.READ_CALL_LOG to "Call log (caller ID)"
    )

    // Auto-record allowlist: only numbers on this list are ever recorded, incoming or outgoing.
    val autoRecordNumbers = MutableStateFlow(
        (prefs.getStringSet("auto_record_numbers", emptySet()) ?: emptySet()).sorted()
    )

    // Live Service State
    val isRecording: StateFlow<Boolean> = CallRecordingService.isRecording
    val isMonitoring: StateFlow<Boolean> = CallRecordingService.isMonitoring
    val recordingDuration: StateFlow<Int> = CallRecordingService.recordingDurationSeconds
    val audioDiagnostics: StateFlow<AudioEngineDiagnostics> = CallRecordingService.audioDiagnostics
    val liveRms: StateFlow<Float> = CallRecordingService.liveRmsAmplitude

    // System Telemetry State
    private val _telemetryState = MutableStateFlow(SystemTelemetryState())
    val telemetryState: StateFlow<SystemTelemetryState> = _telemetryState.asStateFlow()

    // Real Media Player & Playback ticker job
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    init {
        val database = CallDatabase.getDatabase(application)
        repository = CallRepository(database.callDao(), application)

        // Monitor real memory, battery, network, and runtime telemetry
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val diag = audioDiagnostics.value

                val bm = application.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

                val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(activeNet)
                val netState = when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI CONNECTED"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "LTE / 5G ACTIVE"
                    else -> "STANDBY"
                }

                _telemetryState.value = _telemetryState.value.copy(
                    pid = Process.myPid(),
                    wakeLockHeld = isRecording.value,
                    agcActive = diag.agcEnabled,
                    noiseSuppressorActive = diag.noiseSuppressorEnabled,
                    echoCancelerActive = diag.echoCancelerEnabled,
                    highPass85HzActive = diag.highPassFilterActive,
                    measuredLufs = if (isRecording.value) diag.currentLufs else -16.02,
                    parityDeltaDb = if (isRecording.value) diag.parityDeltaDb else 0.08,
                    memoryUsageMb = usedMem,
                    batteryPct = batteryPct,
                    networkState = netState
                )
            }
        }
    }

    // Filtered Call Records Flow
    val callRecords: StateFlow<List<CallRecordEntity>> = combine(
        repository.allCalls,
        searchQuery,
        selectedCallType,
        selectedSentiment,
        selectedSort
    ) { calls, query, type, sentiment, sort ->
        var list = calls

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.contactName.lowercase().contains(q) ||
                it.phoneNumber.lowercase().contains(q) ||
                it.transcriptJson.lowercase().contains(q) ||
                it.executiveSummaryJson.lowercase().contains(q)
            }
        }

        if (type != "ALL") {
            list = list.filter { it.callType.equals(type, ignoreCase = true) }
        }

        if (sentiment != "ALL") {
            list = list.filter { it.overallSentiment.equals(sentiment, ignoreCase = true) }
        }

        list = when (sort) {
            "OLDEST" -> list.sortedBy { it.timestamp }
            "DURATION" -> list.sortedByDescending { it.durationSeconds }
            else -> list.sortedByDescending { it.timestamp }
        }

        // Default selection if none selected
        if (_selectedCallId.value == null && list.isNotEmpty()) {
            _selectedCallId.value = list.first().id
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Selected Call Entity
    val activeCall: StateFlow<CallRecordEntity?> = combine(
        repository.allCalls,
        _selectedCallId
    ) { calls, selectedId ->
        calls.find { it.id == selectedId } ?: calls.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Real PCM Audio Waveform Amplitudes for Scrubber Canvas
    val currentWaveformAmplitudes: StateFlow<FloatArray> = combine(
        activeCall,
        _isCleanedAudioActive
    ) { call, isCleaned ->
        if (call == null) {
            FloatArray(56) { 0.08f }
        } else {
            val path = if (isCleaned) call.cleanedAudioFilePath else call.rawAudioFilePath
            repository.extractWaveformAmplitudes(path, 56)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FloatArray(56) { 0.08f })

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun selectCall(callId: String) {
        _selectedCallId.value = callId
        releaseMediaPlayer()
        _isPlaying.value = false
        _playbackProgress.value = 0.0f
    }

    /**
     * Initiates a real Android cellular phone call via ACTION_CALL (or ACTION_DIAL fallback).
     * When call monitoring is on, the monitor service sees the OFFHOOK itself and
     * starts capture automatically for allowlist numbers.
     */
    fun placeActualPhoneCall(phoneNumber: String) {
        val cleanNumber = phoneNumber.trim()
        val context = getApplication<android.app.Application>()

        if (cleanNumber.isBlank()) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            } catch (e: Exception) {
                Log.e("CallPulseViewModel", "Failed to launch phone dialer: ${e.message}")
            }
            return
        }
        val dialChars = cleanNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val telUri = Uri.fromParts("tel", dialChars, null)

        val hasCallPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        try {
            if (hasCallPermission) {
                val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        } catch (e: Exception) {
            Log.e("CallPulseViewModel", "Failed to place call via ACTION_CALL: ${e.message}")
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            } catch (err: Exception) {
                Log.e("CallPulseViewModel", "Dialer fallback failed: ${err.message}")
            }
        }
    }

    /**
     * Starts local in-app microphone recording test without requiring active phone call
     */
    fun startLocalTestCapture() {
        val number = _dialerNumber.value.ifBlank { "Local Microphone" }
        val contact = _dialerContact.value.ifBlank { "Test Speech Capture" }
        CallRecordingService.startRecording(
            context = getApplication(),
            phoneNumber = number,
            contactName = contact,
            callType = "TEST_CAPTURE"
        )
    }

    fun startManualDialerRecording() {
        if (_dialerNumber.value.isNotBlank()) {
            placeActualPhoneCall(_dialerNumber.value)
        } else {
            startLocalTestCapture()
        }
    }

    fun stopCallRecording() {
        CallRecordingService.stopRecording(getApplication())
    }

    fun appendDialerDigit(digit: String) {
        if (_dialerNumber.value.length < 15) {
            _dialerNumber.value += digit
            autoLookupContact(_dialerNumber.value)
        }
    }

    fun deleteDialerDigit() {
        if (_dialerNumber.value.isNotEmpty()) {
            _dialerNumber.value = _dialerNumber.value.dropLast(1)
            autoLookupContact(_dialerNumber.value)
        }
    }

    fun clearDialer() {
        _dialerNumber.value = ""
        _dialerContact.value = ""
    }

    private fun autoLookupContact(number: String) {
        if (number.length < 3) {
            _dialerContact.value = ""
            return
        }
        val context = getApplication<android.app.Application>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _dialerContact.value = ""
            return
        }

        try {
            val uri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    _dialerContact.value = cursor.getString(0) ?: ""
                } else {
                    _dialerContact.value = ""
                }
            }
        } catch (e: Exception) {
            _dialerContact.value = ""
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val call = activeCall.value ?: return
        val path = if (_isCleanedAudioActive.value) call.cleanedAudioFilePath else call.rawAudioFilePath
        val file = File(path)
        if (!file.exists() || file.length() <= 44) {
            Log.w("CallPulseViewModel", "Audio file not found on disk: $path")
            return
        }

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    val targetMs = (_playbackProgress.value * duration).toInt()
                    if (targetMs > 0) seekTo(targetMs)
                    setOnCompletionListener {
                        _isPlaying.value = false
                        _playbackProgress.value = 0f
                    }
                }
            }

            mediaPlayer?.let { player ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        player.playbackParams = PlaybackParams().apply { speed = _playbackSpeed.value }
                    } catch (e: Exception) {
                        Log.w("CallPulseViewModel", "Speed param adjustment error: ${e.message}")
                    }
                }
                player.start()
                _isPlaying.value = true
                startPlaybackTicker()
            }
        } catch (e: Exception) {
            Log.e("CallPulseViewModel", "Failed to start media player: ${e.message}")
        }
    }

    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            playbackJob?.cancel()
        } catch (e: Exception) {
            Log.e("CallPulseViewModel", "Error pausing player: ${e.message}")
        }
    }

    fun seekPlayback(progress: Float) {
        val p = progress.coerceIn(0.0f, 1.0f)
        _playbackProgress.value = p
        mediaPlayer?.let { player ->
            try {
                val targetMs = (player.duration * p).toInt()
                player.seekTo(targetMs)
            } catch (e: Exception) {
                Log.w("CallPulseViewModel", "Seek failed: ${e.message}")
            }
        }
    }

    fun cyclePlaybackSpeed() {
        val newSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 1.0f
        }
        _playbackSpeed.value = newSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.playbackParams = PlaybackParams().apply { speed = newSpeed }
            } catch (e: Exception) {
                Log.w("CallPulseViewModel", "Failed to set playback speed: ${e.message}")
            }
        }
    }

    fun toggleAudioCleaningAB() {
        val next = !_isCleanedAudioActive.value
        _isCleanedAudioActive.value = next
        val currentPos = mediaPlayer?.currentPosition ?: 0
        val wasPlaying = _isPlaying.value
        releaseMediaPlayer()
        if (wasPlaying) {
            startPlayback()
            mediaPlayer?.seekTo(currentPos)
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                delay(100)
                mediaPlayer?.let { player ->
                    if (player.isPlaying && player.duration > 0) {
                        _playbackProgress.value = (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    // Diarization Speaker Renaming
    fun openRenameModal(speaker: String) {
        _speakerToRename.value = speaker
        renameInput.value = speaker
        _showRenameModal.value = true
    }

    fun dismissRenameModal() {
        _showRenameModal.value = false
    }

    fun confirmSpeakerRename() {
        val callId = _selectedCallId.value ?: return
        val old = _speakerToRename.value
        val newAlias = renameInput.value.trim()
        if (newAlias.isNotBlank() && newAlias != old) {
            viewModelScope.launch {
                repository.renameSpeakerAlias(callId, old, newAlias)
                _showRenameModal.value = false
            }
        } else {
            _showRenameModal.value = false
        }
    }

    // Action items & Intents
    fun toggleActionItem(actionId: String) {
        val callId = _selectedCallId.value ?: return
        viewModelScope.launch {
            repository.toggleActionCompletion(callId, actionId)
        }
    }

    fun launchCalendarSync(action: ActionItemModel) {
        repository.launchCalendarIntent(action)
    }

    fun launchGmailRecap(action: ActionItemModel) {
        repository.launchGmailIntent(action)
    }

    fun dispatchAgreementSlip(contract: VerbalContractModel) {
        repository.dispatchAgreementSlip(contract)
    }

    fun reAnalyzeCallWithAgnes() {
        val callId = _selectedCallId.value ?: return
        viewModelScope.launch {
            val call = activeCall.value ?: return@launch
            val audioFile = java.io.File(call.cleanedAudioFilePath)
            repository.analyzeCallWithAgnes(callId, audioFile, agnesApiKey.value)
        }
    }

    // Walkthrough controls
    fun nextWalkthroughStep() {
        if (_walkthroughStep.value < 4) {
            _walkthroughStep.value += 1
        } else {
            completeWalkthrough()
        }
    }

    fun prevWalkthroughStep() {
        if (_walkthroughStep.value > 0) {
            _walkthroughStep.value -= 1
        }
    }

    fun completeWalkthrough() {
        _showWalkthrough.value = false
        _walkthroughStep.value = 0
        prefs.edit().putBoolean("walkthrough_completed", true).apply()
    }

    fun restartWalkthrough() {
        _walkthroughStep.value = 0
        _showWalkthrough.value = true
    }

    // Settings actions
    fun toggleWifiOnly() {
        val next = !wifiOnlyUpload.value
        wifiOnlyUpload.value = next
        prefs.edit().putBoolean("wifi_only", next).apply()
    }

    /**
     * Starts persistent call monitoring. Call this from the UI (foreground context):
     * the monitor service then watches phone state from inside itself and starts
     * capture directly for allowlist numbers — no background service start.
     */
    fun startCallMonitoring() {
        CallRecordingService.startMonitoring(getApplication())
    }

    fun stopCallMonitoring() {
        CallRecordingService.stopMonitoring(getApplication())
    }

    /**
     * Adds a phone number to the auto-record allowlist.
     * Returns false when the input has too few digits to be a real number.
     */
    fun addAutoRecordNumber(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return false
        val current = autoRecordNumbers.value.toMutableSet()
        val duplicate = current.any { it.filter { c -> c.isDigit() } == digits }
        if (!duplicate) {
            current.add(raw.trim())
            autoRecordNumbers.value = current.sorted()
            prefs.edit().putStringSet("auto_record_numbers", current).apply()
        }
        return true
    }

    fun removeAutoRecordNumber(number: String) {
        val current = autoRecordNumbers.value.toMutableSet()
        if (current.remove(number)) {
            autoRecordNumbers.value = current.sorted()
            prefs.edit().putStringSet("auto_record_numbers", current).apply()
        }
    }

    fun saveAgnesApiKey(key: String) {
        agnesApiKey.value = key
        prefs.edit().putString("agnes_api_key", key).apply()
    }

    fun saveBackendGatewayUrl(url: String) {
        backendGatewayUrl.value = url
        prefs.edit().putString("backend_url", url).apply()
    }

    fun purgeTelemetryCache() {
        viewModelScope.launch {
            repository.clearAllData()
            _selectedCallId.value = null
        }
    }

    // Background operation readiness
    fun refreshBackgroundReadiness() {
        val app = getApplication<Application>()
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isBatteryExemptionGranted.value =
            powerManager?.isIgnoringBatteryOptimizations(app.packageName) == true

        val permissions = buildList {
            add(android.Manifest.permission.RECORD_AUDIO)
            add(android.Manifest.permission.READ_PHONE_STATE)
            add(android.Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        _essentialPermissionsGranted.value = permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBatteryExemption() {
        val app = getApplication<Application>()
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${app.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallPulseViewModel", "Battery exemption request failed: ${e.message}")
        }
    }

    fun openAppSystemSettings() {
        val app = getApplication<Application>()
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${app.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallPulseViewModel", "Open app settings failed: ${e.message}")
        }
    }

    // Samsung (and other OEMs) keep a separate "sleeping apps" list that kills
    // even battery-exempted foreground services. There is no API for it — the
    // user must whitelist the app manually under Battery settings.
    val isSamsungDevice: Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun openBatteryOptimizationSettings() {
        val app = getApplication<Application>()
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.w("CallPulseViewModel", "Battery settings unavailable, falling back to app settings")
            openAppSystemSettings()
        }
    }

    fun parseActions(json: String): List<ActionItemModel> = repository.parseActions(json)
    fun parseContracts(json: String): List<VerbalContractModel> = repository.parseContracts(json)
    fun parseTranscript(json: String): List<DiarizedTurnModel> = repository.parseTranscript(json)
    fun parseSpikes(json: String): List<HesitationSpikeModel> = repository.parseSpikes(json)
    fun parseSummary(json: String): List<String> = repository.parseExecutiveSummary(json)

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        releaseMediaPlayer()
    }
}
