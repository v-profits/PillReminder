package com.semenenko.pillreminder.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.semenenko.pillreminder.R
import com.semenenko.pillreminder.data.IntakeLog
import com.semenenko.pillreminder.data.IntakeStatus
import com.semenenko.pillreminder.data.formatAmount
import com.semenenko.pillreminder.data.Medication
import com.semenenko.pillreminder.data.millisToLdt
import java.io.File
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun dayShortName(index: Int, locale: Locale): String =
    DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, locale)

fun daysLabel(ctx: Context, mask: Int, locale: Locale): String =
    if (mask == Medication.ALL_DAYS) ctx.getString(R.string.every_day)
    else (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { dayShortName(it, locale) }

fun statusLabel(ctx: Context, s: IntakeStatus): String = ctx.getString(
    when (s) {
        IntakeStatus.TAKEN -> R.string.status_taken
        IntakeStatus.SKIPPED -> R.string.status_skipped
        IntakeStatus.MISSED -> R.string.status_missed
    },
)

/** Увеличивается каждый раз, когда приложение возвращается на передний план. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** Экспорт журнала в CSV (для показа врачу). Файл уходит только туда, куда пользователь сам его отправит. */
fun shareJournalCsv(ctx: Context, logs: List<IntakeLog>) {
    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("﻿")
    sb.append(listOf(
        ctx.getString(R.string.csv_date), ctx.getString(R.string.csv_time),
        ctx.getString(R.string.csv_medication), ctx.getString(R.string.csv_dosage),
        ctx.getString(R.string.csv_amount),
        ctx.getString(R.string.csv_status), ctx.getString(R.string.csv_recorded),
    ).joinToString(";")).append("\r\n")
    for (l in logs.sortedBy { it.scheduledAt }) {
        val at = millisToLdt(l.scheduledAt)
        val acted = l.actedAt?.let { millisToLdt(it).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.orEmpty()
        sb.append(listOf(
            at.format(dateFmt), at.format(TimeFmt), esc(l.medName), esc(l.dosage),
            esc(formatAmount(ctx, l.doseQuarters)),
            statusLabel(ctx, l.status), acted,
        ).joinToString(";")).append("\r\n")
    }
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "pill-journal.csv")
    file.writeText(sb.toString(), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.journal_export)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
