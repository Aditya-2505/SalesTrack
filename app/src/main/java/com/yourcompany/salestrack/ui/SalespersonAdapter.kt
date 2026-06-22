package com.yourcompany.salestrack.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yourcompany.salestrack.R
import com.yourcompany.salestrack.databinding.ItemSalespersonBinding
import com.yourcompany.salestrack.model.DailySummary
import com.yourcompany.salestrack.model.Profile
import java.util.Calendar

class SalespersonAdapter(
    private var dataList: List<SalespersonDashboardItem>,
    private val onItemClick: (SalespersonDashboardItem) -> Unit
) : RecyclerView.Adapter<SalespersonAdapter.SalespersonViewHolder>() {

    data class SalespersonDashboardItem(
        val profile: Profile,
        val summary: DailySummary?
    )

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newData: List<SalespersonDashboardItem>) {
        this.dataList = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalespersonViewHolder {
        val binding = ItemSalespersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SalespersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SalespersonViewHolder, position: Int) {
        val item = dataList[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = dataList.size

    inner class SalespersonViewHolder(private val binding: ItemSalespersonBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: SalespersonDashboardItem) {
            val context = binding.root.context
            val profile = item.profile
            val summary = item.summary

            binding.txtSalespersonName.text = profile.fullName
            
            // Set initials avatar
            val initials = profile.fullName.split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it[0].uppercase() }
            binding.txtAvatarInitials.text = if (initials.isNotEmpty()) initials else "ST"

            // Set Stats
            val km = summary?.totalKmDriven ?: 0
            val trips = summary?.timesOut ?: 0
            binding.txtSalespersonStats.text = "$km KM driven · $trips trips out"

            // Set Status Badge based on business rules
            val currentCalendar = Calendar.getInstance()
            val hour = currentCalendar.get(Calendar.HOUR_OF_DAY)
            val isPastNineAm = hour >= 9

            if (summary == null) {
                binding.badgeStatus.text = "missing"
                if (isPastNineAm) {
                    // Past 9:00 AM, highlight missing in red
                    binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.danger))
                    binding.badgeStatus.setBackgroundResource(R.color.danger_light)
                } else {
                    // Before 9:00 AM, show neutral missing/pending style
                    binding.badgeStatus.setTextColor(Color.parseColor("#4B5563")) // Gray
                    binding.badgeStatus.setBackgroundColor(Color.parseColor("#E5E7EB"))
                }
            } else {
                when (summary.status.lowercase()) {
                    "completed" -> {
                        binding.badgeStatus.text = "done"
                        binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                        binding.badgeStatus.setBackgroundResource(R.color.success_light)
                    }
                    "active" -> {
                        binding.badgeStatus.text = "active"
                        binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                        binding.badgeStatus.setBackgroundResource(R.color.warning_light)
                    }
                    else -> {
                        binding.badgeStatus.text = "missing"
                        binding.badgeStatus.setTextColor(Color.parseColor("#4B5563"))
                        binding.badgeStatus.setBackgroundColor(Color.parseColor("#E5E7EB"))
                    }
                }
            }
        }
    }
}
