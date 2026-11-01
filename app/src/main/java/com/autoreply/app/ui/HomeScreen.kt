package com.autoreply.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoreply.app.R
import com.autoreply.app.core.Perm

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val today by vm.todayCount.collectAsStateWithLifecycle()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        vm.refreshToday()
        onPauseOrDispose { }
    }
    val notifOk = remember(tick) { Perm.notificationAccess(ctx) }
    val roleOk = remember(tick) { Perm.callScreeningHeld(ctx) }
    val batteryOk = remember(tick) { Perm.batteryExempt(ctx) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.home_title))

        SwitchRow(
            title = stringResource(R.string.master_switch),
            subtitle = stringResource(if (s.enabled) R.string.master_on else R.string.master_off),
            checked = s.enabled,
            onChange = { v -> vm.update { it.copy(enabled = v) } },
        )

        Text(
            stringResource(R.string.stats_today, today),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        SectionTitle(stringResource(R.string.setup_title))

        StatusCard(
            title = stringResource(R.string.perm_notif_title),
            desc = stringResource(R.string.perm_notif_desc),
            ok = notifOk,
            okText = stringResource(R.string.status_on),
            notOkText = stringResource(R.string.status_off),
            actionLabel = stringResource(R.string.btn_open_settings),
            onAction = { Perm.openNotificationAccess(ctx) },
        )

        if (!notifOk) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.restricted_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.restricted_desc), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { Perm.openAppInfo(ctx) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.btn_app_info)) }
                }
            }
        }

        StatusCard(
            title = stringResource(R.string.perm_role_title),
            desc = stringResource(R.string.perm_role_desc),
            ok = roleOk,
            okText = stringResource(R.string.status_on),
            notOkText = stringResource(R.string.status_off),
            actionLabel = stringResource(R.string.btn_set_default),
            onAction = { Perm.callScreeningRoleIntent(ctx)?.let { roleLauncher.launch(it) } },
        )

        StatusCard(
            title = stringResource(R.string.perm_battery_title),
            desc = stringResource(R.string.perm_battery_desc),
            ok = batteryOk,
            okText = stringResource(R.string.status_on),
            notOkText = stringResource(R.string.status_off),
            actionLabel = stringResource(R.string.btn_allow),
            onAction = { Perm.requestBatteryExemption(ctx) },
        )

        SectionTitle(stringResource(R.string.notes_title))
        HelpText(stringResource(R.string.note_group))
        HelpText(stringResource(R.string.note_calls))
        HelpText(stringResource(R.string.note_reply_button))
        HelpText(stringResource(R.string.note_tos))
    }
}
