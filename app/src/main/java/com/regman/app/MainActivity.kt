package com.regman.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.*
import com.regman.app.ui.*
import com.regman.app.ui.theme.RegManTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RegManTheme {
                val nav = rememberNavController()
                val items = listOf(
                    "overview" to Pair(R.string.nav_overview, Icons.Filled.Home),
                    "pool" to Pair(R.string.nav_pool, Icons.Filled.AccountBox),
                    "tasks" to Pair(R.string.nav_tasks, Icons.Filled.List),
                    "register" to Pair(R.string.nav_register, Icons.Filled.Add),
                    "mailbox" to Pair(R.string.nav_mailbox, Icons.Filled.Email),
                    "proxy" to Pair(R.string.nav_proxy, Icons.Filled.LocationOn),
                    "settings" to Pair(R.string.nav_settings, Icons.Filled.Settings),
                )
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val backStack by nav.currentBackStackEntryAsState()
                            val current = backStack?.destination?.route
                            items.forEach { (route, labelIcon) ->
                                NavigationBarItem(
                                    selected = current == route,
                                    onClick = { nav.navigate(route) { launchSingleTop = true } },
                                    icon = { Icon(labelIcon.second, contentDescription = null) },
                                    label = { Text(stringResource(labelIcon.first)) },
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = "overview", modifier = Modifier.padding(padding)) {
                        composable("overview") { OverviewScreen() }
                        composable("pool") { PoolScreen() }
                        composable("tasks") { TasksScreen() }
                        composable("register") { RegisterScreen() }
                        composable("mailbox") { MailboxScreen() }
                        composable("proxy") { ProxyScreen() }
                        composable("settings") { SettingsScreen() }
                    }
                }
            }
        }
    }
}
