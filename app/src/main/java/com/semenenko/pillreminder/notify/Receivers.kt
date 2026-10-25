package com.semenenko.pillreminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.semenenko.pillreminder.data.AppDatabase
import com.semenenko.pillreminder.data.Intake
import com.semenenko.pillreminder.data.IntakeStatus

/** Максимум часов после времени приёма, пока неотмеченный приём продолжает напоминать. */
private const val REPEAT_MAX_HOURS = 12L

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Срабатывает по будильнику: показывает уведомления и ставит следующий будильник. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async {
            if (intent.action == Reminders.ACTION_SNOOZE_FIRE) {
                val medId = intent.getLongExtra(Notifier.EXTRA_MED, -1)
                val at = intent.getLongExtra(Notifier.EXTRA_AT, -1)
                val db = AppDatabase.get(app)
                val med = db.meds().get(medId)
                // Повторяем, пока приём не отмечен как принятый/пропущенный (не дольше REPEAT_MAX_HOURS)
                val log = db.logs().find(medId, at)
                val pending = log == null || log.status == IntakeStatus.MISSED
                if (System.currentTimeMillis() - at > REPEAT_MAX_HOURS * 3_600_000L) {
                    Reminders.refresh(app)
                } else if (med != null && pending) {
                    Notifier.showDose(app, med, at)
                }
            } else {
                Reminders.refresh(app)
            }
        }
    }
}

/** Кнопки в уведомлении: «Принял», «Отложить», «Пропустить». */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val medId = intent.getLongExtra(Notifier.EXTRA_MED, -1)
        val at = intent.getLongExtra(Notifier.EXTRA_AT, -1)
        if (medId < 0 || at < 0) return
        async {
            when (intent.action) {
                Notifier.ACTION_TAKEN -> Intake.mark(app, medId, at, IntakeStatus.TAKEN)
                Notifier.ACTION_SKIP -> Intake.mark(app, medId, at, IntakeStatus.SKIPPED)
                Notifier.ACTION_DISMISSED -> {
                    // Уведомление смахнули, а приём не отмечен — напомнить ещё раз
                    val log = AppDatabase.get(app).logs().find(medId, at)
                    if (log == null || log.status == IntakeStatus.MISSED) Reminders.scheduleRepeat(app, medId, at)
                }
            }
        }
    }
}

/** Перезагрузка, смена времени/часового пояса, обновление приложения — пересоздаём будильник. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async { Reminders.refresh(app) }
    }
}
