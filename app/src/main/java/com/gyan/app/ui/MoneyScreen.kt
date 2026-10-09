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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gyan.app.data.BudgetEntity
import com.gyan.app.data.ExpenseEntity
import com.gyan.app.data.GyanRepository
import kotlinx.coroutines.launch

private val DEFAULT_CATEGORIES = listOf("Food", "Travel", "College", "Shopping", "Entertainment", "Other")

@Composable
fun MoneyScreen() {
    val context = LocalContext.current
    val repo = remember { GyanRepository.get(context) }
    val expenses by repo.dao.expensesFlow().collectAsStateWithLifecycle(emptyList())
    val budgets by repo.dao.budgetsFlow().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    val monthStart = monthStartMillis()
    val thisMonth = expenses.filter { it.dateMillis >= monthStart }
    val spent = thisMonth.filter { !it.isIncome }.sumOf { it.amount.toDouble() }.toFloat()
    val income = thisMonth.filter { it.isIncome }.sumOf { it.amount.toDouble() }.toFloat()
    val totalBudget = budgets.firstOrNull { it.category == "TOTAL" }?.monthlyAmount ?: 0f

    var showAdd by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editTarget by remember { mutableStateOf<ExpenseEntity?>(null) }
    var budgetEditTarget by remember { mutableStateOf<BudgetEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "Add entry") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Money", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "This month · ${java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                                Text(formatMoney(spent), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Received", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                                Text(formatMoney(income), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        if (totalBudget > 0f) {
                            val left = (totalBudget - spent).coerceAtLeast(0f)
                            LinearProgressIndicator(
                                progress = { (spent / totalBudget).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Budget ${formatMoney(totalBudget)}  •  ${formatMoney(left)} left",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                            TextButton(onClick = { showBudget = true }) { Text("Set monthly budget", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Category budgets")
                }
                val catBudgets = budgets.filter { it.category != "TOTAL" }
                if (catBudgets.isEmpty()) {
                    EmptyState("No category budgets yet")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        catBudgets.forEach { b ->
                            val catSpent = thisMonth.filter { !it.isIncome && it.category == b.category }
                                .sumOf { it.amount.toDouble() }.toFloat()
                            Card(onClick = { budgetEditTarget = b; showBudget = true }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(b.category, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${formatMoney(catSpent)} / ${formatMoney(b.monthlyAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (catSpent > b.monthlyAmount) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (catSpent / b.monthlyAmount).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Recent entries") }
            val recent = expenses.take(30)
            if (recent.isEmpty()) {
                item { EmptyState("No entries yet. Tap + to add one.") }
            } else {
                items(recent.size) { i ->
                    val e = recent[i]
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (e.isIncome) "+ " else "- ") + formatMoney(e.amount) +
                                        (if (e.note.isNotBlank()) "  •  ${e.note}" else ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (e.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${e.category}  •  ${formatDate(e.dateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editTarget = e }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit entry", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { deleteTarget = e }) {
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
        var amount by remember(original?.id) { mutableStateOf(original?.amount?.toString().orEmpty()) }
        var category by remember(original?.id) { mutableStateOf(original?.category ?: DEFAULT_CATEGORIES.first()) }
        var note by remember(original?.id) { mutableStateOf(original?.note.orEmpty()) }
        var dateText by remember(original?.id) { mutableStateOf(original?.dateMillis?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }.orEmpty()) }
        var isIncome by remember(original?.id) { mutableStateOf(original?.isIncome ?: false) }
        DialogForm(
            title = if (original == null) "Add money entry" else "Edit money entry",
            onDismiss = { showAdd = false; editTarget = null },
            saveEnabled = amount.toFloatOrNull() != null && amount.toFloatOrNull()!! > 0f,
            onSave = {
                scope.launch {
                    val day = parseDateTime(dateText) ?: System.currentTimeMillis()
                    repo.dao.upsertExpense(original?.copy(amount = amount.toFloat(), category = category.trim(), note = note.trim(), dateMillis = day, isIncome = isIncome)
                        ?: ExpenseEntity(amount = amount.toFloat(), category = category.trim(), note = note.trim(), dateMillis = day, isIncome = isIncome))
                }
                showAdd = false
                editTarget = null
            }
        ) {
            Text("Type", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("Expense", "Income"), if (isIncome) "Income" else "Expense") {
                isIncome = it == "Income"
            }
            FormField("Amount", amount) { amount = it.filter { c -> c.isDigit() || c == '.' } }
            Text("Category", style = MaterialTheme.typography.labelMedium)
            ChipRow(DEFAULT_CATEGORIES, category) { category = it }
            FormField("Category name", category) { category = it }
            FormField("Date (dd/MM/yyyy)", dateText) { dateText = it }
            FormField("Note (optional)", note) { note = it }
        }
    }

    if (showBudget) {
        val original = budgetEditTarget
        var amount by remember(original?.category) { mutableStateOf((original?.monthlyAmount ?: totalBudget).takeIf { it > 0f }?.toString() ?: "") }
        var category by remember(original?.category) { mutableStateOf(original?.category ?: "TOTAL") }
        DialogForm(
            title = "Set budget",
            onDismiss = { showBudget = false; budgetEditTarget = null },
            saveEnabled = amount.toFloatOrNull() != null && amount.toFloatOrNull()!! > 0f,
            onSave = {
                scope.launch {
                    if (original != null && original.category != category) repo.dao.deleteBudget(original)
                    repo.dao.upsertBudget(BudgetEntity(category, amount.toFloatOrNull() ?: 0f))
                }
                showBudget = false
                budgetEditTarget = null
            }
        ) {
            Text("Budget for", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("TOTAL") + DEFAULT_CATEGORIES, category) { category = it }
            FormField("Budget category", category) { category = it }
            FormField("Monthly amount", amount) { amount = it.filter { c -> c.isDigit() || c == '.' } }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            what = "this entry",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                scope.launch { repo.dao.deleteExpense(target) }
                deleteTarget = null
            }
        )
    }
}
