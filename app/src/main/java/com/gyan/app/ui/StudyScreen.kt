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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Percent
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
import com.gyan.app.data.AttendanceOverrideEntity
import com.gyan.app.data.ClassNoteEntity
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.SessionEntity
import com.gyan.app.data.SubjectEntity
import com.gyan.app.data.TaskEntity
import com.gyan.app.reminders.BootReceiver.Companion.requestCodeFor
import com.gyan.app.reminders.LectureAlarmScheduler
import com.gyan.app.reminders.ReminderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAY_VALUES = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

private fun dateForNextWeekday(dayOfWeek: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    val daysAhead = (dayOfWeek - get(Calendar.DAY_OF_WEEK) + 7) % 7
    add(Calendar.DAY_OF_YEAR, daysAhead)
}.timeInMillis

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
    val attendance by repo.dao.attendanceFlow().collectAsStateWithLifecycle(emptyList())
    val overrides by repo.dao.attendanceOverridesFlow().collectAsStateWithLifecycle(emptyList())
    val subjectMap = subjects.associateBy { it.id }
    val scope = rememberCoroutineScope()

    var selectedDay by remember { mutableStateOf(currentDayOfWeek()) }
    if (!DAY_VALUES.contains(selectedDay)) selectedDay = Calendar.MONDAY
    val dayIndex = DAY_VALUES.indexOf(selectedDay).coerceAtLeast(0)
    val selectedDateMillis = dateForNextWeekday(selectedDay)

    var showAdd by remember { mutableStateOf(false) }
    var showAddOneOff by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var editTarget by remember { mutableStateOf<SessionEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // One-off / makeup class button
                Surface(
                    onClick = { showAddOneOff = true },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Makeup class",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add class", tint = Color.White)
                }
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

            // Show recurring sessions and upcoming one-off classes on the
            // selected weekday; each makeup class card also shows its date.
            val daySessions = sessions.filter { session ->
                val oneOffDate = session.oneOffDateMillis
                if (oneOffDate != null) {
                    oneOffDate >= selectedDateMillis &&
                        Calendar.getInstance().apply { timeInMillis = oneOffDate }.get(Calendar.DAY_OF_WEEK) == selectedDay
                } else {
                    session.dayOfWeek == selectedDay
                }
            }.sortedBy { it.startMinutes }
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
                                        if (s.oneOffDateMillis != null) {
                                            Surface(
                                                color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "MAKEUP • ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(s.oneOffDateMillis))}",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF7C3AED),
                                                    fontWeight = FontWeight.Bold
                                                )
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
                                IconButton(onClick = { editTarget = s }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit class", tint = MaterialTheme.colorScheme.primary)
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
            item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
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
                    val allSessions = repo.dao.sessionsOnce()
                    val allAtt = repo.dao.attendanceOnce()
                    val allOv = repo.dao.attendanceOverridesOnce()
                    LectureAlarmScheduler.reschedule(context, allSessions, subjects, allAtt, allOv)
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

    // ── Add one-off / makeup class ──────────────────────────────────────────
    if (showAddOneOff) {
        var subjectId by remember { mutableStateOf<Long?>(subjects.firstOrNull()?.id) }
        var start by remember { mutableStateOf("09:00") }
        var end by remember { mutableStateOf("10:00") }
        var room by remember { mutableStateOf("") }
        var isLab by remember { mutableStateOf(false) }
        // Date as "dd/MM/yyyy"
        var dateText by remember {
            mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
        }
        val startMin = parseMinutes(start)
        val endMin   = parseMinutes(end)
        val parsedDate = runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).let { sdf ->
                sdf.isLenient = false
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(dateText)!!
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }.getOrNull()
        DialogForm(
            title = "Add one-off / makeup class",
            onDismiss = { showAddOneOff = false },
            saveEnabled = subjectId != null && startMin != null && endMin != null
                    && endMin!! > startMin!! && parsedDate != null && parsedDate >= todayStartMillis(),
            onSave = {
                scope.launch {
                    repo.dao.upsertSession(
                        SessionEntity(
                            subjectId = subjectId!!,
                            dayOfWeek = Calendar.MONDAY, // ignored for one-off
                            startMinutes = startMin!!,
                            endMinutes = endMin!!,
                            room = room.trim(),
                            isLab = isLab,
                            oneOffDateMillis = parsedDate
                        )
                    )
                    val allSessions = repo.dao.sessionsOnce()
                    val allAtt = repo.dao.attendanceOnce()
                    val allOv = repo.dao.attendanceOverridesOnce()
                    LectureAlarmScheduler.reschedule(context, allSessions, subjects, allAtt, allOv)
                }
                showAddOneOff = false
            }
        ) {
            Text("Subject", style = MaterialTheme.typography.labelMedium)
            SubjectPicker(subjects, subjectId) { subjectId = it }
            FormField("Date (dd/MM/yyyy)", dateText) { dateText = it }
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

    editTarget?.let { original ->
        var subjectId by remember(original.id) { mutableStateOf<Long?>(original.subjectId) }
        var day by remember(original.id) { mutableStateOf(original.dayOfWeek) }
        var start by remember(original.id) { mutableStateOf(minutesLabel(original.startMinutes)) }
        var end by remember(original.id) { mutableStateOf(minutesLabel(original.endMinutes)) }
        var room by remember(original.id) { mutableStateOf(original.room) }
        var isLab by remember(original.id) { mutableStateOf(original.isLab) }
        var dateText by remember(original.id) {
            mutableStateOf(original.oneOffDateMillis?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "")
        }
        val startMin = parseMinutes(start)
        val endMin = parseMinutes(end)
        val dateMillis = if (original.oneOffDateMillis == null) null else runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
                .parse(dateText)?.let { Calendar.getInstance().apply { time = it; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis }
        }.getOrNull()
        DialogForm(
            title = "Edit class",
            onDismiss = { editTarget = null },
            saveEnabled = subjectId != null && startMin != null && endMin != null && endMin!! > startMin!! &&
                (original.oneOffDateMillis == null || (dateMillis != null && dateMillis >= todayStartMillis())),
            onSave = {
                scope.launch {
                    repo.dao.upsertSession(original.copy(
                        subjectId = subjectId!!,
                        dayOfWeek = day,
                        startMinutes = startMin!!,
                        endMinutes = endMin!!,
                        room = room.trim(),
                        isLab = isLab,
                        oneOffDateMillis = dateMillis
                    ))
                    LectureAlarmScheduler.reschedule(context, repo.dao.sessionsOnce(), repo.dao.subjectsOnce(), repo.dao.attendanceOnce(), repo.dao.attendanceOverridesOnce())
                }
                editTarget = null
            }
        ) {
            Text("Subject", style = MaterialTheme.typography.labelMedium)
            SubjectPicker(subjects, subjectId) { subjectId = it }
            if (original.oneOffDateMillis == null) {
                Text("Weekday", style = MaterialTheme.typography.labelMedium)
                ChipRow(DAY_LABELS, DAY_LABELS[DAY_VALUES.indexOf(day).coerceAtLeast(0)]) { day = DAY_VALUES[DAY_LABELS.indexOf(it)] }
            } else FormField("Date (dd/MM/yyyy)", dateText) { dateText = it }
            FormField("Start time (HH:MM)", start) { start = it }
            FormField("End time (HH:MM)", end) { end = it }
            FormField("Room", room) { room = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isLab, onCheckedChange = { isLab = it })
                Text("Lab session (tracked separately)")
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = "this class",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    LectureAlarmScheduler.cancelSessions(context, listOf(target))
                    repo.dao.deleteClassNotesForSession(target.id)
                    repo.dao.deleteSession(target)
                    val allSessions = repo.dao.sessionsOnce()
                    val allAtt = repo.dao.attendanceOnce()
                    val allOv = repo.dao.attendanceOverridesOnce()
                    LectureAlarmScheduler.reschedule(context, allSessions, subjects, allAtt, allOv)
                }
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
    val overrides by repo.dao.attendanceOverridesFlow().collectAsStateWithLifecycle(emptyList())
    val classNotes by repo.dao.classNotesFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    suspend fun refreshLectureReminders() {
        LectureAlarmScheduler.reschedule(
            context,
            repo.dao.sessionsOnce(),
            repo.dao.subjectsOnce(),
            repo.dao.attendanceOnce(),
            repo.dao.attendanceOverridesOnce()
        )
    }

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SubjectEntity?>(null) }
    var editSubjectTarget by remember { mutableStateOf<SubjectEntity?>(null) }
    var noteTarget by remember { mutableStateOf<SessionEntity?>(null) }
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
            // ── Mid-semester info banner ──────────────────────────────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📊", fontSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Started mid-semester?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "Tap ✏️ on a subject card to enter your current attendance percentage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            items(subjects.size) { i ->
                val sub = subjects[i]
                val allRecords = attendance.filter { it.subjectId == sub.id }

                // Split theory vs lab records
                val theoryRecords = allRecords.filter { !it.isLab }
                val labRecords = allRecords.filter { it.isLab }

                val theoryOverride = overrides.firstOrNull { it.subjectId == sub.id && !it.isLab }
                val labOverride = overrides.firstOrNull { it.subjectId == sub.id && it.isLab }
                val theoryStats = attendanceStats(theoryRecords, sub.minAttendance, theoryOverride)
                val hasLab = sessions.any { it.subjectId == sub.id && it.isLab }
                val labStats = if (hasLab) attendanceStats(labRecords, sub.minAttendance, labOverride) else null

                // Today's sessions for this subject
                val todaySessions = sessions.filter {
                    it.subjectId == sub.id &&
                        if (it.oneOffDateMillis != null) it.oneOffDateMillis == today
                        else it.dayOfWeek == todayDow
                }
                    .sortedBy { it.startMinutes }

                val subColor = Color(sub.colorArgb.toInt())
                val pctColor = when {
                    theoryStats.total == 0 && theoryOverride == null -> MaterialTheme.colorScheme.onSurfaceVariant
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
                                            if (theoryOverride?.startingPercent != null && theoryOverride.totalBefore == 0) {
                                                Text("Reported", style = MaterialTheme.typography.labelSmall, color = pctColor.copy(alpha = 0.8f), fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    // Lab percentage badge if applicable
                                    if (hasLab && labStats != null) {
                                        val labPctColor = when {
                    labStats.total == 0 && labOverride == null -> MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    color = labPctColor,
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
                                    // Edit override button
                                    var showOverrideDialog by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { editSubjectTarget = sub },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit subject details", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = { showOverrideDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Percent,
                                            contentDescription = "Set starting attendance",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (showOverrideDialog) {
                                        val existingTheory = overrides.firstOrNull { it.subjectId == sub.id && !it.isLab }
                                        val existingLab    = overrides.firstOrNull { it.subjectId == sub.id && it.isLab }
                                        var theoryPct by remember {
                                            mutableStateOf(existingTheory?.let {
                                                (it.startingPercent ?: if (it.totalBefore == 0) 0f else it.attendedBefore * 100f / it.totalBefore).toInt().toString()
                                            } ?: "")
                                        }
                                        var labPct by remember {
                                            mutableStateOf(existingLab?.let {
                                                (it.startingPercent ?: if (it.totalBefore == 0) 0f else it.attendedBefore * 100f / it.totalBefore).toInt().toString()
                                            } ?: "")
                                        }
                                        var theoryTotal by remember { mutableStateOf(existingTheory?.totalBefore?.takeIf { it > 0 }?.toString() ?: "") }
                                        var labTotal by remember { mutableStateOf(existingLab?.totalBefore?.takeIf { it > 0 }?.toString() ?: "") }
                                        val theoryValue = theoryPct.toIntOrNull()?.takeIf { it in 0..100 }
                                        val labValue = labPct.toIntOrNull()?.takeIf { it in 0..100 }
                                        val theoryCount = theoryTotal.toIntOrNull()?.takeIf { it > 0 }
                                        val labCount = labTotal.toIntOrNull()?.takeIf { it > 0 }
                                        DialogForm(
                                            title = "Starting attendance — ${sub.name}",
                                            onDismiss = { showOverrideDialog = false },
                                            saveEnabled = (theoryPct.isBlank() || (theoryValue != null && (theoryTotal.isBlank() || theoryCount != null))) &&
                                                (!hasLab || labPct.isBlank() || (labValue != null && (labTotal.isBlank() || labCount != null))),
                                            onSave = {
                                                scope.launch {
                                                    if (theoryValue == null) {
                                                        repo.dao.clearAttendanceOverride(sub.id, false)
                                                    } else {
                                                        repo.dao.upsertAttendanceOverride(
                                                            AttendanceOverrideEntity(
                                                                subjectId = sub.id, isLab = false,
                                                                attendedBefore = theoryCount?.let { kotlin.math.round(theoryValue * it / 100f).toInt() } ?: 0,
                                                                totalBefore = theoryCount ?: 0,
                                                                startingPercent = if (theoryCount == null) theoryValue.toFloat() else null
                                                            )
                                                        )
                                                    }
                                                    if (hasLab) {
                                                        if (labValue == null) {
                                                            repo.dao.clearAttendanceOverride(sub.id, true)
                                                        } else {
                                                            repo.dao.upsertAttendanceOverride(
                                                                AttendanceOverrideEntity(
                                                                    subjectId = sub.id, isLab = true,
                                                                    attendedBefore = labCount?.let { kotlin.math.round(labValue * it / 100f).toInt() } ?: 0,
                                                                    totalBefore = labCount ?: 0,
                                                                    startingPercent = if (labCount == null) labValue.toFloat() else null
                                                                )
                                                            )
                                                        }
                                                    }
                                                    refreshLectureReminders()
                                                }
                                                showOverrideDialog = false
                                            }
                                        ) {
                                            Text(
                                                "Enter the teacher-reported percentage. If you know how many classes had happened, add that count for accurate skip forecasts. If unknown, GYAN won't guess how many you can miss.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text("Theory attendance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            FormField("Attendance before using GYAN (%)", theoryPct, keyboardType = KeyboardType.Number) { theoryPct = it.filter(Char::isDigit).take(3) }
                                            FormField("Classes held so far (optional)", theoryTotal, keyboardType = KeyboardType.Number) { theoryTotal = it.filter(Char::isDigit).take(5) }
                                            if (hasLab) {
                                                Text("Lab attendance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                FormField("Lab attendance before using GYAN (%)", labPct, keyboardType = KeyboardType.Number) { labPct = it.filter(Char::isDigit).take(3) }
                                                FormField("Lab sessions held so far (optional)", labTotal, keyboardType = KeyboardType.Number) { labTotal = it.filter(Char::isDigit).take(5) }
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
                                        if (theoryOverride != null) {
                                        "${theoryRecords.size} tracked · ${"%.1f".format(theoryStats.pct)}% reported · min ${sub.minAttendance.toInt()}%"
                                        } else {
                                            "${theoryStats.attended}/${theoryStats.total} attended  •  min ${sub.minAttendance.toInt()}%"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        when {
                                            !theoryStats.forecastAvailable && theoryOverride != null -> "Add class count for forecast"
                                            theoryStats.canMiss > 0 -> "😌 Miss ${theoryStats.canMiss}"
                                            theoryStats.mustAttend > 0 -> "⚠️ Attend ${theoryStats.mustAttend} in a row"
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
                                    labStats.total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
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
                                            if (labOverride != null) {
                                                "${labRecords.size} lab sessions tracked  •  ${"%.1f".format(labStats.pct)}%"
                                            } else {
                                                "${labStats.attended}/${labStats.total} attended"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                        when {
                                            !labStats.forecastAvailable && labOverride != null -> "Add class count for forecast"
                                            labStats.canMiss > 0 -> "😌 Miss ${labStats.canMiss}"
                                            labStats.mustAttend > 0 -> "⚠️ Attend ${labStats.mustAttend} in a row"
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
                                                            refreshLectureReminders()
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
                                                            refreshLectureReminders()
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
                                                                refreshLectureReminders()
                                                            }
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                if (classNotes.any { it.subjectId == sub.id && it.sessionId == session.id && it.dayMillis == today }) "Note" else "+ Topic",
                                                modifier = Modifier.clickable { noteTarget = session },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    classNotes.firstOrNull { it.subjectId == sub.id && it.sessionId == session.id && it.dayMillis == today }
                                        ?.let { note ->
                                            Text("📖 ${note.topic}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                refreshLectureReminders()
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
                                                refreshLectureReminders()
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
                                                        refreshLectureReminders()
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
                if (i == (subjects.size - 1) / 2) ContactUsCard()
            }
        }
        item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var semester by remember { mutableStateOf("") }
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
                            semester = semester.trim(),
                            colorArgb = SUBJECT_COLORS[subjects.size % SUBJECT_COLORS.size],
                            minAttendance = minPct.toFloatOrNull()?.coerceIn(0f, 100f) ?: 75f,
                            totalUnits = units.toIntOrNull()?.coerceAtLeast(1) ?: 5
                        )
                    )
                }
                showAdd = false
            }
        ) {
            FormField("Subject name", name) { name = it }
            FormField("Code (optional)", code) { code = it }
            FormField("Semester / term (optional)", semester) { semester = it }
            FormField("Minimum attendance %", minPct) { minPct = it.filter { c -> c.isDigit() } }
            FormField("Total units / chapters", units, keyboardType = KeyboardType.Number) {
                units = it.filter { c -> c.isDigit() }
            }
        }
    }

    editSubjectTarget?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var code by remember(original.id) { mutableStateOf(original.code) }
        var semester by remember(original.id) { mutableStateOf(original.semester) }
        var minPct by remember(original.id) { mutableStateOf(original.minAttendance.toInt().toString()) }
        var units by remember(original.id) { mutableStateOf(original.totalUnits.toString()) }
        var color by remember(original.id) { mutableStateOf(original.colorArgb) }
        DialogForm(
            title = "Edit subject",
            onDismiss = { editSubjectTarget = null },
            saveEnabled = name.isNotBlank() && (minPct.toFloatOrNull()?.let { it in 0f..100f } == true) && (units.toIntOrNull()?.let { it > 0 } == true),
            onSave = {
                scope.launch {
                    repo.dao.upsertSubject(original.copy(name = name.trim(), code = code.trim(), semester = semester.trim(), minAttendance = minPct.toFloat(), totalUnits = units.toInt(), colorArgb = color))
                    LectureAlarmScheduler.reschedule(context, repo.dao.sessionsOnce(), repo.dao.subjectsOnce(), repo.dao.attendanceOnce(), repo.dao.attendanceOverridesOnce())
                }
                editSubjectTarget = null
            }
        ) {
            FormField("Subject name", name) { name = it }
            FormField("Code", code) { code = it }
            FormField("Semester / term", semester) { semester = it }
            FormField("Minimum attendance %", minPct, keyboardType = KeyboardType.Number) { minPct = it.filter { c -> c.isDigit() || c == '.' }.take(5) }
            FormField("Total units / chapters", units, keyboardType = KeyboardType.Number) { units = it.filter(Char::isDigit).take(3) }
            Text("Subject color", style = MaterialTheme.typography.labelMedium)
            ChipRow(SUBJECT_COLORS.indices.map { "Color ${it + 1}" }, "Color ${(SUBJECT_COLORS.indexOf(color).takeIf { it >= 0 } ?: 0) + 1}") {
                color = SUBJECT_COLORS[it.removePrefix("Color ").toInt() - 1]
            }
        }
    }

    noteTarget?.let { session ->
        val oldNote = classNotes.firstOrNull { it.subjectId == session.subjectId && it.sessionId == session.id && it.dayMillis == today }
        var topic by remember(session.id, oldNote?.updatedAt) { mutableStateOf(oldNote?.topic.orEmpty()) }
        DialogForm(
            title = "Class topic · ${subjects.firstOrNull { it.id == session.subjectId }?.name.orEmpty()}",
            onDismiss = { noteTarget = null },
            onSave = {
                scope.launch {
                    if (topic.isBlank()) repo.dao.deleteClassNote(session.subjectId, session.id, today)
                    else repo.dao.upsertClassNote(ClassNoteEntity(session.subjectId, session.id, today, topic.trim()))
                }
                noteTarget = null
            },
            saveEnabled = true
        ) {
            Text("Write what was covered in this meeting. Leave it blank to clear the note.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormField("Today's topic", topic, singleLine = false) { topic = it }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch {
                    LectureAlarmScheduler.cancelSessions(context, sessions.filter { it.subjectId == target.id })
                    repo.dao.deleteClassNotesForSubject(target.id)
                    repo.dao.deleteSubject(target)
                    refreshLectureReminders()
                }
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

    val typedTasks = tasks.filter {
        when (type) {
            "EXAM" -> it.type == "EXAM"
            else -> it.type == "TASK" || it.type == "ASSIGNMENT"
        }
    }
    var taskFilter by remember(type) { mutableStateOf("Pending") }
    val shown = typedTasks.filter {
        when (taskFilter) {
            "Done" -> it.status == "DONE"
            "All" -> true
            else -> it.status != "DONE"
        }
    }.sortedWith(compareBy<TaskEntity> { it.status == "DONE" }.thenBy { it.dueMillis ?: Long.MAX_VALUE })
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }
    var editTaskTarget by remember { mutableStateOf<TaskEntity?>(null) }

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
            item {
                Text(if (type == "EXAM") "Exams" else "Tasks & assignments",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    listOf("Pending ${typedTasks.count { it.status != "DONE" }}", "Done ${typedTasks.count { it.status == "DONE" }}", "All ${typedTasks.size}"),
                    when (taskFilter) {
                        "Pending" -> "Pending ${typedTasks.count { it.status != "DONE" }}"
                        "Done" -> "Done ${typedTasks.count { it.status == "DONE" }}"
                        else -> "All ${typedTasks.size}"
                    }
                ) { selected -> taskFilter = selected.substringBefore(' ') }
            }
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
                            IconButton(onClick = { editTaskTarget = t }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit task", tint = MaterialTheme.colorScheme.primary)
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
            item { ContactUsCard(Modifier.padding(vertical = 8.dp)) }
            item { Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd || editTaskTarget != null) {
        val original = editTaskTarget
        var title by remember(original?.id) { mutableStateOf(original?.title.orEmpty()) }
        var subjectId by remember(original?.id) { mutableStateOf(original?.subjectId) }
        var dueText by remember(original?.id) { mutableStateOf(original?.dueMillis?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) }.orEmpty()) }
        var notes by remember(original?.id) { mutableStateOf(original?.notes.orEmpty()) }
        var kind by remember(original?.id) { mutableStateOf(original?.type ?: if (type == "EXAM") "EXAM" else "ASSIGNMENT") }
        var remind by remember(original?.id) { mutableStateOf(when (original?.reminderMinutesBefore) { 0L -> "At due time"; 1440L -> "1 day before"; else -> "1 hour before" }) }
        val due = parseDateTime(dueText)
        DialogForm(
            title = if (original != null) "Edit ${if (type == "EXAM") "exam" else "task"}" else if (type == "EXAM") "Add exam" else "Add task / assignment",
            onDismiss = { showAdd = false; editTaskTarget = null },
            saveEnabled = title.isNotBlank(),
            onSave = {
                val remindMin = when (remind) {
                    "At due time" -> 0L
                    "1 day before" -> 1440L
                    else -> 60L
                }
                scope.launch {
                    original?.let { ReminderScheduler.cancel(context, requestCodeFor(it.id)) }
                    val task = original?.copy(title = title.trim(), subjectId = subjectId, type = if (type == "EXAM") "EXAM" else kind, dueMillis = due, notes = notes.trim(), reminderMinutesBefore = remindMin)
                        ?: TaskEntity(title = title.trim(), subjectId = subjectId, type = if (type == "EXAM") "EXAM" else kind, dueMillis = due, notes = notes.trim(), reminderMinutesBefore = remindMin)
                    val saved = repo.dao.upsertTask(task)
                    val id = if (original != null) original.id else saved
                    if (due != null && task.status != "DONE") {
                        ReminderScheduler.schedule(
                            context, requestCodeFor(id), "📚 Keep moving: ${title.trim()}",
                            "You still have this task to finish · Due ${formatDateTime(due)}", due - remindMin * 60_000
                        )
                    }
                }
                showAdd = false
                editTaskTarget = null
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
            FormField("Notes", notes, singleLine = false) { notes = it }
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
