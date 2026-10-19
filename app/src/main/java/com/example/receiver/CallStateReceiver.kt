package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.example.service.CallRecordingService

/**
 * Telephony State Machine Broadcast Receiver
 * Tracks TelephonyManager states (RINGING, OFFHOOK, IDLE)
 * Triggers background recording starts and stops.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var savedNumber: String? = null
        private var isIncoming = false

        // Broadcast intent action for UI telemetry
        const val ACTION_TELEPHONY_STATE_CHANGED = "com.example.callpulse.TELEPHONY_STATE_CHANGED"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            savedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            isIncoming = false
            Log.d(TAG, "Outgoing call initiated to: $savedNumber")
            broadcastToUi(context, "OUTGOING_INITIATED", savedNumber)
            return
        }

        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: savedNumber

            if (number != null && number.isNotBlank()) {
                savedNumber = number
            }

            Log.d(TAG, "Telephony state changed: $stateStr for number: $savedNumber")
            broadcastToUi(context, stateStr, savedNumber)

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    isIncoming = true
                    Log.i(TAG, "Incoming call ringing from $savedNumber")
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (lastState != TelephonyManager.EXTRA_STATE_OFFHOOK) {
                        Log.i(TAG, "Call answered / off-hook. Evaluating auto-record rules.")
                        val callType = if (isIncoming) "INCOMING" else "OUTGOING"
                        val number = savedNumber ?: "Unknown Caller"
                        val contactName = resolveContactName(context, number)
                        if (shouldAutoRecord(context, isIncoming, number)) {
                            Log.i(TAG, "Auto-record rule matched. Starting CallRecordingService.")
                            CallRecordingService.startRecording(
                                context = context,
                                phoneNumber = number,
                                contactName = contactName,
                                callType = callType
                            )
                        } else {
                            Log.i(TAG, "Skipping recording: incoming call from $number is not in the auto-record list and record-all-incoming is off.")
                        }
                    }
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                        Log.i(TAG, "Call ended. Stopping CallRecordingService.")
                        CallRecordingService.stopRecording(context)
                    }
                    isIncoming = false
                    savedNumber = null
                }
            }
            lastState = stateStr
        }
    }

    /**
     * Auto-record rule evaluation for incoming calls.
     * - Outgoing calls: unchanged behavior (always record).
     * - Incoming calls: record when the "record all incoming" master toggle is on,
     *   or when the caller's number matches the user's auto-record list.
     * Number matching compares digit sequences, falling back to last-10-digit
     * suffix matching so "+1 (555) 123-4567" matches "5551234567".
     */
    private fun shouldAutoRecord(context: Context, isIncoming: Boolean, number: String?): Boolean {
        if (!isIncoming) return true
        val prefs = context.getSharedPreferences("callpulse_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("record_all_incoming", false)) return true
        val allowed = prefs.getStringSet("auto_record_numbers", emptySet()) ?: emptySet()
        if (allowed.isEmpty()) return false
        val incoming = normalizePhoneNumber(number ?: "")
        if (incoming.isEmpty()) return false
        return allowed.any { candidate ->
            val norm = normalizePhoneNumber(candidate)
            norm.isNotEmpty() && (norm == incoming || norm.takeLast(10) == incoming.takeLast(10))
        }
    }

    private fun normalizePhoneNumber(number: String): String = number.filter { it.isDigit() }

    private fun broadcastToUi(context: Context, state: String, number: String?) {        try {
            val updateIntent = Intent(ACTION_TELEPHONY_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_PHONE_NUMBER, number ?: "")
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast telephony state to UI: ${e.message}")
        }
    }

    private fun resolveContactName(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank() || phoneNumber == "Unknown Caller") return "Unknown Contact"
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: phoneNumber
                } else {
                    phoneNumber
                }
            } ?: phoneNumber
        } catch (e: Exception) {
            phoneNumber
        }
    }
}
