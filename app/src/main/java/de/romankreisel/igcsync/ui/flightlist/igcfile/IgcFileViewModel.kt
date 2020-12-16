package de.romankreisel.igcsync.ui.flightlist.igcfile

import androidx.lifecycle.ViewModel
import de.romankreisel.igcsync.data.model.IgcFile

class IgcFileViewModel(val igcFile: IgcFile) : ViewModel() {

    val filename = this.igcFile.url

}