package com.autoreply.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.autoreply.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class AppSettings(
    val enabled: Boolean,
    // संदेश
    val replyMessage: String,
    val fallbackMessage: String,
    val missedVoiceMessage: String,
    val missedVideoMessage: String,
    val missedCallEnabled: Boolean,
    val extraVoicePhrases: String,
    val extraVideoPhrases: String,
    // WhatsApp वेरिएंट
    val includeBusiness: Boolean,
    // सक्रिय समय (मिनट: 0..1439, दिनों का बिटमास्क: सोम=bit0 ... रवि=bit6)
    val scheduleEnabled: Boolean,
    val startMin: Int,
    val endMin: Int,
    val daysMask: Int,
    // सीमाएँ
    val rateLimitMin: Int,
    val callRateLimitMin: Int,
    val dailyCap: Int,
    val delaySec: Int,
    // कॉल ब्लॉकिंग
    val blockEnabled: Boolean,
    val blockHours: Int,
    // लॉग
    val logRetentionDays: Int,
)

class SettingsRepo(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()

    private val _today = MutableStateFlow(readToday())
    val todayCount: StateFlow<Int> = _today.asStateFlow()

    fun current(): AppSettings = _flow.value

    fun update(block: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val next = block(_flow.value)
            save(next)
            _flow.value = next
        }
    }

    private fun str(key: String, @StringRes default: Int): String =
        p.getString(key, null) ?: ctx.getString(default)

    private fun load() = AppSettings(
        enabled = p.getBoolean("enabled", false),
        replyMessage = str("replyMessage", R.string.default_reply),
        fallbackMessage = str("fallbackMessage", R.string.default_fallback),
        missedVoiceMessage = str("missedVoiceMessage", R.string.default_missed_voice),
        missedVideoMessage = str("missedVideoMessage", R.string.default_missed_video),
        missedCallEnabled = p.getBoolean("missedCallEnabled", true),
        extraVoicePhrases = p.getString("extraVoicePhrases", "") ?: "",
        extraVideoPhrases = p.getString("extraVideoPhrases", "") ?: "",
        includeBusiness = p.getBoolean("includeBusiness", false),
        scheduleEnabled = p.getBoolean("scheduleEnabled", false),
        startMin = p.getInt("startMin", 9 * 60),
        endMin = p.getInt("endMin", 21 * 60),
        daysMask = p.getInt("daysMask", 127),
        rateLimitMin = p.getInt("rateLimitMin", 30),
        callRateLimitMin = p.getInt("callRateLimitMin", 10),
        dailyCap = p.getInt("dailyCap", 100),
        delaySec = p.getInt("delaySec", 3),
        blockEnabled = p.getBoolean("blockEnabled", true),
        blockHours = p.getInt("blockHours", 24),
        logRetentionDays = p.getInt("logRetentionDays", 30),
    )

    private fun save(s: AppSettings) {
        p.edit()
            .putBoolean("enabled", s.enabled)
            .putString("replyMessage", s.replyMessage)
            .putString("fallbackMessage", s.fallbackMessage)
            .putString("missedVoiceMessage", s.missedVoiceMessage)
            .putString("missedVideoMessage", s.missedVideoMessage)
            .putBoolean("missedCallEnabled", s.missedCallEnabled)
            .putString("extraVoicePhrases", s.extraVoicePhrases)
            .putString("extraVideoPhrases", s.extraVideoPhrases)
            .putBoolean("includeBusiness", s.includeBusiness)
            .putBoolean("scheduleEnabled", s.scheduleEnabled)
            .putInt("startMin", s.startMin)
            .putInt("endMin", s.endMin)
            .putInt("daysMask", s.daysMask)
            .putInt("rateLimitMin", s.rateLimitMin)
            .putInt("callRateLimitMin", s.callRateLimitMin)
            .putInt("dailyCap", s.dailyCap)
            .putInt("delaySec", s.delaySec)
            .putBoolean("blockEnabled", s.blockEnabled)
            .putInt("blockHours", s.blockHours)
            .putInt("logRetentionDays", s.logRetentionDays)
            .apply()
    }

    // ---- आज के भेजे गए जवाबों की गिनती (दैनिक सीमा के लिए) ----

    private fun dayStamp(): String = LocalDate.now().toString()

    private fun readToday(): Int =
        if (p.getString("cnt_date", "") == dayStamp()) p.getInt("cnt", 0) else 0

    fun todayCountNow(): Int = readToday()

    fun refreshToday() {
        _today.value = readToday()
    }

    fun incrementToday() {
        synchronized(this) {
            val n = readToday() + 1
            p.edit().putString("cnt_date", dayStamp()).putInt("cnt", n).apply()
            _today.value = n
        }
    }
}
