package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Hospital(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val totalIcuBeds: Int,
    val availableIcuBeds: Int,
    val rating: Double,
    val phone: String,
    val distanceKm: Double? = null,
    val icuStatus: String? = null,
    val suitabilityScore: Double? = null
)

@Serializable
data class HospitalRecommendation(
    val recommended: Hospital,
    val comparison: List<Hospital>
)
