package de.romankreisel.igcsync.ui.flightlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.romankreisel.igcsync.data.model.IgcFile

class FlightListViewModel : ViewModel() {
    var igcFiles: LiveData<List<IgcFile>> = MutableLiveData<List<IgcFile>>()
}