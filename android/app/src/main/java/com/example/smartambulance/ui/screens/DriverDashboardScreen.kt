package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
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

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

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
    var assignedToMeEmergency by remember { mutableStateOf<Emergency?>(null) }

    val driverId = SessionManager.uid ?: ""
    val ACTIVE_STATUSES = listOf("assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached")

    // Listen to real-time Firestore updates for pending and active emergencies
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulanceProfile()
        
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val pendingList = mutableListOf<Emergency>()
                        var activeAssigned: Emergency? = null
                        
                        for (doc in snapshot.documents) {
                            val id = doc.id
                            val userId = doc.getString("userId") ?: ""
                            val patientName = doc.getString("patientName") ?: "Emergency Patient"
                            val type = doc.getString("emergencyType") ?: "General"
                            val desc = doc.getString("description") ?: ""
                            val lat = doc.getDouble("latitude") ?: 0.0
                            val lng = doc.getDouble("longitude") ?: 0.0
                            val severity = doc.getString("severityLevel") ?: "medium"
                            val status = doc.getString("status") ?: "pending"
                            val currentDriverId = doc.getString("driverId")
                            val hospitalName = doc.getString("hospitalName")
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
                                hospitalName = hospitalName,
                                imageUrl = image
                            )
                            
                            if (currentDriverId == driverId && ACTIVE_STATUSES.contains(status.safeLower())) {
                                activeAssigned = emergency
                            } else if (status.safeLower() == "pending") {
                                pendingList.add(emergency)
                            }
                        }
                        
                        pendingEmergencies = pendingList
                        assignedToMeEmergency = activeAssigned
                        if (activeAssigned != null) {
                            viewModel.fetchEmergencyDetails(activeAssigned.id ?: "")
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

    val activeEmergencyToDisplay = assignedEmergency ?: assignedToMeEmergency

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Driver Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Welcome back, ${SessionManager.name ?: "Driver"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(EmergencyHistory) }) {
                        Icon(Icons.Default.DateRange, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Duty Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Duty Information", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val statusStr = ambulance?.status.safeLower()
                        val statusColor = when (statusStr) {
                            "available" -> Color(0xFF2E7D32)
                            "busy" -> Color(0xFFC62828)
                            else -> Color.Gray
                        }
                        Text("Status: ${ambulance?.status.safeUpper()}", fontWeight = FontWeight.Bold, color = statusColor, fontSize = 13.sp)
                    }

                    Text("Driver: ${SessionManager.name ?: "Ambulance Driver"}", fontSize = 13.sp, color = Color.DarkGray)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateAmbulanceStatus("available") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = ambulance?.status != "available"
                        ) {
                            Text("GO ONLINE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.updateAmbulanceStatus("offline") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            enabled = ambulance?.status != "offline"
                        ) {
                            Text("GO OFFLINE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Assigned Active Emergency Section (Matching Web UI Card)
            activeEmergencyToDisplay?.let { e ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🚨 Active Dispatch Assigned", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFB91C1C))
                        Text("You have an active emergency request that needs your immediate response.", fontSize = 12.sp, color = Color(0xFF991B1B))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Patient: ${e.patientName ?: "Emergency Patient"}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                                Text("Type: ${e.emergencyType.safeUpper()}", fontSize = 13.sp, color = Color.DarkGray)
                                Text("Description: ${e.description}", fontSize = 12.sp, color = Color.Gray)
                                Text("Status: ${e.status.safeUpper()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1976D2))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Go to Active Route", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = { viewModel.releaseEmergency(e.id ?: "") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Text("Reject Assignment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Incoming Emergency Broadcasts Section (Matching Web UI)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                Text("📡 Incoming Emergency Broadcasts", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Real-time requests awaiting ambulance assignment.", fontSize = 12.sp, color = Color.Gray)

                if (pendingEmergencies.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No pending emergency reports. Waiting for calls...", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pendingEmergencies, key = { it.id ?: it.hashCode().toString() }) { e ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🚑 ${e.emergencyType.safeUpper()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.Black
                                        )

                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFEF3C7), shape = RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "Awaiting Driver",
                                                color = Color(0xFFD97706),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Patient Name: ${e.patientName ?: "Emergency Patient"}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Text("Description: ${e.description}", fontSize = 12.sp, color = Color.DarkGray)
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Severity:", fontSize = 12.sp, color = Color.DarkGray)
                                                val sevColor = when (e.severityLevel.safeLower()) {
                                                    "critical" -> Color(0xFFB71C1C)
                                                    "high" -> Color(0xFFE11D48)
                                                    "medium" -> Color(0xFFD97706)
                                                    else -> Color(0xFF16A34A)
                                                }
                                                Text(e.severityLevel.safeUpper(), color = sevColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Text("Coordinates: ${e.latitude}, ${e.longitude}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.assignToEmergency(e.id ?: "") },
                                        modifier = Modifier.align(Alignment.End),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text("Accept Assignment", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
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
