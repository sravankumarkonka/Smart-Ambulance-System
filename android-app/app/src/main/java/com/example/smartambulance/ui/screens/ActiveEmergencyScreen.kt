package com.example.smartambulance.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.ui.viewmodel.DriverUiState
import com.example.smartambulance.ui.viewmodel.DriverViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

/**
 * Wrapper to hold GMS location objects in Compose state.
 * Required because Compose cannot infer nullable types for Java classes.
 */
private data class LocationTrackerState(
    val client: FusedLocationProviderClient?,
    val callback: LocationCallback?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveEmergencyScreen(
    emergencyId: String,
    onNavigate: (Any) -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emergency by viewModel.activeEmergency.collectAsStateWithLifecycle()
    val ambulance by viewModel.ambulance.collectAsStateWithLifecycle()

    // Live GPS state
    var isLiveTracking by remember { mutableStateOf(false) }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var currentBearing by remember { mutableStateOf(0f) }
    var trackerState by remember { mutableStateOf(LocationTrackerState(null, null)) }

    // Pulse animation for live indicator
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isLiveTracking = true
            Toast.makeText(context, "📍 Live GPS tracking activated", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    }

    // Start / stop location updates when isLiveTracking changes
    LaunchedEffect(isLiveTracking) {
        if (isLiveTracking) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineGranted && !coarseGranted) {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
                isLiveTracking = false
                return@LaunchedEffect
            }

            val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
            
            // Immediate location acquisition
            try {
                val cts = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { initialLoc ->
                        if (initialLoc != null) {
                            currentLat = initialLoc.latitude
                            currentLng = initialLoc.longitude
                            currentSpeed = initialLoc.speed * 3.6f
                            currentBearing = initialLoc.bearing
                            viewModel.updateLocation(initialLoc.latitude, initialLoc.longitude, emergencyId)
                        }
                    }
            } catch (_: SecurityException) {}

            val locationRequest: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(5f)
                .build()

            val callback: LocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    currentLat = location.latitude
                    currentLng = location.longitude
                    currentSpeed = location.speed * 3.6f  // m/s → km/h
                    currentBearing = location.bearing
                    viewModel.updateLocation(location.latitude, location.longitude, emergencyId)
                }
            }

            client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            trackerState = LocationTrackerState(client, callback)

        } else {
            // Stop location updates
            val client = trackerState.client
            val callback = trackerState.callback
            if (client != null && callback != null) {
                client.removeLocationUpdates(callback)
            }
            trackerState = LocationTrackerState(null, null)
        }
    }

    // Always clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            val client = trackerState.client
            val callback = trackerState.callback
            if (client != null && callback != null) {
                client.removeLocationUpdates(callback)
            }
        }
    }

    LaunchedEffect(emergencyId) {
        viewModel.fetchEmergencyDetails(emergencyId)
        viewModel.fetchAmbulanceProfile()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DriverUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                if (state.message.contains("completed", true) || state.message.contains("cancelled", true)) {
                    isLiveTracking = false
                    onNavigate(DriverDashboard)
                }
            }
            is DriverUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Emergency Navigation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (isLiveTracking) {
                            Text(
                                text = "● LIVE GPS  ${currentSpeed.toInt()} km/h",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(DriverDashboard) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isLiveTracking) {
                            isLiveTracking = false
                            Toast.makeText(context, "📍 GPS tracking stopped", Toast.LENGTH_SHORT).show()
                        } else {
                            isLiveTracking = true
                        }
                    }) {
                        Icon(
                            imageVector = if (isLiveTracking) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = "Toggle GPS",
                            tint = if (isLiveTracking) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        emergency?.let { e ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                // ── MAP (60% of screen) ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .background(Color(0xFF1A237E))
                ) {
                    val driverLat = currentLat ?: ambulance?.latitude ?: 12.9716
                    val driverLng = currentLng ?: ambulance?.longitude ?: 77.5946

                    ActiveEmergencyMapView(
                        emergency = e,
                        driverLat = driverLat,
                        driverLng = driverLng,
                        isLiveTracking = isLiveTracking,
                        currentSpeed = currentSpeed,
                        currentBearing = currentBearing,
                        pulseScale = pulseScale
                    )
                }

                // ── CONTROLS (40% of screen) ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Patient info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(e.patientName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "${e.emergencyType.uppercase()} • ${e.severityLevel.uppercase()}",
                                fontSize = 12.sp,
                                color = when (e.severityLevel) {
                                    "critical" -> Color(0xFFC62828)
                                    "high" -> Color(0xFFEF5350)
                                    "medium" -> Color(0xFFF57C00)
                                    else -> Color(0xFF2E7D32)
                                }
                            )
                        }
                        if (isLiveTracking) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF1B5E20), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("${currentSpeed.toInt()} km/h", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // TRACKING STAGES BANNER (STAGE 1 vs STAGE 2)
                    val isStage2 = e.status in listOf("patient_picked", "hospital_reached")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val stage1Color = if (!isStage2 && e.status != "completed") Color(0xFF1565C0) else Color(0xFF2E7D32)
                            val stage2Color = if (isStage2 && e.status != "completed") Color(0xFF1565C0) else if (e.status == "completed") Color(0xFF2E7D32) else Color.Gray

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(stage1Color, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STAGE 1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Ambulance → Patient", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                                }
                            }

                            Text(" ▶ ", color = Color.Gray, fontSize = 12.sp)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(stage2Color, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STAGE 2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Patient → Hospital", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Navigation to Patient & Hospital (Google Maps Live Traffic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=${e.latitude},${e.longitude}&mode=d")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${e.latitude},${e.longitude}&travelmode=driving")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PATIENT NAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (e.hospitalLatitude != null && e.hospitalLongitude != null) {
                            Button(
                                onClick = {
                                    try {
                                        val gmmIntentUri = Uri.parse("google.navigation:q=${e.hospitalLatitude},${e.hospitalLongitude}&mode=d")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${e.hospitalLatitude},${e.hospitalLongitude}&travelmode=driving")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("HOSPITAL NAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Status action buttons
                    when (e.status) {
                        "assigned", "accepted" -> ActiveStatusButton("🚀 ON THE WAY", Color(0xFF0288D1)) {
                            viewModel.updateEmergencyStatus(e.id ?: "", "on_the_way")
                        }
                        "on_the_way" -> ActiveStatusButton("📍 ARRIVED AT SCENE", Color(0xFFF57C00)) {
                            viewModel.updateEmergencyStatus(e.id ?: "", "reached")
                        }
                        "reached", "arrived" -> ActiveStatusButton("🧑‍⚕️ PATIENT PICKED UP", Color(0xFF7B1FA2)) {
                            viewModel.updateEmergencyStatus(e.id ?: "", "patient_picked")
                        }
                        "patient_picked" -> ActiveStatusButton("🏥 HOSPITAL REACHED", Color(0xFF388E3C)) {
                            viewModel.updateEmergencyStatus(e.id ?: "", "hospital_reached")
                        }
                        "hospital_reached" -> ActiveStatusButton("✅ COMPLETE DISPATCH", Color(0xFF2E7D32)) {
                            viewModel.updateEmergencyStatus(e.id ?: "", "completed")
                        }
                        else -> {}
                    }

                    // Abort / Release row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.releaseEmergency(e.id ?: "") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("RELEASE TASK", fontSize = 12.sp) }

                        OutlinedButton(
                            onClick = { viewModel.updateEmergencyStatus(e.id ?: "", "cancelled") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                        ) { Text("ABORT CASE", fontSize = 12.sp) }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Loading emergency data...", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ActiveStatusButton(label: String, bgColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun ActiveEmergencyMapView(
    emergency: com.example.smartambulance.data.model.Emergency,
    driverLat: Double,
    driverLng: Double,
    isLiveTracking: Boolean,
    currentSpeed: Float,
    currentBearing: Float,
    pulseScale: Float
) {
    val patientLatLng = LatLng(emergency.latitude, emergency.longitude)
    val driverLatLng = LatLng(driverLat, driverLng)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(driverLatLng, 15f)
    }

    LaunchedEffect(driverLat, driverLng) {
        try {
            val boundsBuilder = LatLngBounds.builder()
            boundsBuilder.include(patientLatLng)
            boundsBuilder.include(driverLatLng)
            if (emergency.hospitalLatitude != null && emergency.hospitalLongitude != null) {
                boundsBuilder.include(LatLng(emergency.hospitalLatitude, emergency.hospitalLongitude))
            }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
            )
        } catch (_: Exception) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f))
        }
    }

    var mapLoaded by remember { mutableStateOf(false) }
    var mapLoadTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) {
            kotlinx.coroutines.delay(15_000)
            if (!mapLoaded) mapLoadTimedOut = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A237E))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = true, compassEnabled = true),
            onMapLoaded = {
                mapLoaded = true
                mapLoadTimedOut = false
            }
        ) {
            Marker(
                state = MarkerState(position = patientLatLng),
                title = "📍 Patient: ${emergency.patientName}",
                snippet = "Emergency Location"
            )
            Marker(
                state = MarkerState(position = driverLatLng),
                title = "🚑 Your Ambulance",
                snippet = if (isLiveTracking) "Live GPS • ${currentSpeed.toInt()} km/h" else "Position"
            )
            if (emergency.hospitalLatitude != null && emergency.hospitalLongitude != null) {
                Marker(
                    state = MarkerState(position = LatLng(emergency.hospitalLatitude, emergency.hospitalLongitude)),
                    title = "🏥 ${emergency.hospitalName ?: "Hospital"}",
                    snippet = "Destination"
                )
            }
            val polylinePoints = buildList {
                add(driverLatLng)
                add(patientLatLng)
                if (emergency.hospitalLatitude != null && emergency.hospitalLongitude != null) {
                    add(LatLng(emergency.hospitalLatitude, emergency.hospitalLongitude))
                }
            }
            Polyline(points = polylinePoints, color = Color(0xFF1565C0), width = 14f)
        }

        if (mapLoadTimedOut) {
            com.example.smartambulance.ui.components.OpenStreetMapWebView(
                patientLat = emergency.latitude,
                patientLng = emergency.longitude,
                patientName = emergency.patientName,
                driverLat = driverLat,
                driverLng = driverLng,
                driverName = emergency.driverName ?: "Your Ambulance",
                driverSpeed = currentSpeed.toDouble(),
                hospitalLat = emergency.hospitalLatitude,
                hospitalLng = emergency.hospitalLongitude,
                hospitalName = emergency.hospitalName ?: "Hospital"
            )
        } else if (!mapLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1A237E).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Initializing Maps...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Navigation header card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC0D47A1))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Column {
                        Text(
                            text = when (emergency.status) {
                                "assigned", "accepted", "on_the_way" -> "Navigate to Patient"
                                "reached", "arrived", "patient_picked" -> "Head to Hospital"
                                "hospital_reached" -> "Arrived at Hospital"
                                else -> "Emergency Active"
                            },
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        if (isLiveTracking) {
                            Text("Live GPS • Bearing ${currentBearing.toInt()}°", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
                if (isLiveTracking) {
                    Box(
                        modifier = Modifier
                            .size((10 * pulseScale).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }
    }
}

