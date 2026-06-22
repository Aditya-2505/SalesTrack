package com.yourcompany.salestrack.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yourcompany.salestrack.R
import com.yourcompany.salestrack.databinding.ItemTripBinding
import com.yourcompany.salestrack.model.Trip
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TripAdapter(
    private var trips: List<Trip>,
    private val isCompleted: Boolean = false
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateTrips(newTrips: List<Trip>) {
        this.trips = newTrips
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position], position)
    }

    override fun getItemCount(): Int = trips.size

    inner class TripViewHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(trip: Trip, position: Int) {
            val context = binding.root.context
            
            // Format Time
            val formattedTime = formatTime(trip.timestamp)

            // Setup Details
            if (trip.type == "out") {
                binding.txtTripTitle.text = "Trip ${trip.tripNumber} — Headed out"
                binding.txtTripKmAndTime.text = "$formattedTime · ${formatKm(trip.kmReading)} KM"
                
                // Color Code Indicator: Blue for "out"
                binding.indicatorContainer.background = ContextCompat.getDrawable(context, R.drawable.circle_blue)
                binding.imgDirection.setImageResource(android.R.drawable.arrow_up_float)
                binding.imgDirection.setColorFilter(Color.parseColor("#378ADD"))

                // Active Badge
                val isLastEntry = position == trips.size - 1
                if (isLastEntry && !isCompleted) {
                    binding.badgeActive.visibility = View.VISIBLE
                    binding.badgeActive.text = "Active"
                } else {
                    binding.badgeActive.visibility = View.GONE
                }
                binding.badgeDiff.visibility = View.GONE
            } else {
                binding.txtTripTitle.text = "Trip ${trip.tripNumber} — Returned"
                binding.txtTripKmAndTime.text = "$formattedTime · ${formatKm(trip.kmReading)} KM"

                // Color Code Indicator: Green for "in"
                binding.indicatorContainer.background = ContextCompat.getDrawable(context, R.drawable.circle_green)
                binding.imgDirection.setImageResource(android.R.drawable.arrow_down_float)
                binding.imgDirection.setColorFilter(Color.parseColor("#1D9E75"))

                binding.badgeActive.visibility = View.GONE

                // KM difference calculation
                if (position > 0) {
                    val prevTrip = trips[position - 1]
                    val diff = trip.kmReading - prevTrip.kmReading
                    binding.badgeDiff.visibility = View.VISIBLE
                    binding.badgeDiff.text = "+$diff KM"

                    // Flag with warning if KM jump > 500 KM
                    if (diff > 500) {
                        binding.badgeDiff.setBackgroundColor(Color.parseColor("#FDF0F0"))
                        binding.badgeDiff.setTextColor(Color.parseColor("#E24B4A"))
                        binding.badgeDiff.text = "⚠ +$diff KM"
                    } else {
                        binding.badgeDiff.setBackgroundResource(R.color.success_light)
                        binding.badgeDiff.setTextColor(ContextCompat.getColor(context, R.color.success))
                    }
                } else {
                    binding.badgeDiff.visibility = View.GONE
                }
            }
        }

        private fun formatKm(km: Int): String {
            return String.format(Locale.US, "%,d", km)
        }

        private fun formatTime(isoTimestamp: String?): String {
            if (isoTimestamp == null) return ""
            return try {
                val inputSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = inputSdf.parse(isoTimestamp) ?: return ""
                val outputSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                outputSdf.format(date)
            } catch (e: Exception) {
                try {
                    val inputSdfAlt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val date = inputSdfAlt.parse(isoTimestamp) ?: return ""
                    val outputSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    outputSdf.format(date)
                } catch (e2: Exception) {
                    ""
                }
            }
        }
    }
}
