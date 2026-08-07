package com.example.smartambulance.data.api

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val TAG = "RetrofitClient"
    private const val PORT = 5000

    // Cooldown to prevent redundant re-probing (60 seconds)
    @Volatile
    private var lastProbeTimeMs: Long = 0L
    private const val PROBE_COOLDOWN_MS = 60_000L

    /** Whether the initial probe has found a working host. */
    @Volatile
    private var hostConfirmed = false

    /**
     * Your PC's actual Wi-Fi IP (10.185.251.48) is placed FIRST so physical devices
     * connect immediately without cycling through emulator addresses.
     * 10.0.2.2 is the Android emulator loopback — only valid in emulators.
     */
    @Volatile
    private var currentHost: String = "10.185.251.48"

    private val candidateHosts = listOf(
        "10.185.251.48",  // PC Wi-Fi — Primary for physical device
        "192.168.1.5",    // Alternative LAN IP
        "192.168.0.105",  // Alternative LAN IP
        "10.0.0.2",       // Hotspot fallback
        "10.0.2.2",       // Android Emulator loopback (last resort)
        "127.0.0.1"       // ADB reverse tunnel (adb reverse tcp:5000 tcp:5000)
    )

    /** Returns true if running inside an Android Emulator. */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("google/sdk_gphone")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.lowercase().contains("emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"))
    }

    fun getBaseUrl(): String {
        val host = if (isEmulator()) "10.0.2.2" else currentHost
        return "http://$host:$PORT/"
    }

    fun setPhysicalIp(ip: String) {
        if (ip.isNotBlank()) {
            currentHost = ip.trim()
            rebuildApiService()
            Log.d(TAG, "Backend host manually set to: $ip")
        }
    }

    /** Returns true if the backend host has been confirmed reachable. */
    fun isHostConfirmed(): Boolean = hostConfirmed

    /**
     * Probes all candidate hosts asynchronously and switches to the first one that responds.
     * Called once at app startup so subsequent requests always go to the live host.
     */
    fun probeAndSelectBestHost() {
        if (isEmulator()) {
            currentHost = "10.0.2.2"
            hostConfirmed = true
            rebuildApiService()
            return
        }

        // Skip if we probed recently
        val now = System.currentTimeMillis()
        if (now - lastProbeTimeMs < PROBE_COOLDOWN_MS && hostConfirmed) {
            Log.d(TAG, "Skipping probe — cooldown active, host=$currentHost")
            return
        }
        lastProbeTimeMs = now

        CoroutineScope(Dispatchers.IO).launch {
            for (candidate in candidateHosts) {
                try {
                    val probeClient = OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url("http://$candidate:$PORT/health")
                        .get()
                        .build()
                    val response = probeClient.newCall(req).execute()
                    if (response.isSuccessful || response.code == 404) {
                        // Any HTTP response means the server is alive
                        currentHost = candidate
                        hostConfirmed = true
                        rebuildApiService()
                        Log.d(TAG, "✅ Backend reachable at: $candidate:$PORT")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Host $candidate unreachable: ${e.message}")
                }
            }
            hostConfirmed = false
            Log.e(TAG, "⚠️ No backend host reachable. App will use Firestore direct writes as fallback.")
        }
    }

    private val dynamicHostInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            // Only try fallback hosts if we haven't confirmed a host recently
            if (hostConfirmed) {
                // Known host failed — mark as unconfirmed and throw to let caller
                // handle fallback (e.g. Firestore direct write)
                hostConfirmed = false
                Log.w(TAG, "Confirmed host $currentHost failed — marking unconfirmed")
                throw e
            }

            val originalUrl = request.url
            val failedHost = originalUrl.host
            Log.w(TAG, "Request to $failedHost failed — trying 1 fallback host")

            // Only try the next candidate (not all 6) to keep it fast
            val nextCandidate = candidateHosts.firstOrNull { it != failedHost }
            if (nextCandidate != null) {
                try {
                    val newUrl = originalUrl.newBuilder().host(nextCandidate).build()
                    val newRequest = request.newBuilder().url(newUrl).build()
                    val response = chain.proceed(newRequest)
                    currentHost = nextCandidate
                    hostConfirmed = true
                    rebuildApiService()
                    Log.d(TAG, "✅ Switched to fallback host: $nextCandidate")
                    return@Interceptor response
                } catch (_: Exception) {}
            }
            throw e
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(dynamicHostInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedApiService: ApiService? = null

    val apiService: ApiService
        get() {
            return cachedApiService ?: synchronized(this) {
                cachedApiService ?: rebuildApiService()
            }
        }

    fun rebuildApiService(): ApiService {
        val newService = Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        cachedApiService = newService
        Log.d(TAG, "Retrofit rebuilt with base URL: ${getBaseUrl()}")
        return newService
    }
}
