package de.romankreisel.igcsync.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import de.romankreisel.igcsync.data.model.IgcFile

@Dao
interface IgcFileDao {
    @Query("SELECT * FROM igcfile")
    suspend fun getAll(): List<IgcFile>

    @Query("SELECT * FROM igcfile WHERE url =:url")
    suspend fun findByUrl(url: String): IgcFile?

    @Query("SELECT * FROM igcfile WHERE sha256_checksum=:checksum")
    suspend fun findBySha256Checksum(checksum: String): List<IgcFile>

    @Query("SELECT * FROM igcfile WHERE skip_always = 0 order by start_date desc")
    suspend fun findFilesWithMissingUpload(): List<IgcFile>

    @Insert
    suspend fun insertAll(vararg files: IgcFile)

    @Delete
    suspend fun delete(file: IgcFile)


}