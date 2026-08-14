package com.example.smartambulance.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.smartambulance.Login
import com.example.smartambulance.data.SessionManager
import com.example.smartambulance.data.model.Emergency
import com.example.smartambulance.data.model.Hospital
import com.example.smartambulance.data.model.User
import com.example.smartambulance.data.model.toEmergency
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    onNavigate: (Any) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var pendingAdmins by remember { mutableStateOf<List<User>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var systemEmergencies by remember { mutableStateOf<List<Emergency>>(emptyList()) }
    var systemHospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Dialog state for adding a hospital
    var showAddHospitalDialog by remember { mutableStateOf(false) }

    // 1. Listen to pending admins
    DisposableEffect(Unit) {
        val listener = db.collection("users")
            .whereEqualTo("role", "admin")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    pendingAdmins = snapshot.documents.mapNotNull { doc ->
                        User(
                            uid = doc.id,
                            name = doc.getString("name") ?: doc.getString("displayName") ?: "Admin Candidate",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone") ?: "",
                            role = "admin",
                            status = "pending",
                            approved = false
                        )
                    }
                }
                loading = false
            }
        onDispose { listener.remove() }
    }

    // 2. Listen to all users
    DisposableEffect(Unit) {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    allUsers = snapshot.documents.mapNotNull { doc ->
                        User(
                            uid = doc.id,
                            name = doc.getString("name") ?: doc.getString("displayName") ?: "User",
                            email = doc.getString("email") ?: "",
                            phone = doc.getString("phone") ?: "",
                            role = doc.getString("role") ?: "user",
                            status = doc.getString("status") ?: "pending",
                            approved = doc.getBoolean("approved") ?: false
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    // 3. Listen to system emergencies
    DisposableEffect(Unit) {
        val listener = db.collection("emergencies")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    systemEmergencies = snapshot.documents.mapNotNull { it.toEmergency() }
                        .sortedByDescending { it.createdAt ?: "" }
                }
            }
        onDispose { listener.remove() }
    }

    // 4. Listen to hospitals
    DisposableEffect(Unit) {
        val listener = db.collection("hospitals")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    systemHospitals = snapshot.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        Hospital(
                            id = doc.id,
                            name = d["name"] as? String ?: "Hospital",
                            latitude = (d["latitude"] as? Number)?.toDouble() ?: 12.9716,
                            longitude = (d["longitude"] as? Number)?.toDouble() ?: 77.5946,
                            totalIcuBeds = (d["totalIcuBeds"] as? Number)?.toInt() ?: 10,
                            availableIcuBeds = (d["availableIcuBeds"] as? Number)?.toInt() ?: 5,
                            rating = (d["rating"] as? Number)?.toDouble() ?: 4.8,
                            phone = d["phone"] as? String ?: "080-12345678"
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    fun approveAdmin(uid: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        db.collection("users").document(uid).update(mapOf("status" to "active", "approved" to true, "updatedAt" to now))
            .addOnSuccessListener { Toast.makeText(context, "Admin approved successfully!", Toast.LENGTH_SHORT).show() }
    }

    fun rejectAdmin(uid: String) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date())
        db.collection("users").document(uid).update(mapOf("status" to "rejected", "approved" to false, "updatedAt" to now))
            .addOnSuccessListener { Toast.makeText(context, "Admin request rejected", Toast.LENGTH_SHORT).show() }
    }

    fun updateUserRole(uid: String, newRole: String) {
        db.collection("users").document(uid).update(mapOf("role" to newRole, "approved" to true, "status" to "active"))
            .addOnSuccessListener { Toast.makeText(context, "Role updated to $newRole", Toast.LENGTH_SHORT).show() }
    }

    fun updateUserStatus(uid: String, newStatus: String) {
        val isApproved = newStatus == "active"
        db.collection("users").document(uid).update(mapOf("status" to newStatus, "approved" to isApproved))
            .addOnSuccessListener { Toast.makeText(context, "User status set to $newStatus", Toast.LENGTH_SHORT).show() }
    }

    fun deleteUser(uid: String) {
        db.collection("users").document(uid).delete()
            .addOnSuccessListener { Toast.makeText(context, "User account deleted", Toast.LENGTH_SHORT).show() }
    }

    val filteredUsers = remember(allUsers, searchQuery) {
        if (searchQuery.isBlank()) allUsers
        else allUsers.filter {
            it.name.contains(searchQuery, true) ||
            it.email.contains(searchQuery, true) ||
            it.role.contains(searchQuery, true)
        }
    }

    val totalUsersCount = allUsers.size
    val totalDriversCount = allUsers.count { it.role == "driver" }
    val totalAdminsCount = allUsers.count { it.role == "admin" || it.role == "super_admin" }
    val activeEmergenciesCount = systemEmergencies.count { com.example.smartambulance.data.model.isStatusActive(it.status) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Super Admin Control Center", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        SessionManager.clearSession()
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        onNavigate(Login)
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // KPI Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard("Users", "$totalUsersCount", Color(0xFF1565C0), Modifier.weight(1f))
                KpiCard("Drivers", "$totalDriversCount", Color(0xFF2E7D32), Modifier.weight(1f))
                KpiCard("Admins", "$totalAdminsCount", Color(0xFF6A1B9A), Modifier.weight(1f))
                KpiCard("Active Calls", "$activeEmergenciesCount", Color(0xFFC62828), Modifier.weight(1f))
            }

            // Tab Navigation
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Pending (${pendingAdmins.size})", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Users (${allUsers.size})", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Hospitals (${systemHospitals.size})", fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("System Logs (${systemEmergencies.size})", fontWeight = FontWeight.Bold) })
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else when (selectedTab) {
                // TAB 0: PENDING ADMIN APPROVALS
                0 -> {
                    if (pendingAdmins.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No pending admin approval requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(pendingAdmins) { admin ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(admin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            SuggestionChip(onClick = {}, label = { Text("PENDING ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold) })
                                        }
                                        Text("Email: ${admin.email}", fontSize = 13.sp)
                                        Text("Phone: ${admin.phone.ifEmpty { "N/A" }}", fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                                            Button(
                                                onClick = { admin.uid?.let { approveAdmin(it) } },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("APPROVE ADMIN")
                                            }
                                            OutlinedButton(
                                                onClick = { admin.uid?.let { rejectAdmin(it) } },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("REJECT")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: USER MANAGEMENT
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, email or role...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(filteredUsers) { user ->
                                UserManagementCard(
                                    user = user,
                                    onRoleChange = { role -> user.uid?.let { updateUserRole(it, role) } },
                                    onStatusChange = { status -> user.uid?.let { updateUserStatus(it, status) } },
                                    onDelete = { user.uid?.let { deleteUser(it) } }
                                )
                            }
                        }
                    }
                }

                // TAB 2: HOSPITALS MANAGEMENT
                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showAddHospitalDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADD NEW HOSPITAL")
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(systemHospitals) { h ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(h.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("ICU Beds: ${h.availableIcuBeds} / ${h.totalIcuBeds} Available", fontSize = 13.sp, color = Color(0xFF2E7D32))
                                            Text("Tel: ${h.phone} • Rating ⭐ ${h.rating}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            db.collection("hospitals").document(h.id).delete()
                                                .addOnSuccessListener { Toast.makeText(context, "Hospital removed", Toast.LENGTH_SHORT).show() }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFC62828))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: SYSTEM EMERGENCY AUDIT LOG
                3 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(systemEmergencies) { item ->
                            val statusColor = when (item.status.lowercase()) {
                                "completed" -> Color(0xFF2E7D32)
                                "cancelled", "canceled" -> Color(0xFFC62828)
                                else -> Color(0xFF1565C0)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${item.emergencyType.uppercase()} • ${item.patientName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Box(
                                            modifier = Modifier
                                                .background(statusColor, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(item.status.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text("Description: ${item.description}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!item.driverName.isNullOrBlank()) {
                                        Text("Driver: 🚑 ${item.driverName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("Created: ${item.createdAt?.take(16)?.replace("T", " ") ?: "N/A"}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHospitalDialog) {
        AddHospitalDialog(
            onDismiss = { showAddHospitalDialog = false },
            onAdd = { name, phone, beds ->
                val docRef = db.collection("hospitals").document()
                val data = hashMapOf(
                    "name" to name,
                    "phone" to phone,
                    "totalIcuBeds" to beds,
                    "availableIcuBeds" to beds,
                    "latitude" to 12.9716,
                    "longitude" to 77.5946,
                    "rating" to 4.8
                )
                docRef.set(data).addOnSuccessListener {
                    Toast.makeText(context, "Hospital Added!", Toast.LENGTH_SHORT).show()
                    showAddHospitalDialog = false
                }
            }
        )
    }
}

@Composable
private fun KpiCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserManagementCard(
    user: User,
    onRoleChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var expandedRoleMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(user.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Box {
                    AssistChip(
                        onClick = { expandedRoleMenu = true },
                        label = { Text(user.role.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) }
                    )

                    DropdownMenu(expanded = expandedRoleMenu, onDismissRequest = { expandedRoleMenu = false }) {
                        DropdownMenuItem(text = { Text("USER") }, onClick = { onRoleChange("user"); expandedRoleMenu = false })
                        DropdownMenuItem(text = { Text("DRIVER") }, onClick = { onRoleChange("driver"); expandedRoleMenu = false })
                        DropdownMenuItem(text = { Text("ADMIN") }, onClick = { onRoleChange("admin"); expandedRoleMenu = false })
                        DropdownMenuItem(text = { Text("SUPER ADMIN") }, onClick = { onRoleChange("super_admin"); expandedRoleMenu = false })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = user.status == "active",
                        onClick = { onStatusChange("active") },
                        label = { Text("ACTIVE", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = user.status == "suspended",
                        onClick = { onStatusChange("suspended") },
                        label = { Text("SUSPEND", fontSize = 10.sp) }
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun AddHospitalDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var beds by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Hospital") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Hospital Name") })
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone") })
                OutlinedTextField(value = beds, onValueChange = { beds = it }, label = { Text("ICU Beds Count") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name, phone, beds.toIntOrNull() ?: 10)
                }
            }) {
                Text("ADD")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}
