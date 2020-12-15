package de.romankreisel.igcsync.data.igc

import android.annotation.SuppressLint
import java.text.SimpleDateFormat

class IgcParser {
    companion object {
        @SuppressLint("SimpleDateFormat")
        fun parse(igcContent: String): IgcData {
            try {
                val data = IgcData()
                val lines = igcContent.replace(Regex("\r+\n"), "\n").split("\n")
                val hfdte = lines.filter {
                    it.startsWith("HFDTE")
                }.first()
                if (hfdte.length != 11)
                    throw IgcException("Unexpected HFDTE header in IGC file")

                var firstBRecord = lines.filter {
                    it.startsWith("B")
                }.firstOrNull()
                if (firstBRecord == null) {
                    firstBRecord = "B000000"
                }
                if (firstBRecord.length < 7)
                    throw IgcException("Unexpected B record in IGC file")

                val dateString = hfdte.substring(5) + "-" + firstBRecord.substring(1, 7)

                val date = SimpleDateFormat("ddMMyy-HHmmss").parse(dateString)
                data.startTime = date

                return data

            } catch (e: Exception) {
                throw IgcException("Error parsing IGC", e)
            }
        }
    }
}
