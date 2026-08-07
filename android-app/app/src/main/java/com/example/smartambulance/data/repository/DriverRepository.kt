package com.example.smartambulance.data.repository

import android.util.Log
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.api.RetrofitClient
import com.example.smartambulance.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

@Singleton
class DriverRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val TAG = "DriverRepository"
    private val db = FirebaseFirestore.getInstance()

    private fun isNetworkError(e: Exception): Boolean {
        val msg = e.message ?: ""
        return msg.contains("Failed to connect") ||
               msg.contains("Unable to resolve host") ||
               msg.contains("Connection refused") ||
               msg.contains("timeout") ||
               msg.contains("ECONNREFUSED") ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException ||
               e is java.io.IOException
    }

    suspend fun assignDriver(
        emergencyId: String,
        driverId: String,
        driverName: String,
        driverPhone: String
    ): Result<Boolean> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val request = AssignDriverRequest(driverId, driverName, driverPhone)
                apiService.assignDriver(token, emergencyId, request)
                return Result.success(true)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        // Firestore direct fallback
        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            val updateMap = hashMapOf<String, Any?>(
                "status" to "assigned",
                "driverId" to driverId,
                "assignedDriver" to driverId,
                "driverName" to driverName,
                "driverPhone" to driverPhone,
                "assignedAt" to now,
                "updatedAt" to now
            )

            suspendCoroutine<Unit> { cont ->
                db.collection("emergencies").document(emergencyId)
                    .update(updateMap)
                    .addOnCompleteListener { cont.resume(Unit) }
            }

            try {
                db.collection("drivers").document(driverId).update("availability", false)
                db.collection("ambulances").document(driverId).update(
                    mapOf("status" to "busy", "isAvailable" to false, "updatedAt" to now)
                )
            } catch (_: Exception) {}

            Log.d(TAG, "✅ Emergency assigned via Firestore direct write: $emergencyId to $driverId")
            Result.success(true)
        } catch (fe: Exception) {
            Log.e(TAG, "Firestore assignDriver failed: ${fe.message}")
            Result.failure(Exception("Failed to accept emergency assignment: ${fe.message}"))
        }
    }

    suspend fun updateStatus(
        emergencyId: String,
        status: String
    ): Result<Boolean> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val request = UpdateStatusRequest(status)
                apiService.updateEmergencyStatus(token, emergencyId, request)
                return Result.success(true)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            suspendCoroutine<Unit> { cont ->
                db.collection("emergencies").document(emergencyId)
                    .update(mapOf("status" to status, "updatedAt" to now))
                    .addOnCompleteListener { cont.resume(Unit) }
            }

            val driverId = SessionManager.uid
            if (driverId != null && (status == "completed" || status == "cancelled")) {
                try {
                    db.collection("drivers").document(driverId).update("availability", true)
                    db.collection("ambulances").document(driverId).update(
                        mapOf("status" to "available", "isAvailable" to true, "updatedAt" to now)
                    )
                } catch (_: Exception) {}
            }

            Result.success(true)
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }

    suspend fun releaseEmergency(
        emergencyId: String,
        driverId: String
    ): Result<Boolean> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val body = mapOf("driverId" to driverId)
                apiService.releaseEmergency(token, emergencyId, body)
                return Result.success(true)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            suspendCoroutine<Unit> { cont ->
                db.collection("emergencies").document(emergencyId)
                    .update(mapOf(
                        "status" to "Waiting",
                        "driverId" to null,
                        "assignedDriver" to null,
                        "driverName" to null,
                        "driverPhone" to null,
                        "updatedAt" to now
                    ))
                    .addOnCompleteListener { cont.resume(Unit) }
            }

            try {
                db.collection("drivers").document(driverId).update("availability", true)
                db.collection("ambulances").document(driverId).update(
                    mapOf("status" to "available", "isAvailable" to true, "updatedAt" to now)
                )
            } catch (_: Exception) {}

            Result.success(true)
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }

    suspend fun updateAmbulance(
        driverId: String,
        ambulanceData: Ambulance
    ): Result<Boolean> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val request = UpdateAmbulanceRequest(driverId, ambulanceData)
                apiService.updateAmbulance(token, request)
                return Result.success(true)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            val doc = hashMapOf<String, Any?>(
                "driverId" to driverId,
                "driverUid" to driverId,
                "driverName" to (ambulanceData.driverName ?: SessionManager.name ?: "Driver"),
                "driverPhone" to (ambulanceData.driverPhone ?: SessionManager.phone ?: ""),
                "status" to (ambulanceData.status ?: "available"),
                "isAvailable" to (ambulanceData.status == "available"),
                "latitude" to (ambulanceData.latitude ?: 0.0),
                "longitude" to (ambulanceData.longitude ?: 0.0),
                "updatedAt" to now,
                "lastUpdated" to now
            )

            suspendCoroutine<Unit> { cont ->
                db.collection("ambulances").document(driverId)
                    .set(doc, com.google.firebase.firestore.SetOptions.merge())
                    .addOnCompleteListener { cont.resume(Unit) }
            }

            Result.success(true)
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }

    suspend fun getAmbulanceProfile(driverId: String): Result<Ambulance> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val response = apiService.getAmbulanceProfile(token, driverId)
                return Result.success(response)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val docSnap = suspendCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { cont ->
                db.collection("ambulances").document(driverId).get()
                    .addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
            }

            if (docSnap != null && docSnap.exists()) {
                val d = docSnap.data ?: return Result.failure(Exception("Ambulance profile not found"))
                val amb = Ambulance(
                    id = docSnap.id,
                    driverId = d["driverId"] as? String ?: driverId,
                    driverName = d["driverName"] as? String ?: SessionManager.name ?: "Driver",
                    driverPhone = d["driverPhone"] as? String ?: SessionManager.phone ?: "",
                    status = d["status"] as? String ?: "available",
                    latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0
                )
                Result.success(amb)
            } else {
                val defaultAmb = Ambulance(
                    id = driverId,
                    driverId = driverId,
                    driverName = SessionManager.name ?: "Driver",
                    driverPhone = SessionManager.phone ?: "",
                    status = "available",
                    latitude = 12.9716,
                    longitude = 77.5946
                )
                updateAmbulance(driverId, defaultAmb)
                Result.success(defaultAmb)
            }
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }

    suspend fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        emergencyId: String? = null
    ): Result<Boolean> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val request = UpdateLocationRequest(
                    latitude = latitude,
                    longitude = longitude,
                    emergencyId = emergencyId
                )
                apiService.updateDriverLocation(token, driverId, request)
                return Result.success(true)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            db.collection("ambulances").document(driverId).update(
                mapOf("latitude" to latitude, "longitude" to longitude, "updatedAt" to now)
            )

            if (emergencyId != null) {
                db.collection("emergencies").document(emergencyId).update(
                    mapOf("driverLatitude" to latitude, "driverLongitude" to longitude, "updatedAt" to now)
                )
            }

            Result.success(true)
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }
}
