package com.elak.okulum.rehber

import android.os.Build
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val phone = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (phone.isBlank()) return

        val cache = CallerCache(this)
        val matches = cache.lookupAll(phone)
        val match = matches.firstOrNull()?.copy(extraCount = (matches.size - 1).coerceAtLeast(0))
        cache.addHistory(phone, match, "Gelen Arama")
        if (match != null) CallerCardNotifier.show(this, match, phone)
    }
}
