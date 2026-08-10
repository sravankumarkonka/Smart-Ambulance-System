package com.example.smartambulance.data.repository

import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.AdminStats
import com.example.smartambulance.data.model.Ambulance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getStats(): Result<AdminStats> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getStats(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllAmbulances(): Result<List<Ambulance>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getAllAmbulances(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAvailableAmbulances(): Result<List<Ambulance>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getAvailableAmbulances(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllEmergencies(): Result<List<com.example.smartambulance.data.model.Emergency>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getAllEmergencies(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingDrivers(): Result<List<com.example.smartambulance.data.model.User>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getPendingAdmins(token) // or driver pending
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveDriver(uid: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.approveDriver(token, uid)
            Result.success(response["message"] ?: "Driver approved.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectDriver(uid: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.deleteDriver(token, uid)
            Result.success(response["message"] ?: "Driver rejected.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAuditLogs(): Result<List<com.example.smartambulance.data.model.AuditLog>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getAuditLogs(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
