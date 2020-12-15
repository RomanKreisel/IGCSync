package de.romankreisel.igcsync.data

import android.content.Context
import androidx.room.*
import de.romankreisel.igcsync.data.dao.IgcFileDao
import de.romankreisel.igcsync.data.model.IgcFile
import java.util.*

@Database(entities = arrayOf(IgcFile::class), version = 1, exportSchema = false)
@TypeConverters(IgcSyncDatabase.Converters::class)
abstract class IgcSyncDatabase : RoomDatabase() {
    abstract fun igcFileDao(): IgcFileDao

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
                    ).build()
                    database = myDatabase
                }
                return myDatabase as IgcSyncDatabase
            }
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
    }

}