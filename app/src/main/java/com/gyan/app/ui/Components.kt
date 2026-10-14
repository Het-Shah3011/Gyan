package com.gyan.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat as AndroidDateFormat
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
import androidx.compose.material3.OutlinedButton
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

// ---------------- date / money helpers ----------------

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

@Composable
fun DatePickerField(label: String, selectedMillis: Long, onDateSelected: (Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val selected = Calendar.getInstance().apply { timeInMillis = selectedMillis }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    selected.set(year, month, day, 0, 0, 0)
                    selected.set(Calendar.MILLISECOND, 0)
                    onDateSelected(selected.timeInMillis)
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(formatDate(selectedMillis), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TimePickerField(label: String, selectedMinutes: Int, onTimeSelected: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val safeMinutes = selectedMinutes.coerceIn(0, 23 * 60 + 59)
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onTimeSelected(hour * 60 + minute) },
                safeMinutes / 60,
                safeMinutes % 60,
                AndroidDateFormat.is24HourFormat(context)
            ).show()
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(minutesLabel(safeMinutes), color = MaterialTheme.colorScheme.primary)
        }
    }
}

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

fun minutesLabel(min: Int): String {
    val h24 = min / 60
    val m   = min % 60
    val period = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0  -> 12
        h24 <= 12 -> h24
        else      -> h24 - 12
    }
    return "%02d:%02d %s".format(h12, m, period)
}

fun parseMinutes(text: String): Int? {
    val t = text.trim()
    // Accept 12-hour format: "hh:mm AM" or "hh:mm PM" (returned by minutesLabel)
    val amPmRegex = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)$""", RegexOption.IGNORE_CASE)
    val amPmMatch = amPmRegex.matchEntire(t)
    if (amPmMatch != null) {
        val h = amPmMatch.groupValues[1].toIntOrNull() ?: return null
        val m = amPmMatch.groupValues[2].toIntOrNull() ?: return null
        val period = amPmMatch.groupValues[3].uppercase()
        if (h !in 1..12 || m !in 0..59) return null
        val h24 = when {
            period == "AM" && h == 12 -> 0
            period == "PM" && h != 12 -> h + 12
            else -> h
        }
        return h24 * 60 + m
    }
    // Accept 24-hour format: "HH:MM"
    val p = t.split(":")
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
    val minimum = minPct.coerceIn(0f, 100f).toInt()   // integer percent, e.g. 75
    // ── Exact integer arithmetic — zero float rounding errors ────────────────
    // canMiss:    floor((100*A - M*T) / M)  — classes skippable while staying >= M%
    // mustAttend: ceil((M*T - 100*A) / (100-M))  — classes needed to climb back to M%
    //             ceiling via integer: (num + den - 1) / den
    val canMiss = if (forecastAvailable && pct >= minimum.toFloat() && minimum > 0) {
        val num = 100 * attended - minimum * total
        if (num >= 0) (num / minimum).coerceAtLeast(0) else 0
    } else 0
    val mustAttend = if (forecastAvailable && pct < minimum.toFloat() && minimum < 100) {
        val num = minimum * total - 100 * attended
        val den = 100 - minimum
        if (num > 0 && den > 0) ((num + den - 1) / den).coerceAtLeast(0) else 0
    } else 0
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
