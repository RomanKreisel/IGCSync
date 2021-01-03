package de.romankreisel.igcsync.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.romankreisel.igcsync.data.dao.AlreadyImportedUrlDao
import de.romankreisel.igcsync.data.dao.FlightDao
import de.romankreisel.igcsync.data.model.AlreadyImportedUrl
import de.romankreisel.igcsync.data.model.Flight
import java.time.Duration
import java.util.*

@Database(
    entities = arrayOf(Flight::class, AlreadyImportedUrl::class),
    version = 2,
    exportSchema = true
)
@TypeConverters(IgcSyncDatabase.Converters::class)
abstract class IgcSyncDatabase : RoomDatabase() {
    abstract fun flightDao(): FlightDao
    abstract fun alreadyImportedUrlDao(): AlreadyImportedUrlDao

    companion object {
        private var database: IgcSyncDatabase? = null
        private val databaseLock = object {}

        fun getDatabase(applicationContext: Context): IgcSyncDatabase {
            var myDatabase = database
            if (myDatabase != null) {
                return myDatabase
            }
            synchronized(databaseLock) {
                myDatabase = database
                if (myDatabase == null) {
                    myDatabase = Room.databaseBuilder(
                        applicationContext,
                        IgcSyncDatabase::class.java,
                        "igc-sync"
                    ).addMigrations(Migrate1to2(1, 2))
                        .build()
                    database = myDatabase
                }
                return myDatabase as IgcSyncDatabase
            }
        }
    }


    private class Migrate1to2(startVersion: Int, endVersion: Int) :
        Migration(startVersion, endVersion) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val TABLE_NAME_FLIGHT = "Flight"
            val TABLE_NAME_ALREADY_IMPORTED_URL = "AlreadyImportedUrl"
            val TABLE_NAME_IGC_FILES = "IgcFile"
            database.execSQL("DROP TABLE IF EXISTS `${TABLE_NAME_FLIGHT}`")
            database.execSQL("DROP TABLE IF EXISTS `${TABLE_NAME_ALREADY_IMPORTED_URL}`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_NAME_FLIGHT}` (`sha256_checksum` TEXT NOT NULL, `filename` TEXT NOT NULL, `import_date` INTEGER NOT NULL, `content` TEXT NOT NULL, `start_date` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `dhvxc_flight_url` TEXT, `is_favorite` INTEGER NOT NULL, `is_demo` INTEGER NOT NULL, PRIMARY KEY(`sha256_checksum`))")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_Flight_sha256_checksum` ON `${TABLE_NAME_FLIGHT}` (`sha256_checksum`)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `${TABLE_NAME_ALREADY_IMPORTED_URL}` (`url` TEXT NOT NULL, `sha256_checksum` TEXT NOT NULL, PRIMARY KEY(`url`))")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_AlreadyImportedUrl_sha256_checksum` ON `${TABLE_NAME_ALREADY_IMPORTED_URL}` (`sha256_checksum`)")
            database.execSQL("INSERT INTO ${TABLE_NAME_FLIGHT} SELECT sha256_checksum, filename, import_date, content, start_date, duration, dhvxc_flight_url, is_favorite, is_demo FROM ${TABLE_NAME_IGC_FILES} WHERE content is not null and start_date != 0")
            database.execSQL("DROP TABLE IF EXISTS `${TABLE_NAME_IGC_FILES}`")
        }

    }

    class Converters {
        @TypeConverter
        fun fromTimestamp(value: Long?): Date? {
            return value?.let { Date(it) }
        }

        @TypeConverter
        fun dateToTimestamp(date: Date?): Long? {
            return date?.time
        }

        @TypeConverter
        fun fromDurationMilliseconds(value: Long?): Duration? {
            return value?.let { Duration.ofMillis(it) }
        }

        @TypeConverter
        fun dateToTimestamp(duration: Duration?): Long? {
            return duration?.toMillis()
        }
    }

}