package com.gyan.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gyan.app.data.AttendanceEntity
import com.gyan.app.data.AttendanceOverrideEntity
import com.gyan.app.data.SubjectEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

// ---------------- date / money helpers ----------------

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

fun formatMoney(v: Float): String {
    val s = if (v == v.toLong().toFloat()) v.toLong().toString() else String.format("%.2f", v)
    return "₹$s"
}

fun todayStartMillis(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun monthStartMillis(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun currentDayOfWeek(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

fun daysUntil(millis: Long): Int =
    ((millis - System.currentTimeMillis()) / 86400000L).toInt()

fun minutesLabel(min: Int): String = String.format("%02d:%02d", min / 60, min % 60)

fun parseMinutes(text: String): Int? {
    val p = text.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].trim().toIntOrNull() ?: return null
    val m = p[1].trim().toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

fun parseDateTime(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val patterns = listOf(
        "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd-MM-yyyy HH:mm", "dd-MM-yyyy",
        "dd.MM.yyyy", "yyyy-MM-dd", "dd MMM yyyy", "dd/MM/yy", "dd MMM"
    )
    for (p in patterns) {
        try {
            SimpleDateFormat(p, Locale.getDefault()).parse(t)?.time?.let { return it }
        } catch (e: Exception) { /* try next pattern */ }
    }
    return null
}

fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

// ---------------- attendance math ----------------

data class AttStats(
    val attended: Int,
    val total: Int,
    val pct: Float,
    val canMiss: Int,
    val mustAttend: Int,
    val forecastAvailable: Boolean = true
)

fun attendanceStats(
    records: List<AttendanceEntity>,
    minPct: Float,
    starting: AttendanceOverrideEntity? = null
): AttStats {
    val loggedAttended = records.count { it.status == "PRESENT" }
    val loggedTotal = records.count { it.status == "PRESENT" || it.status == "ABSENT" }
    val hasUnweightedBaseline = starting?.startingPercent != null && starting.totalBefore == 0
    val attended = loggedAttended + (starting?.attendedBefore ?: 0)
    val total = loggedTotal + (starting?.totalBefore ?: 0)
    val pct = when {
        hasUnweightedBaseline -> starting!!.startingPercent!!.coerceIn(0f, 100f)
        total == 0 -> 0f
        else -> attended * 100f / total
    }
    val forecastAvailable = !hasUnweightedBaseline && total > 0
    val minimum = minPct.coerceIn(0f, 100f)
    val m = minimum / 100f
    val canMiss = if (forecastAvailable && pct >= minimum && m > 0f && m < 1f)
        floor(attended / m - total).toInt().coerceAtLeast(0) else 0
    val mustAttend = if (forecastAvailable && pct < minimum && m < 1f)
        ceil((m * total - attended) / (1f - m)).toInt().coerceAtLeast(0) else 0
    return AttStats(attended, total, pct, canMiss, mustAttend, forecastAvailable)
}

val SUBJECT_COLORS = listOf(
    0xFF4F46E5, 0xFF0D9488, 0xFFDB2777, 0xFFEA580C,
    0xFF7C3AED, 0xFF2563EB, 0xFF16A34A, 0xFFDC2626, 0xFF0891B2, 0xFF9333EA
)

// ---------------- small composables ----------------

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(opt) })
        }
    }
}

@Composable
fun SubjectPicker(
    subjects: List<SubjectEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("None") })
        subjects.forEach { s ->
            FilterChip(selected = selectedId == s.id, onClick = { onSelect(s.id) }, label = { Text(s.name) })
        }
    }
}

@Composable
fun ConfirmDeleteDialog(what: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $what?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DialogForm(
    title: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = {
            TextButton(enabled = saveEnabled, onClick = onSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HintChips(options: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }, modifier = Modifier.padding(end = 8.dp)) }
    }
}
