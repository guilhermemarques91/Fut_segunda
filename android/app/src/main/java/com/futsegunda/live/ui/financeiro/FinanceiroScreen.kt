package com.futsegunda.live.ui.financeiro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.domain.EditRef
import com.futsegunda.live.domain.PagarItem
import com.futsegunda.live.domain.ReceberItem
import com.futsegunda.live.domain.SettleAction
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.RecurringExpenseDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.viewmodel.FechamentoReport
import com.futsegunda.live.viewmodel.FinanceiroTab
import com.futsegunda.live.viewmodel.FinanceiroUiState
import com.futsegunda.live.viewmodel.FinanceiroViewModel

private val LANC_REC_TYPES = listOf("Mensalidade", "Jogo Avulso", "Tira Gosto", "Contribuição")
private val EXP_CATEGORIES = listOf(
    "Janta" to "🍖 Janta / Tira Gosto",
    "Campo" to "⚽ Manutenção do Campo",
    "Equipamento" to "👕 Equipamento",
    "Aluguel" to "🏠 Aluguel",
    "Outros" to "📌 Outros",
)
private val MONTH_NAMES_FULL = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

private data class BadgeStyle(val label: String, val color: Color)
private val BADGE_STYLES = mapOf(
    "mensalidade" to BadgeStyle("📆 Mensalidade", FutGreenStart),
    "avulso" to BadgeStyle("💵 Avulso", FutAmber),
    "tiragosto" to BadgeStyle("🍽️ Tira Gosto", FutRed),
    "contribuicao" to BadgeStyle("💚 Contribuição", FutGreenStart),
    "despesa" to BadgeStyle("📋 Despesa", FutBlueAccent),
)

private fun fmtBRL(v: Double): String = "R$ %.2f".format(v)

/**
 * Financeiro nativo — espelha as 3 abas de showFinanceTab() (frontend/index.html:621-825):
 * Lançamentos (feed unificado receita+despesa, formulário inline, quitação em lote),
 * Fechamento (relatório mensal) e Configurações (saldo inicial, tarifas, resumo geral).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceiroScreen(vm: FinanceiroViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showMensalistas by remember { mutableStateOf(false) }
    var editingLancamento by remember { mutableStateOf<LancamentoDto?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseDto?>(null) }
    var editingRecurring by remember { mutableStateOf<RecurringExpenseDto?>(null) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AccentCard {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💰 Em Caixa: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtBRL(state.caixaTotal), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = FutGreenStart)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = state.tab == FinanceiroTab.LANCAMENTOS, onClick = { vm.setTab(FinanceiroTab.LANCAMENTOS) }, label = { Text("💰 Lançamentos") })
            FilterChip(selected = state.tab == FinanceiroTab.FECHAMENTO, onClick = { vm.setTab(FinanceiroTab.FECHAMENTO) }, label = { Text("📊 Fechamento") })
            FilterChip(selected = state.tab == FinanceiroTab.CONFIGURACOES, onClick = { vm.setTab(FinanceiroTab.CONFIGURACOES) }, label = { Text("⚙️ Configurações") })
        }
        Spacer(Modifier.height(8.dp))

        when (state.tab) {
            FinanceiroTab.LANCAMENTOS -> LancamentosTab(
                state = state, vm = vm,
                onMensalistas = { showMensalistas = true },
                onEditLancamento = { editingLancamento = it },
                onEditExpense = { editingExpense = it },
                onEditRecurring = { editingRecurring = it },
            )
            FinanceiroTab.FECHAMENTO -> FechamentoTab(state, vm)
            FinanceiroTab.CONFIGURACOES -> ConfiguracoesTab(state, vm)
        }
    }

    if (showMensalistas) {
        MensalistasDialog(
            state = state,
            onConfirm = { vm.confirmMensalistas(); showMensalistas = false },
            onDismiss = { showMensalistas = false },
        )
    }
    editingLancamento?.let { l ->
        LancamentoEditDialog(lancamento = l, onDismiss = { editingLancamento = null }, onSave = { vm.updateLancamento(it); editingLancamento = null })
    }
    editingExpense?.let { e ->
        ExpenseEditDialog(expense = e, onDismiss = { editingExpense = null }, onSave = { vm.updateExpense(it); editingExpense = null })
    }
    editingRecurring?.let { r ->
        RecurringExpenseEditDialog(recurring = r, onDismiss = { editingRecurring = null }, onSave = { vm.updateRecurringExpense(it); editingRecurring = null })
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.dismissToast() }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
    state.error?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.dismissError() }
        Snackbar(modifier = Modifier.padding(16.dp), containerColor = FutRed) { Text(msg) }
    }
}

// ═══════════════════════════ LANÇAMENTOS ═══════════════════════════

@Composable
private fun LancamentosTab(
    state: FinanceiroUiState,
    vm: FinanceiroViewModel,
    onMensalistas: () -> Unit,
    onEditLancamento: (LancamentoDto) -> Unit,
    onEditExpense: (ExpenseDto) -> Unit,
    onEditRecurring: (RecurringExpenseDto) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            // stats-grid: 4 cards (2x2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinStatCard("💰", fmtBRL(state.caixaTotal), "Em Caixa", FutGreenStart, Modifier.weight(1f))
                    FinStatCard("📥", fmtBRL(state.pendReceberTotal), "A Receber", FutAmber, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FinStatCard("📤", fmtBRL(state.pendPagarTotal), "A Pagar", FutRed, Modifier.weight(1f))
                    FinStatCard("📊", fmtBRL(state.projetado), "Saldo Projetado", FutBlueAccent, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        item {
            OutlinedTextField(
                value = state.search, onValueChange = vm::setSearch,
                label = { Text("🔍 Buscar nome ou descrição…") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "De", value = state.dateFrom, onChange = vm::setDateFrom, modifier = Modifier.weight(1f))
                DateField(label = "Até", value = state.dateTo, onChange = vm::setDateTo, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            Text("STATUS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.statusFilter == "pendente", onClick = { vm.setStatusFilter("pendente") }, label = { Text("⏳ Pendentes") })
                FilterChip(selected = state.statusFilter == "pago", onClick = { vm.setStatusFilter("pago") }, label = { Text("✓ Pagos / Recebidos") })
                FilterChip(selected = state.statusFilter == "all", onClick = { vm.setStatusFilter("all") }, label = { Text("Todos") })
            }
            Spacer(Modifier.height(8.dp))
            Text("TIPO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.badgeFilter == "all", onClick = { vm.setBadgeFilter("all") }, label = { Text("Todos") })
                FilterChip(selected = state.badgeFilter == "mensalidade", onClick = { vm.setBadgeFilter("mensalidade") }, label = { Text("📆 Mensal") })
                FilterChip(selected = state.badgeFilter == "avulso", onClick = { vm.setBadgeFilter("avulso") }, label = { Text("💵 Avulso") })
                FilterChip(selected = state.badgeFilter == "tiragosto", onClick = { vm.setBadgeFilter("tiragosto") }, label = { Text("🍽️ Tira Gosto") })
                FilterChip(selected = state.badgeFilter == "despesa", onClick = { vm.setBadgeFilter("despesa") }, label = { Text("📋 Despesa") })
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm::clearFilters) { Text("✕ Limpar") }
            }
            Spacer(Modifier.height(10.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onMensalistas) { Text("👥 Mensalistas") }
                Spacer(Modifier.width(8.dp))
                GradientButton(onClick = vm::toggleForm) { Text(if (state.showForm) "✕ Fechar" else "➕ Novo Lançamento") }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.showForm) {
            item {
                LancamentoInlineForm(state, vm)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (state.bulkCount > 0) {
            item {
                AccentCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${state.bulkCount} título(s) selecionado(s)", style = MaterialTheme.typography.labelMedium)
                            Text(fmtBRL(state.bulkSum), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FutGreenStart)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = vm::clearSelection) { Text("✕ Limpar") }
                            GradientButton(onClick = vm::bulkSettle) { Text("⚡ Quitar") }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        item {
            val statusSuf = when (state.statusFilter) { "pago" -> "Recebidos"; "all" -> "(todos)"; else -> "Pendentes" }
            SectionHeader("📥 Recebimentos $statusSuf", state.filteredReceber.size, state.filteredReceber.sumOf { it.amount }, FutGreenStart)
        }
        if (state.filteredReceber.isEmpty()) {
            item { EmptyRow() }
        } else {
            items(state.filteredReceber, key = { it.key }) { it2 ->
                ReceberRow(it2, it2.key in state.selected, vm, onEditLancamento, onEditExpense, onEditRecurring)
            }
        }
        item { Spacer(Modifier.height(18.dp)) }

        item {
            val statusSuf = when (state.statusFilter) { "pago" -> "Pagos"; "all" -> "(todos)"; else -> "Pendentes" }
            SectionHeader("📤 A Pagar $statusSuf", state.filteredPagar.size, state.filteredPagar.sumOf { it.amount }, FutRed)
        }
        if (state.filteredPagar.isEmpty()) {
            item { EmptyRow() }
        } else {
            items(state.filteredPagar, key = { it.key }) { it2 ->
                PagarRow(it2, it2.key in state.selected, vm, onEditExpense, onEditRecurring)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FinStatCard(icon: String, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    AccentCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, total: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        Text("$count título(s) · ${fmtBRL(total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRow() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Nenhum registro 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReceberRow(
    it: ReceberItem,
    selected: Boolean,
    vm: FinanceiroViewModel,
    onEditLancamento: (LancamentoDto) -> Unit,
    onEditExpense: (ExpenseDto) -> Unit,
    onEditRecurring: (RecurringExpenseDto) -> Unit,
) {
    val displayName = if (it.apelido.isNotBlank()) "${it.name} (${it.apelido})" else it.name
    LancRowCard(
        icon = it.icon, name = displayName, badge = it.badge, desc = it.desc,
        amount = it.amount, accent = FutGreenStart, sign = "",
        pago = it.status == "pago", paidDate = it.paidDate,
        selected = selected, canSelect = it.settle != null,
        onToggle = { vm.toggleSelect(it.key, it.amount) },
        onSettle = it.settle?.let { st -> { vm.settleOne(st) } },
        onEdit = it.edit?.let { ref -> { openEdit(ref, vm, onEditLancamento, onEditExpense, onEditRecurring) } },
        onDelete = it.edit?.let { ref -> { deleteRef(ref, vm) } },
    )
}

@Composable
private fun PagarRow(
    it: PagarItem,
    selected: Boolean,
    vm: FinanceiroViewModel,
    onEditExpense: (ExpenseDto) -> Unit,
    onEditRecurring: (RecurringExpenseDto) -> Unit,
) {
    LancRowCard(
        icon = it.icon, name = it.name, badge = it.badge, desc = it.desc,
        amount = it.amount, accent = FutRed, sign = "−",
        pago = it.status == "pago", paidDate = it.paidDate,
        selected = selected, canSelect = it.settle != null,
        onToggle = { vm.toggleSelect(it.key, it.amount) },
        onSettle = it.settle?.let { st -> { vm.settleOne(st) } },
        onEdit = it.edit?.let { ref -> { openEdit(ref, vm, {}, onEditExpense, onEditRecurring) } },
        onDelete = it.edit?.let { ref -> { deleteRef(ref, vm) } },
    )
}

private fun openEdit(
    ref: EditRef,
    vm: FinanceiroViewModel,
    onEditLancamento: (LancamentoDto) -> Unit,
    onEditExpense: (ExpenseDto) -> Unit,
    onEditRecurring: (RecurringExpenseDto) -> Unit,
) {
    when (ref) {
        is EditRef.Manual -> vm.state.value.lancamentos.find { it.id == ref.id }?.let(onEditLancamento)
        is EditRef.Expense -> vm.state.value.expenses.find { it.id == ref.id }?.let(onEditExpense)
        is EditRef.Recurring -> vm.state.value.recurringExpenses.find { it.id == ref.id }?.let(onEditRecurring)
    }
}

private fun deleteRef(ref: EditRef, vm: FinanceiroViewModel) {
    when (ref) {
        is EditRef.Manual -> vm.deleteLancamento(ref.id)
        is EditRef.Expense -> vm.deleteExpense(ref.id)
        is EditRef.Recurring -> vm.deleteRecurringExpense(ref.id)
    }
}

@Composable
private fun LancRowCard(
    icon: String, name: String, badge: String, desc: String, amount: Double,
    accent: Color, sign: String, pago: Boolean, paidDate: String?,
    selected: Boolean, canSelect: Boolean,
    onToggle: () -> Unit, onSettle: (() -> Unit)?, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?,
) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!pago) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = canSelect)
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Text(icon, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BADGE_STYLES[badge]?.let { b ->
                        Text(
                            "  ${b.label}", style = MaterialTheme.typography.labelSmall, color = b.color,
                        )
                    }
                }
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$sign${fmtBRL(amount)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                if (pago) {
                    Text(
                        "✓ " + (paidDate?.let { "· ${it.split("-").asReversed().joinToString("/")}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = FutGreenStart,
                    )
                } else if (onSettle != null) {
                    TextButton(onClick = onSettle, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                        Text("✓ Quitar", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                    }
                }
                if (onEdit != null || onDelete != null) {
                    Row {
                        onEdit?.let { IconButton(onClick = it) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = FutBlueAccent, modifier = Modifier.width(18.dp)) } }
                        onDelete?.let { IconButton(onClick = it) { Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = FutRed, modifier = Modifier.width(18.dp)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LancamentoInlineForm(state: FinanceiroUiState, vm: FinanceiroViewModel) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("NOVO LANÇAMENTO", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioPill("📥 Receita", state.formTipo == "receita", Modifier.weight(1f)) { vm.setFormTipo("receita") }
                RadioPill("📤 Despesa", state.formTipo == "despesa", Modifier.weight(1f)) { vm.setFormTipo("despesa") }
            }
            Spacer(Modifier.height(12.dp))

            if (state.formTipo == "receita") {
                PlayerOrGuestField(state, vm)
                Spacer(Modifier.height(8.dp))
                Text("Tipo de Recebimento", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LANC_REC_TYPES.forEach { t ->
                        FilterChip(selected = state.formRecType == t, onClick = { vm.setFormRecType(t) }, label = { Text(t) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.formAmount, onValueChange = vm::setFormAmount, label = { Text("Valor (R$)") }, singleLine = true, modifier = Modifier.weight(1f))
                    DateField(label = "Data", value = state.formDate, onChange = vm::setFormDate, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.formNotes, onValueChange = vm::setFormNotes, label = { Text("Observação (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = state.formDespDesc, onValueChange = vm::setFormDespDesc, label = { Text("Descrição") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Categoria", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXP_CATEGORIES.forEach { (v, label) ->
                        FilterChip(selected = state.formDespCat == v, onClick = { vm.setFormDespCat(v) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = state.formAmount, onValueChange = vm::setFormAmount, label = { Text("Valor (R$)") }, singleLine = true, modifier = Modifier.weight(1f))
                    if (!state.formDespRecurring) {
                        DateField(label = "Data", value = state.formDate, onChange = vm::setFormDate, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = state.formDespRecurring, onCheckedChange = vm::setFormDespRecurring)
                    Text("🔁 Despesa Recorrente — repete todo mês automaticamente", style = MaterialTheme.typography.bodySmall, color = FutBlueAccent)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = state.formNotes, onValueChange = vm::setFormNotes, label = { Text("Observação (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(onClick = vm::saveLancamentoForm, modifier = Modifier.weight(1f)) { Text("💾 Salvar como Pendente") }
                TextButton(onClick = vm::toggleForm) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun PlayerOrGuestField(state: FinanceiroUiState, vm: FinanceiroViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        val label = when {
            state.formIsGuest -> "👤 Convidado (não cadastrado)"
            state.formPlayerId != null -> state.players.find { it.id == state.formPlayerId }?.name ?: "Selecione…"
            else -> "Selecione…"
        }
        OutlinedTextField(value = label, onValueChange = {}, readOnly = true, label = { Text("Jogador / Pagante") }, modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.fillMaxSize().clickable { menuExpanded = true })
        androidx.compose.material3.DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("👤 Convidado (não cadastrado)") }, onClick = { vm.setFormPlayer(null, true); menuExpanded = false })
            state.players.sortedBy { it.name }.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(p.name + (p.apelido?.let { " ($it)" } ?: "")) }, onClick = { vm.setFormPlayer(p.id, false); menuExpanded = false })
            }
        }
    }
    if (state.formIsGuest) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = state.formGuestName, onValueChange = vm::setFormGuestName, label = { Text("Nome do Convidado") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RadioPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = modifier.clickable { showPicker = true },
    )
    if (showPicker) {
        val initialMillis = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(value)?.time
        }.getOrNull()
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        onChange(fmt.format(java.util.Date(millis)))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { androidx.compose.material3.DatePicker(state = pickerState) }
    }
}

@Composable
private fun MensalistasDialog(state: FinanceiroUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val monthStr = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
    val jaGerado = state.currentMonthCharges != null
    val lista = state.mensalistasList
    val total = lista.sumOf { it.monthlyFee ?: 0.0 }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👥 Gerar Mensalidades") },
        text = {
            Column {
                Text("Referência: ${MONTH_NAMES_FULL.getOrElse(monthStr.split("-")[1].toInt() - 1) { "" }}/${monthStr.split("-")[0]} · todos os títulos entram como pendentes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(lista.sortedBy { it.name }) { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📆 ${p.name}", style = MaterialTheme.typography.bodySmall)
                            Text(fmtBRL(p.monthlyFee ?: 0.0), style = MaterialTheme.typography.bodySmall, color = FutGreenStart, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${lista.size} mensalista(s)", style = MaterialTheme.typography.labelSmall)
                    Text(fmtBRL(total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (jaGerado) {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠️ As mensalidades deste mês já foram geradas. Não é possível gerar novamente.", style = MaterialTheme.typography.labelSmall, color = FutAmber)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !jaGerado && lista.isNotEmpty()) { Text("✅ Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ═══════════════════════════ FECHAMENTO ═══════════════════════════

@Composable
private fun FechamentoTab(state: FinanceiroUiState, vm: FinanceiroViewModel) {
    val report = state.fechamentoReport
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.fechamentoNav(-1) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                Text(state.fechamentoLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { vm.fechamentoNav(1) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }
        }
        item { FechamentoReceitasCard(report) }
        item { FechamentoDespesasCard(report) }
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resultado do Mês", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Receitas − Despesas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtBRL(report.saldo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (report.saldo >= 0) FutGreenStart else FutRed)
                    }
                }
            }
        }
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Saldo em Caixa", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Entradas − Saídas (total)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtBRL(report.saldoEmCaixa), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FutBlueAccent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FechamentoReceitasCard(report: FechamentoReport) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Receitas do Mês", style = MaterialTheme.typography.titleSmall, color = FutGreenStart)
            Spacer(Modifier.height(8.dp))
            FinRow("📆 Mensalidade", report.recMensal)
            FinRow("💵 Jogo Avulso", report.recAvulso)
            FinRow("🍽️ Tira Gosto", report.recTiraGosto)
            FinRow("💚 Contribuição", report.recContrib)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(fmtBRL(report.totalReceitas), fontWeight = FontWeight.Bold, color = FutGreenStart, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FechamentoDespesasCard(report: FechamentoReport) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Despesas do Mês", style = MaterialTheme.typography.titleSmall, color = FutRed)
            Spacer(Modifier.height(8.dp))
            report.expByCategory.forEach { (cat, v) -> FinRow(cat, v) }
            if (report.recorrentes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("🔁 Recorrentes", style = MaterialTheme.typography.labelSmall, color = FutBlueAccent)
                report.recorrentes.forEach { r -> FinRow(r.description, r.amount) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(fmtBRL(report.totalDespesas), fontWeight = FontWeight.Bold, color = FutRed, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FinRow(label: String, value: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmtBRL(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════ CONFIGURAÇÕES ═══════════════════════════

@Composable
private fun ConfiguracoesTab(state: FinanceiroUiState, vm: FinanceiroViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💵 Saldo Inicial do Caixa", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Dinheiro que já estava em caixa antes de começar a usar o sistema. Somado automaticamente ao total recebido.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = state.initialBalanceText, onValueChange = vm::setInitialBalanceText, label = { Text("R$") }, singleLine = true, modifier = Modifier.weight(1f))
                        GradientButton(onClick = vm::saveInitialBalance) { Text("💾 Salvar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Saldo atual registrado: ${fmtBRL(state.config.initialBalance)}", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                }
            }
        }
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tarifas por Categoria", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    TarifaRow("📆 Mensal", "Por mês · titulares", FutGreenStart, state.feeMensalText, vm::setFeeMensalText)
                    Spacer(Modifier.height(8.dp))
                    TarifaRow("💵 Avulso", "Por jogo · eventuais", FutAmber, state.feeAvulsoText, vm::setFeeAvulsoText)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("🧤 Goleiro isento", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Não cobra mensalidade/avulso de goleiros", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = state.feeGoleiroIsento, onCheckedChange = vm::setFeeGoleiroIsento)
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientButton(onClick = vm::saveFees, modifier = Modifier.fillMaxWidth()) { Text("💾 Salvar Tarifas e Atualizar Jogadores") }
                    Spacer(Modifier.height(14.dp))
                    Text("📋 Histórico de Cobranças Mensais", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    if (state.chargeHistory.isEmpty()) {
                        Text("Nenhuma mensalidade gerada ainda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.chargeHistory.sortedByDescending { it.month }.forEach { ch ->
                            val total = ch.charges.sumOf { it.amount }
                            val paidCount = ch.charges.count { it.paid }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ch.month, style = MaterialTheme.typography.bodySmall)
                                Text("$paidCount/${ch.charges.size} pagos · ${fmtBRL(total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Para gerar mensalidades use o botão 👥 Mensalistas na aba Lançamentos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumo Geral", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    state.playerFinanceRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.player.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("↑ ${fmtBRL(row.paid)}", style = MaterialTheme.typography.labelSmall, color = FutGreenStart, fontWeight = FontWeight.Bold)
                                if (row.feePending > 0) Text("mens. −${fmtBRL(row.feePending)}", style = MaterialTheme.typography.labelSmall, color = FutAmber)
                                if (row.dinnerPending > 0) Text("janta −${fmtBRL(row.dinnerPending)}", style = MaterialTheme.typography.labelSmall, color = FutRed)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Divider()
                    Spacer(Modifier.height(8.dp))
                    FinRow("Total Recebido", state.totalReceived)
                    FinRow("Pend. Mensalidade", state.totalFeePending)
                    FinRow("Pend. Tira Gosto", state.totalDinnerPending)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TarifaRow(label: String, sub: String, color: Color, valueText: String, onChange: (String) -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = valueText, onValueChange = onChange, singleLine = true, modifier = Modifier.width(100.dp))
        }
    }
}
