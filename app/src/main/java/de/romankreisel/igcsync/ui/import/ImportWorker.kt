package de.romankreisel.igcsync.ui.import

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data.Builder
import androidx.work.WorkerParameters
import de.romankreisel.igcsync.data.FlightImportHelper
import de.romankreisel.igcsync.data.igc.IgcException
import de.romankreisel.igcsync.data.model.AlreadyImportedUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class ImportWorker(context: Context, private var workerParams: WorkerParameters) :
    CoroutineWorker(
        context,
        workerParams
    ) {

    private val flightImportHelper: FlightImportHelper = FlightImportHelper(this.applicationContext)

    override suspend fun doWork(): Result {

        val dataBuilder = Builder()
        try {
            val dataUrlString = workerParams.inputData.getString("dataUrl")
            if (dataUrlString == null || dataUrlString.isEmpty()) {
                return Result.failure()
            }

            val igcDirectoryDocumentFile =
                DocumentFile.fromTreeUri(this.applicationContext, Uri.parse(dataUrlString))

            if (igcDirectoryDocumentFile == null || !igcDirectoryDocumentFile.exists() || !igcDirectoryDocumentFile.isDirectory || !igcDirectoryDocumentFile.canRead()) {
                return Result.failure(
                    dataBuilder.putString(
                        "failedFile",
                        igcDirectoryDocumentFile?.uri.toString()
                    ).build()
                )
            }

            val files = this.getRealFilesForDirectory(igcDirectoryDocumentFile, dataBuilder)
                .filter { !this.flightImportHelper.flightUrlAlreadyKnown(it.uri) }
            this.importIgcNewFiles(files, dataBuilder)
            return Result.success(dataBuilder.build())
        } catch (exception: Exception) {
            Log.e(this.javaClass.canonicalName, "An exception occurred", exception)
            return Result.failure(dataBuilder.build())
        }
    }

    private suspend fun importIgcNewFiles(
        files: List<DocumentFile>,
        dataBuilder: Builder
    ) {
        var progressedFilesCount = 0
        val now = Date(System.currentTimeMillis())
        var importedFilesCount = 0
        val previouslySeenChecksums = HashSet<String>()
        for (file in files) {
            this.setProgressAsync(
                dataBuilder.putInt("filesProgressed", ++progressedFilesCount)
                    .putInt("filesTotal", files.count()).build()
            )
            try {
                val lowerFilename = file.name?.toLowerCase(Locale.getDefault())
                if (lowerFilename == null || !lowerFilename.endsWith(".igc")) continue //ignore everything but IGCs
                if (file.length() > 10 * 1024 * 1024) continue //ignore files > 10MByte - IGCs are supposed to be much smaller

                //OK, so at this point, we know that we haven't seen this file under this path on this storage before.
                //Now let's compare the content (sha256-checksum) to files we've seen before

                val igcContent = withContext<String?>(Dispatchers.IO) {
                    applicationContext.contentResolver.openInputStream(file.uri)?.bufferedReader()
                        ?.readText()
                }
                if (igcContent == null) continue //we also skip files, if we didn't get an inputstream for them
                try {
                    val flight =
                        this.flightImportHelper.createFlight(file.name!!, igcContent, now)
                    if (this.flightImportHelper.alreadyKnown(flight, previouslySeenChecksums)) {
                        this.flightImportHelper.insertAll(
                            AlreadyImportedUrl(
                                file.uri.toString(),
                                flight.sha256Checksum
                            )
                        )
                        continue
                    }
                    previouslySeenChecksums.add(flight.sha256Checksum)
                    if (this.flightImportHelper.flightAboveMinimumDuration(flight)) {
                        importedFilesCount++
                    }
                    this.flightImportHelper.insertAll(flight)
                    this.flightImportHelper.insertAll(
                        AlreadyImportedUrl(
                            file.uri.toString(),
                            flight.sha256Checksum
                        )
                    )
                } catch (e: IgcException) {
                    Log.e(this.javaClass.name, "Error importing IGC from ${file.uri.toString()}", e)
                    this.flightImportHelper.insertAll(AlreadyImportedUrl(file.uri.toString(), ""))
                }
            } catch (exception: Exception) {
                dataBuilder.putString("failedFile", file.uri.toString())
                throw exception
            }
        }
        dataBuilder.putInt("fileCount", importedFilesCount)
    }


    private fun getRealFilesForDirectory(
        directory: DocumentFile,
        dataBuilder: Builder
    ): List<DocumentFile> {
        val list = ArrayList<DocumentFile>()
        directory.listFiles().forEach {
            try {
                if (it.isDirectory) {
                    list.addAll(this.getRealFilesForDirectory(it, dataBuilder))
                } else if (it.isFile) {
                    list.add(it)
                }
            } catch (exception: Exception) {
                dataBuilder.putString("failedFile", it.uri.toString())
                throw exception
            }
        }
        return list
    }
}