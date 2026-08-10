package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun String?.safeLower(): String = (this ?: "").lowercase()
private fun String?.safeUpper(): String = (this ?: "").uppercase()

private fun formatTimestamp(ts: Any?): String {
    if (ts == null) return ""
    return try {
        when (ts) {
            is com.google.firebase.Timestamp -> {
                val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                sdf.format(ts.toDate())
            }
            is String -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(ts.take(19))
                val outFmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                if (date != null) outFmt.format(date) else ts.take(16)
            }
            else -> ts.toString()
        }
    } catch (e: Exception) {
        ""
    }
}

private fun getEmergencyIcon(type: String): String {
    return when (type.safeLower()) {
        "accident" -> "🚗"
        "cardiac" -> "❤\u200D🩹"
        "respiratory" -> "🫁"
        "stroke" -> "🧠"
        "pregnancy" -> "👶"
        "fire" -> "🔥"
        else -> "🚨"
    }
}

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
    var assignedToMeList by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var actionLoading by remember { mutableStateOf(false) }

    val driverId = SessionManager.uid ?: ""
    val ACTIVE_STATUSES = listOf("assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached")

    // Listen to real-time Firestore updates for ALL emergencies
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulanceProfile()

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val pendingList = mutableListOf<Emergency>()
                        val myActiveList = mutableListOf<Emergency>()

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
                            val createdAtRaw = doc.get("createdAt")
                            val createdAtStr = when (createdAtRaw) {
                                is com.google.firebase.Timestamp -> {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    sdf.format(createdAtRaw.toDate())
                                }
                                is String -> createdAtRaw
                                else -> null
                            }

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
                                imageUrl = image,
                                createdAt = createdAtStr
                            )

                            if (currentDriverId == driverId && ACTIVE_STATUSES.contains(status.safeLower())) {
                                myActiveList.add(emergency)
                            } else if (status.safeLower() == "pending") {
                                pendingList.add(emergency)
                            }
                        }

                        pendingEmergencies = pendingList
                        assignedToMeList = myActiveList

                        // Load details for the first assigned emergency
                        if (myActiveList.isNotEmpty()) {
                            viewModel.fetchEmergencyDetails(myActiveList[0].id ?: "")
                        }
                    }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Firestore error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DriverUiState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
                actionLoading = false
            }
            is DriverUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
                actionLoading = false
            }
            else -> {}
        }
    }

    // Merge: prefer ViewModel's activeEmergency (from API) over Firestore snapshot
    val activeEmergencyToDisplay = assignedEmergency ?: assignedToMeList.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Driver Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Welcome back, ${SessionManager.name ?: "Driver"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    // Response History button
                    Button(
                        onClick = { onNavigate(EmergencyHistory) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📜 History", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    // Active Duty badge
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp, end = 4.dp)
                            .background(Color(0xFF22C55E), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Active Duty", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Logout
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
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── SECTION 1: Assigned Emergencies Card ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Assigned Emergencies", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    if (activeEmergencyToDisplay != null) {
                        val e = activeEmergencyToDisplay

                        // Red header
                        Text(
                            "Active Dispatch Assigned",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFDC2626)
                        )
                        Text(
                            "You have an active emergency request that needs your immediate response.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Patient details inner card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Patient: ${e.patientName ?: "Unknown Patient"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Type: ${e.emergencyType.safeUpper()}", fontSize = 13.sp)
                                Text("Description: ${e.description.ifEmpty { "No description provided" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Go to Active Route", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }

                            OutlinedButton(
                                onClick = {
                                    actionLoading = true
                                    viewModel.releaseEmergency(e.id ?: "")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFDC2626))
                            ) {
                                Text("Reject Assignment", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Text(
                            "No active assignments.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ─── SECTION 2: Incoming Emergency Broadcasts ───
            Text("Incoming Emergency Broadcasts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Real-time requests awaiting ambulance assignment.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pendingEmergencies.isEmpty()) {
                // Empty state card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📡", fontSize = 40.sp)
                        Text("Scanning for emergency signals...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("No pending calls in your area currently.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                pendingEmergencies.forEach { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header: Emergency type + Awaiting Driver badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "${getEmergencyIcon(e.emergencyType)} ${e.emergencyType.replaceFirstChar { c -> c.uppercase() }}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    val reportedTime = formatTimestamp(e.createdAt)
                                    if (reportedTime.isNotEmpty()) {
                                        Text(
                                            "Reported: $reportedTime",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Awaiting Driver",
                                        color = Color(0xFFD97706),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Details inner card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Patient Name: ${e.patientName ?: "Unknown Patient"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Description: ${e.description.ifEmpty { "No description provided" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Severity row
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Severity:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        val sevColor = when (e.severityLevel.safeLower()) {
                                            "critical" -> Color(0xFFB91C1C)
                                            "high" -> Color(0xFFDC2626)
                                            "medium" -> Color(0xFFD97706)
                                            else -> Color(0xFF16A34A)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(sevColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                e.severityLevel.replaceFirstChar { c -> c.uppercase() },
                                                color = sevColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Text(
                                        "Coordinates: ${String.format("%.6f", e.latitude)}, ${String.format("%.6f", e.longitude)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Accept Assignment button
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Button(
                                    onClick = {
                                        if (assignedToMeList.isNotEmpty()) {
                                            Toast.makeText(context, "You already have an active emergency assignment!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            actionLoading = true
                                            viewModel.assignToEmergency(e.id ?: "")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !actionLoading,
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text("Accept Assignment", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacer for scroll padding
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
