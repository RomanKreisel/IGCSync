package de.romankreisel.igcsync.ui.flightlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.romankreisel.igcsync.data.model.Flight

class FlightListViewModel : ViewModel() {
    var flights: LiveData<List<Flight>> = MutableLiveData<List<Flight>>()
}