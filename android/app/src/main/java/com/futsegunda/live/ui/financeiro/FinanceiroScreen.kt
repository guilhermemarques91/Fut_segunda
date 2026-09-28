package com.futsegunda.live.ui.financeiro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.RecurringExpenseDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.viewmodel.FinanceiroViewModel

/**
 * Financeiro nativo: registro direto de lançamentos/despesas/mensalidades,
 * com as mesmas mutações atômicas do backend (Fase 3). As visões derivadas
 * mais complexas do painel web (linha por jogo avulso com alocação FIFO,
 * fechamento de tira-gosto) continuam só no painel por enquanto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceiroScreen(vm: FinanceiroViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editingLancamento by remember { mutableStateOf<LancamentoDto?>(null) }
    var showLancamentoForm by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseDto?>(null) }
    var showExpenseForm by remember { mutableStateOf(false) }
    var editingRecurring by remember { mutableStateOf<RecurringExpenseDto?>(null) }
    var showRecurringForm by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            when (tab) {
                0 -> FloatingActionButton(onClick = { editingLancamento = null; showLancamentoForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo lançamento")
                }
                1 -> FloatingActionButton(onClick = { editingExpense = null; showExpenseForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nova despesa")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Lançamentos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Despesas") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Mensalidades") })
            }

            AccentCard(modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Caixa total", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "R$ %.2f".format(state.caixaTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.caixaTotal >= 0) FutGreenStart else FutRed,
                    )
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else when (tab) {
                0 -> LancamentosTab(
                    state = state,
                    onToggleSelected = vm::toggleSelected,
                    onQuitarSelecionados = vm::quitarSelecionados,
                    onEdit = { editingLancamento = it; showLancamentoForm = true },
                    onDelete = vm::deleteLancamento,
                )
                1 -> DespesasTab(
                    state = state,
                    onTogglePaid = vm::toggleExpensePaid,
                    onEdit = { editingExpense = it; showExpenseForm = true },
                    onDelete = vm::deleteExpense,
                    onEditRecurring = { editingRecurring = it; showRecurringForm = true },
                    onNewRecurring = { editingRecurring = null; showRecurringForm = true },
                    onDeleteRecurring = vm::deleteRecurringExpense,
                )
                2 -> MensalidadesTab(
                    state = state,
                    onGerar = vm::generateMonthlyCharges,
                    onPagar = vm::payMonthlyCharge,
                )
            }
        }
    }

    if (showLancamentoForm) {
        LancamentoFormDialog(
            players = state.players,
            initial = editingLancamento,
            onDismiss = { showLancamentoForm = false },
            onSave = { l -> vm.saveLancamento(l); showLancamentoForm = false },
        )
    }
    if (showExpenseForm) {
        ExpenseFormDialog(
            initial = editingExpense,
            onDismiss = { showExpenseForm = false },
            onSave = { e -> vm.saveExpense(e); showExpenseForm = false },
        )
    }
    if (showRecurringForm) {
        RecurringExpenseFormDialog(
            initial = editingRecurring,
            onDismiss = { showRecurringForm = false },
            onSave = { r -> vm.saveRecurringExpense(r); showRecurringForm = false },
        )
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.dismissToast()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
    state.error?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3500)
            vm.dismissError()
        }
        Snackbar(modifier = Modifier.padding(16.dp), containerColor = FutRed) { Text(msg) }
    }
}

@Composable
private fun LancamentosTab(
    state: com.futsegunda.live.viewmodel.FinanceiroUiState,
    onToggleSelected: (Int) -> Unit,
    onQuitarSelecionados: () -> Unit,
    onEdit: (LancamentoDto) -> Unit,
    onDelete: (Int) -> Unit,
) {
    val list = state.lancamentos.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize()) {
        if (state.selectedLancamentoIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.selectedLancamentoIds.size} selecionado(s)")
                IconButton(onClick = onQuitarSelecionados) { Icon(Icons.Filled.Check, contentDescription = "Quitar selecionados") }
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum lançamento") }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(list, key = { it.id ?: it.hashCode() }) { l ->
                    LancamentoRow(
                        lancamento = l,
                        playerName = state.players.find { it.id == l.playerId }?.name,
                        selected = l.id != null && l.id in state.selectedLancamentoIds,
                        onToggleSelected = { l.id?.let(onToggleSelected) },
                        onEdit = { onEdit(l) },
                        onDelete = { l.id?.let(onDelete) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LancamentoRow(
    lancamento: LancamentoDto,
    playerName: String?,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!lancamento.paid) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(lancamento.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(playerName, lancamento.type, lancamento.date).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("R$ %.2f".format(lancamento.amount), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (lancamento.paid) "Pago" else "Pendente",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lancamento.paid) FutGreenStart else FutAmber,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remover") }
        }
    }
}

@Composable
private fun DespesasTab(
    state: com.futsegunda.live.viewmodel.FinanceiroUiState,
    onTogglePaid: (ExpenseDto) -> Unit,
    onEdit: (ExpenseDto) -> Unit,
    onDelete: (Int) -> Unit,
    onEditRecurring: (RecurringExpenseDto) -> Unit,
    onNewRecurring: () -> Unit,
    onDeleteRecurring: (Int) -> Unit,
) {
    val list = state.expenses.sortedByDescending { it.date }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recorrentes", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onNewRecurring) { Icon(Icons.Filled.Add, contentDescription = "Nova despesa recorrente") }
            }
        }
        items(state.recurringExpenses, key = { "rec_${it.id}" }) { r ->
            AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.description, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(r.category, if (r.active) "Ativa" else "Inativa").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("R$ %.2f/mês".format(r.amount), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onEditRecurring(r) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                    IconButton(onClick = { r.id?.let(onDeleteRecurring) }) { Icon(Icons.Filled.Delete, contentDescription = "Remover") }
                }
            }
        }

        item {
            Text("Despesas", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (list.isEmpty()) {
            item { Text("Nenhuma despesa", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(list, key = { it.id ?: it.hashCode() }) { e ->
            AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = e.paid, onCheckedChange = { onTogglePaid(e) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(e.description, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(e.category, e.date).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("R$ %.2f".format(e.amount), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onEdit(e) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
                    IconButton(onClick = { e.id?.let(onDelete) }) { Icon(Icons.Filled.Delete, contentDescription = "Remover") }
                }
            }
        }
    }
}

@Composable
private fun MensalidadesTab(
    state: com.futsegunda.live.viewmodel.FinanceiroUiState,
    onGerar: () -> Unit,
    onPagar: (String, Int) -> Unit,
) {
    val charges = state.currentMonthCharges
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Mês atual: ${state.currentMonth}", style = MaterialTheme.typography.titleSmall)
            com.futsegunda.live.ui.theme.GradientButton(onClick = onGerar) { Text(if (charges == null) "Gerar mensalidades" else "Gerar de novo") }
        }
        Spacer(Modifier.padding(top = 8.dp))
        if (charges == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Mensalidades deste mês ainda não foram geradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(charges.charges, key = { it.playerId }) { c ->
                    val name = state.players.find { it.id == c.playerId }?.name ?: "Jogador #${c.playerId}"
                    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text("R$ %.2f".format(c.amount), style = MaterialTheme.typography.bodySmall)
                            }
                            if (c.paid) {
                                Text("Pago", color = FutGreenStart, style = MaterialTheme.typography.bodySmall)
                            } else {
                                com.futsegunda.live.ui.theme.GradientButton(onClick = { onPagar(charges.month, c.playerId) }) { Text("Pagar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
