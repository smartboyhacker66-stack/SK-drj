package com.autoreply.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.autoreply.app.R
import com.autoreply.app.core.Perm

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoReplyTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Perm.rebindListenerIfNeeded(this)
    }
}

private class TabItem(val label: Int, val icon: ImageVector)

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        TabItem(R.string.tab_home, Icons.Default.Home),
        TabItem(R.string.tab_messages, Icons.Default.Email),
        TabItem(R.string.tab_rules, Icons.Default.DateRange),
        TabItem(R.string.tab_block, Icons.Default.Call),
        TabItem(R.string.tab_logs, Icons.AutoMirrored.Filled.List),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, item ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> MessagesScreen(vm)
                2 -> RulesScreen(vm)
                3 -> BlockScreen(vm)
                else -> LogsScreen(vm)
            }
        }
    }
}
