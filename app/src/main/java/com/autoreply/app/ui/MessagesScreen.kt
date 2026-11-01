package com.autoreply.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoreply.app.R

@Composable
fun MessagesScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(stringResource(R.string.msgs_title))
        HelpText(stringResource(R.string.placeholders_help))

        SectionTitle(stringResource(R.string.sec_chat_replies))
        MessageField(
            label = stringResource(R.string.msg_reply_label),
            initial = s.replyMessage,
            default = ctx.getString(R.string.default_reply),
        ) { v -> vm.update { it.copy(replyMessage = v) } }

        MessageField(
            label = stringResource(R.string.msg_fallback_label),
            initial = s.fallbackMessage,
            default = ctx.getString(R.string.default_fallback),
        ) { v -> vm.update { it.copy(fallbackMessage = v) } }
        HelpText(stringResource(R.string.msg_fallback_help))

        SectionTitle(stringResource(R.string.sec_missed_calls))
        SwitchRow(
            title = stringResource(R.string.switch_missed_call),
            subtitle = stringResource(R.string.missed_call_help),
            checked = s.missedCallEnabled,
            onChange = { v -> vm.update { it.copy(missedCallEnabled = v) } },
        )
        MessageField(
            label = stringResource(R.string.msg_voice_label),
            initial = s.missedVoiceMessage,
            default = ctx.getString(R.string.default_missed_voice),
        ) { v -> vm.update { it.copy(missedVoiceMessage = v) } }

        MessageField(
            label = stringResource(R.string.msg_video_label),
            initial = s.missedVideoMessage,
            default = ctx.getString(R.string.default_missed_video),
        ) { v -> vm.update { it.copy(missedVideoMessage = v) } }

        SectionTitle(stringResource(R.string.sec_advanced))
        HelpText(stringResource(R.string.phrases_help))
        var voicePhrases by rememberSaveable { mutableStateOf(s.extraVoicePhrases) }
        OutlinedTextField(
            value = voicePhrases,
            onValueChange = { t ->
                voicePhrases = t
                vm.update { it.copy(extraVoicePhrases = t) }
            },
            label = { Text(stringResource(R.string.phrases_voice_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        var videoPhrases by rememberSaveable { mutableStateOf(s.extraVideoPhrases) }
        OutlinedTextField(
            value = videoPhrases,
            onValueChange = { t ->
                videoPhrases = t
                vm.update { it.copy(extraVideoPhrases = t) }
            },
            label = { Text(stringResource(R.string.phrases_video_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
