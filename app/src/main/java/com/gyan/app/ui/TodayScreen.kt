package com.gyan.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.gyan.app.data.GyanRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(nav: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { GyanRepository.get(context) }

    val subjects by repo.dao.subjectsFlow().collectAsStateWithLifecycle(emptyList())
    val sessions by repo.dao.sessionsFlow().collectAsStateWithLifecycle(emptyList())
    val attendance by repo.dao.attendanceFlow().collectAsStateWithLifecycle(emptyList())
    val tasks by repo.dao.tasksFlow().collectAsStateWithLifecycle(emptyList())
    val expenses by repo.dao.expensesFlow().collectAsStateWithLifecycle(emptyList())
    val subscriptions by repo.dao.subscriptionsFlow().collectAsStateWithLifecycle(emptyList())
    val budgets by repo.dao.budgetsFlow().collectAsStateWithLifecycle(emptyList())

    val subjectMap = subjects.associateBy { it.id }
    val todaySessions = sessions.filter { it.dayOfWeek == currentDayOfWeek() }.sortedBy { it.startMinutes }
    val startToday = todayStartMillis()
    val upcoming = tasks
        .filter { it.status == "TODO" && it.dueMillis != null && it.dueMillis <= startToday + 7L * 86400000 }
        .sortedBy { it.dueMillis }
    val todaySpent = expenses
        .filter { !it.isIncome && it.dateMillis >= startToday && it.dateMillis < startToday + 86400000 }
        .sumOf { it.amount.toDouble() }.toFloat()
    val monthStart = monthStartMillis()
    val monthSpent = expenses
        .filter { !it.isIncome && it.dateMillis >= monthStart }
        .sumOf { it.amount.toDouble() }.toFloat()
    val totalBudget = budgets.firstOrNull { it.category == "TOTAL" }?.monthlyAmount ?: 0f
    val soonRenewals = subscriptions.filter {
        it.nextRenewalMillis - System.currentTimeMillis() < 14L * 86400000
    }.sortedBy { it.nextRenewalMillis }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ---- Hero greeting card ----
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                greeting() + "! 👋",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { nav.navigate("settings") },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Stats row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Classes today
                        HeroStatCard(
                            modifier = Modifier.weight(1f),
                            value = todaySessions.size.toString(),
                            label = "Classes today",
                            emoji = "📚"
                        )
                        // Tasks due soon
                        HeroStatCard(
                            modifier = Modifier.weight(1f),
                            value = upcoming.size.toString(),
                            label = "Due soon",
                            emoji = "⏰"
                        )
                        // Spent today
                        HeroStatCard(
                            modifier = Modifier.weight(1f),
                            value = formatMoney(todaySpent),
                            label = "Today",
                            emoji = "💰",
                            small = true
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ---- Today's Classes ----
        item {
            DashboardSection(
                title = "Today's Classes",
                subtitle = if (todaySessions.isEmpty()) "Free day! 🎉" else "${todaySessions.size} lecture${if (todaySessions.size != 1) "s" else ""}",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (todaySessions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No classes scheduled today 🎉",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    todaySessions.forEach { s ->
                        val sub = subjectMap[s.subjectId]
                        val subColor = sub?.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.elevatedCardElevation(1.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(64.dp)
                                        .background(
                                            subColor,
                                            RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                                        )
                                )
                                Row(
                                    Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                sub?.name ?: "Unknown",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (s.isLab) {
                                                Surface(
                                                    color = Color(0xFF6D4C41).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "LAB",
                                                        Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF6D4C41),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "${minutesLabel(s.startMinutes)} – ${minutesLabel(s.endMinutes)}" +
                                                if (s.room.isNotBlank()) "  •  Room ${s.room}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Time pill
                                    Surface(
                                        color = subColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            minutesLabel(s.startMinutes),
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = subColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ---- Upcoming Deadlines ----
        item {
            DashboardSection(
                title = "Upcoming Deadlines",
                subtitle = "Next 7 days",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (upcoming.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Nothing due soon. Enjoy! 😊",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    upcoming.take(4).forEach { t ->
                        val due = t.dueMillis ?: 0
                        val isOverdue = due < System.currentTimeMillis()
                        val daysLeft = daysUntil(due)
                        val urgencyColor = when {
                            isOverdue -> Color(0xFFEF4444)
                            daysLeft <= 1 -> Color(0xFFFFA726)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.elevatedCardElevation(1.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Type badge
                                Surface(
                                    color = if (t.type == "EXAM") Color(0xFF7C3AED).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (t.type == "EXAM") "📝" else "📋",
                                        Modifier.padding(8.dp),
                                        fontSize = 16.sp
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subj = t.subjectId?.let { subjectMap[it]?.name }
                                    Text(
                                        buildString {
                                            if (subj != null) append("$subj  •  ")
                                            append(formatDate(due))
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = urgencyColor
                                    )
                                }
                                // Countdown
                                Surface(
                                    color = urgencyColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (isOverdue) "Overdue" else if (daysLeft == 0) "Today" else "${daysLeft}d",
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = urgencyColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    if (upcoming.size > 4) {
                        Text(
                            "+ ${upcoming.size - 4} more in Study tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ---- Attendance Overview ----
        item {
            DashboardSection(
                title = "Attendance Overview",
                subtitle = "${subjects.size} subject${if (subjects.size != 1) "s" else ""}",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (subjects.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Add subjects in the Study tab",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        subjects.forEach { sub ->
                            val theoryRecords = attendance.filter { it.subjectId == sub.id && !it.isLab }
                            val stats = attendanceStats(theoryRecords, sub.minAttendance)
                            val subColor = Color(sub.colorArgb.toInt())
                            val pctColor = when {
                                stats.pct >= sub.minAttendance -> Color(0xFF22C55E)
                                stats.pct >= sub.minAttendance - 5f -> Color(0xFFFFA726)
                                else -> Color(0xFFEF4444)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            Modifier.size(8.dp).clip(CircleShape).background(subColor)
                                        )
                                        Text(
                                            sub.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "%.1f%%".format(stats.pct),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = pctColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${stats.attended}/${stats.total}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (stats.pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = pctColor,
                                    trackColor = pctColor.copy(alpha = 0.12f),
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Budget snapshot ----
        if (totalBudget > 0f) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                DashboardSection(
                    title = "Budget This Month",
                    subtitle = "${formatMoney(monthSpent)} of ${formatMoney(totalBudget)}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    val budgetPct = (monthSpent / totalBudget).coerceIn(0f, 1f)
                    val budgetColor = when {
                        budgetPct < 0.7f -> Color(0xFF22C55E)
                        budgetPct < 0.9f -> Color(0xFFFFA726)
                        else -> Color(0xFFEF4444)
                    }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Spent",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(budgetPct * 100).toInt()}% used",
                                style = MaterialTheme.typography.bodyMedium,
                                color = budgetColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { budgetPct },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = budgetColor,
                            trackColor = budgetColor.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                formatMoney(monthSpent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${formatMoney((totalBudget - monthSpent).coerceAtLeast(0f))} left",
                                style = MaterialTheme.typography.bodySmall,
                                color = budgetColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ---- Soon renewals ----
        if (soonRenewals.isNotEmpty()) {
            item { Spacer(Modifier.height(16.dp)) }
            item {
                DashboardSection(
                    title = "Renewals Due Soon",
                    subtitle = "${soonRenewals.size} upcoming",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    soonRenewals.forEach { s ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.elevatedCardElevation(1.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("🔔", Modifier.padding(8.dp), fontSize = 16.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${formatMoney(s.amount)}  •  ${formatDate(s.nextRenewalMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val d = daysUntil(s.nextRenewalMillis).coerceAtLeast(0)
                                Surface(
                                    color = if (d <= 3) Color(0xFFEF4444).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${d}d",
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (d <= 3) Color(0xFFEF4444)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun HeroStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    emoji: String,
    small: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(
                value,
                style = if (small) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = if (small) 14.sp else 22.sp
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DashboardSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
