package com.gyan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.ScholarshipEntity
import com.gyan.app.data.SubscriptionEntity
import com.gyan.app.data.WarrantyEntity
import com.gyan.app.reminders.ReminderScheduler
import kotlinx.coroutines.launch

@Composable
fun TrackersScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Subscriptions", "Warranties", "Scholarships")

    Scaffold(
        topBar = {
            Column(Modifier.padding(top = 8.dp)) {
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> SubscriptionsTab()
                1 -> WarrantiesTab()
                2 -> ScholarshipsTab()
            }
        }
    }
}

// ------------------------------ SUBSCRIPTIONS ------------------------------

@Composable
fun SubscriptionsTab() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val subs by repo.dao.subscriptionsFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var editTarget by remember { mutableStateOf<SubscriptionEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (subs.isEmpty()) {
                item { EmptyState("No subscriptions tracked") }
            } else {
                items(subs.size) { i ->
                    val s = subs[i]
                    val days = daysUntil(s.nextRenewalMillis)
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${formatMoney(s.amount)} / ${if (s.cycleDays >= 365) "year" else "month"}" +
                                        (if (s.notes.isNotBlank()) "  •  ${s.notes}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (days < 0) "Renewed ${-days} day(s) ago - update it"
                                    else "Renews in $days day(s) • ${formatDate(s.nextRenewalMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (days <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { editTarget = s }) { Icon(Icons.Filled.Edit, contentDescription = "Edit subscription", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { deleteTarget = s }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd || editTarget != null) {
        val original = editTarget
        var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        var amount by remember(original?.id) { mutableStateOf(original?.amount?.toString().orEmpty()) }
        var cycle by remember(original?.id) { mutableStateOf(if ((original?.cycleDays ?: 30) >= 365) "Yearly" else "Monthly") }
        var renewalText by remember(original?.id) { mutableStateOf(original?.nextRenewalMillis?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()) }
        var notes by remember(original?.id) { mutableStateOf(original?.notes.orEmpty()) }
        val renewal = parseDateTime(renewalText)
        DialogForm(
            title = if (original == null) "Add subscription" else "Edit subscription",
            onDismiss = { showAdd = false; editTarget = null },
            saveEnabled = name.isNotBlank() && amount.toFloatOrNull() != null && renewal != null,
            onSave = {
                scope.launch {
                    original?.let { ReminderScheduler.cancel(context, (200000 + it.id).toInt()) }
                    val entity = original?.copy(name = name.trim(), amount = amount.toFloat(), cycleDays = if (cycle == "Yearly") 365 else 30, nextRenewalMillis = renewal!!, notes = notes.trim())
                        ?: SubscriptionEntity(
                            name = name.trim(),
                            amount = amount.toFloatOrNull() ?: 0f,
                            cycleDays = if (cycle == "Yearly") 365 else 30,
                            nextRenewalMillis = renewal!!,
                            notes = notes.trim()
                        )
                    val savedId = repo.dao.upsertSubscription(entity)
                    val id = if (original != null) original.id else savedId
                    ReminderScheduler.schedule(
                        context, (200000 + id).toInt(),
                        "Subscription renewal",
                        "${name.trim()} (${formatMoney(amount.toFloatOrNull() ?: 0f)}) renews on ${formatDate(renewal)}",
                        renewal - 2L * 86400000
                    )
                }
                showAdd = false
                editTarget = null
            }
        ) {
            FormField("Name (e.g. Spotify)", name) { name = it }
            FormField("Amount per cycle", amount) { amount = it.filter { c -> c.isDigit() || c == '.' } }
            Text("Billing cycle", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("Monthly", "Yearly"), cycle) { cycle = it }
            FormField("Next renewal date (dd/MM/yyyy)", renewalText) { renewalText = it }
            FormField("Notes", notes) { notes = it }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = "subscription ${target.name}",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    ReminderScheduler.cancel(context, (200000 + target.id).toInt())
                    repo.dao.deleteSubscription(target)
                }
                deleteTarget = null
            }
        )
    }
}

// ------------------------------ WARRANTIES ------------------------------

@Composable
fun WarrantiesTab() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val warranties by repo.dao.warrantiesFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WarrantyEntity?>(null) }
    var editTarget by remember { mutableStateOf<WarrantyEntity?>(null) }
    val now = System.currentTimeMillis()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (warranties.isEmpty()) {
                item { EmptyState("No warranties tracked") }
            } else {
                items(warranties.size) { i ->
                    val w = warranties[i]
                    val totalMs = w.warrantyMonths * 30L * 86400000L
                    val expiry = w.purchaseMillis + totalMs
                    val fraction = ((now - w.purchaseMillis).toFloat() / totalMs).coerceIn(0f, 1f)
                    val daysLeft = daysUntil(expiry)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(w.product, style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { editTarget = w }) { Icon(Icons.Filled.Edit, contentDescription = "Edit warranty", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { deleteTarget = w }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            Text(
                                "Bought ${formatDate(w.purchaseMillis)}  •  ${w.warrantyMonths} months" +
                                    (if (w.notes.isNotBlank()) "\n${w.notes}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (daysLeft < 0) "⚠️ Warranty expired ${-daysLeft} day(s) ago"
                                else "✅ ${daysLeft} day(s) left  •  expires ${formatDate(expiry)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (daysLeft < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd || editTarget != null) {
        val original = editTarget
        var product by remember(original?.id) { mutableStateOf(original?.product.orEmpty()) }
        var purchaseText by remember(original?.id) { mutableStateOf(original?.purchaseMillis?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()) }
        var months by remember(original?.id) { mutableStateOf(original?.warrantyMonths?.toString() ?: "12") }
        var notes by remember(original?.id) { mutableStateOf(original?.notes.orEmpty()) }
        val purchase = parseDateTime(purchaseText)
        DialogForm(
            title = if (original == null) "Add warranty" else "Edit warranty",
            onDismiss = { showAdd = false; editTarget = null },
            saveEnabled = product.isNotBlank() && purchase != null && (months.toIntOrNull() ?: 0) > 0,
            onSave = {
                scope.launch {
                    repo.dao.upsertWarranty(original?.copy(product = product.trim(), purchaseMillis = purchase!!, warrantyMonths = months.toInt(), notes = notes.trim())
                        ?: WarrantyEntity(product = product.trim(), purchaseMillis = purchase!!, warrantyMonths = months.toIntOrNull() ?: 12, notes = notes.trim()))
                }
                showAdd = false
                editTarget = null
            }
        ) {
            FormField("Product (e.g. Dell G15 laptop)", product) { product = it }
            FormField("Purchase date (dd/MM/yyyy)", purchaseText) { purchaseText = it }
            FormField("Warranty length in months", months) { months = it.filter { c -> c.isDigit() } }
            FormField("Notes", notes) { notes = it }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.product,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { repo.dao.deleteWarranty(target) }
                deleteTarget = null
            }
        )
    }
}

// ------------------------------ SCHOLARSHIPS ------------------------------

@Composable
fun ScholarshipsTab() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val scholarships by repo.dao.scholarshipsFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScholarshipEntity?>(null) }
    var editTarget by remember { mutableStateOf<ScholarshipEntity?>(null) }
    val statuses = listOf("TRACKING", "APPLIED", "APPROVED", "REJECTED")

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (scholarships.isEmpty()) {
                item { EmptyState("No scholarships tracked") }
            } else {
                items(scholarships.size) { i ->
                    val s = scholarships[i]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    if (s.amount > 0f) Text(
                                        formatMoney(s.amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { editTarget = s }) { Icon(Icons.Filled.Edit, contentDescription = "Edit scholarship", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { deleteTarget = s }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            s.deadlineMillis?.let { d ->
                                val days = daysUntil(d)
                                Text(
                                    if (days < 0) "Deadline passed (${formatDate(d)})"
                                    else "⏰ ${formatDate(d)}  •  $days day(s) left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (days < 0) MaterialTheme.colorScheme.onSurfaceVariant
                                    else if (days <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (s.requirements.isNotBlank()) Text(
                                s.requirements,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    s.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (s.status) {
                                        "APPROVED" -> MaterialTheme.colorScheme.primary
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (s.status == "APPROVED") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = s.paid,
                                            onCheckedChange = { checked ->
                                                scope.launch { repo.dao.upsertScholarship(s.copy(paid = checked)) }
                                            }
                                        )
                                        Text("Money received", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd || editTarget != null) {
        val original = editTarget
        var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        var amount by remember(original?.id) { mutableStateOf(original?.amount?.takeIf { it > 0f }?.toString().orEmpty()) }
        var deadlineText by remember(original?.id) { mutableStateOf(original?.deadlineMillis?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()) }
        var requirements by remember(original?.id) { mutableStateOf(original?.requirements.orEmpty()) }
        var status by remember(original?.id) { mutableStateOf(original?.status ?: "TRACKING") }
        val deadline = parseDateTime(deadlineText)
        DialogForm(
            title = if (original == null) "Add scholarship" else "Edit scholarship",
            onDismiss = { showAdd = false; editTarget = null },
            saveEnabled = name.isNotBlank(),
            onSave = {
                scope.launch {
                    repo.dao.upsertScholarship(original?.copy(name = name.trim(), amount = amount.toFloatOrNull() ?: 0f, deadlineMillis = deadline, requirements = requirements.trim(), status = status)
                        ?: ScholarshipEntity(name = name.trim(), amount = amount.toFloatOrNull() ?: 0f, deadlineMillis = deadline, requirements = requirements.trim(), status = status))
                }
                showAdd = false
                editTarget = null
            }
        ) {
            FormField("Scholarship name", name) { name = it }
            FormField("Amount (optional)", amount) { amount = it.filter { c -> c.isDigit() || c == '.' } }
            FormField("Application deadline (dd/MM/yyyy)", deadlineText) { deadlineText = it }
            FormField("Required documents / notes", requirements) { requirements = it }
            Text("Application status", style = MaterialTheme.typography.labelMedium)
            ChipRow(statuses, status) { status = it }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { repo.dao.deleteScholarship(target) }
                deleteTarget = null
            }
        )
    }
}
