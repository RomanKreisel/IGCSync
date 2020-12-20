package de.romankreisel.igcsync.ui.flightlist

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.model.IgcFile
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

    @ColorInt
    fun Context.getColorFromAttr(
            @AttrRes attrColor: Int,
            typedValue: TypedValue = TypedValue(),
            resolveRefs: Boolean = true
    ): Int {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        /*if (position % 2 == 0) {
            holder.contentView.background = holder.contentView.context.getDrawable(android.R.drawable.screen_background_dark_transparent)
        } else {
            holder.contentView.background = holder.contentView.context.getDrawable(android.R.drawable.screen_background_light_transparent)
        }*/

        val igcFile = igcFiles[position]
        val startDate = igcFile.startDate
        if (startDate != null) {
            holder.startTimeTextView.text =
                    SimpleDateFormat.getDateTimeInstance()
                            .format(startDate)
        } else {
            holder.startTimeTextView.text =
                    igcFile.filename
        }
        holder.filenameTextView.text = igcFile.filename
        holder.durationTextView.text = igcFile.duration.toString()
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