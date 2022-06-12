package de.romankreisel.igcsync.ui.flightlist

import android.app.Activity
import android.app.AlertDialog
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
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.IgcSyncDatabase
import de.romankreisel.igcsync.data.dao.FlightDao
import de.romankreisel.igcsync.data.model.Flight
import de.romankreisel.igcsync.igc.IgcData
import de.romankreisel.igcsync.ui.import.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FlightListFragment : Fragment(), Observer<WorkInfo>, FlightItemListener {
    companion object {
        const val REQUEST_CODE_IGC_DATA_DIRECTORY_AND_IMPORT = 1001
    }

    private var recyclerLayoutState: Parcelable? = null
    private lateinit var recyclerViewLayoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var flightListViewModel: FlightListViewModel
    private lateinit var flightDao: FlightDao
    private lateinit var preferences: SharedPreferences
    private lateinit var floatingImportButton: FloatingActionButton
    private lateinit var recyclerViewFlights: RecyclerView
    private val RECYCLER_LAYOUT_STATE = "RecyclerViewLayoutState"


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        this.requireActivity().setTitle(R.string.app_name)
        this.preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)
        this.flightDao =
            IgcSyncDatabase.getDatabase(this.requireActivity().applicationContext).flightDao()
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_flight_list, container, false)
        this.recyclerViewFlights =
            root.findViewById<RecyclerView>(R.id.recycler_view_igc_files)
        this.recyclerViewLayoutManager = LinearLayoutManager(activity)

        val flightsAdapter = FlightsAdapter(
            Collections.unmodifiableList(Collections.emptyList()),
            this@FlightListFragment
        )

        recyclerViewFlights.apply {
            layoutManager = this@FlightListFragment.recyclerViewLayoutManager
            adapter = flightsAdapter
        }

        this.flightListViewModel = ViewModelProvider(this).get(FlightListViewModel::class.java)
        this.flightListViewModel.flights = flightDao.getAllAsLivedata(
            Duration.ofSeconds(
                this@FlightListFragment.preferences.getInt(
                    getString(R.string.preference_minimum_flight_duration_seconds), 60
                ).toLong()
            )
        )
        this.flightListViewModel.flights.observe(viewLifecycleOwner, {
            val myValue = flightListViewModel.flights.value

            if (myValue.isNullOrEmpty()) {
                val igcContent =
                    resources.openRawResource(R.raw.greifenburg).bufferedReader().readText()
                val demoFlight = Flight(
                    "b40257758c2574f7240b12ec68d87c78065a86ee6d0288845740c0831813ebdd",
                    "demoflight.igc",
                    Date(1609081375682),
                    igcContent,
                    Date(1597929737000),
                    Duration.ofHours(2) + Duration.ofMinutes(2) + Duration.ofSeconds(48),
                    "https://www.dhv-xc.de/leonardo/index.php?op=show_flight&flightID=1298039",
                    false,
                    true
                )
                val demoData = ArrayList<Flight>()
                demoData.add(demoFlight)
                flightsAdapter.setData(demoData)
            } else {
                flightsAdapter.setData(myValue)
            }
        })

        this.progressBar = root.findViewById<ProgressBar>(R.id.progress_bar)
        this.progressBar.visibility = View.GONE
        this.progressText = root.findViewById<TextView>(R.id.progress_text)
        this.progressText.visibility = View.GONE

        val layoutState = savedInstanceState?.getParcelable<Parcelable>(RECYCLER_LAYOUT_STATE)

        this.recyclerViewLayoutManager.onRestoreInstanceState(layoutState)
        this.recyclerViewLayoutManager.onRestoreInstanceState(this.recyclerLayoutState)
        return root
    }

    private fun reparseIfRequired() {

        val lastReparse = this.preferences.getString(getString(R.string.preference_reparse_igc), "")
        val reparseNecessaryForVersion = "0.2.1"
        if (!reparseNecessaryForVersion.equals(lastReparse)) {
            this@FlightListFragment.floatingImportButton.isEnabled = false
            this.progressText.visibility = View.VISIBLE
            this.progressText.text = getString(R.string.inform_about_reparse_igc)
            this.progressBar.visibility = View.VISIBLE
            this.progressBar.progress = 0
            this.progressBar.isIndeterminate = false
            CoroutineScope(Dispatchers.IO).launch {
                val db = IgcSyncDatabase.getDatabase(this@FlightListFragment.requireContext())
                val flightDao = db.flightDao()
                val allFlights = flightDao.getAll(Duration.ZERO)
                var count = 0
                allFlights.forEach { flight ->
                    val calendar = GregorianCalendar()
                    calendar.time = flight.startDate
                    val currentYear = calendar.get(Calendar.YEAR)
                    if (currentYear <= 1970) {
                        val igcData = IgcData(flight.content)
                        flight.startDate = igcData.startTime
                        flight.duration = igcData.duration
                        flightDao.update(flight)
                    }
                    this@FlightListFragment.requireActivity().runOnUiThread {
                        this@FlightListFragment.progressBar.progress =
                            (++count * 100) / allFlights.count()
                    }
                }
                this@FlightListFragment.requireActivity().runOnUiThread {
                    this@FlightListFragment.floatingImportButton.isEnabled = true
                    this@FlightListFragment.progressText.visibility = View.GONE
                    this@FlightListFragment.progressBar.visibility = View.GONE
                    this@FlightListFragment.preferences.edit().putString(
                        getString(R.string.preference_reparse_igc),
                        reparseNecessaryForVersion
                    ).apply()
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        this.recyclerLayoutState = this.recyclerViewLayoutManager.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(
            RECYCLER_LAYOUT_STATE,
            this.recyclerViewLayoutManager.onSaveInstanceState()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.floatingImportButton = view.findViewById<FloatingActionButton>(R.id.fab)
        floatingImportButton.setOnClickListener {
            this.importFlights()
        }
        this.reparseIfRequired()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_IGC_DATA_DIRECTORY_AND_IMPORT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    this.requireActivity().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    preferences.edit()
                        .putString(
                            getString(R.string.preference_igc_directory_url),
                            uri.toString()
                        )
                        .apply()
                }
            }
            CoroutineScope(Dispatchers.IO).launch {
                IgcSyncDatabase.getDatabase(this@FlightListFragment.requireContext())
                    .alreadyImportedUrlDao()
                    .deleteAll()
                this@FlightListFragment.requireActivity().runOnUiThread {
                    this@FlightListFragment.importFlights()
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun importFlights() {
        val igcDirectoryUrlString = this.preferences.getString(
            getString(R.string.preference_igc_directory_url),
            null
        )
        if (igcDirectoryUrlString == null || igcDirectoryUrlString.isBlank()) {
            this.startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                REQUEST_CODE_IGC_DATA_DIRECTORY_AND_IMPORT
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
                Handler(Looper.getMainLooper()).postDelayed({
                    this.floatingImportButton.isEnabled = true
                    this.progressBar.visibility = View.GONE
                    this.progressText.visibility = View.GONE
                }, 10000)
            }
            WorkInfo.State.FAILED -> {
                Toast.makeText(
                    this.requireContext(),
                    getString(R.string.label_scan_failed),
                    Toast.LENGTH_LONG
                ).show()
                this.floatingImportButton.isEnabled = true
                this.progressBar.visibility = View.GONE
                this.progressText.visibility = View.GONE
            }
            WorkInfo.State.CANCELLED -> {
                Toast.makeText(
                    this.requireContext(),
                    getString(R.string.label_scan_cancelled),
                    Toast.LENGTH_LONG
                ).show()
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
                        (filesProgressed * 100 / filesTotal)
                    )
                    this.progressBar.isIndeterminate = false
                    this.progressBar.progress = (filesProgressed * 100 / filesTotal)
                }
            }
            else -> {
            }
        }
    }

    override fun onItemClicked(flight: Flight) {
        if (flight.isDemo) {
            AlertDialog.Builder(this.context)
                .setMessage(
                    this.requireContext().getString(R.string.message_this_is_a_demo_flight)
                )
                .setTitle(this.requireContext().getString(R.string.title_demo_flight))
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
        val action = FlightListFragmentDirections.actionFirstFragmentToFlightFragment(flight)
        this.requireActivity().findNavController(R.id.recycler_view_igc_files).navigate(action)
    }

    override fun onItemDeleted(flight: Flight) {
        CoroutineScope(Dispatchers.IO).launch {
            IgcSyncDatabase.getDatabase(this@FlightListFragment.requireContext().applicationContext)
                .flightDao().deleteBySha(flight.sha256Checksum)
        }
    }

    override fun onItemMarkedAsFavorite(flight: Flight) {
        CoroutineScope(Dispatchers.IO).launch {
            flight.isFavorite = !flight.isFavorite
            IgcSyncDatabase.getDatabase(this@FlightListFragment.requireContext().applicationContext)
                .flightDao().update(flight)
        }
    }

}
