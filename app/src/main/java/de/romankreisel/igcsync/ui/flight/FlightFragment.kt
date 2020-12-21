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
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
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
import java.io.IOException
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Pattern


class FlightFragment : Fragment() {
    private lateinit var button_upload: Button
    private lateinit var button_view_in_dhvxc: Button
    private var mapFragment: SupportMapFragment? = null
    private lateinit var igcData: IgcData

    // private var mapView: MapView? = null
    private var dhvButton: ImageButton? = null
    private lateinit var preferences: SharedPreferences
    val args: FlightFragmentArgs by navArgs()

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
        googleMap.uiSettings.isZoomControlsEnabled = true

        googleMap.uiSettings.isCompassEnabled = true
        val firstBRecord = this.igcData.bRecords.first()
        val start = LatLng(firstBRecord.latitude, firstBRecord.longitude)
        googleMap.addMarker(MarkerOptions().position(start).title("Start"))
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        val lastBRecord = this.igcData.bRecords.last()
        val landing = LatLng(lastBRecord.latitude, lastBRecord.longitude)
        googleMap.addMarker(MarkerOptions().position(landing).title("Landing"))

        var lowerBounds = start
        var higherBounds = start

        val positions = ArrayList<LatLng>()
        this.igcData.bRecords.forEach { record ->
            positions.add(LatLng(record.latitude, record.longitude))
            lowerBounds = LatLng(Math.min(lowerBounds.latitude, record.latitude), Math.min(lowerBounds.longitude, record.longitude))
            higherBounds = LatLng(Math.max(higherBounds.latitude, record.latitude), Math.max(higherBounds.longitude, record.longitude))
        }
        val line = PolylineOptions()
        line.addAll(positions)
        val polyLine = googleMap.addPolyline(line)
        polyLine.color = R.color.black

        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds(lowerBounds, higherBounds), 100))
        this.mapFragment?.onResume()
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
        view?.findViewById<TextView>(R.id.flight_name)?.text = args.flight.filename
        this.dhvButton = view?.findViewById<ImageButton>(R.id.button_upload_dhv_xc)
        this.dhvButton?.setOnClickListener {
            if (this.button_upload.visibility == View.GONE) this.button_upload.visibility = View.VISIBLE else this.button_upload.visibility = View.GONE
            if (this.button_view_in_dhvxc.visibility == View.GONE && !args.flight.dhvXcFlightUrl.isNullOrBlank()) this.button_view_in_dhvxc.visibility = View.VISIBLE else this.button_view_in_dhvxc.visibility = View.GONE
        }

        this.button_upload = view?.findViewById<Button>(R.id.button_upload)!!
        this.button_upload.visibility = View.GONE
        this.button_upload.setOnClickListener {
            this.upload()
            this.button_view_in_dhvxc.visibility = View.GONE
            this.button_upload.visibility = View.GONE
        }
        this.button_view_in_dhvxc = view?.findViewById<Button>(R.id.button_view_in_dhvxc)!!
        this.button_view_in_dhvxc.visibility = View.GONE
        this.button_view_in_dhvxc.setOnClickListener {
            this.button_view_in_dhvxc.visibility = View.GONE
            this.button_upload.visibility = View.GONE
            val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(args.flight.dhvXcFlightUrl))
            startActivity(myIntent)
        }

        this.requireActivity().setTitle(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(args.flight.startDate!!))

        this.mapFragment = childFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment?
        this.mapFragment?.onCreate(savedInstanceState)
        this.mapFragment?.getMapAsync(mapsCallback)
        val igcContent = args.flight.content
        if (igcContent != null) {
            this.igcData = IgcData(igcContent)
        }
    }

    private fun upload() {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val parameters = HashMap<String, String>()

            var url = preferences.getString("leonardo_submit_flight_url", "")
            if (url == null || url.isBlank()) {
                url = getString(R.string.default_leonardo_submit_flight_url)
            }

            parameters.put("user", this@FlightFragment.preferences.getString("username", "")!!)
            parameters.put("pass", this@FlightFragment.preferences.getString("password", "")!!)
            parameters.put("igcfn", args.flight.filename)
            parameters.put("IGCigcIGC", args.flight.content!!)
            parameters.put("klasse", "9")
            parameters.put("startType", "1")
            parameters.put("gliderBrandID", "5")
            parameters.put("glider", "Ion 4S")
            parameters.put("gliderCat", "1")
            parameters.put("gliderCertCategory", "64")
            parameters.put("Category", "5")

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
                    TODO("Not yet implemented")
                }

                override fun onResponse(response: Response?) {
                    if (response == null) {
                        TODO("Handle exception")
                    }
                    //val responseCode = response.code()
                    CoroutineScope(Dispatchers.IO).launch {
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
                                val igcFileDao =
                                        IgcSyncDatabase.getDatabase(this@FlightFragment.requireContext())
                                                .igcFileDao()
                                igcFileDao.update(args.flight)
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
