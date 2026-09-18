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
        val matches = cache.lookupAll(phone)
        val match = matches.firstOrNull()?.copy(extraCount = (matches.size - 1).coerceAtLeast(0))
        cache.addHistory(phone, match, "Gelen Arama")
        if (match == null) return

        val related = matches.mapNotNull { m ->
            val parts = listOf(
                m.studentName,
                m.className,
                if (m.schoolNo.isNotBlank()) "No: " + m.schoolNo else ""
            ).filter { it.isNotBlank() }
            parts.joinToString(" · ").takeIf { it.isNotBlank() }
        }.distinct().joinToString("\n")

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
                putExtra("extra", match.extraCount)
                putExtra("related_students", related)
            })
        } catch (_: Exception) { }
    }
}
