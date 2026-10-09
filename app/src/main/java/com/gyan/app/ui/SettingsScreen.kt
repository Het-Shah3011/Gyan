package com.gyan.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gyan.app.backup.BackupManager
import com.gyan.app.BuildConfig
import com.gyan.app.data.GyanRepository
import com.gyan.app.reminders.LectureAlarmScheduler
import com.gyan.app.updates.ApkInstaller
import com.gyan.app.updates.GitHubUpdates
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    themeMode: String = "dark",
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateMessage by remember { mutableStateOf("Check GitHub for the latest release.") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var apkDownloadUrl by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var classRemindersEnabled by remember {
        mutableStateOf(context.getSharedPreferences("gyan_preferences", android.content.Context.MODE_PRIVATE)
            .getBoolean("class_reminders_enabled", true))
    }

    // ── State for import choice dialog ──────────────────────────────────────
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var exportTimetableOnly by remember { mutableStateOf(false) }

    // ── Export launcher ──────────────────────────────────────────────────────
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = if (exportTimetableOnly) {
                    BackupManager.exportTimetable(context, it)
                } else {
                    BackupManager.export(context, it)
                }
                Toast.makeText(
                    context,
                    if (!ok) "Export failed"
                    else if (exportTimetableOnly) "Timetable exported ✅ (.het)"
                    else "Backup exported ✅ (.het)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Import launcher ──────────────────────────────────────────────────────
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showImportChoiceDialog = true
        }
    }

    // ── Import choice dialog ─────────────────────────────────────────────────
    if (showImportChoiceDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportChoiceDialog = false
                pendingImportUri = null
            },
            title = { Text("Import .het file") },
            text = {
                Text(
                    "Timetable only replaces the shared schedule and subjects while keeping your attendance, tasks, money and files. Full restore replaces all local data."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingImportUri!!
                    showImportChoiceDialog = false
                    pendingImportUri = null
                    scope.launch {
                        val result = BackupManager.importTimetableOnly(context, uri)
                        when (result) {
                            is com.gyan.app.backup.ImportResult.Success ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            is com.gyan.app.backup.ImportResult.Error ->
                                Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Timetable only") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val uri = pendingImportUri!!
                        showImportChoiceDialog = false
                        pendingImportUri = null
                        scope.launch {
                            val result = BackupManager.import(context, uri)
                            when (result) {
                                is com.gyan.app.backup.ImportResult.Success ->
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                is com.gyan.app.backup.ImportResult.Error ->
                                    Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Full restore") }
                    TextButton(onClick = {
                        showImportChoiceDialog = false
                        pendingImportUri = null
                    }) { Text("Cancel") }
                }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Text("Choose the look that feels right.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val labels = listOf("Dark", "Light", "System")
                ChipRow(labels, themeMode.replaceFirstChar { it.uppercase() }) {
                    onThemeModeChange(it.lowercase())
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Class reminders", style = MaterialTheme.typography.titleMedium)
                    Text("10-minute timetable alerts with attendance impact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = classRemindersEnabled, onCheckedChange = { enabled ->
                    classRemindersEnabled = enabled
                    context.getSharedPreferences("gyan_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("class_reminders_enabled", enabled).apply()
                    scope.launch {
                        val dao = GyanRepository.get(context).dao
                        LectureAlarmScheduler.reschedule(
                            context, dao.sessionsOnce(), dao.subjectsOnce(),
                            dao.attendanceOnce(), dao.attendanceOverridesOnce()
                        )
                    }
                })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                Text(updateMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (downloading) {
                    if (downloadProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Button(
                    onClick = {
                        checkingUpdate = true
                        updateMessage = "Checking GitHub Releases..."
                        updateUrl = null
                        apkDownloadUrl = null
                        scope.launch {
                            runCatching { GitHubUpdates.latest(BuildConfig.VERSION_NAME) }
                                .onSuccess { update ->
                                    updateUrl = update?.releaseUrl
                                    apkDownloadUrl = update?.apkDownloadUrl
                                    updateMessage = if (update == null)
                                        "You're up to date (v${BuildConfig.VERSION_NAME})."
                                    else if (update.apkDownloadUrl != null)
                                        "GYAN ${update.version} is available! Tap below to download & install."
                                    else
                                        "GYAN ${update.version} is available. Open the release page to download."
                                }
                                .onFailure { updateMessage = "Could not check right now. Try again while online." }
                            checkingUpdate = false
                        }
                    },
                    enabled = !checkingUpdate && !downloading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (checkingUpdate) "Checking..." else "Check for updates") }

                apkDownloadUrl?.let { apkUrl ->
                    Button(
                        onClick = {
                            downloading = true
                            downloadProgress = 0f
                            updateMessage = "Downloading update..."
                            scope.launch {
                                runCatching {
                                    ApkInstaller.downloadAndInstall(context, apkUrl) { dl, total ->
                                        downloadProgress = if (total > 0) dl.toFloat() / total else 0f
                                        val pct = if (total > 0) " (${(downloadProgress * 100).toInt()}%)" else ""
                                        updateMessage = "Downloading update$pct..."
                                    }
                                }.onSuccess {
                                    updateMessage = "Install prompt opened. Follow the on-screen steps."
                                }.onFailure {
                                    updateMessage = "Download failed: ${it.message}"
                                }
                                downloading = false
                                downloadProgress = 0f
                            }
                        },
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (downloading) "Downloading..." else "Download & Install update") }
                }

                updateUrl?.let { url ->
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open GitHub release page") }
                }
            }
        }

        // ── Backup & restore card ────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup & restore (.het)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everything is stored locally. Share a timetable-only .het file with classmates, or export a full backup for yourself. Timetable-only import keeps each person's attendance, files, tasks and money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    exportTimetableOnly = false
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    exportLauncher.launch("gyan_backup_$stamp.het")
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export full backup (.het)")
                }
                OutlinedButton(
                    onClick = {
                        exportTimetableOnly = true
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportLauncher.launch("gyan_timetable_$stamp.het")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export timetable to share (.het)")
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/json",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import .het file")
                }
                Text(
                    "💡 Choose 'Timetable only' to share a schedule without overwriting personal attendance or other data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── About card ───────────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About GYAN", style = MaterialTheme.typography.titleMedium)
                Text(
                    "GYAN v${BuildConfig.VERSION_NAME} — your student life planner.\n" +
                        "• Timetable with 10-min lecture notifications\n" +
                        "• Attendance tracker with skip-safety warnings\n" +
                        "• Mid-semester attendance override\n" +
                        "• Per-class topic notes\n" +
                        "• No third-party ads, trackers, or account required\n" +
                        "• Tasks, exams with reminders\n" +
                        "• Money tracker with budgets\n" +
                        "• Subscriptions, warranties, scholarships\n" +
                        "• Share files into the inbox\n" +
                        "• .het backup — share timetables with classmates\n" +
                        "• Personal records stay on-device — no GYAN account or server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Credits card ─────────────────────────────────────────────────────
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Crafted with ❤️",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            "Created by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "Het Shah",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    "A passionate developer who turned the chaos of student life into a beautifully crafted app. " +
                        "Het Shah built GYAN from the ground up — every feature, every screen, every line of code — " +
                        "with one mission: to make student life simpler, smarter, and stress-free. 🚀",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Het-Shah3011/Gyan")
                            )
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            "Open Source on GitHub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "github.com/Het-Shah3011/Gyan",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "If GYAN helps you, give it a ⭐ on GitHub — it means the world to Het!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // ── Tips card ────────────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tips", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Share a PDF from WhatsApp → choose GYAN → it appears in the Inbox\n" +
                        "• Grant notification permission so lecture alarms fire\n" +
                        "• .het files = JSON inside — share your timetable with classmates\n" +
                        "• Use 'Timetable only' import so their tasks/money stay untouched\n" +
                        "• Set your starting attendance in Attendance tab if you joined mid-sem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ContactUsCard()
        Spacer(Modifier.height(32.dp))
    }
}
