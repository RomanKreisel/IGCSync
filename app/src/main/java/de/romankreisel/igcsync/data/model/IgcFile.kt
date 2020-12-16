package de.romankreisel.igcsync.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Duration
import java.util.*

@Entity
data class IgcFile(
    @PrimaryKey var url: String,
    @ColumnInfo(name = "filename") var filename: String,
    @ColumnInfo(name = "sha256_checksum", index = true) var sha256Checksum: String,
    @ColumnInfo var content: String? = null,
    @ColumnInfo(name = "skip_always") var skipAlways: Boolean = false,
    @ColumnInfo(name = "uploaded_dhv_xc") var uploadedDhvXc: Boolean = false,
    @ColumnInfo(name = "start_date") var startDate: Date? = null,
    @ColumnInfo(name = "duration") var duration: Duration = Duration.ZERO,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        dateFromLong(parcel.readLong()),
        Duration.ofMillis(parcel.readLong())
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(filename)
        parcel.writeString(sha256Checksum)
        parcel.writeString(content)
        parcel.writeByte(if (skipAlways) 1 else 0)
        parcel.writeByte(if (uploadedDhvXc) 1 else 0)
        parcel.writeLong(this.startDate?.time ?: 0)
        parcel.writeLong(this.duration.toMillis())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<IgcFile> {
        override fun createFromParcel(parcel: Parcel): IgcFile {
            return IgcFile(parcel)
        }

        override fun newArray(size: Int): Array<IgcFile?> {
            return arrayOfNulls(size)
        }

        private fun dateFromLong(time: Long): Date {
            val date = Date()
            date.time = time
            return date
        }
    }
}
