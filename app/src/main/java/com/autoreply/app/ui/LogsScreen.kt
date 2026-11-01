package com.autoreply.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoreply.app.R
import com.autoreply.app.data.LogEntry
import com.autoreply.app.data.LogType
import java.text.DateFormat
import java.util.Date

@StringRes
private fun logTypeLabel(type: String): Int = when (type) {
    LogType.REPLY -> R.string.log_reply
    LogType.GROUP_SKIPPED -> R.string.log_group
    LogType.MISSED_VOICE -> R.string.log_missed_voice
    LogType.MISSED_VIDEO -> R.string.log_missed_video
    LogType.BLOCKED_CALL -> R.string.log_blocked
    LogType.SKIPPED_SCHEDULE -> R.string.log_skip_schedule
    LogType.SKIPPED_RATE -> R.string.log_skip_rate
    LogType.SKIPPED_CAP -> R.string.log_skip_cap
    LogType.SKIPPED_NO_ACTION -> R.string.log_skip_noaction
    LogType.SKIPPED_EMPTY -> R.string.log_skip_empty
    LogType.FAILED -> R.string.log_failed
    else -> R.string.log_other
}

private fun matchesFilter(filter: Int, type: String): Boolean = when (filter) {
    1 -> type == LogType.REPLY
    2 -> type == LogType.MISSED_VOICE || type == LogType.MISSED_VIDEO || type == LogType.BLOCKED_CALL
    3 -> type == LogType.GROUP_SKIPPED || type.startsWith("SKIPPED_")
    4 -> type == LogType.FAILED
    else -> true
}

@Composable
fun LogsScreen(vm: MainViewModel) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val filters = listOf(
        R.string.filter_all,
        R.string.filter_replies,
        R.string.filter_calls,
        R.string.filter_skipped,
        R.string.filter_errors,
    )
    val shown = logs.filter { matchesFilter(filter, it.type) }
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle(stringResource(R.string.logs_title))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { i, res ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = i },
                    label = { Text(stringResource(res)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("autoreply-log.csv") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text(stringResource(R.string.btn_export), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(stringResource(R.string.btn_clear), modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (shown.isEmpty()) {
            HelpText(stringResource(R.string.logs_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { entry -> LogItem(entry, dateFmt.format(Date(entry.time))) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_title)) },
            text = { Text(stringResource(R.string.clear_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLogs()
                    confirmClear = false
                }) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LogItem(entry: LogEntry, timeText: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(12.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(logTypeLabel(entry.type)), style = MaterialTheme.typography.titleSmall)
            Text(entry.contact, style = MaterialTheme.typography.bodyLarge)
            Text(timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.message.isNotBlank()) {
                Text(stringResource(R.string.log_message_prefix, entry.message), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
