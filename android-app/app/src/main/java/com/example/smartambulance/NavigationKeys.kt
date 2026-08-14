package com.example.smartambulance

import kotlinx.serialization.Serializable

@Serializable
data object Login

@Serializable
data object Register

@Serializable
data object UserDashboard

@Serializable
data object ReportEmergency

@Serializable
data class TrackAmbulance(val emergencyId: String)

@Serializable
data object EmergencyHistory

@Serializable
data object DriverDashboard

@Serializable
data class ActiveEmergency(val emergencyId: String)

@Serializable
data object AdminDashboard

@Serializable
data object SuperAdminDashboard

@Serializable
data object LiveMap

@Serializable
data object DriverHistory

@Serializable
data object AdminHistory

@Serializable
data object Profile

@Serializable
data object NotificationCenter

