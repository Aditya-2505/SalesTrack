package com.yourcompany.salestrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("created_at") val createdAt: String? = null
)
