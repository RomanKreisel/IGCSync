package de.romankreisel.igcsync.ui.flightlist.igcfile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.romankreisel.igcsync.R

class IgcFileFragment : Fragment() {
    private lateinit var viewModel: IgcFileViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        

        return inflater.inflate(R.layout.igc_file_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(IgcFileViewModel::class.java)
    }

}