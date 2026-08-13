package com.example.smartambulance

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Login : NavKey

@Serializable
data object Register : NavKey

@Serializable
data object UserDashboard : NavKey

@Serializable
data object ReportEmergency : NavKey

@Serializable
data class TrackAmbulance(val emergencyId: String) : NavKey

@Serializable
data object EmergencyHistory : NavKey

@Serializable
data object DriverDashboard : NavKey

@Serializable
data class ActiveEmergency(val emergencyId: String) : NavKey

@Serializable
data object AdminDashboard : NavKey

@Serializable
data object SuperAdminDashboard : NavKey

@Serializable
data object LiveMap : NavKey

@Serializable
data object DriverHistory : NavKey

@Serializable
data object ProfileScreen : NavKey
