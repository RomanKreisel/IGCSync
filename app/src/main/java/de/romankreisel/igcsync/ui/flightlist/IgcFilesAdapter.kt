package de.romankreisel.igcsync.ui.flightlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.model.IgcFile
import java.text.SimpleDateFormat

class IgcFilesAdapter(
    private val igcFiles: List<IgcFile>,
) :
    RecyclerView.Adapter<IgcFilesAdapter.ViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.igc_file_fragment, parent, false)
        val viewHolder = ViewHolder(view)
        viewHolder.filenameTextView = view.findViewById<TextView>(R.id.text_filename)

        // viewHolder.uploadButton = view.findViewById<MaterialButton>(R.id.button_upload_single)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val igcFile = igcFiles[position]
        val startDate = igcFile.startDate
        if (startDate != null) {
            holder.filenameTextView.text =
                SimpleDateFormat.getDateTimeInstance().format(startDate)
        } else {
            holder.filenameTextView.text = igcFiles[position].filename
        }
    }


    override fun getItemCount() = this.igcFiles.count()


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var filenameTextView: TextView
    }
}