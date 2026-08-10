package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
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
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.DriverUiState
import com.example.smartambulance.ui.viewmodel.DriverViewModel
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboardScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: DriverViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambulance by viewModel.ambulance.collectAsStateWithLifecycle()
    val assignedEmergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    var pendingEmergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }

    val driverId = SessionManager.uid ?: ""

    // Listen to real-time Firestore updates for pending emergencies
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulanceProfile()
        
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<Emergency>()
                        var assignedToMe: Emergency? = null
                        
                        for (doc in snapshot.documents) {
                            val id = doc.id
                            val userId = doc.getString("userId") ?: ""
                            val patientName = doc.getString("patientName") ?: ""
                            val type = doc.getString("emergencyType") ?: ""
                            val desc = doc.getString("description") ?: ""
                            val lat = doc.getDouble("latitude") ?: 0.0
                            val lng = doc.getDouble("longitude") ?: 0.0
                            val severity = doc.getString("severityLevel") ?: "medium"
                            val status = doc.getString("status") ?: "pending"
                            val currentDriverId = doc.getString("driverId")
                            val image = doc.getString("imageUrl")
                            
                            val emergency = Emergency(
                                id = id,
                                userId = userId,
                                patientName = patientName,
                                emergencyType = type,
                                description = desc,
                                latitude = lat,
                                longitude = lng,
                                severityLevel = severity,
                                status = status,
                                driverId = currentDriverId,
                                imageUrl = image
                            )
                            
                            if (currentDriverId == driverId && (status == "assigned" || status == "arrived")) {
                                assignedToMe = emergency
                            } else if (status == "pending") {
                                list.add(emergency)
                            }
                        }
                        
                        pendingEmergencies = list
                        if (assignedToMe != null) {
                            viewModel.fetchEmergencyDetails(assignedToMe.id ?: "")
                        }
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error binding Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DriverUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
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
                title = { Text("Driver Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate(EmergencyHistory) }) {
                        Icon(Icons.Default.DateRange, contentDescription = "History")
                    }
                    IconButton(onClick = {
                        SessionManager.clearSession()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        onNavigate(Login)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status and Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Duty Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Driver: ${SessionManager.name ?: "Ambulance Driver"}", fontSize = 14.sp)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ${ambulance?.status?.uppercase() ?: "UNKNOWN"}",
                            fontWeight = FontWeight.Bold,
                            color = if (ambulance?.status == "available") Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.updateAmbulanceStatus("available") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = ambulance?.status != "available"
                            ) {
                                Text("GO ONLINE")
                            }
                            Button(
                                onClick = { viewModel.updateAmbulanceStatus("offline") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                shape = RoundedCornerShape(8.dp),
                                enabled = ambulance?.status != "offline"
                            ) {
                                Text("GO OFFLINE")
                            }
                        }
                    }
                }
            }

            // Assigned Emergency Section
            assignedEmergency?.let { e ->
                if (e.status == "assigned" || e.status == "arrived") {
                    Text("Assigned Active Emergency", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Patient: ${e.patientName}", fontWeight = FontWeight.Bold)
                                Text("Severity: ${e.severityLevel.uppercase()}")
                            }
                            Text("Type: ${e.emergencyType.uppercase()}")
                            Text("Description: ${e.description}")
                            
                            Button(
                                onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("OPEN TELEMETRY & ROUTING")
                            }
                        }
                    }
                }
            } ?: Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Pending Emergencies List
                Text("Pending Emergencies in Queue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (pendingEmergencies.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No pending emergency reports. Waiting for calls...", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingEmergencies) { e ->
                            val severityColor = when (e.severityLevel.lowercase()) {
                                "critical" -> Color(0xFFC62828)
                                "high" -> Color(0xFFEF5350)
                                "medium" -> Color(0xFFF57C00)
                                else -> Color(0xFF2E7D32)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${e.emergencyType.uppercase()} request",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(severityColor, shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = e.severityLevel.uppercase(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Text("Patient: ${e.patientName}", fontSize = 14.sp)
                                    Text("Location: ${e.latitude}, ${e.longitude}", fontSize = 12.sp, color = Color.Gray)
                                    
                                    Button(
                                        onClick = { viewModel.assignToEmergency(e.id ?: "") },
                                        modifier = Modifier.align(Alignment.End),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("ACCEPT DISPATCH")
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
