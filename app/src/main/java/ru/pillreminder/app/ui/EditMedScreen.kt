package ru.pillreminder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.pillreminder.app.MainViewModel
import ru.pillreminder.app.R
import ru.pillreminder.app.data.Medication
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMedScreen(vm: MainViewModel, id: Long, onClose: () -> Unit) {
    var original by remember { mutableStateOf<Medication?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var timesCsv by rememberSaveable { mutableStateOf("08:00") }
    var daysMask by rememberSaveable { mutableStateOf(Medication.ALL_DAYS) }
    var courseDays by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("5") }
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val locale = currentLocale()

    LaunchedEffect(id) {
        if (id != 0L) {
            val m = vm.getMed(id)
            original = m
            if (m != null && !loaded) {
                name = m.name
                dosage = m.dosage
                notes = m.notes
                timesCsv = m.timesCsv
                daysMask = m.daysMask
                courseDays = m.endEpochDay?.let { (it - m.startEpochDay + 1).toString() }.orEmpty()
                stock = m.stock?.toString().orEmpty()
                threshold = m.refillThreshold.toString()
            }
            loaded = true
        }
    }

    val times = timesCsv.split(",").filter { it.isNotBlank() }
    val canSave = name.isNotBlank() && times.isNotEmpty() && daysMask != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == 0L) R.string.med_add else R.string.med_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (id != 0L && original != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dosage, onValueChange = { dosage = it },
                label = { Text(stringResource(R.string.field_dosage)) },
                placeholder = { Text(stringResource(R.string.field_dosage_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_notes)) },
                placeholder = { Text(stringResource(R.string.field_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.field_times), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { timesCsv = times.filter { it != t }.joinToString(",") },
                        label = { Text(t) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            OutlinedButton(onClick = { showTime = true }) { Text(stringResource(R.string.time_add)) }

            Text(stringResource(R.string.field_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { d ->
                    FilterChip(
                        selected = daysMask and (1 shl d) != 0,
                        onClick = { daysMask = daysMask xor (1 shl d) },
                        label = { Text(dayShortName(d, locale)) },
                    )
                }
            }

            OutlinedTextField(
                value = courseDays, onValueChange = { courseDays = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.field_course)) },
                supportingText = { Text(stringResource(R.string.field_course_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = stock, onValueChange = { stock = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.field_stock)) },
                supportingText = { Text(stringResource(R.string.field_stock_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (stock.isNotBlank()) {
                OutlinedTextField(
                    value = threshold, onValueChange = { threshold = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.field_threshold)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                enabled = canSave && loaded,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val base = original
                    val start = base?.startEpochDay ?: LocalDate.now().toEpochDay()
                    val end = courseDays.toIntOrNull()?.takeIf { it > 0 }?.let { start + it - 1 }
                    val sortedTimes = times.distinct().sorted().joinToString(",")
                    val med = (base ?: Medication(name = "")).copy(
                        name = name.trim(),
                        dosage = dosage.trim(),
                        notes = notes.trim(),
                        timesCsv = sortedTimes,
                        daysMask = daysMask,
                        startEpochDay = start,
                        endEpochDay = end,
                        stock = stock.toIntOrNull(),
                        refillThreshold = threshold.toIntOrNull() ?: 5,
                    )
                    val changed = base == null || base.timesCsv != sortedTimes ||
                        base.daysMask != daysMask || base.endEpochDay != end
                    vm.saveMed(med, changed, onClose)
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(8, 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute).format(TimeFmt)
                    timesCsv = (times + t).distinct().sorted().joinToString(",")
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.med_delete_title)) },
            text = { Text(stringResource(R.string.med_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    original?.let { vm.deleteMed(it, onClose) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
