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

    @Volatile
    private var lastProbeTimeMs: Long = 0L
    private const val PROBE_COOLDOWN_MS = 60_000L

    @Volatile
    private var hostConfirmed = false

    @Volatile
    private var currentHost: String = "smart-ambulance-backend-4rbf.onrender.com"

    private const val RENDER_BASE_URL = "https://smart-ambulance-backend-4rbf.onrender.com/api/"
    
    private val candidateHosts = listOf(
        "smart-ambulance-backend-4rbf.onrender.com",
        "10.185.251.48",
        "192.168.1.5",
        "192.168.0.105",
        "10.0.0.2",
        "10.0.2.2",
        "127.0.0.1"
    )

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
        return if (currentHost.contains("onrender.com")) {
            "https://$currentHost/api/"
        } else {
            "http://$currentHost:$PORT/api/"
        }
    }

    fun setPhysicalIp(ip: String) {
        if (ip.isNotBlank()) {
            currentHost = ip.trim()
            rebuildApiService()
            Log.d(TAG, "Backend host manually set to: $ip")
        }
    }

    fun isHostConfirmed(): Boolean = hostConfirmed

    fun probeAndSelectBestHost() {
        val now = System.currentTimeMillis()
        if (now - lastProbeTimeMs < PROBE_COOLDOWN_MS && hostConfirmed) {
            Log.d(TAG, "Skipping probe — cooldown active, host=$currentHost")
            return
        }
        lastProbeTimeMs = now

        val hostsToProbe = if (isEmulator()) {
            listOf("10.0.2.2") + candidateHosts.filter { it != "10.0.2.2" }
        } else {
            candidateHosts
        }

        CoroutineScope(Dispatchers.IO).launch {
            for (candidate in hostsToProbe) {
                try {
                    val timeoutSeconds = if (candidate.contains("onrender.com")) 25L else 2L
                    val probeClient = OkHttpClient.Builder()
                        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .build()
                    val healthUrl = if (candidate.contains("onrender.com")) "https://$candidate/health" else "http://$candidate:$PORT/health"
                    val req = Request.Builder()
                        .url(healthUrl)
                        .get()
                        .build()
                    val response = probeClient.newCall(req).execute()
                    if (response.isSuccessful || response.code == 404) {
                        currentHost = candidate
                        hostConfirmed = true
                        rebuildApiService()
                        Log.d(TAG, "✅ Backend reachable at: $candidate")
                        return@launch
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "❌ Host $candidate unreachable: ${e.message}")
                }
            }
            hostConfirmed = false
            Log.e(TAG, "⚠️ No backend host reachable.")
        }
    }

    private val dynamicHostInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            // If primary Render host timed out during cold start, retry once after a short pause
            if (currentHost.contains("onrender.com")) {
                Log.w(TAG, "Render request failed (${e.message}) — retrying in 2 seconds for server spin up...")
                try {
                    Thread.sleep(2000)
                    return@Interceptor chain.proceed(request)
                } catch (_: Exception) {}
            }

            if (hostConfirmed) {
                hostConfirmed = false
                Log.w(TAG, "Confirmed host $currentHost failed — marking unconfirmed")
                throw e
            }

            val originalUrl = request.url
            val failedHost = originalUrl.host
            Log.w(TAG, "Request to $failedHost failed — trying 1 fallback host")

            val nextCandidate = candidateHosts.firstOrNull { it != failedHost }
            if (nextCandidate != null) {
                try {
                    val scheme = if (nextCandidate.contains("onrender.com")) "https" else "http"
                    val port = if (nextCandidate.contains("onrender.com")) 443 else PORT
                    val newUrl = originalUrl.newBuilder().scheme(scheme).host(nextCandidate).port(port).build()
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
