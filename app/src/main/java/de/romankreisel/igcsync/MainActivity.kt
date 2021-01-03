package de.romankreisel.igcsync

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import de.romankreisel.igcsync.data.FlightImportHelper
import de.romankreisel.igcsync.data.igc.IgcException
import de.romankreisel.igcsync.data.model.Flight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {

    private lateinit var preferences: SharedPreferences
    private lateinit var navController: NavController
    private lateinit var navHostFragment: Fragment
    private lateinit var flightImportHelper: FlightImportHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.flightImportHelper = FlightImportHelper(this.applicationContext)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        this.preferences = this.getPreferences(Context.MODE_PRIVATE)

        this.navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
        this.navController = this.navHostFragment.findNavController()
        this.navController.addOnDestinationChangedListener(this)

        val myIntent = this.intent
        if (myIntent != null) {
            when {
                myIntent.action == Intent.ACTION_SEND -> {
                    val clipData = myIntent.clipData
                    val flights = HashMap<String, String>()
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            val myData = clipData.getItemAt(i)
                            val uri = myData.uri
                            val lines = contentResolver.openInputStream(uri)
                                ?.bufferedReader(Charset.forName("UTF-8"))?.readText()
                            if (!lines.isNullOrBlank()) {
                                flights.put(uri.toString(), lines)
                            }
                        }
                    }
                    this.importFlights(flights)
                }
                myIntent.action == Intent.ACTION_VIEW -> {
                    val intent = intent
                    val uri = intent.data
                    val lines = contentResolver.openInputStream(uri!!)
                        ?.bufferedReader(Charset.forName("UTF-8"))?.readText()
                    if (lines != null) {
                        val flights = HashMap<String, String>()
                        flights.set(uri.toString(), lines)
                        this.importFlights(flights)
                    }
                }
            }
        }
    }

    fun importFlights(flightsUriAndContent: Map<String, String>) {
        val now = Date(System.currentTimeMillis())
        val checksums = HashSet<String>()
        val flightsForImport = ArrayList<Flight>()
        CoroutineScope(Dispatchers.IO).launch {
            flightsUriAndContent.forEach {
                val uri = Uri.parse(it.key)
                val cursor = contentResolver.query(uri!!, null, null, null, null)
                var filename = uri.lastPathSegment ?: ""
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        filename =
                            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    }
                } finally {
                    cursor?.close()
                }
                if (!this@MainActivity.flightImportHelper.flightUrlAlreadyKnown(uri)) {
                    try {
                        val flight = this@MainActivity.flightImportHelper.createFlight(
                            filename,
                            it.value,
                            now
                        )
                        if (!this@MainActivity.flightImportHelper.alreadyKnown(
                                flight,
                                checksums
                            ) && !this@MainActivity.flightImportHelper.flightAboveMinimumDuration(
                                flight
                            )
                        ) {
                            checksums.add(flight.sha256Checksum)
                            flightsForImport.add(flight)
                        }
                    } catch (e: IgcException) {
                        Log.e(this@MainActivity::class.qualifiedName, "Error reading IGC", e)
                    }
                }
            }
            this@MainActivity.runOnUiThread {
                if (flightsForImport.count() <= 0) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.alert_title_no_flights_shared))
                        .setMessage(getString(R.string.alert_text_no_flight_detected_in_shared_content))
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    var text =
                        getString(R.string.alert_text_ImportNewFlights, flightsForImport.count())
                    if (flightsForImport.count() == 1) {
                        text = getString(R.string.alert_text_ImportNewFlight)
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.alert_title_ImportNewFlights))
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            CoroutineScope(Dispatchers.IO).launch {
                                this@MainActivity.flightImportHelper.insertAll(*flightsForImport.toTypedArray())
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }

        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                this@MainActivity.navController.navigate(R.id.action_to_SettingsFragment)
                return true
            }
            R.id.action_about -> {
                this@MainActivity.navController.navigate(R.id.action_to_AboutFragment)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destination.id == R.id.FirstFragment) {
            getSupportActionBar()?.setDisplayHomeAsUpEnabled(false)
        } else {
            getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)
        }
    }
}