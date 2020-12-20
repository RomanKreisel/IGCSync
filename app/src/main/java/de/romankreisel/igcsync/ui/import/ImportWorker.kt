package de.romankreisel.igcsync.ui.import

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data.Builder
import androidx.work.WorkerParameters
import de.romankreisel.igcsync.data.IgcSyncDatabase
import de.romankreisel.igcsync.data.igc.IgcException
import de.romankreisel.igcsync.data.model.IgcFile
import de.romankreisel.igcsync.igc.IgcData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList

class ImportWorker(context: Context, private var workerParams: WorkerParameters) :
    CoroutineWorker(
        context,
        workerParams
    ) {
    override suspend fun doWork(): Result {
        val dataBuilder = Builder()
        try {
            val dataUrlString = workerParams.inputData.getString("dataUrl")
            if (dataUrlString == null || dataUrlString.isEmpty()) {
                return Result.failure()
            }

            val minimumFlightDurationSeconds =
                workerParams.inputData.getInt("minimumFlightDurationSeconds", -1)
            if (minimumFlightDurationSeconds < 0) {
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

            var files = this.getRealFilesForDirectory(igcDirectoryDocumentFile, dataBuilder)
            val igcFileDao = IgcSyncDatabase.getDatabase(this.applicationContext).igcFileDao()
            files = files.filter { igcFileDao.findByUrl(it.uri.toString()) == null }
            this.importIgcNewFiles(files, minimumFlightDurationSeconds, dataBuilder)
            return Result.success(dataBuilder.build())
        } catch (exception: Exception) {
            Log.e(this.javaClass.canonicalName, "An exception occurred", exception)
            return Result.failure(dataBuilder.build())
        }
    }

    private suspend fun importIgcNewFiles(
        files: List<DocumentFile>,
        minimumFlightDurationSeconds: Int,
        dataBuilder: Builder
    ) {
        var newFiles = 0
        val igcFiles = ArrayList<IgcFile>()
        val igcFileDao = IgcSyncDatabase.getDatabase(this.applicationContext).igcFileDao()
        var progressedFilesCount = 0
        val now = Date(System.currentTimeMillis())
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
                val checksum = BigInteger(
                    1, MessageDigest.getInstance("SHA-256").digest(igcContent.toByteArray())
                ).toString(16)

                val igcFile = IgcFile(file.uri.toString(), file.name!!, checksum, now)

                if (!igcFileDao.findBySha256Checksum(checksum).isEmpty()) {
                    //Yay - we prevented a duplicate upload.
                    //Anyway, let's remember this path
                    igcFile.isDuplicate = true
                    igcFiles.add(igcFile)
                } else {
                    //We never saw this content before, so we should remember the content for upload
                    igcFile.content = igcContent
                    try {
                        val igcData = IgcData(igcContent)
                        igcFile.startDate = igcData.startTime
                        igcFile.duration = igcData.duration
                    } catch (e: IgcException) {
                        Log.e("", "Error parsing IGC file", e)
                    }
                    if (igcFile.duration >= Duration.ofSeconds(minimumFlightDurationSeconds.toLong())) {
                        ++newFiles
                    }
                    igcFiles.add(igcFile)
                }
            } catch (exception: Exception) {
                dataBuilder.putString("failedFile", file.uri.toString())
                throw exception
            }
        }
        igcFileDao.insertAll(*igcFiles.toTypedArray())
        dataBuilder.putInt("fileCount", newFiles)
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