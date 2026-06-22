package com.yourcompany.salestrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourcompany.salestrack.databinding.ActivityManagerDashboardBinding
import com.yourcompany.salestrack.supabase.ProfilesRepository
import com.yourcompany.salestrack.supabase.Supabase
import com.yourcompany.salestrack.supabase.TripsRepository
import com.yourcompany.salestrack.ui.SalespersonAdapter
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerDashboardBinding
    private val profilesRepository = ProfilesRepository()
    private val tripsRepository = TripsRepository()
    private lateinit var adapter: SalespersonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtDashboardDate.text = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())

        // Setup RecyclerView
        binding.rvSalespersons.layoutManager = LinearLayoutManager(this)
        adapter = SalespersonAdapter(emptyList()) { item ->
            val intent = Intent(this, SalespersonDetailActivity::class.java).apply {
                putExtra("salesperson_id", item.profile.id)
                putExtra("salesperson_name", item.profile.fullName)
            }
            startActivity(intent)
        }
        binding.rvSalespersons.adapter = adapter

        // Pull to refresh
        binding.swipeRefreshManager.setOnRefreshListener {
            loadDashboardData()
        }

        // Reports Screen Navigation
        binding.btnReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }

        // Logout
        binding.btnManagerLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    Supabase.client.auth.signOut()
                } catch (e: Exception) {
                    // Silently fail if offline, still clear local session
                }
                
                val sharedPref = getSharedPreferences("SalesTrackSession", Context.MODE_PRIVATE)
                sharedPref.edit().clear().apply()

                Toast.makeText(this@ManagerDashboardActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@ManagerDashboardActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }

    private fun loadDashboardData() {
        binding.swipeRefreshManager.isRefreshing = true
        lifecycleScope.launch {
            try {
                // Fetch profiles
                val profiles = profilesRepository.getAllProfiles()
                val salespersons = profiles.filter { it.role.lowercase() == "salesperson" }

                // Fetch today's summaries
                val summaries = tripsRepository.getTodaySummariesForAll()
                val summaryMap = summaries.associateBy { it.userId }

                // Map
                val dashboardItems = salespersons.map { profile ->
                    SalespersonAdapter.SalespersonDashboardItem(
                        profile = profile,
                        summary = summaryMap[profile.id]
                    )
                }

                binding.swipeRefreshManager.isRefreshing = false
                adapter.updateData(dashboardItems)

                if (dashboardItems.isEmpty()) {
                    binding.txtNoSalespersons.visibility = View.VISIBLE
                    binding.rvSalespersons.visibility = View.GONE
                } else {
                    binding.txtNoSalespersons.visibility = View.GONE
                    binding.rvSalespersons.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                binding.swipeRefreshManager.isRefreshing = false
                Toast.makeText(this@ManagerDashboardActivity, "Error loading dashboard: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
