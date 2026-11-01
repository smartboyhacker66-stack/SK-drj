package com.autoreply.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoreply.app.R
import com.autoreply.app.core.Perm
import com.autoreply.app.core.PhoneUtil
import com.autoreply.app.data.BlockedNumber
import kotlinx.coroutines.delay

@Composable
fun BlockScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val list by vm.blocked.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    var number by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.block_title))
        HelpText(stringResource(R.string.block_info))

        if (!Perm.callScreeningHeld(ctx)) {
            Text(
                stringResource(R.string.block_role_warn),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SwitchRow(
            title = stringResource(R.string.switch_block),
            checked = s.blockEnabled,
            onChange = { v -> vm.update { it.copy(blockEnabled = v) } },
        )
        NumberField(
            label = stringResource(R.string.block_hours_label),
            value = s.blockHours,
            min = 1,
            max = 720,
            supporting = stringResource(R.string.block_hours_help),
            onChange = { n -> vm.update { it.copy(blockHours = n) } },
        )

        SectionTitle(stringResource(R.string.block_add_title))
        OutlinedTextField(
            value = number,
            onValueChange = {
                number = it
                showError = false
            },
            label = { Text(stringResource(R.string.number_label)) },
            singleLine = true,
            isError = showError,
            supportingText = {
                Text(stringResource(if (showError) R.string.number_invalid else R.string.number_help))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (PhoneUtil.isValid(number)) {
                    vm.addBlocked(number)
                    number = ""
                    showError = false
                } else {
                    showError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.btn_add_block, s.blockHours)) }

        SectionTitle(stringResource(R.string.block_list_title))
        if (list.isEmpty()) HelpText(stringResource(R.string.block_empty))
        list.forEach { item ->
            BlockItem(
                item = item,
                now = now,
                hours = s.blockHours,
                onRenew = { vm.renewBlocked(item) },
                onRemove = { vm.removeBlocked(item.numberKey) },
            )
        }
    }
}

@Composable
private fun BlockItem(item: BlockedNumber, now: Long, hours: Int, onRenew: () -> Unit, onRemove: () -> Unit) {
    val remaining = item.expiresAt - now
    val active = remaining > 0
    val statusText = if (active) {
        val h = remaining / 3_600_000L
        val m = (remaining % 3_600_000L) / 60_000L
        stringResource(R.string.block_active_fmt, h.toInt(), m.toInt())
    } else {
        stringResource(R.string.block_expired)
    }
    val renewText = stringResource(R.string.btn_renew_fmt, hours)
    val renewDesc = stringResource(R.string.renew_desc, item.display, hours)
    val removeText = stringResource(R.string.btn_remove)
    val removeDesc = stringResource(R.string.remove_desc, item.display)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(item.display, style = MaterialTheme.typography.titleMedium)
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = onRenew,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = renewDesc },
            ) { Text(renewText) }
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = removeDesc },
            ) { Text(removeText) }
        }
    }
}
