package de.romankreisel.igcsync.ui.settings

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
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import de.romankreisel.igcsync.MainActivity
import de.romankreisel.igcsync.R

class SettingsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener,
        AdapterView.OnItemSelectedListener {

    private lateinit var gliderCertificationSpinner: Spinner
    private lateinit var gliderManufacturerText: AutoCompleteTextView
    private lateinit var startTypeSpinner: Spinner
    private lateinit var preferences: SharedPreferences
    private lateinit var gliderCategorySpinner: Spinner
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
                root.findViewById<MaterialButton>(R.id.button_select_data_directory)
        selectDirectoryButton.setOnClickListener {
            requireActivity().startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    MainActivity.REQUEST_CODE_IGC_DATA_DIRECTORY
            )
        }
        settingsViewModel.selectDirectoryButtonText.observe(viewLifecycleOwner, {
            selectDirectoryButton.text = it
        })

        gliderCategorySpinner = root.findViewById<Spinner>(R.id.spinner_glider_category)
        gliderCategorySpinner.apply {
            adapter = ArrayAdapter.createFromResource(
                    this@SettingsFragment.requireContext(),
                    R.array.glider_category,
                    android.R.layout.simple_spinner_dropdown_item
            )
            val preselected =
                    preferences.getString(getString(R.string.preference_glider_category), "")
            onItemSelectedListener = this@SettingsFragment
            if (preselected != null && preselected.isNotBlank()) {
                val index =
                        resources.getStringArray(R.array.glider_category_id).indexOf(preselected)
                setSelection(Math.max(0, index))
            }
        }

        startTypeSpinner = root.findViewById<Spinner>(R.id.spinner_start_type)
        startTypeSpinner.apply {
            adapter = ArrayAdapter.createFromResource(
                    this@SettingsFragment.requireContext(),
                    R.array.start_type,
                    android.R.layout.simple_spinner_dropdown_item
            )
            onItemSelectedListener = this@SettingsFragment
            val preselected =
                    preferences.getString(getString(R.string.preference_start_type), "")
            if (preselected != null && preselected.isNotBlank()) {
                val index =
                        resources.getStringArray(R.array.glider_category_id).indexOf(preselected)
                setSelection(Math.max(0, index))
            }
        }

        gliderManufacturerText =
                root.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView_glider_manufacturer)
        gliderManufacturerText.apply {
            setAdapter(
                    ArrayAdapter.createFromResource(
                            this@SettingsFragment.requireContext(),
                            R.array.glider_manufacturer,
                            android.R.layout.simple_spinner_dropdown_item
                    )
            )
            threshold = 1
            addTextChangedListener {
                preferences.edit()
                        .putString(getString(R.string.preference_glider_manufacturer), text?.toString())
                        .apply()
            }
            val preconfigured =
                    preferences.getString(getString(R.string.preference_glider_manufacturer), "")
            if (preconfigured != null && preconfigured.isNotBlank()) {
                text.clear()
                text.append(preconfigured)
            }
        }

        root.findViewById<EditText>(R.id.text_glider_model).apply {
            text.clear()
            text.append(preferences.getString(getString(R.string.preference_glider_model), ""))
            addTextChangedListener {
                preferences.edit()
                        .putString(getString(R.string.preference_glider_model), text.toString()).apply()
            }
        }

        root.findViewById<EditText>(R.id.text_glider_size).apply {
            text.clear()
            text.append(preferences.getString(getString(R.string.preference_glider_size), ""))
            addTextChangedListener {
                preferences.edit()
                        .putString(getString(R.string.preference_glider_size), text.toString()).apply()
            }
        }


        gliderCertificationSpinner = root.findViewById<Spinner>(R.id.spinner_glider_certification)
        gliderCertificationSpinner.apply {
            adapter = ArrayAdapter.createFromResource(
                    this@SettingsFragment.requireContext(),
                    R.array.glider_certification,
                    android.R.layout.simple_spinner_dropdown_item
            )
            onItemSelectedListener = this@SettingsFragment
            val preselected =
                    preferences.getString(getString(R.string.preference_glider_certification), "")
            if (preselected != null && preselected.isNotBlank()) {
                val index =
                        resources.getStringArray(R.array.glider_certification_id).indexOf(preselected)
                setSelection(Math.max(0, index))
            }
        }

        root.findViewById<EditText>(R.id.edit_text_leonardo_server).apply {
            text.clear()
            var url = preferences.getString(
                    "leonardo_submit_flight_url",
                    getString(R.string.default_leonardo_submit_flight_url)
            )
            if (url == null || url.isBlank()) {
                url = getString(R.string.default_leonardo_submit_flight_url)
            }
            text.append(url)
            addTextChangedListener { text ->
                preferences.edit().putString("leonardo_submit_flight_url", text?.toString()).apply()
            }
        }

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

        preferences.registerOnSharedPreferenceChangeListener(this)
        this.updateViewModel(preferences)
        return root
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

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        when (parent?.id) {
            this.gliderCategorySpinner.id -> {
                val gliderCategoryId =
                        this.resources.getStringArray(R.array.glider_category_id)[position]
                preferences.edit()
                        .putString(getString(R.string.preference_glider_category), gliderCategoryId)
                        .apply()
            }
            this.startTypeSpinner.id -> {
                val startTypeId =
                        this.resources.getStringArray(R.array.start_type_id)[position]
                preferences.edit()
                        .putString(getString(R.string.preference_start_type), startTypeId)
                        .apply()
            }
            this.gliderCertificationSpinner.id -> {
                val startTypeId =
                        this.resources.getStringArray(R.array.glider_certification_id)[position]
                preferences.edit()
                        .putString(getString(R.string.preference_glider_certification), startTypeId)
                        .apply()
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //NOP
    }
}