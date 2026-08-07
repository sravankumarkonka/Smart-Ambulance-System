package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.smartambulance.data.model.Ambulance
import com.example.smartambulance.ui.viewmodel.AdminUiState
import com.example.smartambulance.ui.viewmodel.AdminViewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambulances by viewModel.ambulances.collectAsStateWithLifecycle()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ambulance Fleet Live Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(AdminDashboard) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Live map viewport (takes 50%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .background(Color.LightGray)
            ) {
                val bangaloreLatLng = LatLng(12.9716, 77.5946)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(bangaloreLatLng, 12f)
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    ambulances.forEach { amb ->
                        val lat = amb.latitude
                        val lng = amb.longitude
                        if (lat != null && lng != null) {
                            val isAvailable = amb.status == "available"
                            
                            // available -> green marker, busy -> red marker
                            val markerHue = if (isAvailable) {
                                BitmapDescriptorFactory.HUE_GREEN
                            } else {
                                BitmapDescriptorFactory.HUE_RED
                            }
                            
                            Marker(
                                state = MarkerState(position = LatLng(lat, lng)),
                                title = amb.driverName ?: "Ambulance",
                                snippet = "Status: ${amb.status.uppercase()} | Tel: ${amb.driverPhone ?: ""}",
                                icon = BitmapDescriptorFactory.defaultMarker(markerHue)
                            )
                        }
                    }
                }
            }

            // Ambulance status list (takes 50%)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fleet List", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                if (ambulances.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No registered ambulances found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ambulances) { amb ->
                            val isAvailable = amb.status == "available"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = amb.driverName ?: "Unknown Driver",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Phone: ${amb.driverPhone ?: "N/A"}",
                                            fontSize = 13.sp
                                        )
                                        if (amb.latitude != null && amb.longitude != null) {
                                            Text(
                                                text = "Coords: ${String.format("%.4f", amb.latitude)}, ${String.format("%.4f", amb.longitude)}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
