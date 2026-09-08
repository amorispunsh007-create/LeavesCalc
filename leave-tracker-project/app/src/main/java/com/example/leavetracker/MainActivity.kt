package com.example.leavetracker

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// ---------- DataStore setup ----------
val Context.dataStore by preferencesDataStore(name = "leave_tracker")
val LEAVES_KEY = stringPreferencesKey("leaves_json")
val QUOTAS_KEY = stringPreferencesKey("quotas_json")

// ---------- Data models ----------
data class LeaveEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Double,
    val reason: String
)

data class LeaveType(val name: String, val total: Double)

val defaultQuotas = listOf(
    LeaveType("Earned Leave (EL)", 18.0),
    LeaveType("Casual Leave (CL)", 7.0),
    LeaveType("Sick Leave (SL)", 7.0)
)

// ---------- Serialization helpers (org.json, no extra deps) ----------
fun leavesToJson(leaves: List<LeaveEntry>): String {
    val arr = JSONArray()
    for (l in leaves) {
        val obj = JSONObject()
        obj.put("id", l.id)
        obj.put("type", l.type)
        obj.put("start", l.startDate.toString())
        obj.put("end", l.endDate.toString())
        obj.put("days", l.days)
        obj.put("reason", l.reason)
        arr.put(obj)
    }
    return arr.toString()
}

fun jsonToLeaves(json: String): List<LeaveEntry> {
    if (json.isBlank()) return emptyList()
    val arr = JSONArray(json)
    val result = mutableListOf<LeaveEntry>()
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        result.add(
            LeaveEntry(
                id = obj.getString("id"),
                type = obj.getString("type"),
                startDate = LocalDate.parse(obj.getString("start")),
                endDate = LocalDate.parse(obj.getString("end")),
                days = obj.getDouble("days"),
                reason = obj.optString("reason", "")
            )
        )
    }
    return result
}

fun quotasToJson(quotas: List<LeaveType>): String {
    val obj = JSONObject()
    for (q in quotas) obj.put(q.name, q.total)
    return obj.toString()
}

fun jsonToQuotas(json: String): List<LeaveType> {
    if (json.isBlank()) return defaultQuotas
    val obj = JSONObject(json)
    return obj.keys().asSequence().map { key ->
        LeaveType(key, obj.getDouble(key))
    }.toList()
}

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LeaveTrackerApp()
                }
            }
        }
    }
}

// ---------- Main composable ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveTrackerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var leaves by remember { mutableStateOf<List<LeaveEntry>>(emptyList()) }
    var quotas by remember { mutableStateOf(defaultQuotas) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingQuotaType by remember { mutableStateOf<LeaveType?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // Load persisted data once
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        leaves = jsonToLeaves(prefs[LEAVES_KEY] ?: "")
        quotas = jsonToQuotas(prefs[QUOTAS_KEY] ?: "")
        loaded = true
    }

    fun saveLeaves(newLeaves: List<LeaveEntry>) {
        leaves = newLeaves
        scope.launch {
            context.dataStore.edit { it[LEAVES_KEY] = leavesToJson(newLeaves) }
        }
    }

    fun saveQuotas(newQuotas: List<LeaveType>) {
        quotas = newQuotas
        scope.launch {
            context.dataStore.edit { it[QUOTAS_KEY] = quotasToJson(newQuotas) }
        }
    }

    if (!loaded) return

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Leave Tracker", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add leave")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Balances", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            quotas.forEach { quota ->
                val used = leaves.filter { it.type == quota.name }.sumOf { it.days }
                val remaining = quota.total - used
                BalanceCard(
                    quota = quota,
                    used = used,
                    remaining = remaining,
                    onEdit = { editingQuotaType = quota }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("History", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (leaves.isEmpty()) {
                Text(
                    "No leaves logged yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(leaves.sortedByDescending { it.startDate }) { entry ->
                        LeaveHistoryRow(
                            entry = entry,
                            onDelete = {
                                saveLeaves(leaves.filter { it.id != entry.id })
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLeaveDialog(
            quotas = quotas,
            onDismiss = { showAddDialog = false },
            onConfirm = { entry ->
                saveLeaves(leaves + entry)
                showAddDialog = false
            }
        )
    }

    editingQuotaType?.let { quota ->
        EditQuotaDialog(
            quota = quota,
            onDismiss = { editingQuotaType = null },
            onConfirm = { newTotal ->
                saveQuotas(quotas.map { if (it.name == quota.name) it.copy(total = newTotal) else it })
                editingQuotaType = null
            }
        )
    }
}

// ---------- Balance card ----------
@Composable
fun BalanceCard(quota: LeaveType, used: Double, remaining: Double, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(quota.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${used.trimZero()} used of ${quota.total.trimZero()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${remaining.trimZero()} left",
                    fontWeight = FontWeight.Bold,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${quota.name} quota")
                }
            }
        }
    }
}

fun Double.trimZero(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

// ---------- History row ----------
@Composable
fun LeaveHistoryRow(entry: LeaveEntry, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${entry.type} · ${entry.days.trimZero()} day(s)", fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.startDate} to ${entry.endDate}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (entry.reason.isNotBlank()) {
                    Text(
                        entry.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
            }
        }
    }
}

// ---------- Add leave dialog ----------
@Composable
fun AddLeaveDialog(
    quotas: List<LeaveType>,
    onDismiss: () -> Unit,
    onConfirm: (LeaveEntry) -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(quotas.firstOrNull()?.name ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var reason by remember { mutableStateOf("") }

    fun pickDate(onPicked: (LocalDate) -> Unit) {
        val today = LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            today.year, today.monthValue - 1, today.dayOfMonth
        ).show()
    }

    val days = if (startDate != null && endDate != null && !endDate!!.isBefore(startDate)) {
        ChronoUnit.DAYS.between(startDate, endDate).toDouble() + 1
    } else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Leave") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedType.ifBlank { "Select type" })
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        quotas.forEach { q ->
                            DropdownMenuItem(text = { Text(q.name) }, onClick = {
                                selectedType = q.name
                                typeMenuExpanded = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickDate { startDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(startDate?.toString() ?: "Pick start date")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickDate { endDate = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(endDate?.toString() ?: "Pick end date")
                }
                Spacer(Modifier.height(8.dp))
                if (days > 0) Text("Total: ${days.trimZero()} day(s)")
                if (selectedType.startsWith("Sick") && days > 2) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Reminder: Medical Certificate required for SL beyond 2 consecutive days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedType.isNotBlank() && days > 0,
                onClick = {
                    onConfirm(
                        LeaveEntry(
                            type = selectedType,
                            startDate = startDate!!,
                            endDate = endDate!!,
                            days = days,
                            reason = reason
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------- Edit quota dialog ----------
@Composable
fun EditQuotaDialog(quota: LeaveType, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var value by remember { mutableStateOf(quota.total.trimZero()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${quota.name} Quota") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Total days") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                value.toDoubleOrNull()?.let { onConfirm(it) }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
