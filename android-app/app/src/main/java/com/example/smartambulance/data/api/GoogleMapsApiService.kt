package com.example.smartambulance.data.api

import com.example.smartambulance.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interfaces for Google Maps Platform APIs.
 * Used for Directions, Geocoding, and Places API calls from the Android app.
 *
 * The API key is injected via BuildConfig.MAPS_API_KEY (from gradle.properties).
 * ⚠️ NEVER hardcode API keys — always use BuildConfig fields.
 */

// ─────────────────────────────────────────────────────────────────────────────
// DIRECTIONS API
// ─────────────────────────────────────────────────────────────────────────────
interface GoogleDirectionsApiService {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin")       origin: String,
        @Query("destination")  destination: String,
        @Query("waypoints")    waypoints: String? = null,
        @Query("mode")         mode: String = "driving",
        @Query("departure_time") departureTime: String = "now",
        @Query("traffic_model")  trafficModel: String = "best_guess",
        @Query("alternatives")   alternatives: Boolean = false,
        @Query("units")          units: String = "metric",
        @Query("key")            key: String = BuildConfig.MAPS_API_KEY
    ): DirectionsResponse
}

// ─────────────────────────────────────────────────────────────────────────────
// GEOCODING API
// ─────────────────────────────────────────────────────────────────────────────
interface GoogleGeocodingApiService {
    @GET("maps/api/geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng")     latlng: String,
        @Query("result_type") resultType: String = "street_address|sublocality|locality",
        @Query("key")         key: String = BuildConfig.MAPS_API_KEY
    ): GeocodingResponse

    @GET("maps/api/geocode/json")
    suspend fun forwardGeocode(
        @Query("address") address: String,
        @Query("key")     key: String = BuildConfig.MAPS_API_KEY
    ): GeocodingResponse
}

// ─────────────────────────────────────────────────────────────────────────────
// PLACES API — Nearby Search
// ─────────────────────────────────────────────────────────────────────────────
interface GooglePlacesApiService {
    @GET("maps/api/place/nearbysearch/json")
    suspend fun nearbySearch(
        @Query("location") location: String,
        @Query("radius")   radius: Int = 5000,
        @Query("type")     type: String = "hospital",
        @Query("key")      key: String = BuildConfig.MAPS_API_KEY
    ): PlacesNearbyResponse

    @GET("maps/api/place/details/json")
    suspend fun getPlaceDetails(
        @Query("place_id") placeId: String,
        @Query("fields")   fields: String = "name,formatted_phone_number,opening_hours,rating,formatted_address,geometry",
        @Query("key")      key: String = BuildConfig.MAPS_API_KEY
    ): PlaceDetailsResponse

    @GET("maps/api/place/textsearch/json")
    suspend fun textSearch(
        @Query("query")    query: String,
        @Query("location") location: String? = null,
        @Query("radius")   radius: Int? = null,
        @Query("type")     type: String? = null,
        @Query("key")      key: String = BuildConfig.MAPS_API_KEY
    ): PlacesNearbyResponse

    @GET("maps/api/place/autocomplete/json")
    suspend fun autocomplete(
        @Query("input")    input: String,
        @Query("location") location: String? = null,
        @Query("radius")   radius: Int? = null,
        @Query("types")    types: String = "geocode|establishment",
        @Query("key")      key: String = BuildConfig.MAPS_API_KEY
    ): AutocompleteResponse
}

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

// Directions
data class DirectionsResponse(
    val status: String,
    val routes: List<DirectionsRoute> = emptyList(),
    val error_message: String? = null
)
data class DirectionsRoute(
    val overview_polyline: DirectionsPolyline,
    val legs: List<DirectionsLeg>,
    val summary: String = "",
    val warnings: List<String> = emptyList()
)
data class DirectionsPolyline(val points: String)
data class DirectionsLeg(
    val distance: DirectionsValue,
    val duration: DirectionsValue,
    val duration_in_traffic: DirectionsValue? = null,
    val steps: List<DirectionsStep>,
    val start_address: String = "",
    val end_address: String = ""
)
data class DirectionsStep(
    val distance: DirectionsValue,
    val duration: DirectionsValue,
    val html_instructions: String,
    val maneuver: String? = null
)
data class DirectionsValue(val text: String, val value: Int)

// Geocoding
data class GeocodingResponse(
    val status: String,
    val results: List<GeocodingResult> = emptyList()
)
data class GeocodingResult(
    val formatted_address: String,
    val geometry: GeocodingGeometry
)
data class GeocodingGeometry(val location: LatLngData)
data class LatLngData(val lat: Double, val lng: Double)

// Places Nearby
data class PlacesNearbyResponse(
    val status: String,
    val results: List<PlaceResult> = emptyList(),
    val next_page_token: String? = null
)
data class PlaceResult(
    val place_id: String,
    val name: String,
    val vicinity: String? = null,
    val formatted_address: String? = null,
    val geometry: PlaceGeometry,
    val rating: Float? = null,
    val user_ratings_total: Int? = null,
    val opening_hours: PlaceOpeningHours? = null,
    val types: List<String> = emptyList()
)
data class PlaceGeometry(val location: LatLngData)
data class PlaceOpeningHours(val open_now: Boolean? = null)

// Place Details
data class PlaceDetailsResponse(
    val status: String,
    val result: PlaceDetail? = null
)
data class PlaceDetail(
    val name: String,
    val formatted_phone_number: String? = null,
    val formatted_address: String? = null,
    val website: String? = null,
    val rating: Float? = null,
    val opening_hours: PlaceHours? = null,
    val geometry: PlaceGeometry? = null
)
data class PlaceHours(
    val open_now: Boolean? = null,
    val weekday_text: List<String> = emptyList()
)

// Autocomplete
data class AutocompleteResponse(
    val status: String,
    val predictions: List<AutocompletePrediction> = emptyList()
)
data class AutocompletePrediction(
    val place_id: String,
    val description: String,
    val structured_formatting: StructuredFormatting? = null
)
data class StructuredFormatting(
    val main_text: String,
    val secondary_text: String? = null
)
