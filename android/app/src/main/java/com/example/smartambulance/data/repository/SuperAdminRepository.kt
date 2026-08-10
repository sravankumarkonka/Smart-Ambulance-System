package com.example.smartambulance.data.repository

import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.AuditLog
import com.example.smartambulance.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuperAdminRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getPendingAdmins(): Result<List<User>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val list = apiService.getPendingAdmins(token)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveAdmin(uid: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.approveAdmin(token, uid)
            Result.success(response["message"] ?: "Admin approved successfully.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectAdmin(uid: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = apiService.rejectAdmin(token, uid)
            Result.success(response["message"] ?: "Admin rejected.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun suspendUser(uid: String, role: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = if (role == "driver") {
                apiService.suspendDriver(token, uid)
            } else {
                apiService.suspendAdmin(token, uid)
            }
            Result.success(response["message"] ?: "User suspended.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun activateUser(uid: String, role: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = if (role == "driver") {
                apiService.approveDriver(token, uid)
            } else {
                apiService.approveAdmin(token, uid)
            }
            Result.success(response["message"] ?: "User activated.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(uid: String, role: String): Result<String> {
        return try {
            val token = SessionManager.getFormattedToken()
            val response = if (role == "driver") {
                apiService.deleteDriver(token, uid)
            } else {
                apiService.deleteAdmin(token, uid)
            }
            Result.success(response["message"] ?: "User deleted.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val list = apiService.getAllUsers(token)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAuditLogs(): Result<List<AuditLog>> {
        return try {
            val token = SessionManager.getFormattedToken()
            val list = apiService.getAuditLogs(token)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
