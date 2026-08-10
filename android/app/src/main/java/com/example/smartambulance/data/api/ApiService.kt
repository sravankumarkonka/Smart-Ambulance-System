package com.example.smartambulance.data.api

import com.example.smartambulance.data.model.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/profile/{uid}")
    suspend fun getProfile(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): User

    @POST("auth/profile/{uid}")
    suspend fun saveProfile(
        @Header("Authorization") token: String,
        @Path("uid") uid: String,
        @Body profile: User
    ): User

    // Emergencies
    @POST("emergencies")
    suspend fun createEmergency(
        @Header("Authorization") token: String,
        @Body request: CreateEmergencyRequest
    ): CreateEmergencyResponse

    @GET("emergencies/{id}")
    suspend fun getEmergencyById(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Emergency

    @GET("emergencies/history/{userId}")
    suspend fun getEmergencyHistory(
        @Header("Authorization") token: String,
        @Path("userId") userId: String
    ): List<Emergency>

    @POST("emergencies/{id}/cancel")
    suspend fun cancelEmergency(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Map<String, String>

    @Multipart
    @POST("emergencies/{id}/image")
    suspend fun uploadEmergencyImage(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Part file: MultipartBody.Part
    ): Map<String, String>

    // Drivers
    @POST("driver/emergencies/{id}/assign")
    suspend fun assignDriver(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: AssignDriverRequest
    ): Map<String, String>

    @POST("driver/emergencies/{id}/auto-assign")
    suspend fun autoAssignDriver(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: Map<String, Double>
    ): Map<String, String>

    @PATCH("driver/emergencies/{id}/status")
    suspend fun updateEmergencyStatus(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: UpdateStatusRequest
    ): Map<String, String>

    @POST("driver/emergencies/{id}/release")
    suspend fun releaseEmergency(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body request: Map<String, String>
    ): Map<String, String>

    @POST("driver/ambulances")
    suspend fun updateAmbulance(
        @Header("Authorization") token: String,
        @Body request: UpdateAmbulanceRequest
    ): Map<String, String>

    @GET("driver/ambulances/{driverId}")
    suspend fun getAmbulanceProfile(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String
    ): Ambulance

    @POST("driver/ambulances/{driverId}/location")
    suspend fun updateDriverLocation(
        @Header("Authorization") token: String,
        @Path("driverId") driverId: String,
        @Body request: UpdateLocationRequest
    ): Map<String, String>

    // Admin
    @GET("admin/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): AdminStats

    @GET("admin/ambulances")
    suspend fun getAllAmbulances(
        @Header("Authorization") token: String
    ): List<Ambulance>

    @GET("admin/ambulances/available")
    suspend fun getAvailableAmbulances(
        @Header("Authorization") token: String
    ): List<Ambulance>

    @GET("admin/emergencies")
    suspend fun getAllEmergencies(
        @Header("Authorization") token: String
    ): List<Emergency>

    // Hospitals
    @GET("hospitals")
    suspend fun getHospitals(
        @Header("Authorization") token: String
    ): List<Hospital>

    @GET("hospitals/recommend")
    suspend fun recommendHospital(
        @Header("Authorization") token: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("severityLevel") severityLevel: String
    ): HospitalRecommendation

    // Super Admin & Approvals
    @GET("approval/pending-admins")
    suspend fun getPendingAdmins(
        @Header("Authorization") token: String
    ): List<User>

    @GET("approval/pending-drivers")
    suspend fun getPendingDrivers(
        @Header("Authorization") token: String
    ): List<User>

    @POST("approval/admin/{uid}/approve")
    suspend fun approveAdmin(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @POST("approval/admin/{uid}/reject")
    suspend fun rejectAdmin(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @POST("approval/admin/{uid}/suspend")
    suspend fun suspendAdmin(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @DELETE("approval/admin/{uid}")
    suspend fun deleteAdmin(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @GET("approval/all-users")
    suspend fun getAllUsers(
        @Header("Authorization") token: String
    ): List<User>

    @GET("approval/audit-logs")
    suspend fun getAuditLogs(
        @Header("Authorization") token: String
    ): List<AuditLog>

    @POST("approval/driver/{uid}/approve")
    suspend fun approveDriver(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @POST("approval/driver/{uid}/suspend")
    suspend fun suspendDriver(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>

    @DELETE("approval/driver/{uid}")
    suspend fun deleteDriver(
        @Header("Authorization") token: String,
        @Path("uid") uid: String
    ): Map<String, String>
}
