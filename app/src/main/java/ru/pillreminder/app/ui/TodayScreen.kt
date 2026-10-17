package ru.pillreminder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.pillreminder.app.MainViewModel
import ru.pillreminder.app.R
import ru.pillreminder.app.data.IntakeLog
import ru.pillreminder.app.data.IntakeStatus
import ru.pillreminder.app.data.MISSED_AFTER_MIN
import ru.pillreminder.app.data.Medication
import ru.pillreminder.app.data.formatAmount
import ru.pillreminder.app.data.millisToLdt
import ru.pillreminder.app.data.occurrencesOn
import ru.pillreminder.app.data.toMillis
import java.time.LocalDate
import java.time.LocalDateTime

private data class DoseItem(
    val medId: Long,
    val name: String,
    val dosage: String,
    val notes: String,
    val at: LocalDateTime,
    val log: IntakeLog?,
    val quarters: Int,
)

private enum class DoseState { UPCOMING, DUE, MISSED, TAKEN, SKIPPED }

private fun DoseItem.state(now: LocalDateTime): DoseState = when (log?.status) {
    IntakeStatus.TAKEN -> DoseState.TAKEN
    IntakeStatus.SKIPPED -> DoseState.SKIPPED
    IntakeStatus.MISSED -> DoseState.MISSED
    null -> when {
        at.isAfter(now) -> DoseState.UPCOMING
        at.isAfter(now.minusMinutes(MISSED_AFTER_MIN)) -> DoseState.DUE
        else -> DoseState.MISSED
    }
}

private fun buildToday(meds: List<Medication>, logs: List<IntakeLog>, day: LocalDate): List<DoseItem> {
    val start = day.atStartOfDay().toMillis()
    val end = day.plusDays(1).atStartOfDay().toMillis()
    val todays = logs.filter { it.scheduledAt in start until end }
        .associateBy { it.medicationId to it.scheduledAt }
    val result = mutableMapOf<Pair<Long, Long>, DoseItem>()
    for (m in meds) {
        for (t in m.occurrencesOn(day)) {
            val key = m.id to t.toMillis()
            val log = todays[key]
            result[key] = DoseItem(m.id, m.name, m.dosage, m.notes, t, log, log?.doseQuarters ?: m.quartersAt(t.toLocalTime()))
        }
    }
    // Записи журнала, для которых расписание уже изменилось или лекарство удалено
    for ((key, l) in todays) {
        if (key !in result) {
            result[key] = DoseItem(l.medicationId, l.medName, l.dosage, "", millisToLdt(l.scheduledAt), l, l.doseQuarters)
        }
    }
    return result.values.sortedBy { it.at }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: MainViewModel) {
    val meds by vm.meds.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val items = remember(meds, logs, now.toLocalDate()) { buildToday(meds, logs, now.toLocalDate()) }
    val done = items.count { it.log?.status == IntakeStatus.TAKEN }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_today)) }) },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.today_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.today_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.today_progress, done, items.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LinearProgressIndicator(
                            progress = { done.toFloat() / items.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                items(items, key = { it.medId to it.at.toMillis() }) { item ->
                    DoseCard(item, now, vm)
                }
            }
        }
    }
}

@Composable
private fun DoseCard(item: DoseItem, now: LocalDateTime, vm: MainViewModel) {
    val ctx = LocalContext.current
    val state = item.state(now)
    val scheme = MaterialTheme.colorScheme
    val container = when (state) {
        DoseState.DUE -> scheme.primaryContainer
        DoseState.MISSED -> scheme.tertiaryContainer
        else -> scheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    item.at.format(TimeFmt),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(formatAmount(ctx, item.quarters), item.dosage).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (item.notes.isNotBlank()) {
                        Text(
                            item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (state == DoseState.TAKEN) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary)
            }

            when (state) {
                DoseState.TAKEN, DoseState.SKIPPED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val acted = item.log?.actedAt?.let { millisToLdt(it).format(TimeFmt) }
                    val label = if (state == DoseState.TAKEN) {
                        stringResource(R.string.status_taken_at, acted ?: "")
                    } else {
                        stringResource(R.string.status_skipped)
                    }
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { item.log?.let(vm::undo) }) { Text(stringResource(R.string.action_undo)) }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state == DoseState.DUE) {
                        Text(stringResource(R.string.status_due), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    }
                    if (state == DoseState.MISSED) {
                        Text(stringResource(R.string.status_missed), style = MaterialTheme.typography.labelLarge, color = scheme.tertiary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { vm.mark(item.medId, item.at.toMillis(), IntakeStatus.TAKEN) }) {
                            Text(stringResource(R.string.action_taken))
                        }
                        TextButton(onClick = { vm.mark(item.medId, item.at.toMillis(), IntakeStatus.SKIPPED) }) {
                            Text(stringResource(R.string.action_skip))
                        }
                    }
                }
            }
        }
    }
}
