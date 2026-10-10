package com.gyan.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.StudyFileEntity
import com.gyan.app.files.FileOrganizer
import kotlinx.coroutines.launch

/** Returns categories based on how many units the subject has (defaulting to 5 if unset). */
private fun fileCategories(totalUnits: Int): List<String> {
    val u = totalUnits.coerceAtLeast(1)
    return (1..u).map { "Unit $it" } + listOf("Assignments", "PYQs", "Notes", "Other")
}

/** Fallback when no subject is selected. */
private val DEFAULT_FILE_CATEGORIES = fileCategories(5)

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val subjects by repo.dao.subjectsFlow().collectAsStateWithLifecycle(emptyList())
    val files by repo.dao.filesFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val subjectMap = subjects.associateBy { it.id }
    val matchingFiles = files.filter { file ->
        searchQuery.isBlank() || listOf(
            file.displayName,
            file.category,
            subjectMap[file.subjectId]?.name.orEmpty()
        ).any { it.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val inbox = matchingFiles.filter { it.category == "INBOX" }
    // Group organized files: Subject → (Category → List<file>)
    val organizedBySubject: Map<String, Map<String, List<StudyFileEntity>>> = matchingFiles
        .filter { it.category != "INBOX" }
        .groupBy { subjectMap[it.subjectId]?.name ?: "Unsorted" }
        .mapValues { (_, subFiles) ->
            subFiles.groupBy { it.category }
                .toSortedMap()
        }
        .toSortedMap()

    var organizeTarget by remember { mutableStateOf<StudyFileEntity?>(null) }
    var renameTarget by remember { mutableStateOf<StudyFileEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<StudyFileEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val ok = FileOrganizer.importFromUri(context, it) != null
                Toast.makeText(
                    context,
                    if (ok) "Saved to GYAN inbox" else "Could not import this file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importLauncher.launch(
                    arrayOf(
                        "application/pdf", "image/*", "text/*", "text/plain",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/zip"
                    )
                )
            }) { Icon(Icons.Filled.Add, contentDescription = "Import file") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Files", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search notes and files") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                Text(
                    "Share any PDF, doc or image from WhatsApp, Drive or Downloads straight to GYAN — it lands in Inbox, then organize into subjects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ---- INBOX section ----
            item { SectionTitle("Inbox (${inbox.size})") }
            if (inbox.isEmpty()) {
                item { EmptyState("Inbox is empty") }
            } else {
                items(inbox.size) { i ->
                    FileRow(
                        inbox[i], subjectMap,
                        onOpen = { FileOrganizer.open(context, it) },
                        onOrganize = { organizeTarget = it },
                        onRename = { renameTarget = it },
                        onDelete = { deleteTarget = it }
                    )
                }
            }

            // ---- SUBJECT-grouped sections ----
            organizedBySubject.forEach { (subjectName, categoryMap) ->
                // Subject header with colored dot
                val subjectEntity = subjects.find { it.name == subjectName }
                item {
                    Spacer(Modifier.height(6.dp))
                    SubjectFileHeader(
                        subjectName = subjectName,
                        fileCount = categoryMap.values.sumOf { it.size },
                        colorArgb = subjectEntity?.colorArgb
                    )
                }
                // Category sub-sections
                categoryMap.forEach { (category, group) ->
                    item {
                        CategorySubHeader(category = category, count = group.size)
                    }
                    items(group.size) { i ->
                        FileRow(
                            group[i], subjectMap,
                            onOpen = { FileOrganizer.open(context, it) },
                            onOrganize = { organizeTarget = it },
                            onRename = { renameTarget = it },
                            onDelete = { deleteTarget = it },
                            showSubjectInRow = false
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // ---- organize dialog ----
    organizeTarget?.let { target ->
        var subjectId by remember { mutableStateOf(target.subjectId) }
        // Dynamic categories: use selected subject's totalUnits
        val dynCategories = subjectId?.let { sid ->
            subjects.find { it.id == sid }?.totalUnits?.let { fileCategories(it) }
        } ?: DEFAULT_FILE_CATEGORIES
        var category by remember { mutableStateOf(if (target.category == "INBOX") dynCategories.first() else target.category) }
        // Reset category if it no longer exists in dynCategories when subject changes
        if (category !in dynCategories) category = dynCategories.first()
        DialogForm(
            title = "Organize file",
            onDismiss = { organizeTarget = null },
            onSave = {
                scope.launch { FileOrganizer.organize(context, target, subjectId, category) }
                organizeTarget = null
            }
        ) {
            Text("Subject", style = MaterialTheme.typography.labelMedium)
            SubjectPicker(subjects, subjectId) { sid ->
                subjectId = sid
                // Reset to first valid category when subject changes
                val newCats = sid?.let { subjects.find { it.id == sid }?.totalUnits?.let { fileCategories(it) } } ?: DEFAULT_FILE_CATEGORIES
                category = newCats.first()
            }
            Text("Category", style = MaterialTheme.typography.labelMedium)
            ChipRow(dynCategories, category) { category = it }
        }
    }

    // ---- rename dialog ----
    renameTarget?.let { target ->
        var name by remember { mutableStateOf(target.displayName) }
        DialogForm(
            title = "Rename file",
            onDismiss = { renameTarget = null },
            saveEnabled = name.isNotBlank(),
            onSave = {
                scope.launch { FileOrganizer.rename(context, target, name.trim()) }
                renameTarget = null
            }
        ) {
            FormField("File name", name) { name = it }
        }
    }

    // ---- delete confirm ----
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = target.displayName,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { FileOrganizer.delete(context, target) }
                deleteTarget = null
            }
        )
    }
}

// ---------- Subject header (bold colored bar) ----------
@Composable
private fun SubjectFileHeader(subjectName: String, fileCount: Int, colorArgb: Long?) {
    val dotColor = colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            subjectName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$fileCount file${if (fileCount != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Category sub-header ----------
@Composable
private fun CategorySubHeader(category: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "▸  $category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- File row ----------
@Composable
private fun FileRow(
    entity: StudyFileEntity,
    subjectMap: Map<Long, com.gyan.app.data.SubjectEntity>,
    onOpen: (StudyFileEntity) -> Unit,
    onOrganize: (StudyFileEntity) -> Unit,
    onRename: (StudyFileEntity) -> Unit,
    onDelete: (StudyFileEntity) -> Unit,
    showSubjectInRow: Boolean = true
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        onClick = { onOpen(entity) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (!showSubjectInRow) 16.dp else 0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // File type icon letter
            val ext = entity.displayName.substringAfterLast('.', "").lowercase()
            val (extLabel, extColor) = when {
                ext == "pdf" -> "PDF" to Color(0xFFE53935)
                ext in listOf("jpg", "jpeg", "png", "webp") -> "IMG" to Color(0xFF1E88E5)
                ext in listOf("doc", "docx") -> "DOC" to Color(0xFF1565C0)
                ext in listOf("xls", "xlsx") -> "XLS" to Color(0xFF2E7D32)
                ext == "zip" -> "ZIP" to Color(0xFF6D4C41)
                else -> "TXT" to Color(0xFF546E7A)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(extColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    extLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = extColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entity.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    if (entity.category == "INBOX") "Inbox  •  ${formatDate(entity.addedMillis)}"
                    else if (showSubjectInRow) "${subjectMap[entity.subjectId]?.name ?: "Unsorted"}  •  ${entity.category}"
                    else formatDate(entity.addedMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen(entity) })
                    DropdownMenuItem(text = { Text("Organize") }, onClick = { menuOpen = false; onOrganize(entity) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename(entity) })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete(entity) })
                }
            }
        }
    }
}
