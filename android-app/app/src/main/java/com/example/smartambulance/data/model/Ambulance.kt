package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Ambulance(
    val id: String? = null,
    val status: String,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastUpdated: String? = null
)

@Serializable
data class UpdateAmbulanceRequest(
    val driverId: String,
    val ambulanceData: Ambulance
)

@Serializable
data class AdminStats(
    val activeCount: Int,
    val criticalCount: Int,
    val availableCount: Int,
    val busyCount: Int
)
