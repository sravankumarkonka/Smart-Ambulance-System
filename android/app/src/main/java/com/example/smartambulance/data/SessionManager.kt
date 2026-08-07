package com.example.smartambulance.data

object SessionManager {
    var token: String? = null
    var uid: String? = null
    var role: String? = null
    var name: String? = null
    var phone: String? = null
    var email: String? = null

    val isUserLoggedIn: Boolean
        get() = token != null && uid != null

    fun getFormattedToken(): String {
        return "Bearer ${token ?: ""}"
    }

    fun clearSession() {
        token = null
        uid = null
        role = null
        name = null
        phone = null
        email = null
    }
}
