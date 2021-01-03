package de.romankreisel.igcsync.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Duration
import java.util.*

@Entity
data class Flight(
        @PrimaryKey @ColumnInfo(name = "sha256_checksum", index = true) var sha256Checksum: String,
        @ColumnInfo(name = "filename") var filename: String,
        @ColumnInfo(name = "import_date") var import_date: Date,
        @ColumnInfo(name = "content") var content: String,
        @ColumnInfo(name = "start_date") var startDate: Date,
        @ColumnInfo(name = "duration") var duration: Duration = Duration.ZERO,
        @ColumnInfo(name = "dhvxc_flight_url") var dhvXcFlightUrl: String? = null,
        @ColumnInfo(name = "is_favorite") var isFavorite: Boolean = false,
        @ColumnInfo(name = "is_demo") var isDemo: Boolean = false,
) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            dateFromLong(parcel.readLong()),
            parcel.readString() ?: "",
            dateFromLong(parcel.readLong()),
            Duration.ofMillis(parcel.readLong()),
            parcel.readString(),
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte()) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sha256Checksum)
        parcel.writeString(filename)
        parcel.writeLong(this.import_date.time)
        parcel.writeString(content)
        parcel.writeLong(this.startDate.time)
        parcel.writeLong(this.duration.toMillis())
        parcel.writeString(dhvXcFlightUrl)
        parcel.writeByte(if (isFavorite) 1 else 0)
        parcel.writeByte(if (isDemo) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Flight> {
        override fun createFromParcel(parcel: Parcel): Flight {
            return Flight(parcel)
        }

        override fun newArray(size: Int): Array<Flight?> {
            return arrayOfNulls(size)
        }

        private fun dateFromLong(time: Long): Date {
            val date = Date()
            date.time = time
            return date
        }
    }

}
