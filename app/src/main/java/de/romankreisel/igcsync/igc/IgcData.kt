package de.romankreisel.igcsync.igc

import android.annotation.SuppressLint
import de.romankreisel.igcsync.data.igc.IgcException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.regex.Pattern

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
    var latitude: Double
        private set
    var longitude: Double
        private set

    init {
        val matcher = Pattern.compile("^[B](\\d{6})(\\d{2})(\\d{5})([NS])(\\d{3})(\\d{5})([EW]).*$")
                .matcher(line)
        if (!matcher.find()) {
            throw IgcException("Unexpected B Record line")
        }

        var previousDate: Date = igcData.headers.flightDate ?: Date(0L)
        if (igcData.bRecords.count() > 0) {
            previousDate = igcData.bRecords.last().recordTime
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd")
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val sdtf = SimpleDateFormat("yyyy-MM-dd HHmmss")
        sdtf.timeZone = TimeZone.getTimeZone("UTC")
        this.recordTime = sdtf.parse(
                sdf.format(previousDate) + " " + matcher.group(1)
        ) ?: Date(0)

        //latitude
        val latitudeMultiplier = if (matcher.group(4) == "N") 1 else -1
        this.latitude =
                latitudeMultiplier * matcher.group(2)!!.toInt() + matcher.group(3)!!.toInt() / 60000.0

        //Longitude
        val longitudeMultiplier = if (matcher.group(7) == "E") 1 else -1
        this.longitude =
                longitudeMultiplier * matcher.group(5)!!.toInt() + matcher.group(6)!!.toInt() / 60000.0


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
        val dateHeaderMatcher =
                Pattern.compile("^HFDTE(?:DATE:){0,1}([0-9]{6})(?:,[0-9]*){0,1}$")
                        .matcher(line)
        if (dateHeaderMatcher.find()) {
            val dateString = dateHeaderMatcher.group(1)
            if (dateString != null) {
                val sdf = SimpleDateFormat("ddMMyy")
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                this.flightDate = sdf.parse(dateString)
            }
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