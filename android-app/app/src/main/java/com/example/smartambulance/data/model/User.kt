package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uid: String? = null,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val status: String? = "active",
    val approved: Boolean? = true,
    val photoURL: String? = null
)

@Serializable
data class AuthResponse(
    val uid: String,
    val idToken: String? = null,
    val customToken: String? = null,
    val profile: User? = null
)

@Serializable
data class DriverDetails(
    val uid: String? = null,
    val licenseNumber: String? = null,
    val vehicleNumber: String? = null,
    val experience: String? = null,
    val availability: Boolean? = false
)
