package com.autoreply.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoreply.app.App
import com.autoreply.app.core.CallDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WaListenerService : NotificationListenerService() {

    companion object {
        const val WA = "com.whatsapp"
        const val WA_BIZ = "com.whatsapp.w4b"
        private const val TAG = "AutoReply"
        private const val CACHE_TTL_MS = 12L * 3_600_000L
    }

    private class Cached(val action: NotificationCompat.Action, val at: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actionCache = ConcurrentHashMap<String, Cached>()
    private val handledCalls = LinkedHashSet<String>()
    private val app: App get() = application as App

    override fun onListenerConnected() {
        scope.launch { runCatching { app.maintenance() } }
    }

    override fun onListenerDisconnected() {
        // सिस्टम ने लिस्नर बंद किया हो तो ख़ुद को दोबारा जोड़ने की कोशिश
        try {
            requestRebind(android.content.ComponentName(this, WaListenerService::class.java))
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (t: Throwable) {
            Log.e(TAG, "notification handling failed", t)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val s = app.settings.current()
        if (!s.enabled) return

        val pkg = sbn.packageName
        if (pkg != WA && !(s.includeBusiness && pkg == WA_BIZ)) return

        val n = sbn.notification ?: return
        // कई चैट की समरी नोटिफिकेशन पर जवाब नहीं दिया जा सकता
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val shortcut = n.shortcutId.orEmpty()
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val isMessage = style != null || n.category == Notification.CATEGORY_MESSAGE

        // कुछ फ़ोनों पर WhatsApp की "Missed call" नोटिफिकेशन भी चैट-स्टाइल में आती है;
        // ऐसे में उसे संदेश समझकर छोड़ न दें, इसलिए पहले सख़्त जाँच से कॉल पहचानते हैं।
        val lastForCall = style?.messages?.lastOrNull()?.text?.toString() ?: text
        val strictCall = if (isMessage && s.missedCallEnabled) CallDetector.detectStrict(lastForCall, s) else null

        // ---- सामान्य संदेश ----
        if (isMessage && strictCall == null) {
            // ग्रुप चैट: सख़्ती से अनदेखी (EXTRA_IS_GROUP_CONVERSATION; "@g.us" केवल अतिरिक्त सुरक्षा)
            val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                shortcut.endsWith("@g.us")
            if (isGroup) {
                scope.launch { runCatching { app.engine.onGroupSkipped(title) } }
                return
            }
            if (title.isBlank() || shortcut.startsWith("status@")) return

            val last = style?.messages?.lastOrNull()
            val lastText = last?.text?.toString() ?: text
            val ts = last?.timestamp ?: if (n.`when` > 0) n.`when` else sbn.postTime
            val action = ReplySender.findReplyAction(n)
            if (action != null) {
                actionCache[ReplyEngine.contactKey(title)] = Cached(action, System.currentTimeMillis())
            }
            scope.launch {
                runCatching { app.engine.onMessage(ReplyEngine.Msg(title, ts, lastText, action)) }
            }
            return
        }

        // ---- मिस्ड वॉइस/वीडियो कॉल ----
        if (!s.missedCallEnabled) return
        val body = listOf(text, big).filter { it.isNotBlank() }.joinToString(" ")
        val detection = strictCall ?: CallDetector.detect(body, title, s) ?: return

        if (detection.isGroup || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            scope.launch { runCatching { app.engine.onGroupSkipped(title) } }
            return
        }
        if (title.isBlank()) return

        val dedupeKey = "${sbn.key}|${sbn.postTime}"
        synchronized(handledCalls) {
            if (!handledCalls.add(dedupeKey)) return
            if (handledCalls.size > 200) handledCalls.remove(handledCalls.first())
        }

        val key = ReplyEngine.contactKey(title)
        val now = System.currentTimeMillis()
        val action = ReplySender.findReplyAction(n)
            ?: actionCache[key]?.takeIf { now - it.at < CACHE_TTL_MS }?.action
            ?: findActiveAction(pkg, title)

        scope.launch {
            runCatching { app.engine.onMissedCall(ReplyEngine.Call(title, detection.kind, action)) }
        }
    }

    /** उसी संपर्क की किसी सक्रिय चैट नोटिफिकेशन से Reply एक्शन ढूँढता है। */
    private fun findActiveAction(pkg: String, name: String): NotificationCompat.Action? {
        val list: Array<StatusBarNotification>? = try {
            activeNotifications
        } catch (t: Throwable) {
            null
        }
        if (list == null) return null
        for (sbn in list) {
            if (sbn.packageName != pkg) continue
            val n = sbn.notification ?: continue
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            val e = n.extras ?: continue
            if (e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) continue
            val t = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            if (!t.equals(name, ignoreCase = true)) continue
            val action = ReplySender.findReplyAction(n)
            if (action != null) return action
        }
        return null
    }
}
