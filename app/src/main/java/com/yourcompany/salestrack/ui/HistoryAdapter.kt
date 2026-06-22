package com.yourcompany.salestrack.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yourcompany.salestrack.R
import com.yourcompany.salestrack.databinding.ItemHistoryDayBinding
import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.model.Trip
import com.yourcompany.salestrack.supabase.TripsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter(
    private var summaries: List<DailySummary>,
    private val userId: String,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val tripsRepository = TripsRepository()
    private val expandedStates = mutableMapOf<String, Boolean>() // date -> expanded state

    @SuppressLint("NotifyDataSetChanged")
    fun updateSummaries(newSummaries: List<DailySummary>) {
        this.summaries = newSummaries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(summaries[position])
    }

    override fun getItemCount(): Int = summaries.size

    inner class HistoryViewHolder(private val binding: ItemHistoryDayBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(summary: DailySummary) {
            val context = binding.root.context
            val dateStr = summary.date

            // Format Date
            binding.txtHistoryDate.text = formatDate(dateStr)
            binding.txtHistoryStats.text = "${summary.totalKmDriven} KM · ${summary.timesOut} Trips"

            // Handle Expansion State
            val isExpanded = expandedStates[dateStr] ?: false
            if (isExpanded) {
                binding.layoutExpandedTrips.visibility = View.VISIBLE
                binding.imgExpandArrow.setImageResource(android.R.drawable.arrow_up_float)
                loadTripsIntoExpandedLayout(dateStr, binding.layoutExpandedTrips, context)
            } else {
                binding.layoutExpandedTrips.visibility = View.GONE
                binding.imgExpandArrow.setImageResource(android.R.drawable.arrow_down_float)
            }

            binding.layoutHeader.setOnClickListener {
                val newState = !(expandedStates[dateStr] ?: false)
                expandedStates[dateStr] = newState
                notifyItemChanged(adapterPosition)
            }
        }

        @SuppressLint("SetTextI18n")
        private fun loadTripsIntoExpandedLayout(date: String, container: LinearLayout, context: Context) {
            container.removeAllViews()

            // Show a temporary loading text
            val loadingTv = TextView(context).apply {
                text = "Loading trips..."
                textSize = 12sp
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 8, 0, 8)
            }
            container.addView(loadingTv)

            scope.launch {
                val trips = tripsRepository.getTripsForDate(userId, date)
                
                withContext(Dispatchers.Main) {
                    container.removeAllViews()
                    if (trips.isEmpty()) {
                        val noTripsTv = TextView(context).apply {
                            text = "No individual trip details found."
                            textSize = 12sp
                            setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                            setPadding(0, 8, 0, 8)
                        }
                        container.addView(noTripsTv)
                        return@withContext
                    }

                    // Add Divider
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            setMargins(0, 4, 0, 12)
                        }
                        setBackgroundColor(ContextCompat.getColor(context, R.color.border))
                    }
                    container.addView(divider)

                    trips.forEachIndexed { index, trip ->
                        val tripRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 6, 0, 6)
                            }
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        // Icon dot
                        val dot = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                                setMargins(0, 0, 12, 0)
                            }
                            val color = if (trip.type == "out") "#378ADD" else "#1D9E75"
                            val drawable = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor(color))
                            }
                            background = drawable
                        }
                        tripRow.addView(dot)

                        // Title
                        val typeLabel = if (trip.type == "out") "Out" else "In"
                        val titleTv = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                            text = "Trip ${trip.tripNumber} ($typeLabel)"
                            textSize = 13sp
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                        }
                        tripRow.addView(titleTv)

                        // Time & KM
                        val timeFormatted = formatTime(trip.timestamp)
                        val kmFormatted = String.format(Locale.US, "%,d", trip.kmReading)
                        val descTv = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.5f)
                            text = "$timeFormatted · $kmFormatted KM"
                            textSize = 12sp
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        }
                        tripRow.addView(descTv)

                        // KM diff for returned trip
                        if (trip.type == "in" && index > 0) {
                            val prevTrip = trips[index - 1]
                            val diff = trip.kmReading - prevTrip.kmReading
                            val diffTv = TextView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                text = "+$diff KM"
                                textSize = 11sp
                                setTextColor(ContextCompat.getColor(context, R.color.success))
                                setPadding(8, 4, 8, 4)
                                setBackgroundColor(Color.parseColor("#E8F5F1"))
                            }
                            tripRow.addView(diffTv)
                        }

                        container.addView(tripRow)
                    }
                }
            }
        }

        private fun formatDate(dateStr: String): String {
            return try {
                val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = inputSdf.parse(dateStr) ?: return dateStr
                val outputSdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
                outputSdf.format(date)
            } catch (e: Exception) {
                dateStr
            }
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
                ""
            }
        }
    }
}
