package com.example.smartambulance

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartAmbulanceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.example.smartambulance.data.SessionManager.init(this)
        initializeFirebase()
        // Probe candidate backend hosts async and select the working one
        com.example.smartambulance.data.api.RetrofitClient.probeAndSelectBestHost()
        Log.d("SmartAmbulanceApp", "Backend host probe launched")
    }

    private fun initializeFirebase() {
        try {
            // Programmatically configure Firebase using the web credentials from .env
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBLp0H5GzoriDPGSIuK-Ey0Ml_9Xn4NAEc")
                .setApplicationId("1:686610895218:web:667b86b0d2074d0398cdeb")
                .setProjectId("smart-ambulance-system-599d2")
                .build()

            FirebaseApp.initializeApp(this, options)
            Log.d("SmartAmbulanceApp", "Firebase initialized successfully programmatically!")
        } catch (e: Exception) {
            Log.e("SmartAmbulanceApp", "Failed to initialize Firebase", e)
        }
    }
}
