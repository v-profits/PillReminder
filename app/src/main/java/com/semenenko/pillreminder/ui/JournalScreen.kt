package com.semenenko.pillreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semenenko.pillreminder.MainViewModel
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.IntakeLog
import com.semenenko.pillreminder.data.IntakeStatus
import com.semenenko.pillreminder.data.formatAmount
import com.semenenko.pillreminder.data.millisToLdt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val all by vm.logs.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableIntStateOf(7) } // 7, 30, 0 = всё
    var confirmClear by remember { mutableStateOf(false) }
    val todayStart = LocalDate.now().atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val locale = currentLocale()

    val cutoff = if (period == 0) 0L else LocalDate.now().minusDays(period - 1L).atStartOfDay()
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val logs = all.filter { it.scheduledAt >= cutoff }
    val taken = logs.count { it.status == IntakeStatus.TAKEN }
    val skipped = logs.count { it.status == IntakeStatus.SKIPPED }
    val missed = logs.count { it.status == IntakeStatus.MISSED }
    val total = taken + skipped + missed
    val grouped = logs.groupBy { millisToLdt(it.scheduledAt).toLocalDate() }
    val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tab_journal)) },
                actions = {
                    if (all.any { it.scheduledAt < todayStart }) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.journal_clear))
                        }
                    }
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { shareJournalCsv(ctx, logs) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.journal_export))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // Переключатель периода на всю ширину экрана
                val periods = listOf(7 to R.string.period_7, 30 to R.string.period_30, 0 to R.string.period_all)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { i, (p, label) ->
                        SegmentedButton(
                            selected = period == p,
                            onClick = { period = p },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = periods.size),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            if (total > 0) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.stats_adherence, taken * 100 / total),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.stats_counts, taken, skipped, missed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.journal_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            grouped.forEach { (day, entries) ->
                item(key = "h$day") {
                    Text(
                        day.format(dayFmt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(entries, key = { it.id }) { log -> LogRow(log, vm) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.journal_clear_title)) },
            text = { Text(stringResource(R.string.journal_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearJournalExceptToday() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LogRow(log: IntakeLog, vm: MainViewModel) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var showAmount by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val color: Color = when (log.status) {
        IntakeStatus.TAKEN -> scheme.primary
        IntakeStatus.SKIPPED -> scheme.tertiary
        IntakeStatus.MISSED -> scheme.error
    }
    Box {
        Card(Modifier.fillMaxWidth().clickable { menu = true }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(millisToLdt(log.scheduledAt).format(TimeFmt), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(log.medName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(formatAmount(ctx, log.doseQuarters), log.dosage).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(statusLabel(ctx, log.status), color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_taken)) },
                onClick = { menu = false; vm.mark(log.medicationId, log.scheduledAt, IntakeStatus.TAKEN) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.mark_skipped)) },
                onClick = { menu = false; vm.mark(log.medicationId, log.scheduledAt, IntakeStatus.SKIPPED) },
            )
            if (log.status == IntakeStatus.TAKEN) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mark_change_amount)) },
                    onClick = { menu = false; showAmount = true },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_entry)) },
                onClick = { menu = false; vm.undo(log) },
            )
        }
        if (showAmount) {
            AmountDialog(
                initial = log.doseQuarters,
                confirmLabel = stringResource(R.string.ok),
                onDismiss = { showAmount = false },
            ) { q ->
                showAmount = false
                vm.changeAmount(log, q)
            }
        }
    }
}
