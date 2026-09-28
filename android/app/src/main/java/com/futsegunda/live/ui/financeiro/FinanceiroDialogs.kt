package com.futsegunda.live.ui.financeiro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.RecurringExpenseDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

private val LANCAMENTO_TYPES = listOf("avulso", "tira-gosto", "outro")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LancamentoFormDialog(
    players: List<PlayerDto>,
    initial: LancamentoDto?,
    onDismiss: () -> Unit,
    onSave: (LancamentoDto) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: LANCAMENTO_TYPES.first()) }
    var playerId by remember { mutableStateOf(initial?.playerId) }
    var paid by remember { mutableStateOf(initial?.paid ?: false) }
    var playerMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo lançamento" else "Editar lançamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("Valor (R$)") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LANCAMENTO_TYPES.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = players.find { it.id == playerId }?.name ?: "Avulso (sem jogador)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Jogador") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Overlay transparente por cima do campo read-only pra capturar o toque e abrir o menu.
                    Box(modifier = Modifier.fillMaxSize().clickable { playerMenuExpanded = true })
                    DropdownMenu(expanded = playerMenuExpanded, onDismissRequest = { playerMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Avulso (sem jogador)") }, onClick = { playerId = null; playerMenuExpanded = false })
                        players.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { playerId = p.id; playerMenuExpanded = false })
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = paid, onCheckedChange = { paid = it })
                    Text("Já pago")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (name.isBlank()) return@TextButton
                onSave(
                    LancamentoDto(
                        id = initial?.id,
                        playerId = playerId,
                        name = name,
                        type = type,
                        amount = amount,
                        date = initial?.date ?: todayIso(),
                        notes = initial?.notes,
                        paid = paid,
                        paidDate = if (paid) (initial?.paidDate ?: todayIso()) else null,
                    ),
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ExpenseFormDialog(
    initial: ExpenseDto?,
    onDismiss: () -> Unit,
    onSave: (ExpenseDto) -> Unit,
) {
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var paid by remember { mutableStateOf(initial?.paid ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova despesa" else "Editar despesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Valor (R$)") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = paid, onCheckedChange = { paid = it })
                    Text("Já paga")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (description.isBlank()) return@TextButton
                onSave(
                    ExpenseDto(
                        id = initial?.id,
                        date = initial?.date ?: todayIso(),
                        description = description,
                        category = category.ifBlank { null },
                        amount = amount,
                        paid = paid,
                        paidDate = if (paid) (initial?.paidDate ?: todayIso()) else null,
                        notes = initial?.notes,
                    ),
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun RecurringExpenseFormDialog(
    initial: RecurringExpenseDto?,
    onDismiss: () -> Unit,
    onSave: (RecurringExpenseDto) -> Unit,
) {
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova despesa recorrente" else "Editar despesa recorrente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Valor mensal (R$)") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Ativa")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                if (description.isBlank()) return@TextButton
                onSave(
                    RecurringExpenseDto(
                        id = initial?.id,
                        description = description,
                        category = category.ifBlank { null },
                        amount = amount,
                        active = active,
                        lastPaidMonth = initial?.lastPaidMonth,
                    ),
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
