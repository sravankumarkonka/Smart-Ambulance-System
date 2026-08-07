package com.example.smartambulance.data.repository

import android.util.Log
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.api.RetrofitClient
import com.example.smartambulance.data.model.AdminStats
import com.example.smartambulance.data.model.Ambulance
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val TAG = "AdminRepository"
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

    suspend fun getStats(): Result<AdminStats> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val response = apiService.getStats(token)
                return Result.success(response)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        // Firestore direct fallback for System-Wide Metrics
        return try {
            val emSnap = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                db.collection("emergencies").get()
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
            }

            val ambSnap = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                db.collection("ambulances").get()
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
            }

            var totalCount = 0
            var activeCount = 0
            var criticalCount = 0
            var completedCount = 0

            emSnap.documents.forEach { doc ->
                totalCount++
                val status = (doc.getString("status") ?: "pending").lowercase()
                val severity = (doc.getString("severityLevel") ?: doc.getString("severity") ?: "medium").lowercase()

                if (status == "completed") {
                    completedCount++
                } else if (status != "cancelled") {
                    activeCount++
                }

                if (severity == "critical" && status != "completed" && status != "cancelled") {
                    criticalCount++
                }
            }

            var availableCount = 0
            var busyCount = 0

            ambSnap.documents.forEach { doc ->
                val status = (doc.getString("status") ?: "offline").lowercase()
                if (status == "available") {
                    availableCount++
                } else if (status == "busy") {
                    busyCount++
                }
            }

            val totalUnits = availableCount + busyCount
            val utilization = if (totalUnits > 0) ((busyCount.toDouble() / totalUnits) * 100).toInt() else 0

            val stats = AdminStats(
                activeCount = activeCount,
                criticalCount = criticalCount,
                availableCount = availableCount,
                busyCount = busyCount
            )

            Log.d(TAG, "✅ Calculated AdminStats from Firestore: $stats")
            Result.success(stats)
        } catch (fe: Exception) {
            Log.e(TAG, "Firestore getStats failed: ${fe.message}")
            Result.failure(fe)
        }
    }

    suspend fun getAllAmbulances(): Result<List<Ambulance>> {
        if (RetrofitClient.isHostConfirmed()) {
            try {
                val token = SessionManager.getFormattedToken()
                val response = apiService.getAllAmbulances(token)
                return Result.success(response)
            } catch (e: Exception) {
                if (!isNetworkError(e)) return Result.failure(e)
            }
        }

        return try {
            val snapshot = suspendCoroutine<com.google.firebase.firestore.QuerySnapshot> { cont ->
                db.collection("ambulances").get()
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { err -> cont.resumeWith(Result.failure(err)) }
            }

            val list = snapshot.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                Ambulance(
                    id = doc.id,
                    driverId = d["driverId"] as? String ?: doc.id,
                    driverName = d["driverName"] as? String ?: "Driver",
                    driverPhone = d["driverPhone"] as? String ?: "",
                    status = d["status"] as? String ?: "available",
                    latitude = (d["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (d["longitude"] as? Number)?.toDouble() ?: 0.0
                )
            }

            Result.success(list)
        } catch (fe: Exception) {
            Result.failure(fe)
        }
    }

    suspend fun getAvailableAmbulances(): Result<List<Ambulance>> {
        val result = getAllAmbulances()
        return if (result.isSuccess) {
            Result.success(result.getOrDefault(emptyList()).filter { it.status?.lowercase() == "available" })
        } else {
            result
        }
    }
}
