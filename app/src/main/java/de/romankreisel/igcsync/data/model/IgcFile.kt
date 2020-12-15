package de.romankreisel.igcsync.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class IgcFile(
    @PrimaryKey val url: String,
    @ColumnInfo(name = "filename") val filename: String,
    @ColumnInfo(name = "sha256_checksum", index = true) var sha256Checksum: String,
    @ColumnInfo var content: String? = null,
    @ColumnInfo(name = "skip_always") var skipAlways: Boolean = false,
    @ColumnInfo(name = "uploaded_dhv_xc") var uploadedDhvXc: Boolean = false,
    @ColumnInfo(name = "start_date") var startDate: Date? = null,
)
