package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String,
    val licenseNumber: String? = null,
    val vehicleNumber: String? = null,
    val experience: String? = null
)
