package ru.pillreminder.app.data

import android.content.Context
import ru.pillreminder.app.notify.Notifier

/** Отметка приёма: используется и экранами, и кнопками в уведомлениях. */
object Intake {
    suspend fun mark(ctx: Context, medId: Long, at: Long, status: IntakeStatus) {
        val db = AppDatabase.get(ctx)
        val med = db.meds().get(medId)
        val existing = db.logs().find(medId, at)
        val name = med?.name ?: existing?.medName ?: return
        val dosage = med?.dosage ?: existing?.dosage.orEmpty()
        val wasTaken = existing?.status == IntakeStatus.TAKEN

        db.logs().upsert(
            IntakeLog(
                id = existing?.id ?: 0,
                medicationId = medId,
                medName = name,
                dosage = dosage,
                scheduledAt = at,
                actedAt = System.currentTimeMillis(),
                status = status,
            ),
        )

        if (med != null) {
            if (status == IntakeStatus.TAKEN && !wasTaken) {
                val before = med.stock
                db.meds().decrementStock(medId)
                if (before != null && before > 0) {
                    val after = before - 1
                    if (after <= med.refillThreshold && (before > med.refillThreshold || after == 0)) {
                        Notifier.showLowStock(ctx, med, after)
                    }
                }
            } else if (status != IntakeStatus.TAKEN && wasTaken) {
                db.meds().incrementStock(medId)
            }
        }
        Notifier.cancelDose(ctx, medId, at)
    }

    suspend fun undo(ctx: Context, log: IntakeLog) {
        val db = AppDatabase.get(ctx)
        db.logs().delete(log)
        if (log.status == IntakeStatus.TAKEN) db.meds().incrementStock(log.medicationId)
        Notifier.cancelDose(ctx, log.medicationId, log.scheduledAt)
    }
}
