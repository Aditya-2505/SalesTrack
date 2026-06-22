package com.yourcompany.salestrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourcompany.salestrack.databinding.ActivitySalespersonHomeBinding
import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.model.Trip
import com.yourcompany.salestrack.notifications.AlarmReceiver
import com.yourcompany.salestrack.supabase.TripsRepository
import com.yourcompany.salestrack.ui.TripAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SalespersonHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySalespersonHomeBinding
    private val tripsRepository = TripsRepository()
    private lateinit var tripAdapter: TripAdapter
    private var userId: String = ""
    private var nextTripType: String = "out"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySalespersonHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve user details from SharedPreferences
        val sharedPref = getSharedPreferences("SalesTrackSession", Context.MODE_PRIVATE)
        userId = sharedPref.getString("user_id", "") ?: ""
        val fullName = sharedPref.getString("full_name", "Salesperson") ?: "Salesperson"

        binding.txtWelcomeName.text = "Welcome, $fullName"
        binding.txtTodayDate.text = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())

        // Setup RecyclerView
        binding.rvTrips.layoutManager = LinearLayoutManager(this)
        tripAdapter = TripAdapter(emptyList())
        binding.rvTrips.adapter = tripAdapter

        // History Navigation
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, TripHistoryActivity::class.java))
        }

        // Action Button Click
        binding.btnAction.setOnClickListener {
            checkCameraPermissionAndStartCamera()
        }

        // End Day Button Click
        binding.btnEndDay.setOnClickListener {
            lifecycleScope.launch {
                val success = tripsRepository.completeDay(userId)
                if (success) {
                    Toast.makeText(this@SalespersonHomeActivity, "Day completed successfully!", Toast.LENGTH_SHORT).show()
                    loadTodayData()
                } else {
                    Toast.makeText(this@SalespersonHomeActivity, "Failed to complete day", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Setup Daily Notifications
        setupDailyReminders()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        loadTodayData()
    }

    private fun loadTodayData() {
        lifecycleScope.launch {
            val trips = tripsRepository.getTodayTrips(userId)
            val summary = tripsRepository.getTodaySummary(userId)

            // Update List
            tripAdapter.updateTrips(trips)
            tripAdapter.updateTrips(trips) // Bind isCompleted details
            
            if (trips.isEmpty()) {
                binding.txtNoTrips.visibility = View.VISIBLE
                binding.rvTrips.visibility = View.GONE
            } else {
                binding.txtNoTrips.visibility = View.GONE
                binding.rvTrips.visibility = View.VISIBLE
            }

            // Update Stats
            binding.txtKmDriven.text = "${summary?.totalKmDriven ?: 0} KM"
            binding.txtTimesOut.text = "${summary?.timesOut ?: 0}"

            // Setup Adapter Completed State
            val isCompleted = summary?.status == "completed"
            tripAdapter = TripAdapter(trips, isCompleted)
            binding.rvTrips.adapter = tripAdapter

            // Button State Logic
            updateButtonState(trips, summary)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateButtonState(trips: List<Trip>, summary: DailySummary?) {
        if (summary?.status == "completed") {
            // Day complete
            binding.btnAction.text = "Day complete"
            binding.btnAction.isEnabled = false
            binding.btnAction.setBackgroundColor(Color.parseColor("#1D9E75")) // Green
            binding.btnEndDay.visibility = View.GONE
            nextTripType = "none"
        } else if (trips.isEmpty()) {
            // No entries today
            binding.btnAction.text = "Start day — take photo"
            binding.btnAction.isEnabled = true
            binding.btnAction.setBackgroundColor(Color.parseColor("#378ADD")) // Blue
            binding.btnEndDay.visibility = View.GONE
            nextTripType = "out"
        } else {
            val lastTrip = trips.last()
            if (lastTrip.type == "out") {
                // Last was type "out" -> Going in
                binding.btnAction.text = "I\'m back — take photo"
                binding.btnAction.isEnabled = true
                binding.btnAction.setBackgroundColor(Color.parseColor("#378ADD"))
                binding.btnEndDay.visibility = View.GONE
                nextTripType = "in"
            } else {
                // Last was type "in" -> Going out or Complete
                binding.btnAction.text = "Going out — take photo"
                binding.btnAction.isEnabled = true
                binding.btnAction.setBackgroundColor(Color.parseColor("#378ADD"))
                binding.btnEndDay.visibility = View.VISIBLE
                nextTripType = "out"
            }
        }
    }

    private fun checkCameraPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            startCameraActivity()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraActivity()
        } else if (requestCode == 101) {
            Toast.makeText(this, "Camera permission is required to log mileage", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCameraActivity() {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("trip_type", nextTripType)
        }
        startActivity(intent)
    }

    // Set up local AlarmManager alarms for morning and evening notification checks
    @SuppressLint("ScheduleExactAlarm")
    private fun setupDailyReminders() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Helper to schedule at specific hour/minute
        fun scheduleAlarm(hour: Int, minute: Int, requestCode: Int) {
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                putExtra("request_code", requestCode)
                putExtra("user_id", userId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                // If the time has passed today, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DATE, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }

        // Request Code 1001: Morning alarm (8:30 AM)
        scheduleAlarm(8, 30, 1001)

        // Request Code 1002: Evening alarm (6:00 PM)
        scheduleAlarm(18, 0, 1002)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }
}
