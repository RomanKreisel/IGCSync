package de.romankreisel.igcsync.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import de.romankreisel.igcsync.data.model.Flight
import java.time.Duration

@Dao
interface FlightDao {
    @Query("SELECT * FROM flight where duration >= :minimumDuration  order by start_date desc")
    fun getAll(minimumDuration: Duration? = null): LiveData<List<Flight>>

    @Query("SELECT * FROM flight WHERE sha256_checksum=:checksum")
    suspend fun findBySha256Checksum(checksum: String): List<Flight>

    @Insert
    suspend fun insertAll(vararg files: Flight)

    @Delete
    suspend fun delete(file: Flight)

    @Update
    fun update(flight: Flight)

    @Query("DELETE FROM flight WHERE sha256_checksum = :checksum")
    fun deleteBySha(checksum: String)
}