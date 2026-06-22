package com.yourcompany.salestrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailySummary(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("start_km") val startKm: Int,
    @SerialName("end_km") val endKm: Int? = null,
    @SerialName("total_km_driven") val totalKmDriven: Int = 0,
    @SerialName("times_out") val timesOut: Int = 0,
    val status: String, // "active" or "completed"
    @SerialName("created_at") val createdAt: String? = null
)
