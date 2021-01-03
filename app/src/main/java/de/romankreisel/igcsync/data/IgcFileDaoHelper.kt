package de.romankreisel.igcsync.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import de.romankreisel.igcsync.MainActivity
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.dao.IgcFileDao
import de.romankreisel.igcsync.data.igc.IgcException
import de.romankreisel.igcsync.data.model.IgcFile
import de.romankreisel.igcsync.igc.IgcData
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import kotlin.collections.HashSet

class IgcFileDaoHelper(private val applicationContext: Context) {
    private var preferences: SharedPreferences
    private val igcFileDao: IgcFileDao

    init {
        this.igcFileDao = IgcSyncDatabase.getDatabase(this.applicationContext).igcFileDao()
        this.preferences = this.applicationContext.getSharedPreferences(MainActivity::class.simpleName, Context.MODE_PRIVATE)
    }

    suspend fun insertAll(vararg files: IgcFile) {
        this.igcFileDao.insertAll(*files)
    }

    suspend fun flightUrlAlreadyKnown(uri: Uri): Boolean {
        return igcFileDao.findByUrl(uri.toString()) != null
    }

    suspend fun checkAndCleanPreviouslySeenFlight(igcFile: IgcFile, previouslySeenChecksums: HashSet<String> = HashSet()): Boolean {
        if (!igcFileDao.findBySha256Checksum(igcFile.sha256Checksum).isEmpty() || previouslySeenChecksums.contains(igcFile.sha256Checksum)) {
            //Yay - we prevented a duplicate upload.
            igcFile.isDuplicate = true
            igcFile.content = null
            igcFile.startDate = null
            igcFile.duration = Duration.ZERO
            return true
        } else {
            return false
        }
    }

    suspend fun markDuplicateIgcFiles(igcFiles: List<IgcFile>) {
        val previouslySeenChecksums = HashSet<String>()

        igcFiles.forEach { igcFile ->
            this@IgcFileDaoHelper.checkAndCleanPreviouslySeenFlight(igcFile, previouslySeenChecksums)
            previouslySeenChecksums.add(igcFile.sha256Checksum)
        }
    }

    fun isValidIgcFile(igcFile: IgcFile): Boolean {
        val minimumSeconds = this.preferences.getInt(this.applicationContext.getString(R.string.preference_minimum_flight_duration_seconds), 60)
        return igcFile.duration >= Duration.ofSeconds(minimumSeconds.toLong())
    }

    fun filterInvalidIgcFiles(igcFiles: List<IgcFile>): List<IgcFile> {
        return igcFiles.filter { igcFile ->
            this.isValidIgcFile(igcFile)
        }
    }

    fun createIgcFile(uri: Uri, filename: String, igcContent: String, importTimestamp: Date = Date(System.currentTimeMillis())): IgcFile {
        //Now let's compare the content (sha256-checksum) to files we've seen before
        val checksum = BigInteger(
                1, MessageDigest.getInstance("SHA-256").digest(igcContent.toByteArray())
        ).toString(16)

        val igcFile = IgcFile(uri.toString(), filename, checksum, importTimestamp)

        igcFile.content = igcContent
        try {
            val igcData = IgcData(igcContent)
            igcFile.startDate = igcData.startTime
            igcFile.duration = igcData.duration
        } catch (e: IgcException) {
            Log.e("", "Error parsing IGC file", e)
        }
        return igcFile
    }
}