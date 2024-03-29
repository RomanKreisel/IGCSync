package de.romankreisel.igcsync.ui.flightlist

import android.annotation.SuppressLint
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.romankreisel.igcsync.R
import de.romankreisel.igcsync.data.model.Flight
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class FlightsAdapter(
    flights: List<Flight>,
    val itemClickListener: FlightItemListener
) :
    RecyclerView.Adapter<FlightsAdapter.ViewHolder>() {

    private val flights: ArrayList<Flight>

    init {
        this.flights = ArrayList<Flight>(flights)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.igc_file_fragment, parent, false)
        val viewHolder = ViewHolder(view)
        viewHolder.contentView = view.findViewById<ConstraintLayout>(R.id.igc_file_layout)

        viewHolder.startTimeTextView = view.findViewById<TextView>(R.id.text_start_time)
        viewHolder.durationTextView = view.findViewById<TextView>(R.id.text_duration)
        viewHolder.dhvLogoImageView = view.findViewById<ImageView>(R.id.dhv_logo)
        viewHolder.favoriteSymbol = view.findViewById<ImageView>(R.id.favorite_symbol)
        return viewHolder
    }

    fun setData(flights: List<Flight>) {
        this.flights.clear()
        this.flights.addAll(flights)
        this.notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val flight = flights[position]
        val startDate = flight.startDate
        val myDayOfWeek = SimpleDateFormat("E", Locale.getDefault()).format(startDate)
        val myDate = SimpleDateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
            .format(startDate)
        val myTime = SimpleDateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(startDate)
        @SuppressLint("SetTextI18n")
        holder.startTimeTextView.text = "$myDayOfWeek $myDate, $myTime"
        val d = flight.duration.seconds
        holder.durationTextView.text =
            String.format("%d:%02d:%02d", d / 3600, (d % 3600) / 60, (d % 60))
        if (flight.dhvXcFlightUrl.isNullOrBlank()) {
            holder.dhvLogoImageView.visibility = View.GONE
        } else {
            holder.dhvLogoImageView.visibility = View.VISIBLE
        }

        if (flight.isFavorite) {
            holder.favoriteSymbol.visibility = View.VISIBLE
        } else {
            holder.favoriteSymbol.visibility = View.GONE
        }
        holder.bind(flight, itemClickListener)
    }


    override fun getItemCount() = this.flights.count()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnCreateContextMenuListener {
        private lateinit var flightListener: FlightItemListener
        private lateinit var flight: Flight
        lateinit var contentView: ConstraintLayout
        lateinit var startTimeTextView: TextView
        lateinit var durationTextView: TextView
        lateinit var dhvLogoImageView: ImageView
        lateinit var favoriteSymbol: ImageView

        fun bind(flight: Flight, flightListener: FlightItemListener) {
            this.flight = flight
            this.flightListener = flightListener
            itemView.setOnCreateContextMenuListener(this)
            itemView.setOnClickListener {
                this.flightListener.onItemClicked(flight)
            }
            itemView.setOnLongClickListener {
                itemView.showContextMenu()
                true
            }
        }

        override fun onCreateContextMenu(
            menu: ContextMenu?,
            v: View?,
            menuInfo: ContextMenu.ContextMenuInfo?
        ) {
            if (menu != null && v != null && v.context != null) {
                //menu.setHeaderTitle("Header")
                menu.add(v.context?.getString(R.string.menu_item_view)).setOnMenuItemClickListener {
                    this.flightListener.onItemClicked(this.flight)
                    true
                }
                menu.add(v.context?.getString(if (flight.isFavorite) R.string.menu_item_unmark_as_favorite else R.string.menu_item_mark_as_favorite))
                    .setOnMenuItemClickListener {
                        this.flightListener.onItemMarkedAsFavorite(this.flight)
                        true
                    }
                menu.add(v.context?.getString(R.string.menu_item_delete))
                    .setOnMenuItemClickListener {
                        this.flightListener.onItemDeleted(this.flight)
                        true
                    }.isEnabled = !flight.isDemo
            }
        }
    }
}

interface FlightItemListener {
    fun onItemClicked(flight: Flight)
    fun onItemDeleted(flight: Flight)
    fun onItemMarkedAsFavorite(flight: Flight)
}