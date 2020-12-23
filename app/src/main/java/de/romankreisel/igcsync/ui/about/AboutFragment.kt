package de.romankreisel.igcsync.ui.about

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.romankreisel.igcsync.R

class AboutFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_about, container, false)
        val version =
            this.requireContext().packageManager.getPackageInfo(
                this.requireContext().packageName,
                0
            ).versionName
        @SuppressLint("SetTextI18n")
        root.findViewById<TextView>(R.id.textView_app_name_and_version).text =
            getString(R.string.app_name) + " " + version

        root.findViewById<TextView>(R.id.textView_github).apply {
            text = Html.fromHtml(
                "Visit us on <a href=\"https://github.com/RomanKreisel/IGCSync\">GitHub</a>",
                Html.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
        }

        root.findViewById<TextView>(R.id.textView_pixabay_logo).apply {
            text = Html.fromHtml(
                "Many thanks go to <a href=\"https://pixabay.com/de/users/gdj-1086657/\">Gordon Johnson</a>, for unkowingly providing a great <a href=\"https://pixabay.com/de/vectors/paragleiten-fallschirm-silhouette-4037231/\">image</a> as our new logo",
                Html.FROM_HTML_MODE_LEGACY
            )
            movementMethod = LinkMovementMethod.getInstance()
        }


        return root
    }
}