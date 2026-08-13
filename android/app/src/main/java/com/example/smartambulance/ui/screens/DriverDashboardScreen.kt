package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.text.SimpleDateFormat
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
    } catch (_: Exception) { "" }
}

private fun getEmergencyIcon(type: String): String {
    return when (type.safeLower()) {
        "accident" -> "🚗"; "cardiac" -> "❤\u200D🩹"; "respiratory" -> "🫁"
        "stroke" -> "🧠"; "pregnancy" -> "👶"; "fire" -> "🔥"; else -> "🚨"
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

    // Vehicle readiness checklist
    var hasOxygen by remember { mutableStateOf(false) }
    var hasStretcher by remember { mutableStateOf(false) }
    var hasFirstAid by remember { mutableStateOf(false) }

    val driverId = SessionManager.uid ?: ""
    val driverName = SessionManager.name ?: "Driver"
    val ACTIVE_STATUSES = listOf("assigned", "accepted", "on_the_way", "reached", "arrived", "patient_picked", "hospital_reached")

    // Firestore real-time listener
    LaunchedEffect(Unit) {
        viewModel.fetchAmbulanceProfile()
        try {
            FirebaseFirestore.getInstance().collection("emergencies")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null) {
                        val pendingList = mutableListOf<Emergency>()
                        val myActiveList = mutableListOf<Emergency>()
                        for (doc in snapshot.documents) {
                            val status = doc.getString("status") ?: "pending"
                            val currentDriverId = doc.getString("driverId")
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
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                patientName = doc.getString("patientName") ?: "Emergency Patient",
                                emergencyType = doc.getString("emergencyType") ?: "General",
                                description = doc.getString("description") ?: "",
                                latitude = doc.getDouble("latitude") ?: 0.0,
                                longitude = doc.getDouble("longitude") ?: 0.0,
                                severityLevel = doc.getString("severityLevel") ?: "medium",
                                status = status,
                                driverId = currentDriverId,
                                hospitalName = doc.getString("hospitalName"),
                                imageUrl = doc.getString("imageUrl"),
                                createdAt = createdAtStr
                            )
                            val assignedDriverId = doc.getString("assignedDriver")
                            val statusLower = status.safeLower()
                            val isPending = statusLower == "pending" || statusLower == "waiting"
                            val hasNoDriver = currentDriverId.isNullOrBlank() || currentDriverId == "null"
                            val isAssignedToMe = (currentDriverId == driverId || assignedDriverId == driverId) && ACTIVE_STATUSES.contains(statusLower)

                            if (isAssignedToMe) {
                                myActiveList.add(emergency)
                            } else if (isPending && hasNoDriver) {
                                pendingList.add(emergency)
                            }
                        }
                        pendingEmergencies = pendingList
                        assignedToMeList = myActiveList
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
                viewModel.resetState(); actionLoading = false
            }
            is DriverUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState(); actionLoading = false
            }
            else -> {}
        }
    }

    val activeEmergencyToDisplay = assignedEmergency ?: assignedToMeList.firstOrNull()
    val isOnline = ambulance?.status?.safeLower() != "offline"
    val isBusy = assignedToMeList.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Driver Emergency Command", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(EmergencyHistory) }) {
                        Icon(Icons.Filled.DateRange, "History", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { /* Notifications placeholder */ }) {
                        Icon(Icons.Filled.Notifications, "Notifications", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { onNavigate(ProfileScreen) }) {
                        Icon(Icons.Filled.Person, "Profile", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = {
                        SessionManager.clearSession()
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        onNavigate(Login)
                    }) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Driver Profile Card (matching web screenshot 2) ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(driverName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Callsign: ALS-Unit-01", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Status badge
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isBusy) Color(0xFFDC2626) else if (isOnline) Color(0xFF22C55E) else Color(0xFF6B7280),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isBusy) "BUSY" else if (isOnline) "ONLINE" else "OFFLINE",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider()

                    // Go Online / Go Offline buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.updateAmbulanceStatus("available") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                        ) { Text("GO ONLINE", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                        Button(
                            onClick = { viewModel.updateAmbulanceStatus("offline") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) { Text("GO OFFLINE", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }

            // ─── Vehicle & Equipment Readiness Checklist ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vehicle & Equipment Readiness Checklist", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasOxygen, onCheckedChange = { hasOxygen = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF22C55E)))
                            Text("Medical Oxygen Supply", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasStretcher, onCheckedChange = { hasStretcher = it })
                            Text("Hydraulic Stretcher", fontSize = 13.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hasFirstAid, onCheckedChange = { hasFirstAid = it })
                            Text("First Aid & AED Kit", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 16.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("GPS Telemetry Active", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ─── Active Assignment Section ───
            if (activeEmergencyToDisplay != null) {
                val e = activeEmergencyToDisplay
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🚨 Active Dispatch Assigned", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                        Text("You have an active emergency. Respond immediately.", fontSize = 13.sp, color = Color(0xFF991B1B))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Patient: ${e.patientName ?: "Unknown"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Type: ${e.emergencyType.safeUpper()}", fontSize = 13.sp)
                                Text("Description: ${e.description.ifEmpty { "No description" }}", fontSize = 13.sp, color = Color.Gray)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onNavigate(ActiveEmergency(e.id ?: "")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("🚑 Go to Active Route", fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                            OutlinedButton(
                                onClick = { actionLoading = true; viewModel.releaseEmergency(e.id ?: "") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFDC2626))
                            ) { Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                }
            }

            // ─── Pending Emergencies in Queue ───
            Text("Pending Emergencies in Queue", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            if (pendingEmergencies.isEmpty()) {
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
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Header: type + severity badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${e.emergencyType.safeUpper()} REQUEST",
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                                )
                                val sevColor = when (e.severityLevel.safeLower()) {
                                    "critical" -> Color(0xFFB91C1C); "high" -> Color(0xFFDC2626)
                                    "medium" -> Color(0xFFD97706); else -> Color(0xFF16A34A)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(sevColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(e.severityLevel.safeUpper(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Patient info
                            Text("Patient Name: ${e.patientName ?: "Unknown"}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                            if (e.description.isNotEmpty()) {
                                Text(e.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // GPS Location
                            Text(
                                "GPS Location: ${String.format("%.1f", e.latitude)}, ${String.format("%.1f", e.longitude)}",
                                fontSize = 12.sp, color = Color(0xFF0D9488)
                            )

                            // Accept Dispatch button
                            Button(
                                onClick = {
                                    if (assignedToMeList.isNotEmpty()) {
                                        Toast.makeText(context, "You already have an active assignment!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        actionLoading = true
                                        viewModel.assignToEmergency(e.id ?: "")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !actionLoading
                            ) {
                                Text("ACCEPT DISPATCH", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
