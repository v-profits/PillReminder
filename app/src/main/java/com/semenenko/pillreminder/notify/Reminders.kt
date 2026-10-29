package com.semenenko.pillreminder.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.semenenko.pillreminder.data.AppDatabase
import com.semenenko.pillreminder.data.IntakeLog
import com.semenenko.pillreminder.data.IntakeStatus
import com.semenenko.pillreminder.data.MISSED_AFTER_MIN
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.data.dosesBetween
import com.semenenko.pillreminder.data.millisToLdt
import com.semenenko.pillreminder.data.nextAfter
import com.semenenko.pillreminder.data.toMillis
import java.time.LocalDateTime

object Reminders {
    const val ACTION_DOSE_DUE = "com.semenenko.pillreminder.DOSE_DUE"
    const val ACTION_SNOOZE_FIRE = "com.semenenko.pillreminder.SNOOZE_FIRE"
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
            // Уведомление остаётся в шторке, пока приём не отмечен; убираем только по истечении 12 часов
            if (System.currentTimeMillis() - d.millis > 12L * 3_600_000L) Notifier.cancelDose(ctx, d.med.id, d.millis)
        }
        prefs.lastMissedCheck = upper.toMillis()
    }

    /**
     * Сегодняшние приёмы, время которых уже прошло, а приём не отмечен (или отметка отменена):
     * уведомление показывается снова. Уже висящее в шторке уведомление просто обновляется без звука.
     */
    suspend fun showOverdueToday(ctx: Context) {
        val db = AppDatabase.get(ctx)
        val now = LocalDateTime.now()
        val dayStart = now.toLocalDate().atStartOfDay().minusSeconds(1)
        for (d in dosesBetween(db.meds().getActive(), dayStart, now)) {
            val log = db.logs().find(d.med.id, d.millis)
            if (log == null || log.status == IntakeStatus.MISSED) Notifier.showDose(ctx, d.med, d.millis)
        }
    }

    /**
     * @param reshowOverdue показать снова неотмеченные приёмы за сегодня: после обновления приложения,
     * перезагрузки, добавления или изменения лекарства.
     */
    suspend fun refresh(ctx: Context, reshowOverdue: Boolean = false) {
        processDue(ctx)
        markMissed(ctx)
        if (reshowOverdue) showOverdueToday(ctx)
        rescheduleAll(ctx)
    }

    /** Приём не отмечен: показать уведомление снова через интервал из настроек. */
    fun scheduleRepeat(ctx: Context, medId: Long, at: Long) {
        val minutes = Prefs(ctx).repeatMinutes
        if (minutes > 0) scheduleAfter(ctx, medId, at, minutes)
    }

    private fun repeatIntent(ctx: Context, medId: Long, at: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_SNOOZE_FIRE)
            .setData(Uri.parse("pill://snooze/$medId/$at"))
            .putExtra(Notifier.EXTRA_MED, medId)
            .putExtra(Notifier.EXTRA_AT, at)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun scheduleAfter(ctx: Context, medId: Long, at: Long, minutes: Int) {
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L, repeatIntent(ctx, medId, at))
    }

    /** Приём отмечен (принят/пропущен) — повторов больше не будет. */
    fun cancelRepeat(ctx: Context, medId: Long, at: Long) {
        val pi = repeatIntent(ctx, medId, at)
        alarmManager(ctx).cancel(pi)
        pi.cancel()
    }

    fun cancelAll(ctx: Context) {
        alarmManager(ctx).cancel(nextPendingIntent(ctx))
    }
}
