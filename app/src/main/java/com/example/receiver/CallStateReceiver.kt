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
                        Log.i(TAG, "Call answered / off-hook. Starting CallRecordingService.")
                        val callType = if (isIncoming) "INCOMING" else "OUTGOING"
                        val number = savedNumber ?: "Unknown Caller"
                        val contactName = resolveContactName(context, number)
                        CallRecordingService.startRecording(
                            context = context,
                            phoneNumber = number,
                            contactName = contactName,
                            callType = callType
                        )
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

    private fun broadcastToUi(context: Context, state: String, number: String?) {
        try {
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
