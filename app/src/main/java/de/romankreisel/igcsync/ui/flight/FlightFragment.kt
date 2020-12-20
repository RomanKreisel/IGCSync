package de.romankreisel.igcsync.ui.flight

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.squareup.okhttp.*
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.IgcSyncDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

class FlightFragment : Fragment() {
    private var dhvButton: ImageButton? = null
    private lateinit var preferences: SharedPreferences
    val args: FlightFragmentArgs by navArgs()

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
            val animation = AnimationUtils.loadAnimation(this.requireContext(), R.anim.pulse)
            this.dhvButton?.startAnimation(animation)
            this.upload()
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
            parameters.put("igcfn", args.flight.sha256Checksum)
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