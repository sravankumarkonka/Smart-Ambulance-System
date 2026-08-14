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

        // Firestore direct fallback with Transaction
        return try {
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            val docRef = db.collection("emergencies").document(emergencyId)

            suspendCoroutine<Unit> { cont ->
                db.runTransaction { transaction ->
                    val snap = transaction.get(docRef)
                    if (!snap.exists()) {
                        throw Exception("Emergency request no longer exists")
                    }
                    val existingDriver = snap.getString("driverId") ?: snap.getString("assignedDriver")
                    if (!existingDriver.isNullOrBlank() && existingDriver != "null" && existingDriver != driverId) {
                        throw Exception("This emergency has already been accepted by another driver.")
                    }

                    transaction.update(
                        docRef,
                        mapOf(
                            "status" to "accepted",
                            "driverId" to driverId,
                            "assignedDriver" to driverId,
                            "driverName" to driverName,
                            "driverPhone" to driverPhone,
                            "assignedAt" to now,
                            "updatedAt" to now
                        )
                    )
                }.addOnSuccessListener {
                    cont.resume(Unit)
                }.addOnFailureListener { err ->
                    cont.resumeWith(kotlin.Result.failure(err))
                }
            }

            try {
                db.collection("drivers").document(driverId).update("availability", false)
                db.collection("ambulances").document(driverId).update(
                    mapOf("status" to "busy", "isAvailable" to false, "updatedAt" to now)
                )
            } catch (_: Exception) {}

            Log.d(TAG, "✅ Emergency assigned via Firestore transaction: $emergencyId to $driverId")
            Result.success(true)
        } catch (fe: Exception) {
            Log.e(TAG, "Firestore assignDriver failed: ${fe.message}")
            Result.failure(Exception(fe.message ?: "Failed to accept emergency assignment"))
        }
    }

    suspend fun rejectEmergency(
        emergencyId: String,
        driverId: String
    ): Result<Boolean> {
        return try {
            suspendCoroutine<Unit> { cont ->
                db.collection("emergencies").document(emergencyId)
                    .update("rejectedDrivers", com.google.firebase.firestore.FieldValue.arrayUnion(driverId))
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) cont.resume(Unit)
                        else cont.resumeWith(kotlin.Result.failure(task.exception ?: Exception("Reject failed")))
                    }
            }
            Log.d(TAG, "✅ Emergency $emergencyId rejected by driver $driverId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject emergency: ${e.message}")
            Result.failure(e)
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
                        "status" to "waiting",
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

    suspend fun getDriverHistory(driverId: String): Result<List<Emergency>> {
        return try {
            val list = suspendCoroutine<List<Emergency>> { cont ->
                db.collection("emergencies").get().addOnCompleteListener { task ->
                    if (!task.isSuccessful || task.result == null) {
                        cont.resume(emptyList())
                        return@addOnCompleteListener
                    }
                    val items = task.result.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val currentDriverId = d["driverId"] as? String
                        val assignedDriverId = d["assignedDriver"] as? String
                        if (currentDriverId != driverId && assignedDriverId != driverId) return@mapNotNull null

                        val createdAtRaw = d["createdAt"] ?: d["timestamp"]
                        val createdAtStr = when (createdAtRaw) {
                            is com.google.firebase.Timestamp -> createdAtRaw.toDate().toString()
                            is String -> createdAtRaw
                            else -> null
                        }

                        Emergency(
                            id = doc.id,
                            userId = d["userId"] as? String ?: "",
                            patientName = d["patientName"] as? String ?: "Patient",
                            emergencyType = d["emergencyType"] as? String ?: "general",
                            description = d["description"] as? String ?: "",
                            latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                            severityLevel = d["severityLevel"] as? String ?: d["severity"] as? String ?: "medium",
                            status = d["status"] as? String ?: "pending",
                            hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                            createdAt = createdAtStr
                        )
                    }
                    cont.resume(items.sortedByDescending { it.createdAt ?: "" })
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
