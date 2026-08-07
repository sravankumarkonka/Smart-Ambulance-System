package com.example.smartambulance.data.repository

import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.AuthResponse
import com.example.smartambulance.data.model.LoginRequest
import com.example.smartambulance.data.model.RegisterRequest
import com.example.smartambulance.data.model.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.login(LoginRequest(email.trim(), password))
            // Store details in session manager on successful login
            SessionManager.token = response.idToken
            SessionManager.uid = response.uid
            SessionManager.role = if (response.profile.role == "patient") "user" else response.profile.role
            SessionManager.name = response.profile.name
            SessionManager.phone = response.profile.phone
            SessionManager.email = response.profile.email
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String
    ): Result<AuthResponse> {
        return try {
            val response = apiService.register(
                RegisterRequest(
                    name.trim(),
                    email.trim(),
                    phone.trim(),
                    password,
                    if (role == "patient") "user" else role
                )
            )
            // Store details in session manager on successful register
            SessionManager.token = response.idToken
            SessionManager.uid = response.uid
            SessionManager.role = if (response.profile.role == "patient") "user" else response.profile.role
            SessionManager.name = response.profile.name
            SessionManager.phone = response.profile.phone
            SessionManager.email = response.profile.email
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(uid: String): Result<User> {
        return try {
            val token = SessionManager.getFormattedToken()
            val user = apiService.getProfile(token, uid)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProfile(uid: String, user: User): Result<User> {
        return try {
            val token = SessionManager.getFormattedToken()
            val savedUser = apiService.saveProfile(token, uid, user)
            // Update session manager info
            SessionManager.name = savedUser.name
            SessionManager.phone = savedUser.phone
            Result.success(savedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            suspendCoroutine<Unit> { continuation ->
                auth.sendPasswordResetEmail(email.trim())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            continuation.resume(Unit)
                        } else {
                            val exception = task.exception ?: Exception("Failed to send password reset email")
                            continuation.resumeWith(Result.failure(exception))
                        }
                    }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
