package de.romankreisel.igcsync.data.model

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AlreadyImportedUrl(
        @PrimaryKey @ColumnInfo(name = "url") var url: String,
        @ColumnInfo(name = "sha256_checksum", index = true) var sha256Checksum: String,
) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "") {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(sha256Checksum)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AlreadyImportedUrl> {
        override fun createFromParcel(parcel: Parcel): AlreadyImportedUrl {
            return AlreadyImportedUrl(parcel)
        }

        override fun newArray(size: Int): Array<AlreadyImportedUrl?> {
            return arrayOfNulls(size)
        }
    }

}
