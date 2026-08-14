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
import com.example.smartambulance.data.model.toEmergency
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
    var historyEmergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var oxygenChecked by remember { mutableStateOf(true) }
    var stretcherChecked by remember { mutableStateOf(true) }
    var kitChecked by remember { mutableStateOf(true) }

    val driverId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        ?: SessionManager.uid ?: ""

    // Listen to real-time Firestore updates for pending emergencies
    LaunchedEffect(driverId) {
        viewModel.fetchAmbulanceProfile()

        if (driverId.isBlank()) return@LaunchedEffect

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("DriverDashboard", "Error listening to emergencies: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<Emergency>()
                        val histList = mutableListOf<Emergency>()
                        var assignedToMe: Emergency? = null

                        for (doc in snapshot.documents) {
                            val item = doc.toEmergency() ?: continue
                            val docDriverId = doc.getString("driverId") ?: doc.getString("assignedDriver")
                            val docStatus = item.status
                            val rejectedList = doc.get("rejectedDrivers") as? List<*>

                            val isPending = com.example.smartambulance.data.model.isStatusPending(docStatus)
                            val hasNoDriver = docDriverId.isNullOrBlank() || docDriverId == "null"
                            val isRejectedByMe = rejectedList?.contains(driverId) == true
                            val isAssignedToMe = ((docDriverId == driverId) || (item.driverId == driverId)) && com.example.smartambulance.data.model.isStatusActive(docStatus)
                            val isMyHistory = ((docDriverId == driverId) || (item.driverId == driverId)) && com.example.smartambulance.data.model.isStatusHistory(docStatus)

                            if (isAssignedToMe) {
                                assignedToMe = item
                            } else if (isPending && hasNoDriver && !isRejectedByMe) {
                                list.add(item)
                            }
                            if (isMyHistory) {
                                histList.add(item)
                            }
                        }

                        android.util.Log.d("DriverDashboard", "Firestore snapshot: ${list.size} pending, ${histList.size} history for driver $driverId")
                        pendingEmergencies = list.sortedByDescending { it.createdAt ?: "" }
                        historyEmergencies = histList.sortedByDescending { it.createdAt ?: "" }.take(5)
                        if (assignedToMe != null) {
                            viewModel.setActiveEmergency(assignedToMe)
                        } else {
                            viewModel.clearActiveEmergency()
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
                if (com.example.smartambulance.data.model.isStatusActive(e.status)) {
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
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Patient: ${e.patientName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2E7D32), shape = RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = e.status.uppercase().replace("_", " "),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text("Emergency Type: ${e.emergencyType.uppercase()}", fontWeight = FontWeight.Medium)
                                    Text("Description: ${e.description}", color = Color.DarkGray)
                                    Text("Coordinates: ${e.latitude}, ${e.longitude}", fontSize = 12.sp, color = Color.Gray)
                                    if (!e.hospitalName.isNullOrBlank()) {
                                        Text("Target Hospital: ${e.hospitalName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("OPEN DISPATCH", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.releaseEmergency(e.id ?: "") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
                                        ) {
                                            Text("RELEASE CALL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
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
                            if (!e.createdAt.isNullOrBlank()) {
                                Text("Reported: ${e.createdAt.take(19).replace("T", " ")}", fontSize = 11.sp, color = Color.Gray)
                            }
                            if (!e.imageUrl.isNullOrBlank()) {
                                Text("📷 Accident Evidence Image Attached", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.assignToEmergency(e.id ?: "") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("ACCEPT", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.rejectEmergency(e.id ?: "") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("REJECT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            // 6. Recent Response History
            item {
                Text("Recent Response History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (historyEmergencies.isEmpty()) {
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
                            Text("No response history yet.", color = Color.Gray)
                        }
                    }
                }
            } else {
                items(historyEmergencies) { item ->
                    val statusColor = when (item.status?.lowercase()) {
                        "completed" -> Color(0xFF2E7D32)
                        "cancelled", "canceled" -> Color(0xFFC62828)
                        else -> Color(0xFFF57C00)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.emergencyType.uppercase()} CALL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(statusColor, shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = item.status?.uppercase() ?: "PENDING",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text("Patient: ${item.patientName}", fontSize = 13.sp)
                            if (!item.hospitalName.isNullOrBlank()) {
                                Text("Hospital: 🏥 ${item.hospitalName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Time: ${item.createdAt?.take(16)?.replace("T", " ") ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

        }
    }
}
