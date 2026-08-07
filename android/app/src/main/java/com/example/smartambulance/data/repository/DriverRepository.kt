package com.example.smartambulance.data.repository

import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun assignDriver(
        emergencyId: String,
        driverId: String,
        driverName: String,
        driverPhone: String
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val request = AssignDriverRequest(driverId, driverName, driverPhone)
            apiService.assignDriver(token, emergencyId, request)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun autoAssign(
        emergencyId: String,
        latitude: Double,
        longitude: Double
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val body = mapOf("latitude" to latitude, "longitude" to longitude)
            apiService.autoAssignDriver(token, emergencyId, body)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStatus(
        emergencyId: String,
        status: String
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val request = UpdateStatusRequest(status)
            apiService.updateEmergencyStatus(token, emergencyId, request)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun releaseEmergency(
        emergencyId: String,
        driverId: String
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val body = mapOf("driverId" to driverId)
            apiService.releaseEmergency(token, emergencyId, body)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAmbulance(
        driverId: String,
        ambulanceData: Ambulance
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val request = UpdateAmbulanceRequest(driverId, ambulanceData)
            apiService.updateAmbulance(token, request)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAmbulanceProfile(driverId: String): Result<Ambulance> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getAmbulanceProfile(token, driverId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        emergencyId: String? = null
    ): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            val request = UpdateLocationRequest(
                latitude = latitude,
                longitude = longitude,
                emergencyId = emergencyId
            )
            apiService.updateDriverLocation(token, driverId, request)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
