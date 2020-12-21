package de.romankreisel.igcsync.ui.flightlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.model.IgcFile
import java.text.DateFormat
import java.text.SimpleDateFormat

class IgcFilesAdapter(
        private val igcFiles: List<IgcFile>,
        val itemClickListener: OnItemClickListener
) :
        RecyclerView.Adapter<IgcFilesAdapter.ViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
                LayoutInflater.from(parent.context).inflate(R.layout.igc_file_fragment, parent, false)
        val viewHolder = ViewHolder(view)
        viewHolder.contentView = view.findViewById<ConstraintLayout>(R.id.igc_file_layout)
        viewHolder.filenameTextView = view.findViewById<TextView>(R.id.text_filename)
        viewHolder.startTimeTextView = view.findViewById<TextView>(R.id.text_start_time)
        viewHolder.durationTextView = view.findViewById<TextView>(R.id.text_duration)
        viewHolder.dhvLogoImageView = view.findViewById<ImageView>(R.id.dhv_logo)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val igcFile = igcFiles[position]
        val startDate = igcFile.startDate
        if (startDate != null) {
            val myDate = SimpleDateFormat.getDateInstance(DateFormat.FULL)
                    .format(startDate)
            val myTime = SimpleDateFormat.getTimeInstance(DateFormat.MEDIUM)
                    .format(startDate)
            holder.startTimeTextView.text = "$myDate\n$myTime"
        } else {
            holder.startTimeTextView.text = ""
        }
        holder.filenameTextView.text = igcFile.filename
        val d = igcFile.duration.seconds
        holder.durationTextView.text = String.format("%d:%02d:%02d", d / 3600, (d % 3600) / 60, (d % 60))
        if (igcFile.dhvXcFlightUrl.isNullOrBlank()) {
            holder.dhvLogoImageView.visibility = View.GONE
        } else {
            holder.dhvLogoImageView.visibility = View.VISIBLE
        }
        holder.bind(igcFile, itemClickListener)
    }


    override fun getItemCount() = this.igcFiles.count()


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        lateinit var contentView: ConstraintLayout
        lateinit var startTimeTextView: TextView
        lateinit var filenameTextView: TextView
        lateinit var durationTextView: TextView
        lateinit var dhvLogoImageView: ImageView

        fun bind(igcFile: IgcFile, clickListener: OnItemClickListener) {
            itemView.setOnClickListener {
                clickListener.onItemClicked(igcFile)
            }
        }
    }
}

interface OnItemClickListener {
    fun onItemClicked(igcFile: IgcFile)
}