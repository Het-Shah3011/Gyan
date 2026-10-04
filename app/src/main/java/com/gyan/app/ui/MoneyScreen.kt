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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("This month", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                Text(formatMoney(spent), style = MaterialTheme.typography.headlineSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Received", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(formatMoney(income), style = MaterialTheme.typography.headlineSmall)
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
                        TextButton(onClick = { showBudget = true }) { Text("Set monthly budget") }
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
                            Card(Modifier.fillMaxWidth()) {
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
                            IconButton(onClick = { deleteTarget = e }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(72.dp)) }
        }
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(DEFAULT_CATEGORIES.first()) }
        var note by remember { mutableStateOf("") }
        var isIncome by remember { mutableStateOf(false) }
        DialogForm(
            title = "Add money entry",
            onDismiss = { showAdd = false },
            saveEnabled = amount.toFloatOrNull() != null && amount.toFloatOrNull()!! > 0f,
            onSave = {
                scope.launch {
                    repo.dao.upsertExpense(
                        ExpenseEntity(
                            amount = amount.toFloatOrNull() ?: 0f,
                            category = category, note = note.trim(),
                            dateMillis = System.currentTimeMillis(), isIncome = isIncome
                        )
                    )
                }
                showAdd = false
            }
        ) {
            Text("Type", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("Expense", "Income"), if (isIncome) "Income" else "Expense") {
                isIncome = it == "Income"
            }
            FormField("Amount", amount) { amount = it.filter { c -> c.isDigit() || c == '.' } }
            Text("Category", style = MaterialTheme.typography.labelMedium)
            ChipRow(DEFAULT_CATEGORIES, category) { category = it }
            FormField("Note (optional)", note) { note = it }
        }
    }

    if (showBudget) {
        var amount by remember { mutableStateOf(totalBudget.takeIf { it > 0f }?.toString() ?: "") }
        var category by remember { mutableStateOf("TOTAL") }
        DialogForm(
            title = "Set budget",
            onDismiss = { showBudget = false },
            saveEnabled = amount.toFloatOrNull() != null && amount.toFloatOrNull()!! > 0f,
            onSave = {
                scope.launch {
                    repo.dao.upsertBudget(BudgetEntity(category, amount.toFloatOrNull() ?: 0f))
                }
                showBudget = false
            }
        ) {
            Text("Budget for", style = MaterialTheme.typography.labelMedium)
            ChipRow(listOf("TOTAL") + DEFAULT_CATEGORIES, category) { category = it }
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
