package com.example.smartambulance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartambulance.*
import com.example.smartambulance.data.model.Hospital
import com.example.smartambulance.ui.viewmodel.UserUiState
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

private val EMERGENCY_TYPES = listOf(
    "accident" to "🚗 Road Accident",
    "cardiac" to "❤️ Cardiac",
    "respiratory" to "🫁 Respiratory",
    "stroke" to "🧠 Stroke",
    "pregnancy" to "🤰 Pregnancy",
    "other" to "🏥 Other"
)

private val SEVERITY_LEVELS = listOf(
    "low" to Color(0xFF16A34A),
    "medium" to Color(0xFFD97706),
    "high" to Color(0xFFDC2626),
    "critical" to Color(0xFF7C3AED)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencyScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit = {},
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendation by viewModel.hospitalRecommendation.collectAsStateWithLifecycle()

    var patientName by remember { mutableStateOf("") }
    var emergencyType by remember { mutableStateOf("accident") }
    var description by remember { mutableStateOf("") }
    var severityLevel by remember { mutableStateOf("medium") }

    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var readableAddress by remember { mutableStateOf("") }
    var detectingLocation by remember { mutableStateOf(false) }
    var locationDetected by remember { mutableStateOf(false) }

    var selectedHospital by remember { mutableStateOf<Hospital?>(null) }

    // GPS location detection
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun detectGpsLocation() {
        detectingLocation = true
        try {
            val cancellationToken = CancellationTokenSource()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            latitude = location.latitude
                            longitude = location.longitude
                            locationDetected = true
                            // Reverse geocode
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    readableAddress = addresses[0].getAddressLine(0) ?: ""
                                }
                            } catch (_: Exception) {
                                readableAddress = "${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}"
                            }
                            Toast.makeText(context, "GPS location detected!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not get location. Using default.", Toast.LENGTH_SHORT).show()
                            // Fallback to Bangalore
                            latitude = 12.9716
                            longitude = 77.5946
                            readableAddress = "Bangalore, India (default)"
                            locationDetected = true
                        }
                        detectingLocation = false
                    }
                    .addOnFailureListener {
                        latitude = 12.9716
                        longitude = 77.5946
                        readableAddress = "Bangalore, India (default)"
                        locationDetected = true
                        detectingLocation = false
                    }
            } else {
                latitude = 12.9716
                longitude = 77.5946
                readableAddress = "Bangalore, India (default)"
                locationDetected = true
                detectingLocation = false
            }
        } catch (e: Exception) {
            latitude = 12.9716
            longitude = 77.5946
            readableAddress = "Bangalore, India (default)"
            locationDetected = true
            detectingLocation = false
        }
    }

    // Location permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            detectGpsLocation()
        } else {
            latitude = 12.9716
            longitude = 77.5946
            readableAddress = "Bangalore, India (default)"
            locationDetected = true
            Toast.makeText(context, "Location permission denied. Using default location.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-detect location on first launch
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            detectGpsLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Fetch hospital recommendation when location or severity changes
    LaunchedEffect(latitude, longitude, severityLevel) {
        if (latitude != 0.0 && longitude != 0.0) {
            viewModel.recommendHospital(latitude, longitude, severityLevel)
        }
    }

    LaunchedEffect(recommendation) {
        recommendation?.recommended?.let {
            selectedHospital = it
        }
    }

    // Handle UI state — auto-navigate to tracking on success (matching web)
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UserUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                val emergencyId = state.emergencyId
                if (emergencyId != null) {
                    // Navigate to tracking screen (like web's navigate('/user/track/:id'))
                    onNavigate(TrackAmbulance(emergencyId))
                } else {
                    onBack()
                }
            }
            is UserUiState.Error -> {
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
                        Text("Request Emergency Assistance", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Dispatched to nearest available driver", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE53935),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Patient Name
            Text("Patient Information", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = patientName,
                onValueChange = { patientName = it },
                label = { Text("Patient Full Name") },
                placeholder = { Text("Enter patient full name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Emergency Type & Severity side by side
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emergency Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    EMERGENCY_TYPES.forEach { (value, label) ->
                        FilterChip(
                            selected = emergencyType == value,
                            onClick = { emergencyType = value },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Severity Level", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    SEVERITY_LEVELS.forEach { (value, color) ->
                        FilterChip(
                            selected = severityLevel == value,
                            onClick = { severityLevel = value },
                            label = {
                                Text(
                                    text = value.uppercase(),
                                    color = if (severityLevel == value) Color.White else color,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Brief Description of Situation") },
                placeholder = { Text("Mention symptoms, consciousness state, hazards...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )

            // Location Section (matching web's dashed border location panel)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍 Incident Location", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    detectGpsLocation()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            enabled = !detectingLocation,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (detectingLocation) "⏳ Locating..." else "🛰️ Detect GPS", fontSize = 12.sp)
                        }
                    }

                    if (readableAddress.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Text(
                                text = "🏠 Address: $readableAddress",
                                modifier = Modifier.padding(10.dp),
                                fontSize = 13.sp,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = if (latitude != 0.0) String.format("%.6f", latitude) else "",
                            onValueChange = { latitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Latitude", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (longitude != 0.0) String.format("%.6f", longitude) else "",
                            onValueChange = { longitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Longitude", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // Hospital Recommendation Section
            HorizontalDivider()
            Text("🏥 Select Hospital", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            recommendation?.let { rec ->
                rec.comparison.forEach { hospital ->
                    val isSelected = selectedHospital?.id == hospital.id
                    val isCritical = severityLevel == "critical" || severityLevel == "high"
                    val isNoIcu = hospital.availableIcuBeds == 0

                    Card(
                        onClick = { selectedHospital = hospital },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = hospital.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Distance: ${hospital.distanceKm} km", fontSize = 13.sp)
                                Text(
                                    "ICU: ${hospital.availableIcuBeds}/${hospital.totalIcuBeds} (${hospital.icuStatus})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isNoIcu) Color.Red else if (hospital.availableIcuBeds < 3) Color(0xFFF57C00) else Color(0xFF2E7D32)
                                )
                            }

                            if (isCritical && isNoIcu) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "⚠️ ICU beds full. Not recommended for critical cases.",
                                    color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } ?: run {
                if (latitude != 0.0) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text("Detect GPS location to see hospital recommendations.", color = Color.Gray, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            if (uiState is UserUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = {
                        val lat = if (latitude != 0.0) latitude else 12.9716
                        val lng = if (longitude != 0.0) longitude else 77.5946
                        viewModel.reportEmergency(
                            patientName = patientName,
                            emergencyType = emergencyType,
                            description = description,
                            latitude = lat,
                            longitude = lng,
                            severityLevel = severityLevel,
                            hospitalName = selectedHospital?.name,
                            hospitalLatitude = selectedHospital?.latitude,
                            hospitalLongitude = selectedHospital?.longitude
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("🚨 REQUEST IMMEDIATE DISPATCH", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
