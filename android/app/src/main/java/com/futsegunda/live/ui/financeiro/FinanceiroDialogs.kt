package com.futsegunda.live.ui.financeiro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.RecurringExpenseDto

private val LANC_TYPES = listOf("Mensalidade", "Jogo Avulso", "Tira Gosto", "Contribuição")
private val EXP_CATS = listOf("Janta", "Campo", "Equipamento", "Aluguel", "Outros")

/** Espelha openEditLancamento() (frontend/index.html:2677) — edita 1 lançamento manual pendente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoEditDialog(
    lancamento: LancamentoDto,
    onDismiss: () -> Unit,
    onSave: (LancamentoDto) -> Unit,
) {
    val isGuest = lancamento.playerId == null
    var name by remember { mutableStateOf(lancamento.name) }
    var type by remember { mutableStateOf(lancamento.type) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("%.2f".format(lancamento.amount)) }
    var date by remember { mutableStateOf(lancamento.date) }
    var notes by remember { mutableStateOf(lancamento.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Editar Lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, enabled = isGuest,
                    label = { Text(if (isGuest) "Nome (convidado)" else "Jogador") },
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { Text("Tipo") }, modifier = Modifier.fillMaxWidth())
                    Box(modifier = Modifier.fillMaxSize().clickable { typeMenuExpanded = true })
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        LANC_TYPES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeMenuExpanded = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, singleLine = true, label = { Text("Valor (R$)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = date, onValueChange = { date = it }, singleLine = true, label = { Text("Data (aaaa-mm-dd)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, singleLine = true, label = { Text("Observações") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (amount <= 0 || date.isBlank() || (isGuest && name.isBlank())) return@TextButton
                onSave(lancamento.copy(name = name, type = type, amount = amount, date = date, notes = notes.trim().ifBlank { null }))
            }) { Text("💾 Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕ Cancelar") } },
    )
}

/** Espelha openEditExpense() (frontend/index.html:2625) — edita 1 despesa avulsa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditDialog(
    expense: ExpenseDto,
    onDismiss: () -> Unit,
    onSave: (ExpenseDto) -> Unit,
) {
    var description by remember { mutableStateOf(expense.description) }
    var amountText by remember { mutableStateOf("%.2f".format(expense.amount)) }
    var date by remember { mutableStateOf(expense.date) }
    var category by remember { mutableStateOf(expense.category ?: EXP_CATS.first()) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(expense.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Editar Despesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, singleLine = true, label = { Text("Descrição") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, singleLine = true, label = { Text("Valor (R$)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = date, onValueChange = { date = it }, singleLine = true, label = { Text("Data (aaaa-mm-dd)") }, modifier = Modifier.weight(1f))
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Categoria") }, modifier = Modifier.fillMaxWidth())
                    Box(modifier = Modifier.fillMaxSize().clickable { catMenuExpanded = true })
                    DropdownMenu(expanded = catMenuExpanded, onDismissRequest = { catMenuExpanded = false }) {
                        EXP_CATS.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenuExpanded = false }) }
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, singleLine = true, label = { Text("Observações") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (amount <= 0 || description.isBlank() || date.isBlank()) return@TextButton
                onSave(expense.copy(description = description, amount = amount, date = date, category = category, notes = notes.trim().ifBlank { null }))
            }) { Text("💾 Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕ Cancelar") } },
    )
}

/** Espelha openEditRecurring() (frontend/index.html:2732) — edita 1 despesa recorrente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpenseEditDialog(
    recurring: RecurringExpenseDto,
    onDismiss: () -> Unit,
    onSave: (RecurringExpenseDto) -> Unit,
) {
    var description by remember { mutableStateOf(recurring.description) }
    var amountText by remember { mutableStateOf("%.2f".format(recurring.amount)) }
    var category by remember { mutableStateOf(recurring.category ?: EXP_CATS.first()) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(recurring.active) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Editar Despesa Recorrente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, singleLine = true, label = { Text("Descrição") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it }, singleLine = true, label = { Text("Valor (R$/mês)") }, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Categoria") }, modifier = Modifier.fillMaxWidth())
                        Box(modifier = Modifier.fillMaxSize().clickable { catMenuExpanded = true })
                        DropdownMenu(expanded = catMenuExpanded, onDismissRequest = { catMenuExpanded = false }) {
                            EXP_CATS.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catMenuExpanded = false }) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Ativa")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (amount <= 0 || description.isBlank()) return@TextButton
                onSave(recurring.copy(description = description, amount = amount, category = category, active = active))
            }) { Text("💾 Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("✕ Cancelar") } },
    )
}
