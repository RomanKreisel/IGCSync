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
import com.google.android.flexbox.FlexboxLayout
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
import java.io.IOException
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Pattern


class FlightFragment : Fragment() {
    private lateinit var layout_upload_buttons: FlexboxLayout
    private lateinit var text_upload_into: TextView
    private lateinit var button_upload_flightbook: Button
    private lateinit var button_upload_performance_pg: Button
    private lateinit var button_upload_sport_pg: Button
    private lateinit var button_upload_standard_hg: Button
    private lateinit var button_upload_fun_hg: Button
    private lateinit var button_upload_fun_pg: Button
    private lateinit var button_upload_tandem_pg: Button
    private lateinit var progressBar: ProgressBar
    private var googleMap: GoogleMap? = null
    private lateinit var button_upload_standard_pg: Button
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


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
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

        this.button_upload_fun_pg =
            this.requireView().findViewById<Button>(R.id.button_upload_fun_cup_pg)!!
        this.button_upload_fun_pg.setOnClickListener {
            this.upload(4)
        }

        this.button_upload_fun_hg =
            this.requireView().findViewById<Button>(R.id.button_upload_fun_cup_hg)!!
        this.button_upload_fun_hg.setOnClickListener {
            this.upload(1)
        }

        this.button_upload_standard_pg =
            this.requireView().findViewById<Button>(R.id.button_upload_standard_pg)!!
        this.button_upload_standard_pg.setOnClickListener {
            this.upload(5)
        }

        this.button_upload_standard_hg =
            this.requireView().findViewById<Button>(R.id.button_upload_standard_hg)!!
        this.button_upload_standard_hg.setOnClickListener {
            this.upload(2)
        }

        this.button_upload_sport_pg =
            this.requireView().findViewById<Button>(R.id.button_upload_sport_pg)!!
        this.button_upload_sport_pg.setOnClickListener {
            this.upload(1)
        }

        this.button_upload_performance_pg =
            this.requireView().findViewById<Button>(R.id.button_upload_performance_pg)!!
        this.button_upload_performance_pg.setOnClickListener {
            this.upload(2)
        }

        this.button_upload_tandem_pg =
            this.requireView().findViewById<Button>(R.id.button_upload_tandem_pg)!!
        this.button_upload_tandem_pg.setOnClickListener {
            this.upload(3)
        }

        this.button_upload_flightbook =
            this.requireView().findViewById<Button>(R.id.button_upload_flightbook)!!
        this.button_upload_flightbook.setOnClickListener {
            this.upload(6)
        }

        this.text_upload_into = this.requireView().findViewById<TextView>(R.id.textView_upload_into)
        this.layout_upload_buttons =
            this.requireView().findViewById<FlexboxLayout>(R.id.layout_upload_buttons)


        this.button_view_in_dhvxc =
            this.requireView().findViewById<Button>(R.id.button_view_in_dhvxc)!!
        this.button_view_in_dhvxc.setOnClickListener {
            this.toggleVisibilityForAllDhvXcButtons()
            val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(args.flight.dhvXcFlightUrl))
            startActivity(myIntent)
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

        this.layout_upload_buttons.visibility = visibility

        this.button_view_in_dhvxc.visibility =
            if (args.flight.dhvXcFlightUrl.isNullOrBlank()) View.GONE else visibility

        val gliderCategory = this@FlightFragment.preferences.getString(
            getString(R.string.preference_glider_category),
            "1"
        ) ?: "1"

        val gliderCertification = this@FlightFragment.preferences.getString(
            getString(R.string.preference_glider_certification),
            "1"
        ) ?: "1"

        //Before we check for all conditions, we disable all buttons first:
        this.button_upload_fun_pg.visibility = View.GONE
        this.button_upload_fun_hg.visibility = View.GONE
        this.button_upload_tandem_pg.visibility = View.GONE
        this.button_upload_standard_pg.visibility = View.GONE
        this.button_upload_standard_hg.visibility = View.GONE
        this.button_upload_sport_pg.visibility = View.GONE
        this.button_upload_performance_pg.visibility = View.GONE
        this.button_upload_flightbook.visibility = View.GONE
        this.text_upload_into.visibility = View.GONE
        if (args.flight.isDemo) {
            return
        }
        this.text_upload_into.visibility = visibility
        this.button_upload_flightbook.visibility = visibility
        if (gliderCategory == "1" /*PG*/) {
            if (gliderCertification == "1" /*LTF 1*/ || gliderCertification == "32" /*EN/LTF A*/) {
                this.button_upload_fun_pg.visibility = visibility
            }
            if (gliderCertification == "1" /*LTF 1*/ || gliderCertification == "32" /*EN/LTF A*/ || gliderCertification == "2" /*LTF 1-2*/ || gliderCertification == "64" /*EN/LTF B*/) {
                this.button_upload_standard_pg.visibility = visibility
            }
            if (gliderCertification == "4" /*LTF 2*/ || gliderCertification == "128" /*EN/LTF C*/) {
                this.button_upload_sport_pg.visibility = visibility
            }
            if (gliderCertification == "8" /*LTF 2-3*/ || gliderCertification == "16" /*LTF 3*/ || gliderCertification == "256" /*EN/LTF D*/) {
                this.button_upload_performance_pg.visibility = visibility
            }
            val offerTandem = preferences.getBoolean(
                getString(R.string.preference_offer_upload_tandem_cup),
                false
            )
            if (offerTandem) {
                this.button_upload_tandem_pg.visibility = visibility
            }
        } else if (gliderCategory == "2" /*Flex Wing*/) {
            this.button_upload_standard_hg.visibility = visibility
            this.button_upload_fun_hg.visibility = visibility
        } else if (gliderCategory == "4" /*Rigid Wing*/) {
            this.button_upload_standard_hg.visibility = visibility
        }

    }

    private fun upload(category: Int) {
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

            val url = getString(R.string.default_leonardo_submit_flight_url)
            parameters.put("user", this@FlightFragment.preferences.getString("username", "")!!)
            parameters.put("pass", this@FlightFragment.preferences.getString("password", "")!!)
            parameters.put("igcfn", args.flight.filename)
            parameters.put("IGCigcIGC", args.flight.content)
            parameters.put("Category", category.toString())


            //Start type
            val startTypeIds = this@FlightFragment.resources.getStringArray(R.array.start_type_id)
            val startType = this@FlightFragment.preferences.getString(
                getString(R.string.preference_start_type),
                startTypeIds[0]
            )
                ?: startTypeIds[0]
            if (!startType.isNullOrBlank()) {
                parameters.put("startType", startType)
            }


            //Manufacturer, Glider Model and Size
            val manufacturers =
                this@FlightFragment.resources.getStringArray(R.array.glider_manufacturer)
            val manufacturer = this@FlightFragment.preferences.getString(
                getString(R.string.preference_glider_manufacturer),
                ""
            )
                ?: ""
            val manufacturerId = manufacturers.indexOf(manufacturer)
            var gliderName = ""
            if (manufacturerId >= 0) {
                parameters.put("gliderBrandID", (manufacturerId + 1).toString())
            } else {
                gliderName = "$manufacturer "
            }
            gliderName += this@FlightFragment.preferences.getString(
                getString(R.string.preference_glider_model),
                ""
            )
                ?: ""
            val gliderSize = this@FlightFragment.preferences.getString(
                getString(R.string.preference_glider_size),
                ""
            )
                ?: ""
            if (!gliderName.isBlank() && !gliderSize.isBlank()) {
                gliderName += " $gliderSize"
            }

            if (gliderName.isNotBlank()) {
                parameters.put("glider", gliderName)
            }


            // Glider Category
            val gliderCategoryIds =
                this@FlightFragment.resources.getStringArray(R.array.glider_category_id)
            val gliderCategory = this@FlightFragment.preferences.getString(
                getString(R.string.preference_glider_category),
                gliderCategoryIds[0]
            )
                ?: gliderCategoryIds[0]
            if (!gliderCategory.isNullOrBlank()) {
                parameters.put("gliderCat", gliderCategory)
            }

            // Glider Certification Category
            val gliderCertificationCategoryIds =
                this@FlightFragment.resources.getStringArray(R.array.glider_category_id)
            val gliderCertificationCategory = this@FlightFragment.preferences.getString(
                getString(R.string.preference_glider_certification),
                gliderCertificationCategoryIds[0]
            )
                ?: gliderCertificationCategoryIds[0]
            if (!gliderCertificationCategory.isNullOrEmpty()) {
                parameters.put("gliderCertCategory", gliderCertificationCategory)
            }


            val content = StringBuilder()
            parameters.forEach { key, value ->
                if (!content.isEmpty()) {
                    content.append('&')
                }

                content.append(URLEncoder.encode(key, "UTF-8")).append("=")
                    .append(URLEncoder.encode(value, "UTF-8"))
            }


            val body = RequestBody.create(
                MediaType.parse("application/x-www-form-urlencoded"),
                content.toString()
            )
            val request = Request.Builder()
                .url(url)
                .header("Accept-Language", "en")
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
                    this@FlightFragment.requireActivity().runOnUiThread {
                        this@FlightFragment.progressBar.visibility = View.GONE
                        this@FlightFragment.progressBar.clearAnimation()
                    }
                    if (response == null) {
                        this@FlightFragment.requireActivity().runOnUiThread {
                            AlertDialog.Builder(this@FlightFragment.requireContext())
                                .setMessage(getString(R.string.alert_text_error_during_upload))
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                        return
                    }
                    if (response.code() != 200) {
                        this@FlightFragment.requireActivity().runOnUiThread {
                            AlertDialog.Builder(this@FlightFragment.requireContext())
                                .setMessage(getString(R.string.alert_text_error_during_upload))
                                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                        return
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        @Suppress("BlockingMethodInNonBlockingContext") //Unfortunately, this library doesn't offer a suspendable function
                        val responseBody = response.body().string()

                        val alertBuilder = AlertDialog.Builder(this@FlightFragment.requireContext())
                        val correctedText =
                            responseBody.replace("href='/", "href='https://www.dhv-xc.de/")

                        val matcher =
                            Pattern.compile(".*(https://www.dhv-xc.de(/xc/modules){0,1}(/leonardo){0,1}/index\\.php\\?op=show_flight&flightID=[0-9]+).*")
                                .matcher(correctedText)
                        if (matcher.find()) {
                            val group = matcher.group(1)
                            val text = group?.toString()
                            if (!text.isNullOrBlank()) {
                                args.flight.dhvXcFlightUrl = text
                                val flightDao =
                                    IgcSyncDatabase.getDatabase(this@FlightFragment.requireContext())
                                        .flightDao()
                                flightDao.update(args.flight)
                            }
                        }


                        this@FlightFragment.requireActivity().runOnUiThread {
                            this@FlightFragment.dhvButton?.animation?.cancel()
                            alertBuilder
                                .setMessage(
                                    Html.fromHtml(
                                        correctedText,
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
            })
        }
    }
}
