package ru.pillreminder.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.pillreminder.app.data.AppDatabase
import ru.pillreminder.app.data.Intake
import ru.pillreminder.app.data.IntakeStatus
import ru.pillreminder.app.data.MISSED_AFTER_MIN

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
                if (System.currentTimeMillis() - at > MISSED_AFTER_MIN * 60_000L) {
                    // Окно приёма закрыто: больше не напоминаем, отмечаем как «не отмечено»
                    Reminders.refresh(app)
                } else if (med != null && db.logs().find(medId, at) == null) {
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
                    if (AppDatabase.get(app).logs().find(medId, at) == null) Reminders.scheduleRepeat(app, medId, at)
                }
                Notifier.ACTION_SNOOZE -> {
                    Notifier.cancelDose(app, medId, at)
                    Reminders.scheduleSnooze(app, medId, at)
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
