package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val name: String,
    val email: String,
    val phone: String,
    val role: String = "user",
    val createdAt: String? = null
)

@Serializable
data class AuthResponse(
    val uid: String,
    val profile: User,
    val customToken: String? = null,
    val idToken: String? = null
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)
