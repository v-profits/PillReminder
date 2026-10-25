package com.semenenko.pillreminder.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semenenko.pillreminder.MainViewModel
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.Medication
import com.semenenko.pillreminder.data.formatAmount
import com.semenenko.pillreminder.data.quartersGlyph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val meds by vm.meds.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_meds)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.med_add))
            }
        },
    ) { padding ->
        if (meds.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.meds_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.meds_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(meds, key = { it.id }) { med ->
                    MedCard(med, onClick = { onEdit(med.id) }, onActive = { vm.setActive(med, it) })
                }
            }
        }
    }
}

@Composable
private fun MedCard(med: Medication, onClick: () -> Unit, onActive: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val locale = currentLocale()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(med.name, style = MaterialTheme.typography.titleMedium)
                if (med.dosage.isNotBlank()) Text(med.dosage, style = MaterialTheme.typography.bodyMedium)
                Text(
                    med.schedule().joinToString(", ") { "${it.first.format(TimeFmt)} (${quartersGlyph(it.second)})" } +
                        " · " + daysLabel(ctx, med.daysMask, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val stock = med.stock
                if (stock != null) {
                    val low = stock <= med.refillThreshold * 4
                    Text(
                        stringResource(R.string.stock_left, formatAmount(ctx, stock)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = med.active, onCheckedChange = onActive)
        }
    }
}
