package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.ui.viewmodel.DriverUiState
import com.example.smartambulance.ui.viewmodel.DriverViewModel
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboardScreen(
    onNavigate: (Any) -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambulance by viewModel.ambulance.collectAsStateWithLifecycle()
    val assignedEmergency by viewModel.activeEmergency.collectAsStateWithLifecycle()

    var pendingEmergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var oxygenChecked by remember { mutableStateOf(true) }
    var stretcherChecked by remember { mutableStateOf(true) }
    var kitChecked by remember { mutableStateOf(true) }

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
                            val status = doc.getString("status") ?: "pending"
                            val patientName = doc.getString("patientName") ?: "Emergency Patient"
                            val emergencyType = doc.getString("emergencyType") ?: "accident"
                            val description = doc.getString("description") ?: ""
                            val latitude = doc.getDouble("latitude") ?: 0.0
                            val longitude = doc.getDouble("longitude") ?: 0.0
                            val severityLevel = doc.getString("severityLevel") ?: "medium"
                            val docDriverId = doc.getString("driverId")

                            val item = Emergency(
                                id = id,
                                userId = userId,
                                patientName = patientName,
                                emergencyType = emergencyType,
                                description = description,
                                latitude = latitude,
                                longitude = longitude,
                                severityLevel = severityLevel,
                                status = status,
                                driverId = docDriverId
                            )

                            if (docDriverId == driverId && status != "completed" && status != "cancelled") {
                                assignedToMe = item
                            } else if ((status == "pending" || status == "Waiting" || status == "waiting") && docDriverId == null) {
                                list.add(item)
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
                title = { Text("Driver Emergency Command", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate(DriverHistory) }) {
                        Icon(Icons.Default.DateRange, contentDescription = "History")
                    }
                    IconButton(onClick = { onNavigate(NotificationCenter) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = { onNavigate(Profile) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Duty Status & Profile Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = SessionManager.name ?: "Ambulance Driver",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Callsign: ALS-Unit-01",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }

                            val currentStatus = ambulance?.status?.lowercase() ?: "offline"
                            val statusBg = when (currentStatus) {
                                "available" -> Color(0xFF2E7D32)
                                "busy" -> Color(0xFFC62828)
                                else -> Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .background(statusBg, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = currentStatus.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                        // Full-width side-by-side action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.updateAmbulanceStatus("available") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(10.dp),
                                enabled = ambulance?.status != "available"
                            ) {
                                Text("GO ONLINE", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.updateAmbulanceStatus("offline") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161)),
                                shape = RoundedCornerShape(10.dp),
                                enabled = ambulance?.status != "offline"
                            ) {
                                Text("GO OFFLINE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Vehicle Readiness Checklist
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vehicle & Equipment Readiness Checklist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = oxygenChecked, onCheckedChange = { oxygenChecked = it })
                                Text("Medical Oxygen Supply", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = stretcherChecked, onCheckedChange = { stretcherChecked = it })
                                Text("Hydraulic Stretcher", fontSize = 13.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = kitChecked, onCheckedChange = { kitChecked = it })
                                Text("First Aid & AED Kit", fontSize = 13.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "GPS Active",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GPS Telemetry Active", fontSize = 12.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }

            // 3. Active Emergency Section
            assignedEmergency?.let { e ->
                if (e.status == "assigned" || e.status == "arrived") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🚨 Active Emergency Dispatch", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFC62828))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Patient: ${e.patientName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Severity: ${e.severityLevel.uppercase()}", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                    }
                                    Text("Emergency Type: ${e.emergencyType.uppercase()}")
                                    Text("Description: ${e.description}")
                                    Text("Coordinates: ${e.latitude}, ${e.longitude}", fontSize = 12.sp, color = Color.DarkGray)

                                    Button(
                                        onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("OPEN TELEMETRY & ROUTING")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Pending Emergencies Queue Title
            item {
                Text("Pending Emergencies in Queue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            // 5. Pending Emergencies List
            if (pendingEmergencies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No pending emergency reports. Waiting for calls...", color = Color.Gray)
                        }
                    }
                }
            } else {
                items(pendingEmergencies) { e ->
                    val severityColor = when (e.severityLevel.lowercase()) {
                        "critical" -> Color(0xFFC62828)
                        "high" -> Color(0xFFEF5350)
                        "medium" -> Color(0xFFF57C00)
                        else -> Color(0xFF2E7D32)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${e.emergencyType.uppercase()} REQUEST",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(severityColor, shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = e.severityLevel.uppercase(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text("Patient Name: ${e.patientName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Description: ${e.description}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("GPS Location: ${e.latitude}, ${e.longitude}", fontSize = 12.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = { viewModel.assignToEmergency(e.id ?: "") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ACCEPT DISPATCH", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
