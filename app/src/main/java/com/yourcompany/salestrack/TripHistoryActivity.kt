package com.yourcompany.salestrack

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourcompany.salestrack.databinding.ActivityTripHistoryBinding
import com.yourcompany.salestrack.supabase.TripsRepository
import com.yourcompany.salestrack.ui.HistoryAdapter
import kotlinx.coroutines.launch

class TripHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripHistoryBinding
    private val tripsRepository = TripsRepository()
    private lateinit var historyAdapter: HistoryAdapter
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPref = getSharedPreferences("SalesTrackSession", Context.MODE_PRIVATE)
        userId = sharedPref.getString("user_id", "") ?: ""

        // Setup RecyclerView
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(emptyList(), userId, lifecycleScope)
        binding.rvHistory.adapter = historyAdapter

        // Back Button
        binding.btnHistoryBack.setOnClickListener {
            finish()
        }

        // Swipe Refresh
        binding.swipeRefreshHistory.setOnRefreshListener {
            loadHistory()
        }

        loadHistory()
    }

    private fun loadHistory() {
        binding.swipeRefreshHistory.isRefreshing = true
        lifecycleScope.launch {
            val summaries = tripsRepository.getSalespersonHistory(userId)
            binding.swipeRefreshHistory.isRefreshing = false

            historyAdapter.updateSummaries(summaries)

            if (summaries.isEmpty()) {
                binding.txtNoHistory.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.txtNoHistory.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
            }
        }
    }
}
