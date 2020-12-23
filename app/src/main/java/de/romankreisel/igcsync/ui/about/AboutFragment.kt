package de.romankreisel.igcsync.ui.about

import android.annotation.SuppressLint
import android.os.Bundle
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

        return root
    }
}