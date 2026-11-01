package com.autoreply.app

import android.app.Application
import com.autoreply.app.data.AppDb
import com.autoreply.app.data.LogEntry
import com.autoreply.app.data.SettingsRepo
import com.autoreply.app.service.ReplyEngine

class App : Application() {
    val settings: SettingsRepo by lazy { SettingsRepo(this) }
    val db: AppDb by lazy { AppDb.build(this) }
    val engine: ReplyEngine by lazy { ReplyEngine(this) }

    suspend fun log(type: String, contact: String, message: String = "", detail: String = "") {
        db.logs().insert(
            LogEntry(
                time = System.currentTimeMillis(),
                type = type,
                contact = contact,
                message = message,
                detail = detail,
            )
        )
    }

    /** पुराने लॉग और बहुत पुरानी एक्सपायर हो चुकी ब्लॉक एंट्री साफ़ करता है। */
    suspend fun maintenance() {
        val now = System.currentTimeMillis()
        val days = settings.current().logRetentionDays
        if (days > 0) db.logs().deleteBefore(now - days * 86_400_000L)
        db.blocked().deleteExpiredBefore(now - 7 * 86_400_000L)
    }
}
