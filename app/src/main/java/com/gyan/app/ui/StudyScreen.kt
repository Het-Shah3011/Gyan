package com.gyan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyan.app.data.AttendanceEntity
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.SessionEntity
import com.gyan.app.data.SubjectEntity
import com.gyan.app.data.TaskEntity
import com.gyan.app.reminders.BootReceiver.Companion.requestCodeFor
import com.gyan.app.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.util.Calendar

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAY_VALUES = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

@Composable
fun StudyScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Timetable", "Attendance", "Tasks", "Exams")

    Column(Modifier.fillMaxSize()) {
        // Custom premium tab bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (tab < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[tab])
                                .height(3.dp)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (tab == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        when (tab) {
            0 -> TimetableTab()
            1 -> AttendanceTab()
            2 -> TasksTab("ASSIGNMENT", includeTasks = true)
            3 -> TasksTab("EXAM", includeTasks = false)
        }
    }
}

// ------------------------------- TIMETABLE -------------------------------

@Composable
fun TimetableTab() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val subjects by repo.dao.subjectsFlow().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.dao.sessionsFlow().collectAsStateWithLifecycle(emptyList())
    val subjectMap = subjects.associateBy { it.id }
    val scope = rememberCoroutineScope()

    var selectedDay by remember { mutableStateOf(currentDayOfWeek()) }
    if (!DAY_VALUES.contains(selectedDay)) selectedDay = Calendar.MONDAY
    val dayIndex = DAY_VALUES.indexOf(selectedDay).coerceAtLeast(0)

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add class", tint = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            // ---- Day selector pills ----
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        val isSelected = i == dayIndex
                        val isToday = DAY_VALUES[i] == currentDayOfWeek()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedDay = DAY_VALUES[i] }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) Color.White
                                    else if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isToday) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        Modifier.size(4.dp).clip(CircleShape)
                                            .background(if (isSelected) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // ---- Day heading ----
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DAY_LABELS[dayIndex],
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (DAY_VALUES[dayIndex] == currentDayOfWeek()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Today",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }

            val daySessions = sessions.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinutes }
            if (daySessions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📚", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No classes on ${DAY_LABELS[dayIndex]}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Enjoy your free day!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                // Timeline view
                items(daySessions.size) { i ->
                    val s = daySessions[i]
                    val sub = subjectMap[s.subjectId]
                    val subColor = sub?.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
                    val isLast = i == daySessions.size - 1

                    Row(Modifier.fillMaxWidth()) {
                        // Timeline column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(56.dp)
                        ) {
                            Text(
                                minutesLabel(s.startMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.size(12.dp).clip(CircleShape)
                                    .background(subColor)
                            )
                            if (!isLast) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(72.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(subColor.copy(alpha = 0.4f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Session card
                        ElevatedCard(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 0.dp else 12.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Color accent bar
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(80.dp)
                                        .background(
                                            subColor,
                                            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                                        )
                                )
                                Column(
                                    Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            sub?.name ?: "Unknown Subject",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (s.isLab) {
                                            Surface(
                                                color = Color(0xFF6D4C41).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Science,
                                                        contentDescription = null,
                                                        tint = Color(0xFF6D4C41),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        "LAB",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF6D4C41),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${minutesLabel(s.startMinutes)} – ${minutesLabel(s.endMinutes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (s.room.isNotBlank()) {
                                            Text(
                                                "•",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                "Room ${s.room}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (sub?.code?.isNotBlank() == true) {
                                        Text(
                                            sub.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = subColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                IconButton(onClick = { deleteTarget = s }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAdd) {
        var subjectId by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
        var start by remember { mutableStateOf("09:00") }
        var end by remember { mutableStateOf("10:00") }
        var room by remember { mutableStateOf("") }
        var isLab by remember { mutableStateOf(false) }
        val startMin = parseMinutes(start)
        val endMin = parseMinutes(end)
        DialogForm(
            title = "Add class to ${DAY_LABELS[dayIndex]}",
            onDismiss = { showAdd = false },
            saveEnabled = subjectId != null && startMin != null && endMin != null && endMin!! > startMin!!,
            onSave = {
                scope.launch {
                    repo.dao.upsertSession(
                        SessionEntity(
                            subjectId = subjectId!!,
                            dayOfWeek = selectedDay,
                            startMinutes = startMin!!,
                            endMinutes = endMin!!,
                            room = room.trim(),
                            isLab = isLab
                        )
                    )
                }
                showAdd = false
            }
        ) {
            Text("Subject", style = MaterialTheme.typography.labelMedium)
            SubjectPicker(subjects, subjectId) { subjectId = it }
            FormField("Start time (HH:MM)", start) { start = it }
            FormField("End time (HH:MM)", end) { end = it }
            FormField("Room (optional)", room) { room = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isLab, onCheckedChange = { isLab = it })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Lab session", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Lab attendance is tracked separately",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = "this class",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { repo.dao.deleteSession(target) }
                deleteTarget = null
            }
        )
    }
}

// ------------------------------- ATTENDANCE -------------------------------

@Composable
fun AttendanceTab() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val subjects by repo.dao.subjectsFlow().collectAsStateWithLifecycle(emptyList())
    val attendance by repo.dao.attendanceFlow().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.dao.sessionsFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SubjectEntity?>(null) }
    val today = todayStartMillis()
    val todayDow = currentDayOfWeek()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Attendance",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track per-class and lab sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = { showAdd = true },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Add subject",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (subjects.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎓", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No subjects yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Add your first subject to start tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(subjects.size) { i ->
                val sub = subjects[i]
                val allRecords = attendance.filter { it.subjectId == sub.id }

                // Split theory vs lab records
                val theoryRecords = allRecords.filter { !it.isLab }
                val labRecords = allRecords.filter { it.isLab }

                val theoryStats = attendanceStats(theoryRecords, sub.minAttendance)
                val hasLab = sessions.any { it.subjectId == sub.id && it.isLab }
                val labStats = if (hasLab) attendanceStats(labRecords, sub.minAttendance) else null

                // Today's sessions for this subject
                val todaySessions = sessions.filter { it.subjectId == sub.id && it.dayOfWeek == todayDow }
                    .sortedBy { it.startMinutes }

                val subColor = Color(sub.colorArgb.toInt())
                val pctColor = when {
                    theoryStats.pct >= sub.minAttendance -> Color(0xFF22C55E)
                    theoryStats.pct >= sub.minAttendance - 5f -> Color(0xFFFFA726)
                    else -> Color(0xFFEF4444)
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column {
                        // Gradient top bar
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(subColor, subColor.copy(alpha = 0.4f))
                                    )
                                )
                        )
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header row
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                            .background(subColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            sub.name.take(2).uppercase(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = subColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            sub.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (sub.code.isNotBlank()) {
                                            Text(
                                                sub.code,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Theory percentage badge
                                    Surface(
                                        color = pctColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "%.1f%%".format(theoryStats.pct),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = pctColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (hasLab) {
                                                Text(
                                                    "Theory",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = pctColor.copy(alpha = 0.7f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                    // Lab percentage badge if applicable
                                    if (hasLab && labStats != null) {
                                        val labPctColor = when {
                                            labStats.pct >= sub.minAttendance -> Color(0xFF22C55E)
                                            labStats.pct >= sub.minAttendance - 5f -> Color(0xFFFFA726)
                                            else -> Color(0xFFEF4444)
                                        }
                                        Surface(
                                            color = Color(0xFF6D4C41).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(
                                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    "%.1f%%".format(labStats.pct),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = Color(0xFF6D4C41),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Lab",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF6D4C41).copy(alpha = 0.7f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { deleteTarget = sub },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Theory progress bar
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (hasLab) {
                                    Text(
                                        "Theory",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (theoryStats.pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = pctColor,
                                    trackColor = pctColor.copy(alpha = 0.12f),
                                    strokeCap = StrokeCap.Round
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${theoryStats.attended}/${theoryStats.total} attended  •  min ${sub.minAttendance.toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        when {
                                            theoryStats.canMiss > 0 -> "😌 Miss ${theoryStats.canMiss}"
                                            theoryStats.mustAttend > 0 -> "⚠️ Need ${theoryStats.mustAttend}"
                                            theoryStats.total == 0 -> "No data"
                                            else -> "On edge"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (theoryStats.canMiss > 0) Color(0xFF22C55E)
                                        else if (theoryStats.mustAttend > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Lab progress bar (if applicable)
                            if (hasLab && labStats != null) {
                                val labPctColor = when {
                                    labStats.pct >= sub.minAttendance -> Color(0xFF22C55E)
                                    labStats.pct >= sub.minAttendance - 5f -> Color(0xFFFFA726)
                                    else -> Color(0xFFEF4444)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Science,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF6D4C41)
                                        )
                                        Text(
                                            "Lab",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF6D4C41),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (labStats.pct / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF6D4C41),
                                        trackColor = Color(0xFF6D4C41).copy(alpha = 0.12f),
                                        strokeCap = StrokeCap.Round
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${labStats.attended}/${labStats.total} attended",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            when {
                                                labStats.canMiss > 0 -> "😌 Miss ${labStats.canMiss}"
                                                labStats.mustAttend > 0 -> "⚠️ Need ${labStats.mustAttend}"
                                                labStats.total == 0 -> "No data"
                                                else -> "On edge"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (labStats.canMiss > 0) Color(0xFF22C55E)
                                            else if (labStats.mustAttend > 0) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // ---- Today's sessions quick-mark ----
                            if (todaySessions.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                                Text(
                                    "Today's classes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                // Each session gets its own mark row
                                todaySessions.forEach { session ->
                                    val sessionRecord = allRecords.firstOrNull {
                                        it.dayMillis == today && it.sessionId == session.id
                                    }
                                    val sessionColor = if (session.isLab) Color(0xFF6D4C41) else subColor

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Time + type
                                            Column(Modifier.width(72.dp)) {
                                                Text(
                                                    minutesLabel(session.startMinutes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = sessionColor
                                                )
                                                if (session.isLab) {
                                                    Text(
                                                        "LAB",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF6D4C41),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }

                                            // Status indicator
                                            val statusColor = when (sessionRecord?.status) {
                                                "PRESENT" -> Color(0xFF22C55E)
                                                "ABSENT" -> Color(0xFFEF4444)
                                                else -> MaterialTheme.colorScheme.outlineVariant
                                            }
                                            Box(
                                                Modifier.size(8.dp).clip(CircleShape).background(statusColor)
                                            )

                                            // Present / Absent chips
                                            Row(
                                                Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val chipShape = RoundedCornerShape(8.dp)
                                                // Present chip
                                                val presentSelected = sessionRecord?.status == "PRESENT"
                                                Surface(
                                                    onClick = {
                                                        scope.launch {
                                                            repo.dao.markAttendance(
                                                                AttendanceEntity(
                                                                    subjectId = sub.id,
                                                                    dayMillis = today,
                                                                    status = "PRESENT",
                                                                    sessionId = session.id,
                                                                    isLab = session.isLab
                                                                )
                                                            )
                                                        }
                                                    },
                                                    color = if (presentSelected) Color(0xFF22C55E) else Color(0xFF22C55E).copy(alpha = 0.1f),
                                                    shape = chipShape,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        "✓ Present",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                                        textAlign = TextAlign.Center,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (presentSelected) Color.White else Color(0xFF22C55E),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                // Absent chip
                                                val absentSelected = sessionRecord?.status == "ABSENT"
                                                Surface(
                                                    onClick = {
                                                        scope.launch {
                                                            repo.dao.markAttendance(
                                                                AttendanceEntity(
                                                                    subjectId = sub.id,
                                                                    dayMillis = today,
                                                                    status = "ABSENT",
                                                                    sessionId = session.id,
                                                                    isLab = session.isLab
                                                                )
                                                            )
                                                        }
                                                    },
                                                    color = if (absentSelected) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                                    shape = chipShape,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        "✗ Absent",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                                        textAlign = TextAlign.Center,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (absentSelected) Color.White else Color(0xFFEF4444),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            // Undo
                                            if (sessionRecord != null) {
                                                Text(
                                                    "Undo",
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            scope.launch {
                                                                repo.dao.clearAttendance(sub.id, today, session.id)
                                                            }
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Manual mark when no sessions are scheduled today
                                val todayRecord = allRecords.firstOrNull { it.dayMillis == today && it.sessionId == 0L }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Mark today:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val chipShape = RoundedCornerShape(8.dp)
                                    val presentSelected = todayRecord?.status == "PRESENT"
                                    Surface(
                                        onClick = {
                                            scope.launch {
                                                repo.dao.markAttendance(
                                                    AttendanceEntity(sub.id, today, "PRESENT", 0L, false)
                                                )
                                            }
                                        },
                                        color = if (presentSelected) Color(0xFF22C55E) else Color(0xFF22C55E).copy(alpha = 0.1f),
                                        shape = chipShape
                                    ) {
                                        Text(
                                            "✓ Present",
                                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (presentSelected) Color.White else Color(0xFF22C55E),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    val absentSelected = todayRecord?.status == "ABSENT"
                                    Surface(
                                        onClick = {
                                            scope.launch {
                                                repo.dao.markAttendance(
                                                    AttendanceEntity(sub.id, today, "ABSENT", 0L, false)
                                                )
                                            }
                                        },
                                        color = if (absentSelected) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.1f),
                                        shape = chipShape
                                    ) {
                                        Text(
                                            "✗ Absent",
                                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (absentSelected) Color.White else Color(0xFFEF4444),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (todayRecord != null) {
                                        Text(
                                            "Undo",
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    scope.launch {
                                                        repo.dao.clearAttendance(sub.id, today, 0L)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var minPct by remember { mutableStateOf("75") }
        var units by remember { mutableStateOf("5") }
        DialogForm(
            title = "Add subject",
            onDismiss = { showAdd = false },
            saveEnabled = name.isNotBlank(),
            onSave = {
                scope.launch {
                    repo.dao.upsertSubject(
                        SubjectEntity(
                            name = name.trim(),
                            code = code.trim(),
                            colorArgb = SUBJECT_COLORS[subjects.size % SUBJECT_COLORS.size],
                            minAttendance = minPct.toFloatOrNull() ?: 75f,
                            totalUnits = units.toIntOrNull()?.coerceAtLeast(1) ?: 5
                        )
                    )
                }
                showAdd = false
            }
        ) {
            FormField("Subject name", name) { name = it }
            FormField("Code (optional)", code) { code = it }
            FormField("Minimum attendance %", minPct) { minPct = it.filter { c -> c.isDigit() } }
            FormField("Total units / chapters", units, keyboardType = KeyboardType.Number) {
                units = it.filter { c -> c.isDigit() }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { repo.dao.deleteSubject(target) }
                deleteTarget = null
            }
        )
    }
}

// ------------------------------- TASKS / EXAMS -------------------------------

@Composable
fun TasksTab(type: String, includeTasks: Boolean) {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val subjects by repo.dao.subjectsFlow().collectAsStateWithLifecycle(emptyList())
    val tasks by repo.dao.tasksFlow().collectAsStateWithLifecycle(emptyList())
    val subjectMap = subjects.associateBy { it.id }
    val scope = rememberCoroutineScope()

    val shown = tasks.filter {
        when (type) {
            "EXAM" -> it.type == "EXAM"
            else -> it.type == "TASK" || it.type == "ASSIGNMENT"
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (shown.isEmpty()) {
                item { EmptyState(if (type == "EXAM") "No exams added" else "No tasks or assignments") }
            } else {
                items(shown.size) { i ->
                    val t = shown[i]
                    val overdue = t.dueMillis != null && t.dueMillis < System.currentTimeMillis() && t.status == "TODO"
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(1.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (t.status == "DONE")
                                MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.status == "DONE",
                                onCheckedChange = { checked ->
                                    val updated = t.copy(status = if (checked) "DONE" else "TODO")
                                    scope.launch {
                                        repo.dao.upsertTask(updated)
                                        if (checked) ReminderScheduler.cancel(context, requestCodeFor(t.id))
                                    }
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (t.status == "DONE") MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                val subj = t.subjectId?.let { subjectMap[it]?.name }
                                val detail = buildString {
                                    if (subj != null) append(subj).append("  •  ")
                                    if (t.dueMillis != null) {
                                        append(formatDateTime(t.dueMillis))
                                        if (type == "EXAM" || overdue) {
                                            val d = daysUntil(t.dueMillis)
                                            append(if (d >= 0) "  •  in $d day${if (d == 1) "" else "s"}" else "  •  overdue")
                                        }
                                    } else append("No due date")
                                }
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (overdue) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deleteTarget = t }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var subjectId by remember { mutableStateOf<Long?>(null) }
        var dueText by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(if (type == "EXAM") "EXAM" else "ASSIGNMENT") }
        var remind by remember { mutableStateOf("1 hour before") }
        val due = parseDateTime(dueText)
        DialogForm(
            title = if (type == "EXAM") "Add exam" else "Add task / assignment",
            onDismiss = { showAdd = false },
            saveEnabled = title.isNotBlank(),
            onSave = {
                val remindMin = when (remind) {
                    "At due time" -> 0L
                    "1 day before" -> 1440L
                    else -> 60L
                }
                scope.launch {
                    val id = repo.dao.upsertTask(
                        TaskEntity(
                            title = title.trim(), subjectId = subjectId,
                            type = if (type == "EXAM") "EXAM" else kind,
                            dueMillis = due, reminderMinutesBefore = remindMin
                        )
                    )
                    if (due != null) {
                        ReminderScheduler.schedule(
                            context, requestCodeFor(id), title.trim(),
                            "Due ${formatDateTime(due)}", due - remindMin * 60_000
                        )
                    }
                }
                showAdd = false
            }
        ) {
            if (type != "EXAM") {
                Text("Type", style = MaterialTheme.typography.labelMedium)
                ChipRow(listOf("ASSIGNMENT", "TASK"), kind) { kind = it }
            }
            FormField("Title", title) { title = it }
            Text("Subject", style = MaterialTheme.typography.labelMedium)
            SubjectPicker(subjects, subjectId) { subjectId = it }
            FormField("Due date & time (dd/MM/yyyy HH:mm)", dueText) { dueText = it }
            Text("Remind me", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("At due time", "1 hour before", "1 day before"), remind) { remind = it }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    ReminderScheduler.cancel(context, requestCodeFor(target.id))
                    repo.dao.deleteTask(target)
                }
                deleteTarget = null
            }
        )
    }
}
