package com.yourcompany.salestrack

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourcompany.salestrack.databinding.ActivitySalespersonDetailBinding
import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.supabase.TripsRepository
import com.yourcompany.salestrack.ui.TripAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SalespersonDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalespersonDetailBinding
    private val tripsRepository = TripsRepository()
    private var salespersonId: String = ""
    private var salespersonName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalespersonDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        salespersonId = intent.getStringExtra("salesperson_id") ?: ""
        salespersonName = intent.getStringExtra("salesperson_name") ?: "Employee"

        binding.txtDetailName.text = salespersonName

        binding.btnDetailBack.setOnClickListener {
            finish()
        }

        binding.rvDetailTrips.layoutManager = LinearLayoutManager(this)

        loadDetailData()
    }

    @SuppressLint("SetTextI18n")
    private fun loadDetailData() {
        lifecycleScope.launch {
            // 1. Fetch today's trips
            val trips = tripsRepository.getTodayTrips(salespersonId)
            val todaySummary = tripsRepository.getTodaySummary(salespersonId)
            val isCompleted = todaySummary?.status == "completed"

            val adapter = TripAdapter(trips, isCompleted)
            binding.rvDetailTrips.adapter = adapter

            if (trips.isEmpty()) {
                binding.txtDetailNoTrips.visibility = View.VISIBLE
                binding.rvDetailTrips.visibility = View.GONE
            } else {
                binding.txtDetailNoTrips.visibility = View.GONE
                binding.rvDetailTrips.visibility = View.VISIBLE
            }

            // 2. Fetch weekly summaries (last 7 days)
            val weeklySummaries = tripsRepository.getWeeklySummaries(salespersonId)
            
            // Calculate totals
            val totalKm = weeklySummaries.sumOf { it.totalKmDriven }
            val totalTrips = weeklySummaries.sumOf { it.timesOut }
            binding.txtWeeklyKm.text = "$totalKm KM"
            binding.txtWeeklyTrips.text = totalTrips.toString()

            // 3. Render 7-day Bar Chart programmatically
            renderWeeklyChart(weeklySummaries)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderWeeklyChart(summaries: List<DailySummary>) {
        binding.chartContainer.removeAllViews()

        val summaryMap = summaries.associateBy { it.date }
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfDayAbbrev = SimpleDateFormat("E", Locale.getDefault()) // e.g. "Mon"

        // Generate past 7 days (ending today)
        val chartDays = mutableListOf<ChartDayData>()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -6) // Start 6 days ago

        for (i in 0..6) {
            val dateStr = sdfDate.format(calendar.time)
            val dayName = sdfDayAbbrev.format(calendar.time)
            val daySummary = summaryMap[dateStr]
            
            chartDays.add(
                ChartDayData(
                    date = dateStr,
                    dayName = dayName,
                    kmDriven = daySummary?.totalKmDriven ?: 0
                )
            )
            calendar.add(Calendar.DATE, 1)
        }

        // Find max KM value for scaling heights
        val maxKm = chartDays.maxOf { it.kmDriven }.coerceAtLeast(10) // Min scaling threshold

        // Get container height (160dp in pixels)
        val chartHeightPx = dpToPx(160)
        val barMaxHeight = chartHeightPx - dpToPx(40) // leave space for labels top & bottom

        chartDays.forEach { day ->
            // Parent Layout for a single column
            val columnLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

            // KM label above bar
            val labelTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = if (day.kmDriven > 0) "${day.kmDriven}" else ""
                textSize = 9sp
                setTextColor(ContextCompat.getColor(this@SalespersonDetailActivity, R.color.text_secondary))
                gravity = Gravity.CENTER
            }
            columnLayout.addView(labelTv)

            // Scaled Bar View
            val barHeight = ((day.kmDriven.toFloat() / maxKm.toFloat()) * barMaxHeight).toInt().coerceAtLeast(dpToPx(4))
            val barView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(16), barHeight).apply {
                    setMargins(0, 4, 0, 4)
                }
                val drawable = GradientDrawable().apply {
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(ContextCompat.getColor(this@SalespersonDetailActivity, R.color.primary))
                }
                background = drawable
            }
            columnLayout.addView(barView)

            // Day label below bar
            val dayTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = day.dayName
                textSize = 10sp
                setTextColor(ContextCompat.getColor(this@SalespersonDetailActivity, R.color.text_primary))
                gravity = Gravity.CENTER
            }
            columnLayout.addView(dayTv)

            binding.chartContainer.addView(columnLayout)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private data class ChartDayData(
        val date: String,
        val dayName: String,
        val kmDriven: Int
    )
}
