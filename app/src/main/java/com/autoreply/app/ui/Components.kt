@file:OptIn(ExperimentalMaterial3Api::class)

package com.autoreply.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoreply.app.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

fun formatMin(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** पूरी पंक्ति दबाने पर बदलने वाला स्विच; स्क्रीन रीडर "शीर्षक, विवरण, स्विच, चालू/बंद" पढ़ता है। */
@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) HelpText(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun NumberField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    supporting: String,
    onChange: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in min..max
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val f = t.filter { it.isDigit() }.take(5)
            text = f
            val n = f.toIntOrNull()
            if (n != null && n in min..max) onChange(n)
        },
        label = { Text(label) },
        singleLine = true,
        isError = !valid,
        supportingText = {
            Text(if (valid) supporting else stringResource(R.string.range_error, min, max))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun MessageField(label: String, initial: String, default: String, onChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val resetText = stringResource(R.string.reset_default)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            label = { Text(label) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                text = default
                onChange(default)
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "$resetText: $label" },
        ) {
            Text(resetText)
        }
    }
}

@Composable
fun TimeButton(label: String, minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true },
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text("$label: ${formatMin(minutes)}")
    }
    if (show) {
        val st = rememberTimePickerState(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label) },
            text = { TimeInput(state = st) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(st.hour * 60 + st.minute)
                    show = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** सोम..रवि के चुनने लायक चिप्स (चुना हुआ होने पर ✓ आइकन भी दिखता है, सिर्फ़ रंग पर निर्भर नहीं)। */
@Composable
fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.values().forEach { d ->
            val bit = 1 shl (d.value - 1)
            val selected = (mask and bit) != 0
            val full = d.getDisplayName(TextStyle.FULL, Locale.getDefault())
            FilterChip(
                selected = selected,
                onClick = { onChange(if (selected) mask and bit.inv() else mask or bit) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = full },
            )
        }
    }
}

fun daysSummary(mask: Int, allText: String): String {
    if (mask and 127 == 127) return allText
    return DayOfWeek.values()
        .filter { (mask and (1 shl (it.value - 1))) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
fun StatusCard(
    title: String,
    desc: String,
    ok: Boolean,
    okText: String,
    notOkText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Text(if (ok) okText else notOkText, fontWeight = FontWeight.Bold)
                HelpText(desc)
            }
            if (!ok) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}
