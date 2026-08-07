package com.example.smartambulance.data.repository

import com.example.smartambulance.BuildConfig
import com.example.smartambulance.data.api.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.*

/**
 * Repository for Google Maps Platform API calls.
 * 
 * Provides:
 *  - Route calculation via Directions API (traffic-aware ETA)
 *  - Reverse/forward geocoding
 *  - Nearby hospital/place search via Places API
 *  - Place detail lookup
 * 
 * API key is read from BuildConfig.MAPS_API_KEY (set in gradle.properties).
 */
class GoogleMapsRepository {

    private val gmapsRetrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    private val directionsService: GoogleDirectionsApiService by lazy {
        gmapsRetrofit.create(GoogleDirectionsApiService::class.java)
    }

    private val geocodingService: GoogleGeocodingApiService by lazy {
        gmapsRetrofit.create(GoogleGeocodingApiService::class.java)
    }

    private val placesService: GooglePlacesApiService by lazy {
        gmapsRetrofit.create(GooglePlacesApiService::class.java)
    }

    // ─── Directions API ────────────────────────────────────────────────────

    /**
     * Fetches a route from origin to destination.
     * Returns decoded polyline points and ETA.
     * @param origin [lat, lng]
     * @param destination [lat, lng]
     */
    suspend fun getRoute(origin: Pair<Double, Double>, destination: Pair<Double, Double>): RouteResult? {
        return try {
            val originStr = "${origin.first},${origin.second}"
            val destStr = "${destination.first},${destination.second}"
            val response = directionsService.getDirections(origin = originStr, destination = destStr)

            if (response.status != "OK" || response.routes.isEmpty()) {
                android.util.Log.w("GoogleMapsRepo", "Directions API: ${response.status} ${response.error_message}")
                return null
            }

            val route = response.routes[0]
            val leg = route.legs[0]
            val durationSec = leg.duration_in_traffic?.value ?: leg.duration.value
            val distanceM = leg.distance.value

            val polylinePoints = decodePolyline(route.overview_polyline.points)
            val steps = leg.steps.map { step ->
                RouteStep(
                    instruction = step.html_instructions.replace(Regex("<[^>]*>"), "").trim(),
                    distanceText = step.distance.text,
                    durationText = step.duration.text,
                    distanceM = step.distance.value,
                    durationSec = step.duration.value
                )
            }

            RouteResult(
                polylinePoints = polylinePoints,
                distanceMeters = distanceM,
                durationSeconds = durationSec,
                distanceText = leg.distance.text,
                durationText = leg.duration.text,
                trafficDurationText = leg.duration_in_traffic?.text,
                summary = route.summary,
                steps = steps,
                warnings = route.warnings
            )
        } catch (e: Exception) {
            android.util.Log.e("GoogleMapsRepo", "Directions API error: ${e.message}")
            null
        }
    }

    // ─── Geocoding API ─────────────────────────────────────────────────────

    /**
     * Reverse geocodes coordinates to a human-readable address.
     * Returns the formatted address string, or null on failure.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            val response = geocodingService.reverseGeocode(latlng = "$lat,$lng")
            if (response.status == "OK" && response.results.isNotEmpty()) {
                response.results[0].formatted_address
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleMapsRepo", "Reverse geocode error: ${e.message}")
            null
        }
    }

    /**
     * Forward geocodes an address string to coordinates.
     */
    suspend fun forwardGeocode(address: String): Pair<Double, Double>? {
        return try {
            val response = geocodingService.forwardGeocode(address = address)
            if (response.status == "OK" && response.results.isNotEmpty()) {
                val loc = response.results[0].geometry.location
                Pair(loc.lat, loc.lng)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleMapsRepo", "Forward geocode error: ${e.message}")
            null
        }
    }

    // ─── Places API ────────────────────────────────────────────────────────

    /**
     * Searches for nearby hospitals (or another type of place).
     * @param lat latitude
     * @param lng longitude
     * @param type Google Places type string (default: "hospital")
     * @param radiusMeters search radius in meters (max 50000)
     */
    suspend fun searchNearbyHospitals(
        lat: Double, lng: Double,
        type: String = "hospital",
        radiusMeters: Int = 5000
    ): List<NearbyPlace> {
        return try {
            val response = placesService.nearbySearch(
                location = "$lat,$lng",
                radius = radiusMeters,
                type = type
            )

            if (response.status != "OK" && response.status != "ZERO_RESULTS") {
                android.util.Log.w("GoogleMapsRepo", "Places nearby: ${response.status}")
                return emptyList()
            }

            response.results.map { place ->
                NearbyPlace(
                    placeId = place.place_id,
                    name = place.name,
                    vicinity = place.vicinity ?: place.formatted_address ?: "",
                    lat = place.geometry.location.lat,
                    lng = place.geometry.location.lng,
                    rating = place.rating,
                    userRatingsTotal = place.user_ratings_total ?: 0,
                    isOpenNow = place.opening_hours?.open_now,
                    distanceKm = haversineDistance(lat, lng, place.geometry.location.lat, place.geometry.location.lng)
                )
            }.sortedBy { it.distanceKm }
        } catch (e: Exception) {
            android.util.Log.e("GoogleMapsRepo", "Places nearby error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets place details (phone, hours, rating, address) by placeId.
     */
    suspend fun getPlaceDetails(placeId: String): PlaceDetails? {
        return try {
            val response = placesService.getPlaceDetails(placeId = placeId)
            if (response.status != "OK" || response.result == null) return null

            val r = response.result
            PlaceDetails(
                name = r.name,
                phone = r.formatted_phone_number,
                address = r.formatted_address,
                website = r.website,
                rating = r.rating,
                isOpenNow = r.opening_hours?.open_now,
                weekdayText = r.opening_hours?.weekday_text ?: emptyList(),
                lat = r.geometry?.location?.lat,
                lng = r.geometry?.location?.lng
            )
        } catch (e: Exception) {
            android.util.Log.e("GoogleMapsRepo", "Place details error: ${e.message}")
            null
        }
    }

    // ─── Polyline Decoder ──────────────────────────────────────────────────

    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            points.add(Pair(lat / 1e5, lng / 1e5))
        }
        return points
    }

    // ─── Distance Helper ───────────────────────────────────────────────────

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

// ─── Domain Models ─────────────────────────────────────────────────────────

data class RouteResult(
    val polylinePoints: List<Pair<Double, Double>>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val distanceText: String,
    val durationText: String,
    val trafficDurationText: String?,
    val summary: String,
    val steps: List<RouteStep>,
    val warnings: List<String>
)

data class RouteStep(
    val instruction: String,
    val distanceText: String,
    val durationText: String,
    val distanceM: Int,
    val durationSec: Int
)

data class NearbyPlace(
    val placeId: String,
    val name: String,
    val vicinity: String,
    val lat: Double,
    val lng: Double,
    val rating: Float?,
    val userRatingsTotal: Int,
    val isOpenNow: Boolean?,
    val distanceKm: Double
)

data class PlaceDetails(
    val name: String,
    val phone: String?,
    val address: String?,
    val website: String?,
    val rating: Float?,
    val isOpenNow: Boolean?,
    val weekdayText: List<String>,
    val lat: Double?,
    val lng: Double?
)
