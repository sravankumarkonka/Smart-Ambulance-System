package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartambulance.*
import com.example.smartambulance.ui.components.FleetMarkerData
import com.example.smartambulance.ui.components.OpenStreetMapWebView
import com.example.smartambulance.ui.viewmodel.AdminUiState
import com.example.smartambulance.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    onNavigate: (Any) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambulances by viewModel.ambulances.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var focusedDriverId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchAmbulances()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is AdminUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val filteredAmbulances = remember(ambulances, selectedFilter) {
        when (selectedFilter) {
            "AVAILABLE" -> ambulances.filter { it.status == "available" }
            "BUSY" -> ambulances.filter { it.status != "available" }
            else -> ambulances
        }
    }

    val fleetMarkerDataList = remember(filteredAmbulances) {
        filteredAmbulances.mapIndexed { idx, amb ->
            var lat = amb.latitude ?: 0.0
            var lng = amb.longitude ?: 0.0
            if (lat == 0.0 || lng == 0.0) {
                // Distribute unpositioned ambulances around Bangalore center
                lat = 12.9716 + (idx * 0.008)
                lng = 77.5946 + (idx * 0.008)
            }
            FleetMarkerData(
                id = amb.id ?: amb.driverId ?: "amb_$idx",
                driverName = amb.driverName ?: "Ambulance Driver",
                latitude = lat,
                longitude = lng,
                isAvailable = amb.status == "available",
                phone = amb.driverPhone ?: "N/A"
            )
        }
    }

    val focusedMarker = fleetMarkerDataList.firstOrNull { it.id == focusedDriverId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambulance Fleet Live Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(AdminDashboard) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchAmbulances() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Fleet")
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
            // Live map viewport (50% height) - Uses OpenStreetMapWebView for 100% reliable tile rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .background(Color(0xFF0F172A))
            ) {
                OpenStreetMapWebView(
                    modifier = Modifier.fillMaxSize(),
                    driverLat = focusedMarker?.latitude,
                    driverLng = focusedMarker?.longitude,
                    driverName = focusedMarker?.driverName ?: "Fleet Center",
                    fleetMarkers = fleetMarkerDataList,
                    zoomLevel = if (focusedMarker != null) 15 else 12
                )
            }

            // Fleet List Controls (50% height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fleet Inventory (${filteredAmbulances.size})", fontSize = 17.sp, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = selectedFilter == "AVAILABLE",
                            onClick = { selectedFilter = "AVAILABLE" },
                            label = { Text("AVAILABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = selectedFilter == "BUSY",
                            onClick = { selectedFilter = "BUSY" },
                            label = { Text("BUSY", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                if (ambulances.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No registered fleet ambulances found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAmbulances) { amb ->
                            val isAvailable = amb.status == "available"
                            val ambId = amb.id ?: amb.driverId ?: ""
                            val isSelected = ambId == focusedDriverId

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { focusedDriverId = ambId },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.LocationOn, null, tint = if (isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                            Text(
                                                text = amb.driverName ?: "Unknown Driver",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Text(
                                            text = "Phone: ${amb.driverPhone ?: "N/A"}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val hasRealCoords = amb.latitude != null && amb.longitude != null && amb.latitude != 0.0
                                        Text(
                                            text = if (hasRealCoords) "GPS: ${String.format("%.4f", amb.latitude)}, ${String.format("%.4f", amb.longitude)}" else "GPS: Location Pending",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = amb.status.uppercase(),
                                            color = if (isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
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
