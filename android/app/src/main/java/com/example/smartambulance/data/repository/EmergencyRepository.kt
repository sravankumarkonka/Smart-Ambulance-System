package com.example.smartambulance.data.repository

import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun createEmergency(
        patientName: String,
        emergencyType: String,
        description: String,
        latitude: Double,
        longitude: Double,
        severityLevel: String,
        hospitalName: String? = null,
        hospitalLatitude: Double? = null,
        hospitalLongitude: Double? = null
    ): Result<CreateEmergencyResponse> {
        return try {
            val token = SessionManager.getFormattedToken()
            val userId = SessionManager.uid ?: throw Exception("User is not logged in")
            val request = CreateEmergencyRequest(
                userId = userId,
                patientName = patientName.trim(),
                emergencyType = emergencyType,
                description = description.trim(),
                latitude = latitude,
                longitude = longitude,
                severityLevel = severityLevel,
                hospitalName = hospitalName,
                hospitalLatitude = hospitalLatitude,
                hospitalLongitude = hospitalLongitude
            )
            val response = apiService.createEmergency(token, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEmergencyById(id: String): Result<Emergency> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getEmergencyById(token, id)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEmergencyHistory(userId: String): Result<List<Emergency>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getEmergencyHistory(token, userId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelEmergency(id: String): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            apiService.cancelEmergency(token, id)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHospitals(): Result<List<Hospital>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getHospitals(token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recommendHospital(
        latitude: Double,
        longitude: Double,
        severityLevel: String
    ): Result<HospitalRecommendation> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.recommendHospital(token, latitude, longitude, severityLevel)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadEmergencyImage(
        id: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val requestFile = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull(), 0, imageBytes.size)
            val body = MultipartBody.Part.createFormData("file", "accident_scene.jpg", requestFile)
            val response = apiService.uploadEmergencyImage(token, id, body)
            val imageUrl = response["imageUrl"] ?: throw Exception("Image upload response does not contain imageUrl")
            Result.success(imageUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
