package de.romankreisel.igcsync.igc

import android.annotation.SuppressLint
import de.romankreisel.igcsync.data.igc.IgcException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*

class IgcData(igcContent: String) {
    val bRecords: MutableList<BRecord> = ArrayList<BRecord>()
    val headers = Headers()

    init {
        val lines = igcContent.replace(Regex("\r+\n"), "\n").split("\n")
        lines.forEach { line ->
            if (line.length > 0) {
                when (line[0]) {
                    'H' -> this.headers.parseHeaderLine(line)
                    'B' -> this.bRecords.add(BRecord(this, line))

                }
            }
        }
    }

    val startTime: Date
        get() {
            if (this.bRecords.isEmpty()) {
                return this.headers.flightDate ?: Date(0)
            } else {
                return this.bRecords.first().recordTime
            }
        }

    val endTime: Date
        get() {
            if (this.bRecords.isEmpty()) {
                return this.headers.flightDate ?: Date(0)
            } else {
                return this.bRecords.last().recordTime
            }
        }

    val duration: Duration
        get() {
            return Duration.ofMillis(this.endTime.time - this.startTime.time)
        }
}


@SuppressLint("SimpleDateFormat")
class BRecord(igcData: IgcData, line: String) {

    var recordTime: Date
        private set

    init {
        if (!line.startsWith('B') || line.length < 7) {
            throw IgcException("Unexpected B Record line")
        }

        var previousDate: Date = igcData.headers.flightDate ?: Date(0L)
        if (igcData.bRecords.count() > 0) {
            previousDate = igcData.bRecords.last().recordTime
        }

        this.recordTime = SimpleDateFormat("yyyy-MM-dd HHmmss").parse(
            SimpleDateFormat("yyyy-MM-dd").format(previousDate) + " " + line.substring(
                1,
                7
            )
        ) ?: Date(0)


        if (igcData.bRecords.count() > 0 && igcData.bRecords.last().recordTime.time > this.recordTime.time) { //the flight just outlasted midnight (utc)
            val calendar = GregorianCalendar.getInstance()
            calendar.time = this.recordTime
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            this.recordTime = calendar.time
        }
    }

}

class Headers {
    @SuppressLint("SimpleDateFormat")
    fun parseHeaderLine(line: String) {
        if (line.startsWith("HFDTE") && line.length == 11) {
            this.flightDate = SimpleDateFormat("ddMMyy").parse(line.substring(5))
        } else if (line.startsWith("HFPLT")) {
            this.pilot = line.substring(5)
        } else if (line.startsWith("HFGTYGLIDERTYPE")) {
            this.glider = line.substring(15)
        }
    }

    var glider: String = ""
    var pilot: String = ""
    var flightDate: Date? = null
}