package ru.pillreminder.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE active = 1")
    suspend fun getActive(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): Medication?

    @Insert
    suspend fun insert(m: Medication): Long

    @Update
    suspend fun update(m: Medication)

    @Delete
    suspend fun delete(m: Medication)

    @Query("UPDATE medications SET stock = MAX(stock - :quarters, 0) WHERE id = :id AND stock IS NOT NULL")
    suspend fun decrementStock(id: Long, quarters: Int)

    @Query("UPDATE medications SET stock = stock + :quarters WHERE id = :id AND stock IS NOT NULL")
    suspend fun incrementStock(id: Long, quarters: Int)

    @Query("DELETE FROM medications")
    suspend fun clear()
}

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: IntakeLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(log: IntakeLog): Long

    @Query("SELECT * FROM intake_log ORDER BY scheduledAt DESC")
    fun observeAll(): Flow<List<IntakeLog>>

    @Query("SELECT * FROM intake_log WHERE medicationId = :medId AND scheduledAt = :at LIMIT 1")
    suspend fun find(medId: Long, at: Long): IntakeLog?

    @Delete
    suspend fun delete(log: IntakeLog)

    @Query("DELETE FROM intake_log")
    suspend fun clear()
}

/** v1 → v2: доза за приём и остаток в четвертях таблетки (старые остатки умножаются на 4). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN doseQuarters INTEGER NOT NULL DEFAULT 4")
        db.execSQL("UPDATE medications SET stock = stock * 4 WHERE stock IS NOT NULL")
        db.execSQL("ALTER TABLE intake_log ADD COLUMN doseQuarters INTEGER NOT NULL DEFAULT 4")
    }
}

@Database(entities = [Medication::class, IntakeLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meds(): MedicationDao
    abstract fun logs(): LogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pills.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
