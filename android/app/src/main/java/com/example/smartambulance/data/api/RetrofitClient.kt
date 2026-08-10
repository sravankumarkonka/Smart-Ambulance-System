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

    private const val RENDER_HOST = "smart-ambulance-backend-4rbf.onrender.com"

    private val candidateHosts = listOf(
        RENDER_HOST
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
            hostConfirmed = true
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
            listOf("10.0.2.2", RENDER_HOST)
        } else {
            listOf(RENDER_HOST)
        }

        CoroutineScope(Dispatchers.IO).launch {
            for (candidate in hostsToProbe) {
                try {
                    val timeoutSeconds = if (candidate.contains("onrender.com")) 45L else 3L
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
            currentHost = RENDER_HOST
            rebuildApiService()
            Log.w(TAG, "⚠️ Probe completed — default host set to Render.")
        }
    }

    private val dynamicHostInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val originalUrl = request.url
        try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            // If primary Render host timed out during cold start, retry once after a 3s pause
            if (currentHost.contains("onrender.com") || originalUrl.host.contains("onrender.com")) {
                Log.w(TAG, "Render request failed (${e.message}) — retrying once in 3s for server spin up...")
                try {
                    Thread.sleep(3000)
                    val retryResponse = chain.proceed(request)
                    if (retryResponse.isSuccessful) {
                        hostConfirmed = true
                    }
                    return@Interceptor retryResponse
                } catch (retryErr: Exception) {
                    Log.e(TAG, "Render retry failed: ${retryErr.message}")
                }
            }

            // On emulator only, try 10.0.2.2 fallback
            if (isEmulator() && !originalUrl.host.equals("10.0.2.2")) {
                try {
                    val newUrl = originalUrl.newBuilder().scheme("http").host("10.0.2.2").port(PORT).build()
                    val newRequest = request.newBuilder().url(newUrl).build()
                    val response = chain.proceed(newRequest)
                    currentHost = "10.0.2.2"
                    hostConfirmed = true
                    rebuildApiService()
                    Log.d(TAG, "✅ Switched to emulator fallback host: 10.0.2.2")
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
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
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
