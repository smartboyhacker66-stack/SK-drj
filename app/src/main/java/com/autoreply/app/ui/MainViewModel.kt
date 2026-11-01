package com.autoreply.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoreply.app.App
import com.autoreply.app.core.PhoneUtil
import com.autoreply.app.data.AppSettings
import com.autoreply.app.data.BlockedNumber
import com.autoreply.app.data.LogEntry
import com.autoreply.app.data.TimeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application as App

    val settings: StateFlow<AppSettings> = a.settings.flow
    val todayCount: StateFlow<Int> = a.settings.todayCount

    val logs: StateFlow<List<LogEntry>> = a.db.logs().observe(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blocked: StateFlow<List<BlockedNumber>> = a.db.blocked().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules: StateFlow<List<TimeRule>> = a.db.rules().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) { runCatching { a.maintenance() } }
    }

    fun update(block: (AppSettings) -> AppSettings) = a.settings.update(block)

    fun refreshToday() = a.settings.refreshToday()

    // ---- ब्लॉक सूची ----

    fun addBlocked(raw: String) {
        val key = PhoneUtil.key(raw)
        if (key.isEmpty()) return
        block(key, raw.trim())
    }

    fun renewBlocked(b: BlockedNumber) = block(b.numberKey, b.display)

    private fun block(key: String, display: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val hours = a.settings.current().blockHours
            a.db.blocked().upsert(BlockedNumber(key, display, now, now + hours * 3_600_000L))
        }
    }

    fun removeBlocked(key: String) {
        viewModelScope.launch(Dispatchers.IO) { a.db.blocked().delete(key) }
    }

    // ---- समय-आधारित नियम ----

    fun saveRule(rule: TimeRule) {
        viewModelScope.launch(Dispatchers.IO) { a.db.rules().upsert(rule) }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { a.db.rules().delete(id) }
    }

    // ---- लॉग ----

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) { a.db.logs().clear() }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = a.db.logs().all()
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            a.contentResolver.openOutputStream(uri)?.use { os ->
                os.bufferedWriter(Charsets.UTF_8).use { w ->
                    w.write("﻿") // एक्सेल में हिंदी सही दिखे
                    w.write("time,type,contact,message,detail\n")
                    for (r in rows) {
                        val cells = listOf(fmt.format(Date(r.time)), r.type, r.contact, r.message, r.detail)
                        w.write(cells.joinToString(",") { csv(it) })
                        w.write("\n")
                    }
                }
            }
        }
    }

    private fun csv(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""
}
