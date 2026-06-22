package com.yourcompany.salestrack

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourcompany.salestrack.databinding.ActivityReportsBinding
import com.yourcompany.salestrack.databinding.ItemReportRowBinding
import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.model.Profile
import com.yourcompany.salestrack.supabase.ProfilesRepository
import com.yourcompany.salestrack.supabase.TripsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val profilesRepository = ProfilesRepository()
    private val tripsRepository = TripsRepository()

    private var salespersonList: List<Profile> = emptyList()
    private var profileMap: Map<String, Profile> = emptyMap()
    private var reportSummaries: List<DailySummary> = emptyList()

    private var selectedUserId: String? = null // null means "All"
    private var fromDateStr: String = ""
    private var toDateStr: String = ""

    private lateinit var reportAdapter: ReportAdapter

    private val CREATE_FILE_REQUEST_CODE = 4444

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Date Filters (Default to last 7 days)
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        toDateStr = sdf.format(cal.time)
        cal.add(Calendar.DATE, -6)
        fromDateStr = sdf.format(cal.time)

        binding.btnFromDate.text = "From: $fromDateStr"
        binding.btnToDate.text = "To: $toDateStr"

        binding.btnReportsBack.setOnClickListener {
            finish()
        }

        // Date Pickers
        binding.btnFromDate.setOnClickListener {
            showDatePicker(true)
        }
        binding.btnToDate.setOnClickListener {
            showDatePicker(false)
        }

        // Setup RecyclerView
        binding.rvReports.layoutManager = LinearLayoutManager(this)
        reportAdapter = ReportAdapter(emptyList())
        binding.rvReports.adapter = reportAdapter

        // Load Salesperson list for Spinner
        loadFilterDropdown()

        // Export Button
        binding.btnExportCsv.setOnClickListener {
            if (reportSummaries.isEmpty()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            triggerCsvSaveIntent()
        }
    }

    private fun showDatePicker(isFromDate: Boolean) {
        val calendar = Calendar.getInstance()
        val currentStr = if (isFromDate) fromDateStr else toDateStr
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(currentStr)
            if (date != null) {
                calendar.time = date
            }
        } catch (e: Exception) {}

        val picker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val calSelected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calSelected.time)
                if (isFromDate) {
                    fromDateStr = formatted
                    binding.btnFromDate.text = "From: $fromDateStr"
                } else {
                    toDateStr = formatted
                    binding.btnToDate.text = "To: $toDateStr"
                }
                loadReportData()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun loadFilterDropdown() {
        lifecycleScope.launch {
            try {
                val profiles = profilesRepository.getAllProfiles()
                salespersonList = profiles.filter { it.role.lowercase() == "salesperson" }
                profileMap = profiles.associateBy { it.id }

                val spinnerItems = mutableListOf<String>()
                spinnerItems.add("All Salespersons")
                salespersonList.forEach {
                    spinnerItems.add(it.fullName)
                }

                val adapter = ArrayAdapter(this@ReportsActivity, android.R.layout.simple_spinner_item, spinnerItems)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerSalesperson.adapter = adapter

                binding.spinnerSalesperson.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedUserId = if (position == 0) {
                            null
                        } else {
                            salespersonList[position - 1].id
                        }
                        loadReportData()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

            } catch (e: Exception) {
                Toast.makeText(this@ReportsActivity, "Failed to load filters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadReportData() {
        lifecycleScope.launch {
            try {
                val summaries = tripsRepository.getReportSummaries(fromDateStr, toDateStr, selectedUserId)
                reportSummaries = summaries
                reportAdapter.updateSummaries(summaries)
            } catch (e: Exception) {
                Toast.makeText(this@ReportsActivity, "Failed to load report data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerCsvSaveIntent() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "SalesTrack_Report_${System.currentTimeMillis()}.csv")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                writeCsvToStream(uri)
            }
        }
    }

    private fun writeCsvToStream(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val csvBuilder = StringBuilder()
                csvBuilder.append("Date,Name,Times Out,Total KM,Start KM,End KM\n")
                
                reportSummaries.forEach { summary ->
                    val name = profileMap[summary.userId]?.fullName ?: "Unknown"
                    csvBuilder.append("${summary.date},\"$name\",${summary.timesOut},${summary.totalKmDriven},${summary.startKm},${summary.endKm ?: 0}\n")
                }

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvBuilder.toString().toByteArray())
                }

                Toast.makeText(this@ReportsActivity, "Report exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@ReportsActivity, "Failed to export CSV: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Inner Adapter to bind summary records to table rows
    inner class ReportAdapter(private var summariesList: List<DailySummary>) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

        @SuppressLint("NotifyDataSetChanged")
        fun updateSummaries(newList: List<DailySummary>) {
            this.summariesList = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
            val binding = ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ReportViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
            holder.bind(summariesList[position])
        }

        override fun getItemCount(): Int = summariesList.size

        inner class ReportViewHolder(private val rowBinding: ItemReportRowBinding) : RecyclerView.ViewHolder(rowBinding.root) {
            @SuppressLint("SetTextI18n")
            fun bind(summary: DailySummary) {
                rowBinding.txtRowDate.text = summary.date
                rowBinding.txtRowName.text = profileMap[summary.userId]?.fullName ?: "Unknown"
                rowBinding.txtRowTrips.text = summary.timesOut.toString()
                rowBinding.txtRowTotalKm.text = "${summary.totalKmDriven} KM"
            }
        }
    }
}
