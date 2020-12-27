package de.romankreisel.igcsync.ui.flightlist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
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
import de.romankreisel.igcsync.data.model.IgcFile
import de.romankreisel.igcsync.ui.import.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FlightListFragment : Fragment(), Observer<WorkInfo>, OnItemClickListener {

    private var recyclerLayoutState: Parcelable? = null
    private lateinit var recyclerViewLayoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var flightListViewModel: FlightListViewModel
    private lateinit var igcFileDao: IgcFileDao
    private lateinit var preferences: SharedPreferences
    private lateinit var floatingImportButton: FloatingActionButton
    private lateinit var recyclerViewIgcFiles: RecyclerView
    private val RECYCLER_LAYOUT_STATE = "RecyclerViewLayoutState"


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        this.requireActivity().setTitle(R.string.app_name)
        this.preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)
        this.igcFileDao =
                IgcSyncDatabase.getDatabase(this.requireActivity().applicationContext).igcFileDao()
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_flight_list, container, false)
        this.recyclerViewIgcFiles =
                root.findViewById<RecyclerView>(R.id.recycler_view_igc_files)
        this.recyclerViewLayoutManager = LinearLayoutManager(activity)
        recyclerViewIgcFiles.apply {
            layoutManager = this@FlightListFragment.recyclerViewLayoutManager
            adapter = IgcFilesAdapter(
                    Collections.unmodifiableList(Collections.emptyList()),
                    this@FlightListFragment
            )
        }

        this.flightListViewModel = ViewModelProvider(this).get(FlightListViewModel::class.java)
        this.flightListViewModel.igcFiles.observe(viewLifecycleOwner, {
            val myValue = flightListViewModel.igcFiles.value
            if (myValue != null) {
                recyclerViewIgcFiles.adapter = IgcFilesAdapter(myValue, this)
            } else {
                recyclerViewIgcFiles.adapter =
                        IgcFilesAdapter(Collections.unmodifiableList(Collections.emptyList()), this)
            }
        })

        this.progressBar = root.findViewById<ProgressBar>(R.id.progress_bar)
        this.progressBar.visibility = View.GONE
        this.progressText = root.findViewById<TextView>(R.id.progress_text)
        this.progressText.visibility = View.GONE

        val layoutState = savedInstanceState?.getParcelable<Parcelable>(RECYCLER_LAYOUT_STATE)

        if (flightListViewModel.igcFiles.value.isNullOrEmpty()) {
            this.UpdateView()
        }
        this.recyclerViewLayoutManager.onRestoreInstanceState(layoutState)
        this.recyclerViewLayoutManager.onRestoreInstanceState(this.recyclerLayoutState)
        return root
    }

    override fun onPause() {
        super.onPause()
        this.recyclerLayoutState = this.recyclerViewLayoutManager.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(RECYCLER_LAYOUT_STATE, this.recyclerViewLayoutManager.onSaveInstanceState())
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
            var myIgcFiles = igcFileDao.getAll(Duration.ofSeconds(this@FlightListFragment.preferences.getInt(getString(R.string.preference_minimum_flight_duration_seconds), 60).toLong()))
            if (myIgcFiles.isEmpty()) {
                val igcContent = resources.openRawResource(R.raw.greifenburg).bufferedReader().readText()
                val demoIgcFile = IgcFile(
                        "https://www.dhv-xc.de/leonardo/index.php?op=show_flight&flightID=1298039",
                        "demoflight.igc",
                        "b40257758c2574f7240b12ec68d87c78065a86ee6d0288845740c0831813ebdd",
                        Date(1609081375682),
                        igcContent,
                        false,
                        Date(1597929737000),
                        Duration.ofHours(2) + Duration.ofMinutes(2) + Duration.ofSeconds(48),
                        "https://www.dhv-xc.de/leonardo/index.php?op=show_flight&flightID=1298039",
                        false,
                        true
                )
                myIgcFiles = ArrayList<IgcFile>()
                myIgcFiles.add(demoIgcFile)
            }

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
                androidx.work.Data.Builder()
                        .putString("dataUrl", igcDirectoryUrlString)
                        .putInt(
                                "minimumFlightDurationSeconds",
                                this.preferences.getInt(
                                        getString(R.string.preference_minimum_flight_duration_seconds),
                                        60
                                )
                        )
                        .build()
        ).build()
        val workManager = WorkManager.getInstance(this.requireContext())
        this.floatingImportButton.isEnabled = false
        this.progressBar.visibility = View.VISIBLE
        this.progressText.text = getString(R.string.toast_scan_for_new_flights)
        this.progressBar.isIndeterminate = true
        this.progressText.visibility = View.VISIBLE
        workManager.enqueue(scanWorkRequest)

        workManager.getWorkInfoByIdLiveData(scanWorkRequest.id).observeForever(this)
    }

    override fun onChanged(workInfo: WorkInfo?) {
        when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
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
                this.progressBar.progress = 100
                this.progressBar.isIndeterminate = false
                this.progressText.text = text
                this.UpdateView()
                Handler(Looper.getMainLooper()).postDelayed({
                    this.floatingImportButton.isEnabled = true
                    this.progressBar.visibility = View.GONE
                    this.progressText.visibility = View.GONE
                }, 10000)
            }
            WorkInfo.State.FAILED -> {
                Toast.makeText(this.requireContext(), getString(R.string.label_scan_failed), Toast.LENGTH_LONG).show()
                this.floatingImportButton.isEnabled = true
                this.progressBar.visibility = View.GONE
                this.progressText.visibility = View.GONE
            }
            WorkInfo.State.CANCELLED -> {
                Toast.makeText(this.requireContext(), getString(R.string.label_scan_cancelled), Toast.LENGTH_LONG).show()
                this.floatingImportButton.isEnabled = true
                this.progressBar.visibility = View.GONE
                this.progressText.visibility = View.GONE
            }
            WorkInfo.State.RUNNING -> {
                val filesProgressed = workInfo.progress.getInt("filesProgressed", 0)
                val filesTotal = workInfo.progress.getInt("filesTotal", 0)
                if (filesTotal > 0) {
                    this.progressText.text = getString(
                            R.string.label_scanning_progress,
                            (filesProgressed * 100 / filesTotal))
                    this.progressBar.isIndeterminate = false
                    this.progressBar.progress = (filesProgressed * 100 / filesTotal)
                }
            }
            else -> {
            }
        }
    }

    override fun onItemClicked(igcFile: IgcFile) {
        val action = FlightListFragmentDirections.actionFirstFragmentToFlightFragment(igcFile)
        this.requireActivity().findNavController(R.id.recycler_view_igc_files).navigate(action)
    }
}