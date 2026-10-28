package com.semenenko.pillreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.semenenko.pillreminder.data.AppDatabase
import com.semenenko.pillreminder.data.Intake
import com.semenenko.pillreminder.data.IntakeLog
import com.semenenko.pillreminder.data.IntakeStatus
import com.semenenko.pillreminder.data.Medication
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.data.toMillis
import com.semenenko.pillreminder.notify.Reminders

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val meds = db.meds().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs = db.logs().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshReminders() {
        viewModelScope.launch(Dispatchers.IO) { Reminders.refresh(app) }
    }

    fun mark(medId: Long, at: Long, status: IntakeStatus, quarters: Int? = null) {
        viewModelScope.launch(Dispatchers.IO) { Intake.mark(app, medId, at, status, quarters) }
    }

    fun changeAmount(log: IntakeLog, quarters: Int) {
        viewModelScope.launch(Dispatchers.IO) { Intake.changeAmount(app, log, quarters) }
    }

    fun undo(log: IntakeLog) {
        viewModelScope.launch(Dispatchers.IO) { Intake.undo(app, log) }
    }

    suspend fun getMed(id: Long): Medication? = withContext(Dispatchers.IO) { db.meds().get(id) }

    fun saveMed(med: Medication, scheduleChanged: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Расписание действует с начала сегодняшнего дня: прошедшее сегодня время без отметки напомнит о себе
                val m = if (scheduleChanged) {
                    med.copy(effectiveFrom = java.time.LocalDate.now().atStartOfDay().toMillis())
                } else med
                if (m.id == 0L) db.meds().insert(m) else db.meds().update(m)
                Reminders.refresh(app, reshowOverdue = true)
            }
            onDone()
        }
    }

    fun setActive(med: Medication, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.meds().update(med.copy(active = active, effectiveFrom = System.currentTimeMillis()))
            Reminders.refresh(app)
        }
    }

    fun deleteMed(med: Medication, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.meds().delete(med)
                Reminders.refresh(app)
            }
            onDone()
        }
    }

    /** Очистить журнал, кроме записей за сегодня. */
    fun clearJournalExceptToday() {
        val startOfToday = java.time.LocalDate.now().atStartOfDay().toMillis()
        viewModelScope.launch(Dispatchers.IO) { db.logs().deleteBefore(startOfToday) }
    }

    fun clearJournal() {
        viewModelScope.launch(Dispatchers.IO) { db.logs().clear() }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            db.logs().clear()
            db.meds().clear()
            Reminders.cancelAll(app)
            val p = Prefs(app)
            val now = System.currentTimeMillis()
            p.lastNotified = now
            p.lastMissedCheck = now
        }
    }
}
