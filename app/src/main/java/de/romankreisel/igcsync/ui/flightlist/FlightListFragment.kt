package de.romankreisel.igcsync.ui.flightlist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.romankreisel.igcsync.MainActivity
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.IgcSyncDatabase
import de.romankreisel.igcsync.data.dao.IgcFileDao
import de.romankreisel.igcsync.ui.import.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FlightListFragment : Fragment(), Observer<WorkInfo> {

    private lateinit var flightListViewModel: FlightListViewModel
    private lateinit var igcFileDao: IgcFileDao
    private lateinit var preferences: SharedPreferences
    private lateinit var floatingImportButton: FloatingActionButton
    private lateinit var recyclerViewIgcFiles: RecyclerView


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        this.preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)
        this.igcFileDao = IgcSyncDatabase.getDatabase(this.requireActivity().applicationContext).igcFileDao()
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_flight_list, container, false)
        this.recyclerViewIgcFiles =
                root.findViewById<RecyclerView>(R.id.recycler_view_igc_files)
        recyclerViewIgcFiles.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = IgcFilesAdapter(
                    Collections.unmodifiableList(Collections.emptyList())
            )
        }
        this.flightListViewModel = ViewModelProvider(this).get(FlightListViewModel::class.java)
        this.flightListViewModel.igcFiles.observe(viewLifecycleOwner, {
            val myValue = flightListViewModel.igcFiles.value
            if (myValue != null) {
                recyclerViewIgcFiles.adapter = IgcFilesAdapter(myValue)
            } else {
                recyclerViewIgcFiles.adapter =
                        IgcFilesAdapter(Collections.unmodifiableList(Collections.emptyList()))
            }
        })

        this.UpdateView()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.floatingImportButton = view.findViewById<FloatingActionButton>(R.id.fab)
        floatingImportButton.setOnClickListener {
            this.importFlights()
        }
    }

    private fun UpdateView() {
        CoroutineScope(Dispatchers.IO).launch {
            val myIgcFiles = igcFileDao.findFilesWithMissingUpload()
            requireActivity().runOnUiThread {
                flightListViewModel.igcFiles.value = Collections.unmodifiableList(myIgcFiles)
            }
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
            requireActivity().startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    MainActivity.REQUEST_CODE_IGC_DATA_DIRECTORY
            )
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
        Toast.makeText(this.requireContext(), getString(R.string.toast_scan_for_new_flights), Toast.LENGTH_LONG).show()

        val animation = AnimationUtils.loadAnimation(this.requireContext(), R.anim.pulse)
        this.floatingImportButton.startAnimation(animation)

        workManager.enqueue(scanWorkRequest)

        workManager.getWorkInfoByIdLiveData(scanWorkRequest.id).observeForever(this)
    }

    override fun onChanged(workInfo: WorkInfo?) {
        when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                this.floatingImportButton.isEnabled = true
                this.floatingImportButton.animation.cancel()
                val fileCount = workInfo.outputData.getInt("fileCount", 0)
                val text: String
                if (fileCount > 1) {
                    text = getString(
                            R.string.label_scan_finished_with_files_found,
                            workInfo.outputData.getInt("fileCount", 0)
                    )
                } else if (fileCount > 0) {
                    text = getString(R.string.label_scan_finished_with_file_found)
                } else {
                    text = getString(R.string.label_scan_finished_with_no_files_found)
                }
                Toast.makeText(this.requireContext(), text, Toast.LENGTH_LONG).show()
            }
            WorkInfo.State.FAILED -> {
                Toast.makeText(this.requireContext(), "Failed", Toast.LENGTH_LONG).show()
                //TODO: better error message
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
                //TODO: better error message
                /*this.importViewModel.text.apply {
                    value = getString(R.string.label_scan_cancelled)
                }
                this.importViewModel.scanButtonAvailable.apply { value = true }

                 */
            }
            WorkInfo.State.RUNNING -> {
                /*
                val filesProgressed = workInfo.progress.getInt("filesProgressed", 0)
                val filesTotal = workInfo.progress.getInt("filesTotal", 0)
                if (filesTotal > 0) {
                    this.floatingImportButton.animate()
                }*/
                /*    this.importViewModel.text.apply {
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