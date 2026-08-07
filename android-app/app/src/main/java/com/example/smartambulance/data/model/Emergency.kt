package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Emergency(
    val id: String? = null,
    val userId: String,
    val patientName: String,
    val emergencyType: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val severityLevel: String = "medium",
    val status: String = "pending",
    val createdAt: String? = null,
    val hospitalName: String? = null,
    val hospitalLatitude: Double? = null,
    val hospitalLongitude: Double? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverLatitude: Double? = null,
    val driverLongitude: Double? = null,
    val driverSpeed: Double? = null,
    val driverHeading: Double? = null,
    val assignedAt: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class CreateEmergencyRequest(
    val userId: String,
    val patientName: String,
    val emergencyType: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val severityLevel: String,
    val hospitalName: String? = null,
    val hospitalLatitude: Double? = null,
    val hospitalLongitude: Double? = null
)

@Serializable
data class CreateEmergencyResponse(
    val id: String
)

@Serializable
data class AssignDriverRequest(
    val driverId: String,
    val driverName: String,
    val driverPhone: String
)

@Serializable
data class UpdateStatusRequest(
    val status: String
)

@Serializable
data class UpdateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val emergencyId: String? = null
)

@Serializable
data class RouteResponse(
    val distanceKm: Double? = null,
    val durationMinutes: Double? = null,
    val polyline: String? = null,
    val status: String? = null
)
