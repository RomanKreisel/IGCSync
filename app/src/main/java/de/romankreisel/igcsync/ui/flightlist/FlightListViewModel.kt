package de.romankreisel.igcsync.ui.flightlist

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.romankreisel.igcsync.data.model.IgcFile
import java.util.*
import kotlin.collections.ArrayList

class FlightListViewModel : ViewModel() {
    val igcFiles = MutableLiveData<List<IgcFile>>().apply {
        value = Collections.unmodifiableList(ArrayList<IgcFile>())
    }

}