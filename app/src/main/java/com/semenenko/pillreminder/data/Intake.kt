package com.semenenko.pillreminder.data

import android.content.Context
import com.semenenko.pillreminder.notify.Notifier

/** Отметка приёма: используется и экранами, и кнопками в уведомлениях. */
object Intake {
    /**
     * @param quartersOverride фактически принятое количество (в четвертях таблетки), если оно
     * отличается от запланированного. По умолчанию берётся доза из расписания.
     */
    suspend fun mark(
        ctx: Context,
        medId: Long,
        at: Long,
        status: IntakeStatus,
        quartersOverride: Int? = null,
    ) {
        val db = AppDatabase.get(ctx)
        val med = db.meds().get(medId)
        val existing = db.logs().find(medId, at)
        val name = med?.name ?: existing?.medName ?: return
        val dosage = med?.dosage ?: existing?.dosage.orEmpty()
        val wasTaken = existing?.status == IntakeStatus.TAKEN

        // Приём уже отмечен как принятый, меняется только количество
        if (wasTaken && status == IntakeStatus.TAKEN && quartersOverride != null && existing != null) {
            changeAmount(ctx, existing, quartersOverride)
            return
        }

        // Если приём уже был отмечен как принятый, используем ту долю, что списали тогда
        val quarters = quartersOverride
            ?: existing?.takeIf { wasTaken }?.doseQuarters
            ?: med?.quartersAt(millisToLdt(at).toLocalTime())
            ?: existing?.doseQuarters
            ?: 4

        db.logs().upsert(
            IntakeLog(
                id = existing?.id ?: 0,
                medicationId = medId,
                medName = name,
                dosage = dosage,
                scheduledAt = at,
                actedAt = System.currentTimeMillis(),
                status = status,
                doseQuarters = quarters,
            ),
        )

        if (med != null) {
            if (status == IntakeStatus.TAKEN && !wasTaken) {
                deduct(ctx, med, quarters)
            } else if (status != IntakeStatus.TAKEN && wasTaken) {
                db.meds().incrementStock(medId, quarters)
            }
        }
        Notifier.cancelDose(ctx, medId, at)
    }

    /** Изменить количество у уже принятой дозы; остаток пересчитывается на разницу. */
    suspend fun changeAmount(ctx: Context, log: IntakeLog, newQuarters: Int) {
        if (newQuarters <= 0 || newQuarters == log.doseQuarters) return
        val db = AppDatabase.get(ctx)
        db.logs().upsert(log.copy(doseQuarters = newQuarters))
        if (log.status == IntakeStatus.TAKEN) {
            val delta = newQuarters - log.doseQuarters
            if (delta > 0) {
                db.meds().get(log.medicationId)?.let { deduct(ctx, it, delta) }
            } else {
                db.meds().incrementStock(log.medicationId, -delta)
            }
        }
    }

    suspend fun undo(ctx: Context, log: IntakeLog) {
        val db = AppDatabase.get(ctx)
        db.logs().delete(log)
        if (log.status == IntakeStatus.TAKEN) db.meds().incrementStock(log.medicationId, log.doseQuarters)
        Notifier.cancelDose(ctx, log.medicationId, log.scheduledAt)
        // Отметку отменили, а время приёма сегодня уже прошло — напоминаем снова
        val scheduled = millisToLdt(log.scheduledAt)
        if (scheduled.toLocalDate() == java.time.LocalDate.now() && !scheduled.isAfter(java.time.LocalDateTime.now())) {
            db.meds().get(log.medicationId)?.takeIf { it.active }?.let { Notifier.showDose(ctx, it, log.scheduledAt) }
        }
    }

    /** Списать [quarters] четвертей с остатка и, если запас на исходе, показать уведомление. */
    private suspend fun deduct(ctx: Context, med: Medication, quarters: Int) {
        val db = AppDatabase.get(ctx)
        val before = med.stock
        db.meds().decrementStock(med.id, quarters)
        if (before != null && before > 0) {
            val after = maxOf(before - quarters, 0)
            val limit = med.refillThreshold * 4
            if (after <= limit && (before > limit || after == 0)) {
                Notifier.showLowStock(ctx, med, after)
            }
        }
    }
}
