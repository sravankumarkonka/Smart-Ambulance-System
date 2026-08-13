package com.example.smartambulance.data.repository

import android.util.Log
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.api.RetrofitClient
import com.example.smartambulance.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

@Singleton
class EmergencyRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val TAG = "EmergencyRepository"
    private val db = FirebaseFirestore.getInstance()

    private fun parseErrorMessage(e: Exception): String {
        if (e is retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(errorBody)
                    if (json.has("details")) {
                        val details = json.getJSONArray("details")
                        if (details.length() > 0) {
                            val firstDetail = details.getJSONObject(0)
                            if (firstDetail.has("message")) return firstDetail.getString("message")
                        }
                    }
                    if (json.has("error")) {
                        val err = json.getString("error")
                        if (err.isNotBlank() && err != "Validation failed.") return err
                    }
                } catch (_: Exception) {}
            }
        }
        return e.message ?: "An unexpected error occurred."
    }

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

    /**
     * Creates an emergency — tries backend first, falls back to direct Firestore write.
     */
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
        val token = SessionManager.getFormattedToken()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: SessionManager.uid
            ?: return Result.failure(Exception("You are not logged in. Please log in and try again."))

        // Try backend first — but skip if we already know host is unreachable
        if (RetrofitClient.isHostConfirmed()) {
            try {
                Log.d(TAG, "Attempting backend emergency creation...")
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
                Log.d(TAG, "✅ Emergency created via backend: ${response.id}")
                return Result.success(response)
            } catch (e: Exception) {
                if (!isNetworkError(e)) {
                    Log.e(TAG, "Backend emergency creation failed (non-network): ${e.message}")
                    return Result.failure(Exception(parseErrorMessage(e)))
                }
                Log.w(TAG, "Backend unreachable (${e.message}). Using Firestore direct write fallback.")
            }
        } else {
            Log.d(TAG, "Backend host not confirmed — skipping to Firestore direct write")
        }

        // Firestore direct write fallback
        return try {
            val now = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
            ).format(java.util.Date())

            val docRef = db.collection("emergencies").document()
            val requestId = docRef.id

            val severityLevelMap = mapOf("low" to 1, "medium" to 2, "high" to 3, "critical" to 4)

            val emergencyData = hashMapOf(
                "requestId" to requestId,
                "patientUid" to userId,
                "userId" to userId,
                "userEmail" to (SessionManager.email ?: ""),
                "patientName" to patientName.trim(),
                "phone" to (SessionManager.phone ?: ""),
                "emergencyType" to emergencyType,
                "description" to description.trim(),
                "severity" to severityLevel,
                "severityLevel" to severityLevel,
                "severityScore" to (severityLevelMap[severityLevel] ?: 2),
                "latitude" to latitude,
                "longitude" to longitude,
                "status" to "Waiting",
                "assignedDriver" to null,
                "driverId" to null,
                "driverName" to null,
                "driverPhone" to null,
                "hospital" to hospitalName,
                "hospitalName" to hospitalName,
                "hospitalLatitude" to hospitalLatitude,
                "hospitalLongitude" to hospitalLongitude,
                "accidentImage" to null,
                "imageUrl" to null,
                "timestamp" to now,
                "createdAt" to now,
                "updatedAt" to now
            )

            suspendCoroutine<Unit> { cont ->
                docRef.set(emergencyData).addOnCompleteListener { cont.resume(Unit) }
            }

            Log.d(TAG, "✅ Emergency created via Firestore direct write: $requestId")
            Result.success(CreateEmergencyResponse(id = requestId))
        } catch (fe: Exception) {
            Log.e(TAG, "Firestore direct write also failed: ${fe.message}")
            Result.failure(Exception("Failed to submit emergency: ${fe.message}. Please check your internet connection."))
        }
    }

    suspend fun getEmergencyById(id: String): Result<Emergency> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getEmergencyById(token, id)
            Result.success(response)
        } catch (e: Exception) {
            // Firestore fallback
            try {
                val docSnap = suspendCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { cont ->
                    db.collection("emergencies").document(id).get()
                        .addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
                }
                if (docSnap != null && docSnap.exists()) {
                    val emergency = docSnap.toEmergency() ?: return Result.failure(Exception("Emergency not found"))
                    Result.success(emergency)
                } else {
                    Result.failure(Exception("Emergency not found"))
                }
            } catch (fe: Exception) {
                Result.failure(fe)
            }
        }
    }

    fun listenToPatientEmergencies(
        userId: String,
        onSuccess: (List<Emergency>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        val userEmail = SessionManager.email ?: ""
        return db.collection("emergencies")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in patient emergencies listener: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        val docUserId = doc.getString("userId") ?: doc.getString("patientUid") ?: ""
                        val docEmail = doc.getString("userEmail") ?: ""
                        if (docUserId == userId || (userEmail.isNotBlank() && docEmail == userEmail)) {
                            doc.toEmergency()
                        } else {
                            null
                        }
                    }.sortedByDescending { it.createdAt ?: "" }
                    Log.d(TAG, "Patient real-time snapshot updated: ${list.size} emergencies found for user $userId")
                    onSuccess(list)
                }
            }
    }


    suspend fun getEmergencyHistory(userId: String): Result<List<Emergency>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.getEmergencyHistory(token, userId)
            Result.success(response)
        } catch (e: Exception) {
            Log.w(TAG, "Backend getEmergencyHistory failed (${e.message}). Falling back to Firestore.")
            try {
                // Query by userId
                val snapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                    db.collection("emergencies")
                        .whereEqualTo("userId", userId)
                        .get()
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Emergency(
                        id = doc.id,
                        userId = d["userId"] as? String ?: userId,
                        patientName = d["patientName"] as? String ?: "Patient",
                        emergencyType = d["emergencyType"] as? String ?: "general",
                        description = d["description"] as? String ?: "",
                        latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                        severityLevel = d["severityLevel"] as? String ?: d["severity"] as? String ?: "medium",
                        status = d["status"] as? String ?: "pending",
                        createdAt = d["createdAt"] as? String,
                        hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                        driverId = d["driverId"] as? String,
                        driverName = d["driverName"] as? String,
                        driverPhone = d["driverPhone"] as? String,
                        imageUrl = d["imageUrl"] as? String
                    )
                }.toMutableList()

                // Also query by patientUid to catch emergencies created with different field name
                try {
                    val patientUidSnapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                        db.collection("emergencies")
                            .whereEqualTo("patientUid", userId)
                            .get()
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
                    }
                    patientUidSnapshot.documents.forEach { doc ->
                        if (list.none { it.id == doc.id }) {
                            val d = doc.data ?: return@forEach
                            list.add(
                                Emergency(
                                    id = doc.id,
                                    userId = d["userId"] as? String ?: userId,
                                    patientName = d["patientName"] as? String ?: "Patient",
                                    emergencyType = d["emergencyType"] as? String ?: "general",
                                    description = d["description"] as? String ?: "",
                                    latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                                    longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                                    severityLevel = d["severityLevel"] as? String ?: d["severity"] as? String ?: "medium",
                                    status = d["status"] as? String ?: "pending",
                                    createdAt = d["createdAt"] as? String,
                                    hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                                    driverId = d["driverId"] as? String,
                                    driverName = d["driverName"] as? String,
                                    driverPhone = d["driverPhone"] as? String,
                                    imageUrl = d["imageUrl"] as? String
                                )
                            )
                        }
                    }
                } catch (patientErr: Exception) {
                    Log.w(TAG, "patientUid query failed (${patientErr.message}), using userId results only")
                }

                // Also query by userEmail if available
                val userEmail = SessionManager.email
                if (!userEmail.isNullOrBlank()) {
                    try {
                        val emailSnapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                            db.collection("emergencies")
                                .whereEqualTo("userEmail", userEmail)
                                .get()
                                .addOnSuccessListener { cont.resume(it) }
                                .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
                        }
                        emailSnapshot.documents.forEach { doc ->
                            if (list.none { it.id == doc.id }) {
                                val d = doc.data ?: return@forEach
                                list.add(
                                    Emergency(
                                        id = doc.id,
                                        userId = d["userId"] as? String ?: userId,
                                        patientName = d["patientName"] as? String ?: "Patient",
                                        emergencyType = d["emergencyType"] as? String ?: "general",
                                        description = d["description"] as? String ?: "",
                                        latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                                        longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0,
                                        severityLevel = d["severityLevel"] as? String ?: d["severity"] as? String ?: "medium",
                                        status = d["status"] as? String ?: "pending",
                                        createdAt = d["createdAt"] as? String,
                                        hospitalName = d["hospitalName"] as? String ?: d["hospital"] as? String,
                                        driverId = d["driverId"] as? String,
                                        driverName = d["driverName"] as? String,
                                        driverPhone = d["driverPhone"] as? String,
                                        imageUrl = d["imageUrl"] as? String
                                    )
                                )
                            }
                        }
                    } catch (emailErr: Exception) {
                        Log.w(TAG, "userEmail query failed (${emailErr.message})")
                    }
                }

                list.sortByDescending { it.createdAt ?: "" }
                Result.success(list.toList())
            } catch (fe: Exception) {
                Result.failure(fe)
            }
        }
    }

    suspend fun cancelEmergency(id: String): Result<Boolean> {
        return try {
            val token = SessionManager.getFormattedToken()
            apiService.cancelEmergency(token, id)
            Result.success(true)
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                // Firestore direct update fallback
                try {
                    suspendCoroutine<Unit> { cont ->
                        db.collection("emergencies").document(id)
                            .update("status", "cancelled")
                            .addOnCompleteListener { cont.resume(Unit) }
                    }
                    Result.success(true)
                } catch (fe: Exception) {
                    Result.failure(fe)
                }
            } else {
                Result.failure(e)
            }
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

    /**
     * Uploads an emergency evidence image to the backend.
     */
    suspend fun uploadEmergencyImage(
        id: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val requestFile = okhttp3.RequestBody.create(
                mimeType.toMediaTypeOrNull(),
                imageBytes
            )
            val body = okhttp3.MultipartBody.Part.createFormData("image", "emergency_$id.jpg", requestFile)
            
            val response = apiService.uploadEmergencyImage(token, id, body)
            val imageUrl = response["imageUrl"] ?: ""
            
            // Also update Firestore with the image URL if available
            if (imageUrl.isNotBlank()) {
                try {
                    db.collection("emergencies").document(id).update("imageUrl", imageUrl)
                } catch (_: Exception) {}
            }
            
            Result.success(imageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Image upload failed: ${e.message}")
            // Even if upload fails, we return a success with empty string to not block the flow
            // as it's an optional feature.
            Result.success("")
        }
    }
}

