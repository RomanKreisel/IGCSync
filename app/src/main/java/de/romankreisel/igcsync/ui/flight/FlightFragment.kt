package de.romankreisel.igcsync.ui.flight

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.squareup.okhttp.*
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.IgcSyncDatabase
import de.romankreisel.igcsync.igc.IgcData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Pattern


class FlightFragment : Fragment() {
    private lateinit var button_upload: Button
    private lateinit var progressBar: ProgressBar
    private var googleMap: GoogleMap? = null
    private lateinit var button_view_in_dhvxc: Button
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var igcData: IgcData

    // private var mapView: MapView? = null
    private var dhvButton: ImageButton? = null
    private lateinit var preferences: SharedPreferences
    val args: FlightFragmentArgs by navArgs()
    private var dhvXcButtonsVisible = true

    private val mapsCallback = OnMapReadyCallback { googleMap ->
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        this.googleMap = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        // googleMap.setPadding(0, 0, 0, 160)
        val firstBRecord = this.igcData.bRecords.first()
        val start = LatLng(firstBRecord.latitude, firstBRecord.longitude)
        googleMap.addMarker(MarkerOptions().position(start).title(getString(R.string.marker_start)))
        googleMap.mapType = this.preferences.getInt(
            getString(R.string.preference_map_type),
            GoogleMap.MAP_TYPE_TERRAIN
        )

        val lastBRecord = this.igcData.bRecords.last()
        val landing = LatLng(lastBRecord.latitude, lastBRecord.longitude)
        googleMap.addMarker(
            MarkerOptions().position(landing).title(getString(R.string.marker_landing))
        )

        var lowerBounds = start
        var higherBounds = start

        val positions = ArrayList<LatLng>()
        this.igcData.bRecords.forEach { record ->
            positions.add(LatLng(record.latitude, record.longitude))
            lowerBounds = LatLng(
                Math.min(lowerBounds.latitude, record.latitude),
                Math.min(lowerBounds.longitude, record.longitude)
            )
            higherBounds = LatLng(
                Math.max(higherBounds.latitude, record.latitude),
                Math.max(higherBounds.longitude, record.longitude)
            )
        }
        val line = PolylineOptions()
        line.addAll(positions)
        val polyLine = googleMap.addPolyline(line)
        polyLine.color = ContextCompat.getColor(this.requireContext(), R.color.flighttrack_on_map)

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(
                    lowerBounds,
                    higherBounds
                ), 100
            )
        )
        this.mapFragment.onResume()
    }

    companion object {
        fun newInstance() = FlightFragment()
    }

    private lateinit var viewModel: FlightViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        this.preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)
        return inflater.inflate(R.layout.fragment_flight, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(FlightViewModel::class.java)

        requireView().findViewById<ImageButton>(R.id.button_map_mode)?.setOnClickListener {
            val myGoogleMap = this.googleMap
            if (myGoogleMap != null) {
                when (myGoogleMap.mapType) {
                    GoogleMap.MAP_TYPE_TERRAIN -> myGoogleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                    GoogleMap.MAP_TYPE_HYBRID -> myGoogleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                    GoogleMap.MAP_TYPE_NORMAL -> myGoogleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                }
                this.preferences.edit()
                    .putInt(getString(R.string.preference_map_type), myGoogleMap.mapType).apply()
            }
        }

        this.dhvButton = this.requireView().findViewById<ImageButton>(R.id.button_upload_dhv_xc)
        this.dhvButton?.setOnClickListener {
            this.toggleVisibilityForAllDhvXcButtons()
        }

        this.button_upload =
            this.requireView().findViewById(R.id.button_upload)!!
        this.button_upload.setOnClickListener {
            this.upload()
        }

        this.button_view_in_dhvxc =
            this.requireView().findViewById(R.id.button_view_in_dhvxc)!!
        this.button_view_in_dhvxc.setOnClickListener {
            this.toggleVisibilityForAllDhvXcButtons()
            //example of an "old" url: https://www.dhv-xc.de/leonardo/index.php?op=show_flight&flightID=1298039
            //another example:         https://de.dhv-xc.de/xc/modules/leonardo/index.php?op=show_flight&flightID=1298039
            val matcher = Pattern.compile(".*(flightID=)(\\d+).*")
                .matcher(args.flight.dhvXcFlightUrl!!)
            if (args.flight.dhvXcFlightUrl!!.contains("leonardo") && matcher.matches()) {
                val flightId = matcher.group(2)
                val flightUrl = "https://dhv-xc.de/flight/" + flightId
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(flightUrl)))
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.flight.dhvXcFlightUrl)))
            }

        }

        this.toggleVisibilityForAllDhvXcButtons()

        this.requireActivity().title =
            SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(args.flight.startDate)

        this.mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        this.mapFragment.onCreate(savedInstanceState)
        this.mapFragment.getMapAsync(mapsCallback)
        val igcContent = args.flight.content
        this.igcData = IgcData(igcContent)

        this.progressBar = this.requireView().findViewById<ProgressBar>(R.id.progressBar)
        this.progressBar.visibility = View.GONE
    }

    private fun toggleVisibilityForAllDhvXcButtons() {
        this.dhvXcButtonsVisible = !this.dhvXcButtonsVisible
        var visibility = View.GONE
        if (this.dhvXcButtonsVisible) {
            visibility = View.VISIBLE
        }

        this.button_view_in_dhvxc.visibility =
            if (args.flight.dhvXcFlightUrl.isNullOrBlank()) View.GONE else visibility

        //Before we check for all conditions, we disable all buttons first:
        this.button_upload.visibility = View.GONE
        if (args.flight.isDemo) {
            return
        }
        this.button_upload.visibility = visibility
    }

    private fun upload() {
        this.toggleVisibilityForAllDhvXcButtons()
        this.progressBar.visibility = View.VISIBLE
        this.progressBar.isIndeterminate = true
        this.progressBar.animate()

        Toast.makeText(
            this.requireContext(),
            getString(R.string.alert_text_upload_started),
            Toast.LENGTH_LONG
        ).show()


        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val parameters = HashMap<String, String>()

            val url = getString(R.string.default_dhvxc_upload_flights_url)
            val json = JSONObject()
            json.put("user", this@FlightFragment.preferences.getString("username", ""))
            json.put("pass", this@FlightFragment.preferences.getString("password", ""))
            json.put("igcname", args.flight.filename)
            json.put("igccontent", args.flight.content)

            val content = StringBuilder()
            parameters.forEach { key, value ->
                if (!content.isEmpty()) {
                    content.append('&')
                }

                content.append(URLEncoder.encode(key, "UTF-8")).append("=")
                    .append(URLEncoder.encode(value, "UTF-8"))
            }


            val body = RequestBody.create(
                MediaType.parse("application/json"),
                json.toString(0)
            )
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(request: Request?, e: IOException?) {
                    this@FlightFragment.progressBar.visibility = View.VISIBLE
                    this@FlightFragment.progressBar.clearAnimation()
                    this@FlightFragment.requireActivity().runOnUiThread {
                        AlertDialog.Builder(this@FlightFragment.requireContext())
                            .setMessage(getString(R.string.alert_text_error_during_upload))
                            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                }

                override fun onResponse(response: Response?) {
                    val responseBodyString = response?.body()?.string()
                    val responseCode = response?.code()
                    var success = false
                    var message = "An unknown error occurred"
                    if (responseBodyString != null) {
                        try {
                            val responseJson = JSONObject(responseBodyString)
                            success = responseJson.getBoolean("success")
                            message = responseJson.getString("message")
                        } catch (e: JSONException) {
                            //TODO: some logging?
                        }

                    }
                    this@FlightFragment.requireActivity().runOnUiThread {
                        this@FlightFragment.progressBar.visibility = View.GONE
                        this@FlightFragment.progressBar.clearAnimation()
                        if (responseCode == null || responseBodyString == null) {
                            AlertDialog.Builder(this@FlightFragment.requireContext())
                                .setMessage(getString(R.string.alert_text_error_during_upload) + "\n" + message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        } else if (response.code() == 401) {
                            AlertDialog.Builder(this@FlightFragment.requireContext())
                                .setMessage(getString(R.string.alert_text_error_during_upload) + "\n" + message)
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        } else if (response.code() != 200) {
                            parseFlightIdFromUploadResponseMessage(message)
                            this@FlightFragment.requireActivity().runOnUiThread {
                                AlertDialog.Builder(this@FlightFragment.requireContext())
                                    .setMessage(getString(R.string.alert_text_error_during_upload) + "\n" + message)
                                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                        } else {
                            parseFlightIdFromUploadResponseMessage(message)
                            if (!success) {
                                this@FlightFragment.requireActivity().runOnUiThread {
                                    AlertDialog.Builder(this@FlightFragment.requireContext())
                                        .setMessage(getString(R.string.alert_text_error_during_upload) + "\n" + message)
                                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                            dialog.dismiss()
                                        }
                                        .show()
                                }
                            } else {
                                val alertBuilder =
                                    AlertDialog.Builder(this@FlightFragment.requireContext())
                                alertBuilder
                                    .setMessage(
                                        Html.fromHtml(
                                            message,
                                            Html.FROM_HTML_MODE_LEGACY
                                        )
                                    )
                                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .show()
                                    .findViewById<TextView>(android.R.id.message).movementMethod =
                                    LinkMovementMethod.getInstance()

                            }
                        }
                    }
                }
            })
        }
    }

    private fun parseFlightIdFromUploadResponseMessage(message: String) {
        val regex = getString(R.string.regex_flight_id_parser)
        val matcher = Pattern.compile(regex)
            .matcher(message)
        if (matcher.matches()) {
            val flightId = matcher.group(1)
            val flightUrl = "https://dhv-xc.de/flight/" + flightId
            args.flight.dhvXcFlightUrl = flightUrl
            CoroutineScope(Dispatchers.IO).launch {
                val flightDao =
                    IgcSyncDatabase.getDatabase(this@FlightFragment.requireContext())
                        .flightDao()
                flightDao.update(args.flight)
            }
        }
    }
}
