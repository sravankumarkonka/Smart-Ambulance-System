package com.example.smartambulance.data.repository

import android.util.Log
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.model.AuthResponse
import com.example.smartambulance.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService
) {

    private val TAG = "AuthRepository"
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun parseErrorMessage(e: Exception): String {
        if (e is retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(errorBody)
                    if (json.has("error")) {
                        val err = json.getString("error")
                        if (err.isNotBlank()) return err
                    }
                } catch (_: Exception) {}
            }
        }
        val msg = e.message ?: "An unexpected error occurred."
        return when {
            msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("INVALID_PASSWORD") ->
                "Invalid email or password. Please try again."
            msg.contains("EMAIL_NOT_FOUND") -> "No account found with this email."
            msg.contains("USER_DISABLED") -> "This account has been disabled."
            msg.contains("TOO_MANY_REQUESTS") -> "Too many attempts. Please wait and try again."
            else -> msg
        }
    }

    /**
     * LOGIN — Authenticates via Firebase Auth, fetches Firestore profile, checks approval+active status.
     */
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            Log.d(TAG, "Attempting Firebase Auth login for: $email")

            // 1. Sign in via Firebase Auth SDK
            val authResult = suspendCoroutine<com.google.firebase.auth.AuthResult> { continuation ->
                firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            continuation.resume(task.result!!)
                        } else {
                            continuation.resumeWith(
                                kotlin.Result.failure(task.exception ?: Exception("Login failed"))
                            )
                        }
                    }
            }

            val firebaseUser = authResult.user ?: throw Exception("Login failed: no user returned")
            val uid = firebaseUser.uid

            // 2. Fetch user profile from Firestore
            val docSnap = suspendCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { cont ->
                db.collection("users").document(uid).get()
                    .addOnCompleteListener { task ->
                        cont.resume(if (task.isSuccessful) task.result else null)
                    }
            }

            if (docSnap == null || !docSnap.exists()) {
                firebaseAuth.signOut()
                throw Exception("User profile not found in database. Please register first.")
            }

            val role = docSnap.getString("role") ?: "user"
            val status = docSnap.getString("status") ?: "pending"
            val approved = docSnap.getBoolean("approved") ?: false
            val name = docSnap.getString("name") ?: docSnap.getString("displayName") ?: (firebaseUser.displayName ?: "User")
            val phone = docSnap.getString("phone") ?: (firebaseUser.phoneNumber ?: "")

            // 3. Gate check: Must be approved and active
            if (!approved || status != "active") {
                firebaseAuth.signOut()
                val statusMsg = when (status) {
                    "pending" -> if (role == "admin") "Your admin account is pending Super Admin approval." else "Your driver account is pending Admin approval."
                    "rejected" -> "Your account request was rejected."
                    "suspended" -> "Your account has been suspended."
                    else -> "Your account is not active."
                }
                throw Exception(statusMsg)
            }

            // 4. Get Firebase ID Token
            val idToken = suspendCoroutine<String> { continuation ->
                firebaseUser.getIdToken(true).addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result?.token != null) {
                        continuation.resume(task.result!!.token!!)
                    } else {
                        continuation.resume("firebase-token-$uid")
                    }
                }
            }

            // 5. Update lastLogin in Firestore
            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
            docSnap.reference.update(mapOf("updatedAt" to now))

            val userProfile = User(name = name, email = email.trim(), phone = phone, role = role)

            SessionManager.token = idToken
            SessionManager.uid = uid
            SessionManager.role = role
            SessionManager.name = name
            SessionManager.phone = phone
            SessionManager.email = email.trim()

            Log.d(TAG, "✅ Login successful. Role: $role, UID: $uid")
            Result.success(AuthResponse(uid = uid, idToken = idToken, customToken = null, profile = userProfile))

        } catch (e: Exception) {
            Log.e(TAG, "Login failed: ${e.message}")
            Result.failure(Exception(parseErrorMessage(e)))
        }
    }

    /**
     * REGISTER — Creates Firebase Auth account + Firestore user doc with status/approval workflows.
     */
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String
    ): Result<AuthResponse> {
        return try {
            Log.d(TAG, "Attempting registration for: $email (role=$role)")

            // 1. Create Firebase Auth account
            val authResult = suspendCoroutine<com.google.firebase.auth.AuthResult> { continuation ->
                firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            continuation.resume(task.result!!)
                        } else {
                            continuation.resumeWith(
                                kotlin.Result.failure(task.exception ?: Exception("Registration failed"))
                            )
                        }
                    }
            }

            val firebaseUser = authResult.user ?: throw Exception("Registration failed: no user returned")
            val uid = firebaseUser.uid

            // Update Auth Display Name
            val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            suspendCoroutine<Unit> { cont ->
                firebaseUser.updateProfile(profileUpdate).addOnCompleteListener { cont.resume(Unit) }
            }

            val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())

            val actualRole = if (role == "driver") "driver" else if (role == "admin") "admin" else "user"
            val isUserRole = actualRole == "user"
            val status = if (isUserRole) "active" else "pending"
            val approved = isUserRole

            val userDoc = hashMapOf(
                "uid" to uid,
                "name" to name.trim(),
                "displayName" to name.trim(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "photoURL" to "",
                "role" to actualRole,
                "status" to status,
                "approved" to approved,
                "createdAt" to now,
                "updatedAt" to now
            )

            // Write user profile to Firestore
            suspendCoroutine<Unit> { cont ->
                db.collection("users").document(uid).set(userDoc)
                    .addOnCompleteListener { cont.resume(Unit) }
            }

            // Driver-specific document setup
            if (actualRole == "driver") {
                val driverDoc = hashMapOf(
                    "uid" to uid,
                    "name" to name.trim(),
                    "email" to email.trim(),
                    "phone" to phone.trim(),
                    "availability" to false,
                    "createdAt" to now
                )
                db.collection("drivers").document(uid).set(driverDoc)

                db.collection("ambulances").document(uid).set(
                    hashMapOf(
                        "driverId" to uid,
                        "driverUid" to uid,
                        "driverName" to name.trim(),
                        "driverPhone" to phone.trim(),
                        "latitude" to 0.0,
                        "longitude" to 0.0,
                        "heading" to 0.0,
                        "speed" to 0.0,
                        "status" to "unavailable",
                        "isAvailable" to false,
                        "updatedAt" to now,
                        "lastUpdated" to now
                    )
                )

                // Notification for Admin
                val notificationDoc = hashMapOf(
                    "type" to "driver_registration",
                    "targetRole" to "admin",
                    "senderUid" to uid,
                    "senderName" to name.trim(),
                    "senderEmail" to email.trim(),
                    "message" to "New driver ${name.trim()} registered and needs approval.",
                    "read" to false,
                    "createdAt" to now
                )
                db.collection("notifications").add(notificationDoc)
            }

            if (actualRole == "admin") {
                // Notification for Super Admin
                val notificationDoc = hashMapOf(
                    "type" to "admin_registration",
                    "targetRole" to "super_admin",
                    "senderUid" to uid,
                    "senderName" to name.trim(),
                    "senderEmail" to email.trim(),
                    "message" to "New admin ${name.trim()} registered and needs Super Admin approval.",
                    "read" to false,
                    "createdAt" to now
                )
                db.collection("notifications").add(notificationDoc)
            }

            // Write audit log
            db.collection("audit_logs").add(
                hashMapOf(
                    "action" to "registration",
                    "performedBy" to uid,
                    "targetUid" to uid,
                    "details" to hashMapOf("role" to actualRole, "email" to email.trim()),
                    "createdAt" to now
                )
            )

            // If driver or admin, sign out so they can't immediately navigate
            if (!isUserRole) {
                firebaseAuth.signOut()
                val msg = if (actualRole == "driver") "Registration successful! Awaiting Admin approval." else "Registration successful! Awaiting Super Admin approval."
                throw Exception(msg)
            }

            // Get ID token for user auto-login
            val idToken = suspendCoroutine<String> { continuation ->
                firebaseUser.getIdToken(true).addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result?.token != null) {
                        continuation.resume(task.result!!.token!!)
                    } else {
                        continuation.resume("firebase-token-$uid")
                    }
                }
            }

            val userProfile = User(name = name.trim(), email = email.trim(), phone = phone.trim(), role = actualRole)

            SessionManager.token = idToken
            SessionManager.uid = uid
            SessionManager.role = actualRole
            SessionManager.name = name.trim()
            SessionManager.phone = phone.trim()
            SessionManager.email = email.trim()

            Result.success(AuthResponse(uid = uid, idToken = idToken, customToken = null, profile = userProfile))

        } catch (e: Exception) {
            Log.e(TAG, "Registration failed: ${e.message}")
            Result.failure(Exception(parseErrorMessage(e)))
        }
    }
}
