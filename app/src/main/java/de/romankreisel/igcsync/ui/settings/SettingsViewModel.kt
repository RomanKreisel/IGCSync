package de.romankreisel.igcsync.ui.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


class SettingsViewModel : ViewModel() {

    val selectDirectoryButtonText = MutableLiveData<String>()

    val usernameEditText = MutableLiveData<String>()

    val passwordEditText = MutableLiveData<String>()


}