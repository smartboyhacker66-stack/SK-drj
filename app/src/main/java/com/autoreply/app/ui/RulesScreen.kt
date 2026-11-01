package com.autoreply.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoreply.app.R
import com.autoreply.app.data.TimeRule

@Composable
fun RulesScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TimeRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.rules_title))

        SectionTitle(stringResource(R.string.sec_general))
        SwitchRow(
            title = stringResource(R.string.switch_business),
            subtitle = stringResource(R.string.switch_business_desc),
            checked = s.includeBusiness,
            onChange = { v -> vm.update { it.copy(includeBusiness = v) } },
        )

        SectionTitle(stringResource(R.string.sec_active_hours))
        SwitchRow(
            title = stringResource(R.string.switch_schedule),
            subtitle = stringResource(R.string.switch_schedule_desc),
            checked = s.scheduleEnabled,
            onChange = { v -> vm.update { it.copy(scheduleEnabled = v) } },
        )
        if (s.scheduleEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeButton(
                    label = stringResource(R.string.start_time),
                    minutes = s.startMin,
                    onChange = { m -> vm.update { it.copy(startMin = m) } },
                    modifier = Modifier.weight(1f),
                )
                TimeButton(
                    label = stringResource(R.string.end_time),
                    minutes = s.endMin,
                    onChange = { m -> vm.update { it.copy(endMin = m) } },
                    modifier = Modifier.weight(1f),
                )
            }
            DayChips(mask = s.daysMask, onChange = { m -> vm.update { it.copy(daysMask = m) } })
        }

        SectionTitle(stringResource(R.string.sec_limits))
        NumberField(
            label = stringResource(R.string.rate_limit_label),
            value = s.rateLimitMin,
            min = 1,
            max = 1440,
            supporting = stringResource(R.string.rate_limit_help),
            onChange = { n -> vm.update { it.copy(rateLimitMin = n) } },
        )
        NumberField(
            label = stringResource(R.string.call_rate_label),
            value = s.callRateLimitMin,
            min = 0,
            max = 1440,
            supporting = stringResource(R.string.call_rate_help),
            onChange = { n -> vm.update { it.copy(callRateLimitMin = n) } },
        )
        NumberField(
            label = stringResource(R.string.daily_cap_label),
            value = s.dailyCap,
            min = 0,
            max = 10000,
            supporting = stringResource(R.string.daily_cap_help),
            onChange = { n -> vm.update { it.copy(dailyCap = n) } },
        )
        NumberField(
            label = stringResource(R.string.delay_label),
            value = s.delaySec,
            min = 0,
            max = 60,
            supporting = stringResource(R.string.delay_help),
            onChange = { n -> vm.update { it.copy(delaySec = n) } },
        )

        SectionTitle(stringResource(R.string.sec_rules))
        HelpText(stringResource(R.string.rules_help))
        if (rules.isEmpty()) HelpText(stringResource(R.string.no_rules))
        rules.forEach { rule ->
            RuleItem(
                rule = rule,
                onToggle = { v -> vm.saveRule(rule.copy(enabled = v)) },
                onEdit = { editing = rule },
                onDelete = { vm.deleteRule(rule.id) },
            )
        }
        Button(
            onClick = { creating = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.rule_add), modifier = Modifier.padding(start = 8.dp))
        }

        SectionTitle(stringResource(R.string.sec_logs_settings))
        NumberField(
            label = stringResource(R.string.retention_label),
            value = s.logRetentionDays,
            min = 0,
            max = 3650,
            supporting = stringResource(R.string.retention_help),
            onChange = { n -> vm.update { it.copy(logRetentionDays = n) } },
        )
    }

    if (creating) {
        RuleDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { r ->
                vm.saveRule(r)
                creating = false
            },
        )
    }
    editing?.let { current ->
        RuleDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = { r ->
                vm.saveRule(r)
                editing = null
            },
        )
    }
}

@Composable
private fun RuleItem(rule: TimeRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val name = rule.label.ifBlank { stringResource(R.string.rule_untitled) }
    val timeText = stringResource(R.string.rule_time_fmt, formatMin(rule.startMin), formatMin(rule.endMin))
    val days = daysSummary(rule.daysMask, stringResource(R.string.days_all))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("$timeText · $days", style = MaterialTheme.typography.bodyMedium)
                Text(rule.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
            SwitchRow(
                title = stringResource(R.string.rule_enabled_fmt, name),
                checked = rule.enabled,
                onChange = onToggle,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rule_edit_desc, name))
                }
                IconButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.rule_delete_desc, name))
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(initial: TimeRule?, onDismiss: () -> Unit, onSave: (TimeRule) -> Unit) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var message by remember { mutableStateOf(initial?.message ?: "") }
    var start by remember { mutableIntStateOf(initial?.startMin ?: (9 * 60)) }
    var end by remember { mutableIntStateOf(initial?.endMin ?: (18 * 60)) }
    var mask by remember { mutableIntStateOf(initial?.daysMask ?: 127) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.rule_add else R.string.rule_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.rule_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.rule_message_field)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeButton(
                    label = stringResource(R.string.start_time),
                    minutes = start,
                    onChange = { start = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeButton(
                    label = stringResource(R.string.end_time),
                    minutes = end,
                    onChange = { end = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                DayChips(mask = mask, onChange = { mask = it })
                HelpText(stringResource(R.string.placeholders_help))
            }
        },
        confirmButton = {
            TextButton(
                enabled = message.isNotBlank() && mask != 0,
                onClick = {
                    onSave(
                        TimeRule(
                            id = initial?.id ?: 0,
                            label = label.trim(),
                            startMin = start,
                            endMin = end,
                            daysMask = mask,
                            message = message,
                            enabled = initial?.enabled ?: true,
                        )
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
