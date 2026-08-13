package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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
import com.example.smartambulance.ui.viewmodel.UserViewModel
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: UserViewModel = viewModel()
) {
    val context = LocalContext.current
    val name = SessionManager.name ?: "Patient"
    val userId = SessionManager.uid ?: ""

    // Real-time active emergency from Firestore
    var activeEmergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }

    val ACTIVE_STATUSES = listOf("pending", "waiting", "assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached")

    // Firestore real-time listener for user's active emergencies
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("emergencies")
                    .whereEqualTo("userId", userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot != null) {
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
                                            hospitalName = doc.getString("hospitalName"),
                                            imageUrl = doc.getString("imageUrl")
                                        )
                                    )
                                }
                            }
                            activeEmergencies = activeList
                        }
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { onNavigate(ProfileScreen) }) {
                        Icon(Icons.Filled.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = {
                        SessionManager.clearSession()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        onNavigate(Login)
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Hello, $name!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Need emergency assistance? You can request an ambulance immediately below.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Quick Actions Title
            Text(
                text = "Services",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Service Buttons Grid - 3 cards matching web (Report Emergency, Live Tracking, View History)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Report Emergency
                Card(
                    onClick = { onNavigate(ReportEmergency) },
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Report",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Report Emergency",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // Live Tracking
                Card(
                    onClick = {
                        val active = activeEmergencies.firstOrNull()
                        if (active != null) {
                            onNavigate(TrackAmbulance(active.id ?: ""))
                        } else {
                            Toast.makeText(context, "No active emergency to track", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Live Tracking",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Tracking",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // View History
                Card(
                    onClick = { onNavigate(EmergencyHistory) },
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "View History",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Active Request Tracker Section
            if (activeEmergencies.isNotEmpty()) {
                Text(
                    text = "Active Request Tracker",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start),
                    color = MaterialTheme.colorScheme.onSurface
                )

                activeEmergencies.forEach { emergency ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                                val statusColor = when (emergency.status.lowercase()) {
                                    "pending", "waiting" -> Color(0xFFD97706)
                                    "assigned", "accepted" -> Color(0xFF1976D2)
                                    "on_the_way", "arrived", "reached" -> Color(0xFF2E7D32)
                                    else -> Color(0xFF7B1FA2)
                                }
                                Text(
                                    text = "Status: ${emergency.status.uppercase()}",
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                                Text(
                                    text = "Type: ${emergency.emergencyType.uppercase()}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            Text(
                                text = "Patient: ${emergency.patientName}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            if (emergency.driverName != null) {
                                Text(
                                    text = "Ambulance: 🚑 ${emergency.driverName} (${emergency.driverPhone})",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Searching for nearest available ambulance...",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { onNavigate(TrackAmbulance(emergency.id ?: "")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("TRACK LIVE AMBULANCE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚑", fontSize = 32.sp)
                        Text(
                            text = "No active emergency requests.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Report an emergency to get started.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
