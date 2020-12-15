package de.romankreisel.igcsync

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.romankreisel.igcsync.ui.import.ImportWorker

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FlightListFragment : Fragment(), Observer<WorkInfo> {

    private lateinit var floatingImportButton: FloatingActionButton
    private lateinit var preferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_flight_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            // findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        this.floatingImportButton = view.findViewById<FloatingActionButton>(R.id.fab)
        floatingImportButton.setOnClickListener {
            this.importFlights()
        }
    }

    private fun importFlights() {
        val igcDirectoryUrlString = this.preferences.getString(
            getString(R.string.preference_igc_directory_url),
            null
        )
        if (igcDirectoryUrlString == null || igcDirectoryUrlString.isBlank()) {
            Toast.makeText(
                this.requireContext(),
                getString(R.string.warning_error_accessing_data_directory),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val igcDirectoryDocumentFile =
            DocumentFile.fromTreeUri(this.requireContext(), Uri.parse(igcDirectoryUrlString))

        if (igcDirectoryDocumentFile == null || !igcDirectoryDocumentFile.exists() || !igcDirectoryDocumentFile.isDirectory || !igcDirectoryDocumentFile.canRead()) {
            Toast.makeText(
                this.requireContext(),
                getString(R.string.warning_error_accessing_data_directory),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val scanWorkRequest = OneTimeWorkRequest.Builder(ImportWorker::class.java).setInputData(
            androidx.work.Data.Builder().putString("dataUrl", igcDirectoryUrlString).build()
        ).build()
        val workManager = WorkManager.getInstance(this.requireContext())
        this.floatingImportButton.isEnabled = false
        Toast.makeText(this.requireContext(), "Started", Toast.LENGTH_LONG).show()
        workManager.enqueue(scanWorkRequest)

        workManager.getWorkInfoByIdLiveData(scanWorkRequest.id).observeForever(this)
    }

    override fun onChanged(workInfo: WorkInfo?) {
        when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                this.floatingImportButton.isEnabled = true
                Toast.makeText(this.requireContext(), "Succeeded", Toast.LENGTH_LONG).show()

                /*this.importViewModel.text.apply {
                        val fileCount = workInfo.outputData.getInt("fileCount", 0)
                        if (fileCount > 1) {
                            value = getString(
                                R.string.label_scan_finished_with_files_found,
                                workInfo.outputData.getInt("fileCount", 0)
                            )
                        } else if (fileCount > 0) {
                            value = getString(R.string.label_scan_finished_with_file_found)
                        } else {
                            value = getString(R.string.label_scan_finished_with_no_files_found)
                        }
                    }
                    this.importViewModel.scanButtonAvailable.apply { value = true }
                     */
            }
            WorkInfo.State.FAILED -> {
                Toast.makeText(this.requireContext(), "Failed", Toast.LENGTH_LONG).show()
                /*this.importViewModel.text.apply {
                    value = getString(
                        R.string.label_scan_failed,
                        workInfo.outputData.getString("failedFile")
                    )
                }
                this.importViewModel.scanButtonAvailable.apply { value = true }
                 */
            }
            WorkInfo.State.CANCELLED -> {
                Toast.makeText(this.requireContext(), "Cancelled", Toast.LENGTH_LONG).show()
                /*this.importViewModel.text.apply {
                    value = getString(R.string.label_scan_cancelled)
                }
                this.importViewModel.scanButtonAvailable.apply { value = true }

                 */
            }
            WorkInfo.State.RUNNING -> {
                /*val filesProgressed = workInfo.progress.getInt("filesProgressed", 0)
                val filesTotal = workInfo.progress.getInt("filesTotal", 0)
                if (filesTotal > 0) {
                    this.importViewModel.text.apply {
                        value = getString(
                            R.string.label_scanning_progress,
                            (filesProgressed * 100 / filesTotal)
                        )
                    }
                }

                 */
            }
            else -> {
            }
        }
    }
}