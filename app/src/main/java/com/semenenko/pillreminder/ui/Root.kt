package com.semenenko.pillreminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.semenenko.pillreminder.MainViewModel
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.notify.Notifier

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", R.string.tab_today, Icons.Outlined.Today),
    Tab("meds", R.string.tab_meds, Icons.Outlined.Medication),
    Tab("journal", R.string.tab_journal, Icons.Outlined.History),
    Tab("settings", R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun PillRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var showDisclaimer by remember { mutableStateOf(!prefs.disclaimerAccepted) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_text)) },
            confirmButton = {
                TextButton(onClick = {
                    prefs.disclaimerAccepted = true
                    showDisclaimer = false
                    if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.disclaimer_ok)) }
            },
        )
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(vm) }
            composable("meds") {
                MedsScreen(vm, onAdd = { nav.navigate("edit/0") }, onEdit = { nav.navigate("edit/$it") })
            }
            composable("journal") { JournalScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EditMedScreen(vm, id = it.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() })
            }
        }
    }
}
