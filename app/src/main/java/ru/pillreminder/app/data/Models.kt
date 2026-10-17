package ru.pillreminder.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class IntakeStatus { TAKEN, SKIPPED, MISSED }

/** Через сколько минут после времени приёма неотмеченная доза считается пропущенной. */
const val MISSED_AFTER_MIN = 120L

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val notes: String = "",
    /** Времена приёма "HH:mm,HH:mm" */
    val timesCsv: String = "08:00",
    /** Дни недели: бит 0 = понедельник ... бит 6 = воскресенье */
    val daysMask: Int = ALL_DAYS,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val endEpochDay: Long? = null,
    /** Остаток в четвертях таблетки (4 = одна таблетка). */
    val stock: Int? = null,
    /** Сколько принимать за раз, в четвертях таблетки (1 = ¼, 2 = ½, 4 = 1). */
    val doseQuarters: Int = 4,
    /** Порог напоминания о покупке, в целых таблетках. */
    val refillThreshold: Int = 5,
    val active: Boolean = true,
    /** Приёмы раньше этого момента не существуют (после создания/смены расписания). */
    val effectiveFrom: Long = System.currentTimeMillis(),
) {
    fun times(): List<LocalTime> =
        timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it.trim()) }.sorted()

    companion object {
        const val ALL_DAYS = 127
    }
}

@Entity(
    tableName = "intake_log",
    indices = [Index(value = ["medicationId", "scheduledAt"], unique = true)],
)
data class IntakeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val medName: String,
    val dosage: String,
    /** Запланированное время приёма, epoch millis */
    val scheduledAt: Long,
    /** Когда пользователь отметил приём (null для автоматически пропущенных) */
    val actedAt: Long?,
    val status: IntakeStatus,
    /** Сколько таблетки было принято/списано, в четвертях. */
    val doseQuarters: Int = 4,
)

fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun millisToLdt(ms: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

data class Dose(val med: Medication, val at: LocalDateTime) {
    val millis: Long get() = at.toMillis()
}

fun Medication.occurrencesOn(date: LocalDate): List<LocalDateTime> {
    val day = date.toEpochDay()
    val end = endEpochDay
    if (!active || day < startEpochDay || (end != null && day > end)) return emptyList()
    if ((daysMask and (1 shl (date.dayOfWeek.value - 1))) == 0) return emptyList()
    return times().map { date.atTime(it) }.filter { it.toMillis() >= effectiveFrom }
}

/** Приёмы в интервале (fromExclusive, toInclusive]. */
fun dosesBetween(
    meds: List<Medication>,
    fromExclusive: LocalDateTime,
    toInclusive: LocalDateTime,
): List<Dose> {
    val out = mutableListOf<Dose>()
    var d = fromExclusive.toLocalDate()
    val last = toInclusive.toLocalDate()
    while (!d.isAfter(last)) {
        for (m in meds) {
            for (t in m.occurrencesOn(d)) {
                if (t.isAfter(fromExclusive) && !t.isAfter(toInclusive)) out += Dose(m, t)
            }
        }
        d = d.plusDays(1)
    }
    return out.sortedBy { it.at }
}

fun Medication.nextAfter(after: LocalDateTime): LocalDateTime? {
    var d = after.toLocalDate()
    val end = endEpochDay
    repeat(400) {
        occurrencesOn(d).firstOrNull { it.isAfter(after) }?.let { return it }
        if (end != null && d.toEpochDay() > end) return null
        d = d.plusDays(1)
    }
    return null
}
