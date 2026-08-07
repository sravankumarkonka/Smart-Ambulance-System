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
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            // Programmatically configure Firebase using the web credentials from .env
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyBLp0H5GzoriDPGSIuK-Ey0Ml_9Xn4NAEc")
                .setApplicationId("1:686610895218:web:667b86b0d2074d0398cdeb")
                .setProjectId("smart-ambulance-system-599d2")
                .setStorageBucket("smart-ambulance-system-599d2.firebasestorage.app")
                .build()

            FirebaseApp.initializeApp(this, options)
            Log.d("SmartAmbulanceApp", "Firebase initialized successfully programmatically!")
        } catch (e: Exception) {
            Log.e("SmartAmbulanceApp", "Failed to initialize Firebase", e)
        }
    }
}
