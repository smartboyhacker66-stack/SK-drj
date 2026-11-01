package com.autoreply.app.core

import com.autoreply.app.data.AppSettings
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

object PhoneUtil {
    /** देवनागरी अंकों समेत किसी भी यूनिकोड अंक को सामान्य 0-9 में बदलकर सिर्फ़ अंक रखता है। */
    private fun digitsOnly(raw: String): String =
        raw.mapNotNull { c -> Character.digit(c, 10).takeIf { it >= 0 } }.joinToString("")

    /** मिलान की कुंजी: आख़िरी 10 अंक (देश कोड के साथ/बिना दोनों एक जैसे मिलेंगे)। */
    fun key(raw: String): String {
        val d = digitsOnly(raw)
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun isValid(raw: String): Boolean = digitsOnly(raw).length in 5..15
}

object Template {
    /** {name} {time} {date} {day} प्लेसहोल्डर भरता है। */
    fun render(template: String, name: String, now: ZonedDateTime): String {
        val time = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val date = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return template
            .replace("{name}", name)
            .replace("{time}", time)
            .replace("{date}", date)
            .replace("{day}", day)
    }
}

object Schedule {
    fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    private fun inWindow(nowMin: Int, start: Int, end: Int): Boolean = when {
        start == end -> true               // पूरे दिन
        start < end -> nowMin in start until end
        else -> nowMin >= start || nowMin < end   // रात पार करने वाली विंडो
    }

    /** क्या अभी का समय दी गई विंडो और दिनों में आता है? */
    fun matches(now: ZonedDateTime, start: Int, end: Int, mask: Int): Boolean {
        val nowMin = now.hour * 60 + now.minute
        // रात पार करने वाली विंडो का आधी-रात के बाद वाला हिस्सा पिछले दिन का माना जाता है
        val effectiveDay = if (start > end && nowMin < end) now.dayOfWeek.minus(1) else now.dayOfWeek
        val dayOk = (mask and dayBit(effectiveDay)) != 0
        return dayOk && inWindow(nowMin, start, end)
    }
}

enum class CallKind { VOICE, VIDEO }

class CallDetection(val kind: CallKind, val isGroup: Boolean)

/**
 * WhatsApp की "मिस्ड कॉल" नोटिफिकेशन पहचानता है (अंग्रेज़ी और हिंदी)।
 * WhatsApp कोई सीधा इवेंट नहीं देता, इसलिए नोटिफिकेशन के टेक्स्ट से पहचान होती है।
 * अगर आपके फ़ोन पर पहचान न हो, तो सेटिंग्स में "अतिरिक्त वाक्यांश" जोड़ सकते हैं।
 */
object CallDetector {
    private val groupWords = listOf("group", "ग्रुप")
    private val videoWords = listOf("video", "वीडियो", "विडियो")

    private fun parse(csv: String): List<String> =
        csv.split(',', '\n').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

    private fun looksLikeMissedCall(t: String): Boolean =
        (t.contains("missed") && t.contains("call")) ||
            (t.contains("कॉल") && (t.contains("मिस") || t.contains("छूट")))

    /**
     * चैट-स्टाइल (MessagingStyle) नोटिफिकेशन के लिए सख़्त जाँच: टेक्स्ट छोटा हो और "Missed…/मिस्ड…" से
     * शुरू हो, ताकि कोई दोस्त "I missed your call" लिखे तो उसे मिस्ड-कॉल न मान लिया जाए।
     */
    fun detectStrict(text: String, s: AppSettings): CallDetection? {
        val t = text.trim().lowercase(Locale.ROOT)
        if (t.isEmpty() || t.length > 60) return null
        val body = t.trimStart { it.isDigit() || it.isWhitespace() }
        val extraVideo = parse(s.extraVideoPhrases)
        val extraVoice = parse(s.extraVoicePhrases)
        val startsLikeCall = body.startsWith("missed") || body.startsWith("मिस") || body.startsWith("छूट")
        val matched = (startsLikeCall && looksLikeMissedCall(body)) ||
            extraVideo.any { body.startsWith(it) } ||
            extraVoice.any { body.startsWith(it) }
        if (!matched) return null
        val isVideo = videoWords.any { body.contains(it) } || extraVideo.any { body.startsWith(it) }
        return CallDetection(if (isVideo) CallKind.VIDEO else CallKind.VOICE, groupWords.any { body.contains(it) })
    }

    fun detect(body: String, title: String, s: AppSettings): CallDetection? {
        val extraVideo = parse(s.extraVideoPhrases)
        val extraVoice = parse(s.extraVoicePhrases)
        for ((src, checkGroup) in listOf(body to true, title to false)) {
            if (src.isBlank()) continue
            val t = src.lowercase(Locale.ROOT)
            val isVideo = videoWords.any { t.contains(it) } || extraVideo.any { t.contains(it) }
            val isVoiceExtra = extraVoice.any { t.contains(it) }
            if (looksLikeMissedCall(t) || isVoiceExtra || extraVideo.any { t.contains(it) }) {
                val isGroup = checkGroup && groupWords.any { t.contains(it) }
                return CallDetection(if (isVideo) CallKind.VIDEO else CallKind.VOICE, isGroup)
            }
        }
        return null
    }
}
