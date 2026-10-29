package com.niramoy.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE deleted = 0 ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE deleted = 0")
    suspend fun getAll(): List<MedicineEntity>

    @Query("SELECT * FROM medicines")
    suspend fun getAllIncludingDeleted(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: String): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MedicineEntity)

    @Query("DELETE FROM medicines")
    suspend fun clear()
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE date >= :from ORDER BY date")
    fun observeSince(from: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE medicineId = :medId ORDER BY date DESC")
    fun observeForMedicine(medId: String): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE date = :date")
    suspend fun forDate(date: String): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs")
    suspend fun getAll(): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun get(id: String): DoseLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(l: DoseLogEntity)

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM dose_logs")
    suspend fun clear()
}

@Dao
interface HealthLogDao {
    @Query("SELECT * FROM health_logs WHERE deleted = 0 ORDER BY at DESC")
    fun observeAll(): Flow<List<HealthLogEntity>>

    @Query("SELECT * FROM health_logs")
    suspend fun getAll(): List<HealthLogEntity>

    @Query("SELECT * FROM health_logs WHERE id = :id")
    suspend fun get(id: String): HealthLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(h: HealthLogEntity)

    @Query("DELETE FROM health_logs")
    suspend fun clear()
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE deleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE deleted = 0")
    suspend fun getActive(): List<VisitEntity>

    @Query("SELECT * FROM visits")
    suspend fun getAll(): List<VisitEntity>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun get(id: String): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: VisitEntity)

    @Query("DELETE FROM visits")
    suspend fun clear()
}

@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DoctorEntity>>

    @Query("SELECT * FROM doctors")
    suspend fun getAll(): List<DoctorEntity>

    @Query("SELECT * FROM doctors WHERE id = :id")
    suspend fun get(id: String): DoctorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: DoctorEntity)

    @Query("DELETE FROM doctors")
    suspend fun clear()
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE deleted = 0 ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<RecordFileEntity>>

    @Query("SELECT * FROM records")
    suspend fun getAll(): List<RecordFileEntity>

    @Query("SELECT * FROM records WHERE deleted = 0 AND remotePath = '' AND localPath != ''")
    suspend fun pendingUploads(): List<RecordFileEntity>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: String): RecordFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecordFileEntity)

    @Query("DELETE FROM records")
    suspend fun clear()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE deleted = 0 ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE rowId = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE rowId = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(s: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun clear()
}
