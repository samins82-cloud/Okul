package com.elak.okulum.rehber

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService

class CallerIdService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)

        val phone = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (phone.isBlank()) return
        val match = CallerCache(this).lookup(phone) ?: return
        CallerOverlay.show(this, match)
        try {
            startActivity(Intent(this, IncomingCallerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("guardian", match.guardianName)
                putExtra("relationship", match.relationship)
                putExtra("student", match.studentName)
                putExtra("school_no", match.schoolNo)
                putExtra("class_name", match.className)
                putExtra("phone", phone)
                putExtra("extra", match.extraCount)
            })
        } catch (_: Exception) { }
    }
}
