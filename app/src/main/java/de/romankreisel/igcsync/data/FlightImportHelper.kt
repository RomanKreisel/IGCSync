package de.romankreisel.igcsync.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import de.romankreisel.igcsync.MainActivity
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.dao.AlreadyImportedUrlDao
import de.romankreisel.igcsync.data.dao.FlightDao
import de.romankreisel.igcsync.data.model.AlreadyImportedUrl
import de.romankreisel.igcsync.data.model.Flight
import de.romankreisel.igcsync.igc.IgcData
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import kotlin.collections.HashSet

class FlightImportHelper(private val applicationContext: Context) {
    private var preferences: SharedPreferences
    private val flightDao: FlightDao
    private val alreadyImportedUrlDao: AlreadyImportedUrlDao

    init {
        val database = IgcSyncDatabase.getDatabase(this.applicationContext)
        this.flightDao = database.flightDao()
        this.alreadyImportedUrlDao = database.alreadyImportedUrlDao()
        this.preferences = this.applicationContext.getSharedPreferences(
            MainActivity::class.simpleName,
            Context.MODE_PRIVATE
        )
    }

    suspend fun insertAll(vararg flights: Flight) {
        this.flightDao.insertAll(*flights)
    }

    suspend fun insertAll(vararg alreadyImportedUrl: AlreadyImportedUrl) {
        this.alreadyImportedUrlDao.insertAll(*alreadyImportedUrl)
    }

    suspend fun flightUrlAlreadyKnown(uri: Uri): Boolean {
        return this.alreadyImportedUrlDao.findByUrl(uri.toString()).isNotEmpty()
    }

    suspend fun alreadyKnown(
        flight: Flight,
        previouslySeenChecksums: HashSet<String> = HashSet()
    ): Boolean {
        return !flightDao.findBySha256Checksum(flight.sha256Checksum)
            .isEmpty() || previouslySeenChecksums.contains(flight.sha256Checksum)
    }

    fun flightAboveMinimumDuration(flight: Flight): Boolean {
        val minimumSeconds = this.preferences.getInt(
            this.applicationContext.getString(R.string.preference_minimum_flight_duration_seconds),
            60
        )
        return flight.duration >= Duration.ofSeconds(minimumSeconds.toLong())
    }

    fun createFlight(
        filename: String,
        igcContent: String,
        importTimestamp: Date = Date(System.currentTimeMillis())
    ): Flight {
        //Now let's compare the content (sha256-checksum) to files we've seen before
        val checksum = BigInteger(
            1, MessageDigest.getInstance("SHA-256").digest(igcContent.toByteArray())
        ).toString(16)
        val igcData = IgcData(igcContent)
        val flight = Flight(
            checksum,
            filename,
            importTimestamp,
            igcContent,
            igcData.startTime,
            igcData.duration
        )
        return flight
    }
}