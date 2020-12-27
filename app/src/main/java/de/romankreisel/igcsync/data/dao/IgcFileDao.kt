package de.romankreisel.igcsync.data.dao

import androidx.room.*
import de.romankreisel.igcsync.data.model.IgcFile
import java.time.Duration

@Dao
interface IgcFileDao {
    @Query("SELECT * FROM igcfile where duration >= :minimumDuration  order by start_date desc")
    suspend fun getAll(minimumDuration: Duration? = null): List<IgcFile>

    @Query("SELECT * FROM igcfile WHERE url =:url")
    suspend fun findByUrl(url: String): IgcFile?

    @Query("SELECT * FROM igcfile WHERE sha256_checksum=:checksum")
    suspend fun findBySha256Checksum(checksum: String): List<IgcFile>

    @Insert
    suspend fun insertAll(vararg files: IgcFile)

    @Delete
    suspend fun delete(file: IgcFile)

    @Update
    fun update(flight: IgcFile)

    @Query("DELETE FROM igcfile WHERE sha256_checksum = :checksum")
    fun deleteBySha(checksum: String)
}