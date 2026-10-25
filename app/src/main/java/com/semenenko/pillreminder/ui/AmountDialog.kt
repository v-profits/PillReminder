package com.semenenko.pillreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.formatAmount
import com.semenenko.pillreminder.data.quartersGlyph

/**
 * Диалог «Сколько принято?»: показывается только когда пользователь сам открыл его
 * (например, при высоком давлении принял 2 таблетки вместо 1). Количество в четвертях таблетки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmountDialog(
    initial: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf(initial) }
    val options = (listOf(1, 2, 3, 4, 6, 8, 10, 12, 16) + initial).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dose_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { opt ->
                        FilterChip(
                            selected = q == opt,
                            onClick = { q = opt },
                            label = { Text(quartersGlyph(opt)) },
                        )
                    }
                }
                Text(formatAmount(ctx, q), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(q) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
