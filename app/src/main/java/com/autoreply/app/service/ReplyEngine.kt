package com.autoreply.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.autoreply.app.App
import com.autoreply.app.core.CallKind
import com.autoreply.app.core.Schedule
import com.autoreply.app.core.Template
import com.autoreply.app.data.ContactState
import com.autoreply.app.data.LogType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/** WhatsApp की नोटिफिकेशन के इनलाइन "Reply" बटन से संदेश भेजता है। */
object ReplySender {
    fun findReplyAction(n: Notification): NotificationCompat.Action? {
        for (i in 0 until NotificationCompat.getActionCount(n)) {
            val a = NotificationCompat.getAction(n, i)
            if (a != null && !a.remoteInputs.isNullOrEmpty()) return a
        }
        for (a in NotificationCompat.WearableExtender(n).actions) {
            if (!a.remoteInputs.isNullOrEmpty()) return a
        }
        return null
    }

    fun send(ctx: Context, action: NotificationCompat.Action, text: String): Boolean {
        val inputs = action.remoteInputs
        if (inputs.isNullOrEmpty()) return false
        val pi = action.actionIntent ?: return false
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val results = Bundle()
        for (ri in inputs) results.putCharSequence(ri.resultKey, text)
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return try {
            pi.send(ctx, 0, intent)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }
}

/**
 * सारे फ़ैसले यहाँ होते हैं: समय, रेट लिमिट, दैनिक सीमा, संदेश का चुनाव, भेजना और लॉग।
 * (ग्रुप चैट की छँटाई सर्विस में EXTRA_IS_GROUP_CONVERSATION से पहले ही हो चुकी होती है।)
 */
class ReplyEngine(private val app: App) {
    private val mutex = Mutex()

    // संपर्क -> हमारा आख़िरी भेजा संदेश (अपने ही जवाब पर दोबारा जवाब न देने के लिए)
    private val lastSent = HashMap<String, String>()

    class Msg(val contact: String, val ts: Long, val text: String, val action: NotificationCompat.Action?)

    class Call(val contact: String, val kind: CallKind, val action: NotificationCompat.Action?)

    private class Plan(
        val contact: String,
        val text: String,
        val action: NotificationCompat.Action,
        val type: String,
        val delaySec: Int,
    )

    companion object {
        fun contactKey(name: String): String = name.trim().lowercase()
    }

    suspend fun onGroupSkipped(name: String) {
        app.log(LogType.GROUP_SKIPPED, name.ifBlank { "-" })
    }

    suspend fun onMessage(m: Msg) {
        val plan = mutex.withLock { decideMessage(m) } ?: return
        execute(plan)
    }

    suspend fun onMissedCall(c: Call) {
        val plan = mutex.withLock { decideCall(c) } ?: return
        execute(plan)
    }

    private suspend fun skip(
        st: ContactState,
        ts: Long,
        type: String,
        contact: String,
        detail: String = "",
    ): Plan? {
        app.db.contacts().upsert(st.copy(lastHandledTs = maxOf(st.lastHandledTs, ts)))
        app.log(type, contact, detail = detail)
        return null
    }

    private suspend fun decideMessage(m: Msg): Plan? {
        val s = app.settings.current()
        if (!s.enabled) return null

        val key = contactKey(m.contact)
        val dao = app.db.contacts()
        val st = dao.get(key) ?: ContactState(contactKey = key)

        // यही संदेश पहले संभाला जा चुका है (नोटिफिकेशन दोबारा पोस्ट हुई)
        if (m.ts <= st.lastHandledTs) return null

        // हमारा ही भेजा जवाब नोटिफिकेशन में दिख रहा है -> लूप से बचाव
        val prev = lastSent[key]
        if (prev != null && prev == m.text) {
            dao.upsert(st.copy(lastHandledTs = maxOf(st.lastHandledTs, m.ts)))
            return null
        }

        val now = System.currentTimeMillis()
        val zdt = ZonedDateTime.now()

        if (s.scheduleEnabled && !Schedule.matches(zdt, s.startMin, s.endMin, s.daysMask)) {
            return skip(st, m.ts, LogType.SKIPPED_SCHEDULE, m.contact)
        }

        val gap = s.rateLimitMin.coerceAtLeast(1) * 60_000L
        if (now - st.lastReplyAt < gap) {
            return skip(st, m.ts, LogType.SKIPPED_RATE, m.contact)
        }

        if (s.dailyCap > 0 && app.settings.todayCountNow() >= s.dailyCap) {
            return skip(st, m.ts, LogType.SKIPPED_CAP, m.contact)
        }

        val action = m.action ?: return skip(st, m.ts, LogType.SKIPPED_NO_ACTION, m.contact)

        // समय-आधारित नियम पहले, फिर सामान्य संदेश, फिर यूनिवर्सल फ़ॉलबैक
        val rule = app.db.rules().enabled().firstOrNull {
            Schedule.matches(zdt, it.startMin, it.endMin, it.daysMask)
        }
        var template = rule?.message?.takeIf { it.isNotBlank() } ?: s.replyMessage
        if (template.isBlank()) template = s.fallbackMessage
        if (template.isBlank()) return skip(st, m.ts, LogType.SKIPPED_EMPTY, m.contact)

        val text = Template.render(template, m.contact, zdt)
        lastSent[key] = text
        dao.upsert(st.copy(lastReplyAt = now, lastHandledTs = maxOf(st.lastHandledTs, m.ts)))
        return Plan(m.contact, text, action, LogType.REPLY, s.delaySec)
    }

    private suspend fun decideCall(c: Call): Plan? {
        val s = app.settings.current()
        if (!s.enabled || !s.missedCallEnabled) return null

        val key = contactKey(c.contact)
        val dao = app.db.contacts()
        val st = dao.get(key) ?: ContactState(contactKey = key)
        val now = System.currentTimeMillis()
        val zdt = ZonedDateTime.now()

        if (s.scheduleEnabled && !Schedule.matches(zdt, s.startMin, s.endMin, s.daysMask)) {
            app.log(LogType.SKIPPED_SCHEDULE, c.contact)
            return null
        }
        if (s.callRateLimitMin > 0 && now - st.lastCallReplyAt < s.callRateLimitMin * 60_000L) {
            app.log(LogType.SKIPPED_RATE, c.contact)
            return null
        }
        val action = c.action
        if (action == null) {
            app.log(LogType.SKIPPED_NO_ACTION, c.contact)
            return null
        }

        val isVideo = c.kind == CallKind.VIDEO
        var template = if (isVideo) s.missedVideoMessage else s.missedVoiceMessage
        if (template.isBlank()) template = s.fallbackMessage
        if (template.isBlank()) {
            app.log(LogType.SKIPPED_EMPTY, c.contact)
            return null
        }

        val text = Template.render(template, c.contact, zdt)
        lastSent[key] = text
        dao.upsert(st.copy(lastCallReplyAt = now))
        val type = if (isVideo) LogType.MISSED_VIDEO else LogType.MISSED_VOICE
        return Plan(c.contact, text, action, type, s.delaySec)
    }

    private suspend fun execute(p: Plan) {
        if (p.delaySec > 0) delay(p.delaySec * 1000L)
        val ok = ReplySender.send(app, p.action, p.text)
        if (ok) {
            app.log(p.type, p.contact, p.text)
            if (p.type == LogType.REPLY) app.settings.incrementToday()
        } else {
            app.log(LogType.FAILED, p.contact, p.text, "reply action expired")
        }
    }
}
