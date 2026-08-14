package com.example.smartambulance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Emergency(
    val id: String? = null,
    val userId: String,
    val patientName: String,
    val emergencyType: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val severityLevel: String = "medium",
    val status: String = "pending",
    val createdAt: String? = null,
    val hospitalName: String? = null,
    val hospitalLatitude: Double? = null,
    val hospitalLongitude: Double? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverLatitude: Double? = null,
    val driverLongitude: Double? = null,
    val driverSpeed: Double? = null,
    val driverHeading: Double? = null,
    val assignedAt: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class CreateEmergencyRequest(
    val userId: String,
    val patientName: String,
    val emergencyType: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val severityLevel: String,
    val hospitalName: String? = null,
    val hospitalLatitude: Double? = null,
    val hospitalLongitude: Double? = null
)

@Serializable
data class CreateEmergencyResponse(
    val id: String
)

@Serializable
data class AssignDriverRequest(
    val driverId: String,
    val driverName: String,
    val driverPhone: String
)

@Serializable
data class UpdateStatusRequest(
    val status: String
)

@Serializable
data class UpdateLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val emergencyId: String? = null
)


@Serializable
data class RouteResponse(
    val distanceKm: Double? = null,
    val durationMinutes: Double? = null,
    val polyline: String? = null,
    val status: String? = null
)

fun isStatusActive(status: String?): Boolean {
    val s = (status ?: "").trim().lowercase()
    if (s.isBlank()) return false
    return when (s) {
        "completed", "cancelled", "canceled", "rejected", "closed" -> false
        else -> true
    }
}

fun isStatusHistory(status: String?): Boolean {
    val s = (status ?: "").trim().lowercase()
    if (s.isBlank()) return false
    return when (s) {
        "completed", "cancelled", "canceled", "rejected", "closed" -> true
        else -> false
    }
}

fun isStatusPending(status: String?): Boolean {
    val s = (status ?: "").trim().lowercase()
    return s == "pending" || s == "waiting" || s == "unassigned" || s == "new" || s == "requested" || s == "created"
}

fun isValidCoordinate(lat: Double?, lng: Double?): Boolean {
    if (lat == null || lng == null) return false
    if (lat == 0.0 && lng == 0.0) return false
    return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0
}


fun parseFirestoreTimestamp(raw: Any?): String? {
    if (raw == null) return null
    return when (raw) {
        is com.google.firebase.Timestamp -> {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(raw.toDate())
        }
        is String -> raw
        is Number -> {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date(raw.toLong()))
        }
        else -> null
    }
}

fun com.google.firebase.firestore.DocumentSnapshot.toEmergency(): Emergency? {
    if (!exists()) return null
    val d = data ?: return null
    val docId = id

    val userIdVal = getString("userId")
        ?: getString("patientUid")
        ?: ""

    val patientNameVal = getString("patientName")
        ?: getString("name")
        ?: "Emergency Patient"

    val emergencyTypeVal = getString("emergencyType")
        ?: getString("type")
        ?: "accident"

    val descriptionVal = getString("description") ?: ""

    val latitudeVal = getDouble("latitude")
        ?: (get("coordinates") as? Map<*, *>)?.get("latitude") as? Double
        ?: 0.0

    val longitudeVal = getDouble("longitude")
        ?: (get("coordinates") as? Map<*, *>)?.get("longitude") as? Double
        ?: 0.0

    val severityVal = getString("severityLevel")
        ?: getString("severity")
        ?: "medium"

    val statusVal = (getString("status") ?: "pending").trim().lowercase()

    val createdAtVal = parseFirestoreTimestamp(get("createdAt") ?: get("timestamp"))

    val hospitalNameVal = getString("hospitalName")
        ?: getString("hospital")

    val hospitalLatVal = getDouble("hospitalLatitude")
    val hospitalLngVal = getDouble("hospitalLongitude")

    val driverIdVal = getString("driverId")
        ?: getString("assignedDriver")

    val driverNameVal = getString("driverName")
    val driverPhoneVal = getString("driverPhone")
    val driverLatVal = getDouble("driverLatitude")
    val driverLngVal = getDouble("driverLongitude")
    val driverSpeedVal = getDouble("driverSpeed")
    val driverHeadingVal = getDouble("driverHeading")
    val assignedAtVal = parseFirestoreTimestamp(get("assignedAt"))
    val imageUrlVal = getString("imageUrl")
        ?: getString("accidentImage")

    return Emergency(
        id = docId,
        userId = userIdVal,
        patientName = patientNameVal,
        emergencyType = emergencyTypeVal,
        description = descriptionVal,
        latitude = latitudeVal,
        longitude = longitudeVal,
        severityLevel = severityVal,
        status = statusVal,
        createdAt = createdAtVal,
        hospitalName = hospitalNameVal,
        hospitalLatitude = hospitalLatVal,
        hospitalLongitude = hospitalLngVal,
        driverId = driverIdVal,
        driverName = driverNameVal,
        driverPhone = driverPhoneVal,
        driverLatitude = driverLatVal,
        driverLongitude = driverLngVal,
        driverSpeed = driverSpeedVal,
        driverHeading = driverHeadingVal,
        assignedAt = assignedAtVal,
        imageUrl = imageUrlVal
    )
}

