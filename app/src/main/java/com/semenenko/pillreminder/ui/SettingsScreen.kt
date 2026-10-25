package com.semenenko.pillreminder.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.semenenko.pillreminder.BuildConfig
import com.semenenko.pillreminder.MainViewModel
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.notify.Notifier
import com.semenenko.pillreminder.notify.Reminders
import kotlin.math.roundToInt

private fun currentLangTag(): String =
    AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty().let { if (it == "ru" || it == "en") it else "" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val tick = rememberResumeTick()

    var theme by remember { mutableIntStateOf(prefs.themeMode) }
    var lang by remember { mutableStateOf(currentLangTag()) }
    var hideLock by remember { mutableStateOf(prefs.hideOnLockScreen) }
    var repeat by remember { mutableIntStateOf(prefs.repeatMinutes) }
    val setRepeat = { v: Int ->
        val c = v.coerceIn(0, 180)
        repeat = c
        prefs.repeatMinutes = c
    }
    var confirmClearJournal by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifOk = remember(tick) { Notifier.canPost(ctx) }
    val exactOk = remember(tick) { Reminders.canScheduleExact(ctx) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Разрешения ---
            if (!notifOk) {
                WarningCard(R.string.warn_notifications, R.string.action_allow) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                }
            }
            if (!exactOk) {
                WarningCard(R.string.warn_exact_alarm, R.string.action_open_settings) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                }
            }

            // --- Тема ---
            SectionTitle(R.string.settings_theme)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
                    AppCompatDelegate.MODE_NIGHT_NO to R.string.theme_light,
                    AppCompatDelegate.MODE_NIGHT_YES to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = {
                            theme = mode
                            prefs.themeMode = mode
                            AppCompatDelegate.setDefaultNightMode(mode)
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            // --- Язык ---
            SectionTitle(R.string.settings_language)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to R.string.lang_system, "ru" to R.string.lang_ru, "en" to R.string.lang_en).forEach { (tag, label) ->
                    FilterChip(
                        selected = lang == tag,
                        onClick = {
                            lang = tag
                            AppCompatDelegate.setApplicationLocales(
                                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                else LocaleListCompat.forLanguageTags(tag),
                            )
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            HorizontalDivider()

            // --- Уведомления ---
            SectionTitle(R.string.settings_notifications)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_hide_lock))
                    Text(
                        stringResource(R.string.settings_hide_lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = hideLock, onCheckedChange = { hideLock = it; prefs.hideOnLockScreen = it })
            }
            // Повтор напоминания, если уведомление смахнули, а приём не отмечен
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_repeat), Modifier.weight(1f))
                Text(
                    if (repeat == 0) stringResource(R.string.settings_repeat_off) else stringResource(R.string.minutes_short, repeat),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { setRepeat(repeat - 1) }, enabled = repeat > 0) { Text("−") }
                Slider(
                    value = repeat.toFloat(),
                    onValueChange = { setRepeat(it.roundToInt()) },
                    valueRange = 0f..180f,
                    steps = 179,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                FilledTonalIconButton(onClick = { setRepeat(repeat + 1) }, enabled = repeat < 180) { Text("+") }
            }
            Text(
                stringResource(R.string.settings_repeat_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Notifier.showTest(ctx) }) { Text(stringResource(R.string.settings_test_notification)) }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text(stringResource(R.string.settings_battery)) }
            }
            Text(
                stringResource(R.string.settings_battery_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // --- Данные ---
            SectionTitle(R.string.settings_data)
            Text(
                stringResource(R.string.settings_data_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmClearJournal = true }) { Text(stringResource(R.string.settings_clear_journal)) }
                OutlinedButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.settings_delete_all)) }
            }

            HorizontalDivider()

            // --- О приложении ---
            SectionTitle(R.string.settings_about)
            Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmClearJournal) {
        ConfirmDialog(R.string.settings_clear_journal, R.string.settings_clear_journal_text, { confirmClearJournal = false }) {
            vm.clearJournal()
        }
    }
    if (confirmDeleteAll) {
        ConfirmDialog(R.string.settings_delete_all, R.string.settings_delete_all_text, { confirmDeleteAll = false }) {
            vm.deleteAllData()
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun WarningCard(text: Int, button: Int, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(text), color = MaterialTheme.colorScheme.onTertiaryContainer)
            TextButton(onClick = onClick) { Text(stringResource(button)) }
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, text: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
