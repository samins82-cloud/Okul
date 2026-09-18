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
        val cache = CallerCache(this)
        val match = cache.lookup(phone)
        cache.addHistory(phone, match, "Gelen Arama")
        if (match == null) return

        CallerOverlay.show(this, match)
        try {
            startActivity(Intent(this, IncomingCallerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("guardian", match.guardianName)
                putExtra("relationship", match.relationship)
                putExtra("student", match.studentName)
                putExtra("school_no", match.schoolNo)
                putExtra("class_name", match.className)
                putExtra("student_id", match.studentId)
                putExtra("has_photo", match.hasPhoto)
                putExtra("photo_version", match.photoVersion)
                putExtra("phone", phone)
                putExtra("extra", match.extraCount)\n                putExtra("related_students", matches.mapNotNull { m ->\n                    val parts = listOf(m.studentName, m.className, if (m.schoolNo.isNotBlank()) "No: " + m.schoolNo else "").filter { it.isNotBlank() }\n                    parts.joinToString(" · ").takeIf { it.isNotBlank() }\n                }.distinct().joinToString("\\n"))
            })
        } catch (_: Exception) { }
    }
}
