package de.romankreisel.igcsync.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.romankreisel.igcsync.data.model.AlreadyImportedUrl

@Dao
interface AlreadyImportedUrlDao {
    @Query("SELECT * FROM alreadyimportedurl WHERE url =:url")
    suspend fun findByUrl(url: String): List<AlreadyImportedUrl>

    @Query("SELECT * FROM alreadyimportedurl WHERE sha256_checksum=:checksum")
    suspend fun findBySha256Checksum(checksum: String): List<AlreadyImportedUrl>

    @Insert
    suspend fun insertAll(vararg alreadyImportedUrlDao: AlreadyImportedUrl)

    @Query("DELETE FROM alreadyimportedurl")
    suspend fun deleteAll()
}