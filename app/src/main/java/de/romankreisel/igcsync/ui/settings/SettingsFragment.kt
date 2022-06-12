package de.romankreisel.igcsync.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.IgcSyncDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val REQUEST_CODE_IGC_DATA_DIRECTORY = 1000
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var defaultIgcDataDirectoryButtonTextColorStateList: ColorStateList
    private lateinit var root: View


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)
        root = inflater.inflate(R.layout.fragment_settings, container, false)
        preferences = this.requireActivity().getPreferences(Context.MODE_PRIVATE)

        this.requireActivity().setTitle(R.string.title_settings)

        root.findViewById<TextView>(R.id.label_igc_data_directory).apply {
            this@SettingsFragment.defaultIgcDataDirectoryButtonTextColorStateList = this.textColors
            if (preferences.getString(getString(R.string.preference_igc_directory_url), "")
                    .isNullOrBlank()
            ) {
                setTextColor(Color.RED)
                setTypeface(null, Typeface.BOLD)
            }
        }

        root.findViewById<EditText>(R.id.text_minimim_flight_duration_seconds).apply {
            val preferenceValue = preferences.getInt(
                getString(R.string.preference_minimum_flight_duration_seconds),
                -1
            )
            if (preferenceValue >= 0) {
                text.clear()
                text.append(preferenceValue.toString())
            }
            addTextChangedListener {
                if (!text.toString().isBlank()) {
                    preferences.edit().putInt(
                        getString(R.string.preference_minimum_flight_duration_seconds),
                        text.toString().toInt()
                    ).apply()
                }
            }
        }

        val selectDirectoryButton =
            root.findViewById<Button>(R.id.button_select_data_directory)
        selectDirectoryButton.setOnClickListener {
            this.startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                REQUEST_CODE_IGC_DATA_DIRECTORY
            )
        }
        settingsViewModel.selectDirectoryButtonText.observe(viewLifecycleOwner, {
            selectDirectoryButton.text = it
        })

        root.findViewById<EditText>(R.id.edit_text_username).apply {
            text.clear()
            text.append(preferences.getString("username", ""))
            addTextChangedListener { text ->
                preferences.edit().putString("username", text?.toString()).apply()
            }
        }


        root.findViewById<EditText>(R.id.edit_text_password).apply {
            text.clear()
            text.append(preferences.getString("password", ""))
            addTextChangedListener { text ->
                preferences.edit().putString("password", text?.toString()).apply()
            }
        }

        root.findViewById<CheckBox>(R.id.checkbox_offer_upload_to_tandem).apply {
            isChecked = preferences.getBoolean(
                getString(R.string.preference_offer_upload_tandem_cup),
                false
            )
            setOnCheckedChangeListener { _, isChecked ->
                preferences.edit()
                    .putBoolean(getString(R.string.preference_offer_upload_tandem_cup), isChecked)
                    .apply()
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(this)
        this.updateViewModel(preferences)
        return root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_IGC_DATA_DIRECTORY) {
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
                IgcSyncDatabase.getDatabase(this@SettingsFragment.requireContext())
                    .alreadyImportedUrlDao()
                    .deleteAll()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    private fun updateViewModel(sharedPreferences: SharedPreferences) {
        val igcDirectoryUrlString = sharedPreferences.getString(
            getString(R.string.preference_igc_directory_url),
            null
        )
        if (igcDirectoryUrlString != null && igcDirectoryUrlString.isNotBlank()) {
            root.findViewById<TextView>(R.id.label_igc_data_directory).apply {
                setTextColor(defaultIgcDataDirectoryButtonTextColorStateList)
                setTypeface(null, Typeface.NORMAL)
            }
            val igcDirectoryDocumentFile =
                DocumentFile.fromTreeUri(this.requireActivity(), Uri.parse(igcDirectoryUrlString))
            settingsViewModel.selectDirectoryButtonText.apply {
                value = igcDirectoryDocumentFile?.name
            }
        } else {
            settingsViewModel.selectDirectoryButtonText.apply {
                value = getString(R.string.button_select_data_directory)
            }
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences != null) {
            when (key) {
                getString(R.string.preference_igc_directory_url) ->
                    this.updateViewModel(sharedPreferences)
            }
        }
    }
}