package com.example.smartambulance.data

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "SmartAmbulanceSession"
    private const val KEY_TOKEN = "token"
    private const val KEY_UID = "uid"
    private const val KEY_ROLE = "role"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"
    private const val KEY_EMAIL = "email"
    private const val KEY_STATUS = "status"
    private const val KEY_APPROVED = "approved"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs?.getString(KEY_TOKEN, null)
        set(value) = prefs?.edit()?.putString(KEY_TOKEN, value)?.apply() ?: Unit

    var uid: String?
        get() = prefs?.getString(KEY_UID, null)
        set(value) = prefs?.edit()?.putString(KEY_UID, value)?.apply() ?: Unit

    var role: String?
        get() = prefs?.getString(KEY_ROLE, null)
        set(value) = prefs?.edit()?.putString(KEY_ROLE, value)?.apply() ?: Unit

    var name: String?
        get() = prefs?.getString(KEY_NAME, null)
        set(value) = prefs?.edit()?.putString(KEY_NAME, value)?.apply() ?: Unit

    var phone: String?
        get() = prefs?.getString(KEY_PHONE, null)
        set(value) = prefs?.edit()?.putString(KEY_PHONE, value)?.apply() ?: Unit

    var email: String?
        get() = prefs?.getString(KEY_EMAIL, null)
        set(value) = prefs?.edit()?.putString(KEY_EMAIL, value)?.apply() ?: Unit

    var status: String?
        get() = prefs?.getString(KEY_STATUS, null)
        set(value) = prefs?.edit()?.putString(KEY_STATUS, value)?.apply() ?: Unit

    var approved: Boolean
        get() = prefs?.getBoolean(KEY_APPROVED, false) ?: false
        set(value) = prefs?.edit()?.putBoolean(KEY_APPROVED, value)?.apply() ?: Unit

    val isUserLoggedIn: Boolean
        get() = !token.isNullOrBlank() && !uid.isNullOrBlank() && approved && status == "active"

    fun getFormattedToken(): String {
        return if (!token.isNullOrBlank()) "Bearer $token" else ""
    }

    fun clearSession() {
        prefs?.edit()?.clear()?.apply()
    }
}
