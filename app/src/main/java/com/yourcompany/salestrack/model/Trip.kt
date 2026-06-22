package com.yourcompany.salestrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val date: String,
    val type: String, // "out" or "in"
    @SerialName("km_reading") val kmReading: Int,
    @SerialName("photo_url") val photoUrl: String?,
    @SerialName("trip_number") val tripNumber: Int,
    val timestamp: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
