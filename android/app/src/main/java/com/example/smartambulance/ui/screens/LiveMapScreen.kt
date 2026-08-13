package com.example.smartambulance.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.AdminViewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val ambulances by viewModel.ambulances.collectAsStateWithLifecycle()

    var emergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var selectedEmergency by remember { mutableStateOf<Emergency?>(null) }
    var selectedTabFilter by remember { mutableIntStateOf(0) } // 0 = Active Emergencies, 1 = Fleet Ambulances

    // Firestore real-time listener for active emergencies
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulances()

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val ACTIVE_STATUSES = listOf("pending", "assigned", "on_the_way", "reached", "arrived", "patient_picked")
                        val activeList = mutableListOf<Emergency>()
                        for (doc in snapshot.documents) {
                            val status = doc.getString("status") ?: "pending"
                            if (ACTIVE_STATUSES.contains(status.lowercase())) {
                                activeList.add(
                                    Emergency(
                                        id = doc.id,
                                        userId = doc.getString("userId") ?: "",
                                        patientName = doc.getString("patientName") ?: "Emergency Patient",
                                        emergencyType = doc.getString("emergencyType") ?: "General",
                                        description = doc.getString("description") ?: "",
                                        latitude = doc.getDouble("latitude") ?: 0.0,
                                        longitude = doc.getDouble("longitude") ?: 0.0,
                                        severityLevel = doc.getString("severityLevel") ?: "medium",
                                        status = status,
                                        driverId = doc.getString("driverId"),
                                        driverName = doc.getString("driverName"),
                                        driverPhone = doc.getString("driverPhone"),
                                        driverLatitude = doc.getDouble("driverLatitude"),
                                        driverLongitude = doc.getDouble("driverLongitude"),
                                        hospitalName = doc.getString("hospitalName"),
                                        hospitalLatitude = doc.getDouble("hospitalLatitude"),
                                        hospitalLongitude = doc.getDouble("hospitalLongitude"),
                                        imageUrl = doc.getString("imageUrl")
                                    )
                                )
                            }
                        }
                        emergencies = activeList
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Firestore error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambulance & Dispatch Live Map", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchAmbulances() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live map viewport (takes 50% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.50f)
                    .background(Color.LightGray)
            ) {
                val centerLatLng = LatLng(
                    selectedEmergency?.latitude ?: 12.9716,
                    selectedEmergency?.longitude ?: 77.5946
                )
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(centerLatLng, 12f)
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    // Render patient emergency markers
                    emergencies.forEach { em ->
                        val lat = em.latitude
                        val lng = em.longitude
                        if (lat != 0.0 && lng != 0.0) {
                            val markerHue = when (em.severityLevel.safeLower()) {
                                "critical" -> BitmapDescriptorFactory.HUE_RED
                                "high" -> BitmapDescriptorFactory.HUE_ORANGE
                                "medium" -> BitmapDescriptorFactory.HUE_YELLOW
                                else -> BitmapDescriptorFactory.HUE_GREEN
                            }
                            Marker(
                                state = MarkerState(position = LatLng(lat, lng)),
                                title = "📍 ${em.patientName}",
                                snippet = "${em.emergencyType.safeUpper()} | Status: ${em.status.safeUpper()}",
                                icon = BitmapDescriptorFactory.defaultMarker(markerHue)
                            )
                        }

                        // Render driver marker if driver coordinates present
                        if (em.driverLatitude != null && em.driverLongitude != null) {
                            val dLat = em.driverLatitude
                            val dLng = em.driverLongitude
                            Marker(
                                state = MarkerState(position = LatLng(dLat, dLng)),
                                title = "🚑 ${em.driverName ?: "Ambulance"}",
                                snippet = "Assigned to ${em.patientName}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )

                            // Route line between driver & patient
                            Polyline(
                                points = listOf(LatLng(dLat, dLng), LatLng(lat, lng)),
                                color = Color(0xFF2563EB),
                                width = 5f
                            )
                        }
                    }

                    // Render general fleet ambulances
                    ambulances.forEach { amb ->
                        val aLat = amb.latitude
                        val aLng = amb.longitude
                        if (aLat != null && aLng != null) {
                            val isAvailable = amb.status.safeLower() == "available"
                            Marker(
                                state = MarkerState(position = LatLng(aLat, aLng)),
                                title = "🚑 ${amb.driverName ?: "Ambulance Fleet"}",
                                snippet = "Status: ${amb.status.safeUpper()} | Phone: ${amb.driverPhone ?: "N/A"}",
                                icon = BitmapDescriptorFactory.defaultMarker(
                                    if (isAvailable) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_ROSE
                                )
                            )
                        }
                    }
                }
            }

            // Status Tabs & List View (takes 50% height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.50f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabRow(selectedTabIndex = selectedTabFilter) {
                    Tab(
                        selected = selectedTabFilter == 0,
                        onClick = { selectedTabFilter = 0 },
                        text = { Text("Active Dispatches (${emergencies.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTabFilter == 1,
                        onClick = { selectedTabFilter = 1 },
                        text = { Text("Fleet Units (${ambulances.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                when (selectedTabFilter) {
                    0 -> {
                        if (emergencies.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No active emergencies on live map.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(emergencies, key = { it.id ?: it.hashCode().toString() }) { em ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedEmergency = em },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selectedEmergency?.id == em.id) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(em.patientName ?: "Unknown Patient", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                val sevColor = when (em.severityLevel.safeLower()) {
                                                    "critical" -> Color(0xFFDC2626); "high" -> Color(0xFFEA580C)
                                                    "medium" -> Color(0xFFD97706); else -> Color(0xFF16A34A)
                                                }
                                                Text(em.severityLevel.safeUpper(), color = sevColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }

                                            Text("Type: ${em.emergencyType.safeUpper()} | Status: ${em.status.safeUpper()}", fontSize = 12.sp, color = Color.DarkGray)

                                            if (em.driverName != null) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Driver: 🚑 ${em.driverName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                                                    em.driverPhone?.let { phoneNum ->
                                                        IconButton(onClick = {
                                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum"))
                                                            context.startActivity(intent)
                                                        }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.Call, "Call", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            em.id?.let { emId ->
                                                TextButton(
                                                    onClick = { onNavigate(TrackAmbulance(emId)) },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) {
                                                    Text("Track Emergency →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (ambulances.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No registered fleet units.", color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(ambulances) { amb ->
                                    val isAvailable = amb.status.safeLower() == "available"
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("🚑 ${amb.driverName ?: "Ambulance Driver"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Phone: ${amb.driverPhone ?: "N/A"}", fontSize = 12.sp, color = Color.Gray)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isAvailable) Color(0xFFDCFCE7) else Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    amb.status.safeUpper(),
                                                    color = if (isAvailable) Color(0xFF16A34A) else Color(0xFFDC2626),
                                                    fontWeight = FontWeight.Bold, fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
