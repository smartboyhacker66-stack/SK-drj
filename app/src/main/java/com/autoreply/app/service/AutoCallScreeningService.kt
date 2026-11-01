package com.autoreply.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.autoreply.app.App
import com.autoreply.app.core.PhoneUtil
import com.autoreply.app.data.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * सिर्फ़ ब्लॉक-सूची के (अभी भी सक्रिय) नंबरों की इनकमिंग फ़ोन कॉल को चुपचाप रिजेक्ट करती है।
 * बाकी सभी कॉल सामान्य रूप से बजती हैं। कोई भी गड़बड़ी हो तो कॉल को अनुमति दी जाती है।
 */
class AutoCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val app = application as App
        val allow = CallScreeningService.CallResponse.Builder().build()
        val s = app.settings.current()
        val number = callDetails.handle?.schemeSpecificPart

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING ||
            !s.blockEnabled ||
            number.isNullOrBlank()
        ) {
            respondToCall(callDetails, allow)
            return
        }

        scope.launch {
            val response = try {
                val key = PhoneUtil.key(number)
                val hit = if (key.isNotEmpty()) {
                    app.db.blocked().findActive(key, System.currentTimeMillis())
                } else {
                    null
                }
                if (hit != null) {
                    runCatching { app.log(LogType.BLOCKED_CALL, hit.display) }
                    CallScreeningService.CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .build()
                } else {
                    allow
                }
            } catch (t: Throwable) {
                allow
            }
            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
