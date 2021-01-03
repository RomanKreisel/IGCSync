package de.romankreisel.igcsync.ui.flightlist.flight

import androidx.lifecycle.ViewModel
import de.romankreisel.igcsync.data.model.Flight

class FlightViewModel(val flight: Flight) : ViewModel() {

    val filename = this.flight.filename

}