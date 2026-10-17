package ru.pillreminder.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import ru.pillreminder.app.data.AppDatabase
import ru.pillreminder.app.data.IntakeLog
import ru.pillreminder.app.data.IntakeStatus
import ru.pillreminder.app.data.MISSED_AFTER_MIN
import ru.pillreminder.app.data.Prefs
import ru.pillreminder.app.data.dosesBetween
import ru.pillreminder.app.data.millisToLdt
import ru.pillreminder.app.data.nextAfter
import ru.pillreminder.app.data.toMillis
import java.time.LocalDateTime

object Reminders {
    const val ACTION_DOSE_DUE = "ru.pillreminder.app.DOSE_DUE"
    const val ACTION_SNOOZE_FIRE = "ru.pillreminder.app.SNOOZE_FIRE"
    private const val REQ_NEXT = 1

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    private fun setAlarm(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = alarmManager(ctx)
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun nextPendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_DOSE_DUE)
        return PendingIntent.getBroadcast(ctx, REQ_NEXT, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Ставит один будильник на ближайший приём среди всех лекарств. */
    suspend fun rescheduleAll(ctx: Context) {
        val meds = AppDatabase.get(ctx).meds().getActive()
        val after = LocalDateTime.now().plusSeconds(2)
        val next = meds.mapNotNull { it.nextAfter(after) }.minOrNull()
        val pi = nextPendingIntent(ctx)
        if (next == null) {
            alarmManager(ctx).cancel(pi)
        } else {
            setAlarm(ctx, next.toMillis(), pi)
        }
    }

    /** Показывает уведомления по приёмам, наступившим с прошлой проверки. */
    suspend fun processDue(ctx: Context) {
        val db = AppDatabase.get(ctx)
        val prefs = Prefs(ctx)
        val meds = db.meds().getActive()
        val nowReal = LocalDateTime.now()
        val upper = nowReal.plusSeconds(2)
        val floor = nowReal.minusHours(3)
        val last = millisToLdt(prefs.lastNotified.takeIf { it > 0 } ?: System.currentTimeMillis())
        val from = if (last.isAfter(floor)) last else floor
        for (d in dosesBetween(meds, from, upper)) {
            if (db.logs().find(d.med.id, d.millis) == null) Notifier.showDose(ctx, d.med, d.millis)
        }
        prefs.lastNotified = upper.toMillis()
    }

    /** Неотмеченные приёмы старше MISSED_AFTER_MIN записываются в журнал как пропущенные. */
    suspend fun markMissed(ctx: Context) {
        val db = AppDatabase.get(ctx)
        val prefs = Prefs(ctx)
        val now = LocalDateTime.now()
        val upper = now.minusMinutes(MISSED_AFTER_MIN)
        val floor = now.minusDays(30)
        val last = millisToLdt(prefs.lastMissedCheck.takeIf { it > 0 } ?: System.currentTimeMillis())
        val from = if (last.isAfter(floor)) last else floor
        if (!upper.isAfter(from)) return
        val meds = db.meds().getActive()
        for (d in dosesBetween(meds, from, upper)) {
            db.logs().insertIgnore(
                IntakeLog(
                    medicationId = d.med.id,
                    medName = d.med.name,
                    dosage = d.med.dosage,
                    scheduledAt = d.millis,
                    actedAt = null,
                    status = IntakeStatus.MISSED,
                    doseQuarters = d.quarters,
                ),
            )
            Notifier.cancelDose(ctx, d.med.id, d.millis)
        }
        prefs.lastMissedCheck = upper.toMillis()
    }

    suspend fun refresh(ctx: Context) {
        processDue(ctx)
        markMissed(ctx)
        rescheduleAll(ctx)
    }

    fun scheduleSnooze(ctx: Context, medId: Long, at: Long) {
        val minutes = Prefs(ctx).snoozeMinutes
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_SNOOZE_FIRE)
            .setData(Uri.parse("pill://snooze/$medId/$at"))
            .putExtra(Notifier.EXTRA_MED, medId)
            .putExtra(Notifier.EXTRA_AT, at)
        val pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L, pi)
    }

    fun cancelAll(ctx: Context) {
        alarmManager(ctx).cancel(nextPendingIntent(ctx))
    }
}
