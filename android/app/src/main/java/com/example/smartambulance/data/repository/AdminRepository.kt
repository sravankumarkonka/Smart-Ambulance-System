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
}
