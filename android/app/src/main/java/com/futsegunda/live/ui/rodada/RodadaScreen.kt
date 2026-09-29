package com.futsegunda.live.ui.rodada

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.viewmodel.RodadaStage
import com.futsegunda.live.viewmodel.RodadaSummary
import com.futsegunda.live.viewmodel.RodadaUiState
import com.futsegunda.live.viewmodel.RodadaViewModel
import com.futsegunda.live.viewmodel.TeamZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class RodadaSubTab { PRESENCA, TIRA_GOSTO, TIMES, RESULTADO }
private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/**
 * Espelha a estrutura de 2 etapas do painel (frontend/index.html:912-1037):
 * landing (histórico de rodadas + "Nova Rodada") → gerenciar (data → cards
 * de Presença/Tira Gosto/Times/Resultado, uma vez criada).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RodadaScreen(onMatchStarted: () -> Unit, vm: RodadaViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (state.stage) {
                RodadaStage.LANDING -> RodadaLanding(state, vm)
                RodadaStage.PICK_DATE -> RodadaPickDate(state, vm)
                RodadaStage.MANAGE -> RodadaManage(state, vm, onMatchStarted)
            }
        }
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

@Composable
private fun RodadaLanding(state: RodadaUiState, vm: RodadaViewModel) {
    var deleteDate by remember { mutableStateOf<String?>(null) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                Text("⚽", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                GradientButton(onClick = { vm.openNewRodada() }) { Text("+ Nova Rodada") }
            }
            Text(
                "RODADAS ANTERIORES", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (state.summaries.isEmpty()) {
            item { Text("Nenhuma rodada registrada ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.summaries, key = { it.date }) { s ->
                RodadaSummaryCard(s, onEdit = { vm.openExistingRodada(s.date) }, onDelete = { deleteDate = s.date })
            }
        }
    }

    deleteDate?.let { date ->
        DeleteRodadaDialog(
            onDismiss = { deleteDate = null },
            onConfirm = { password ->
                vm.openExistingRodada(date)
                vm.deleteRodada(password) { deleteDate = null }
            },
        )
    }
}

@Composable
private fun RodadaSummaryCard(s: RodadaSummary, onEdit: () -> Unit, onDelete: () -> Unit) {
    val d = runCatching { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(DATE_FMT.parse(s.date)!!) }.getOrDefault(s.date)
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(d.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val pending = s.result == null || s.result.pending || s.result.homeScore == null
                if (!pending) {
                    Text("${s.result?.homeScore}×${s.result?.awayScore}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                } else if (s.result != null) {
                    Text("⏳ Pendente", style = MaterialTheme.typography.labelSmall, color = FutAmber)
                } else {
                    Text("🔄 Em Andamento", style = MaterialTheme.typography.labelSmall, color = FutBlueAccent)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("👥 ${s.confirmedCount} jogadores", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                if (s.homeCount + s.awayCount > 0) {
                    Text("⚽ ${s.homeCount}×${s.awayCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (s.avulsoCount > 0) {
                Text("⚠️ ${s.avulsoCount} avulso(s)", style = MaterialTheme.typography.labelSmall, color = FutAmber)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✏️ Editar", style = MaterialTheme.typography.labelSmall, color = FutGreenStart, modifier = Modifier.clickable(onClick = onEdit))
                Text("🗑️ Excluir", style = MaterialTheme.typography.labelSmall, color = FutRed, modifier = Modifier.clickable(onClick = onDelete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RodadaPickDate(state: RodadaUiState, vm: RodadaViewModel) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            TextButton(onClick = { vm.openLanding() }) { Text("← Voltar") }
            Text("Nova Rodada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        AccentCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📅 Dados da Rodada", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.date, onValueChange = {}, readOnly = true, label = { Text("Data") },
                        modifier = Modifier.weight(1f).clickable { showPicker = true },
                    )
                    GradientButton(onClick = { vm.createRodada() }) { Text("✓ Criar Rodada") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Selecione a data e clique em Criar Rodada. Ela entrará no histórico como Em Andamento e os demais campos serão liberados.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showPicker) {
        val initialMillis = runCatching { DATE_FMT.parse(state.date)?.time }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        vm.setNewDate(utcFmt.format(Date(millis)))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun RodadaManage(state: RodadaUiState, vm: RodadaViewModel, onMatchStarted: () -> Unit) {
    var tab by remember { mutableStateOf(RodadaSubTab.PRESENCA) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAvulsoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val d = runCatching { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(DATE_FMT.parse(state.date)!!) }.getOrDefault(state.date)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.openLanding() }) { Text("← Voltar") }
                Text(d.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    RodadaSubTab.PRESENCA to "👥 Presença",
                    RodadaSubTab.TIRA_GOSTO to "🍽️ Tira Gosto",
                    RodadaSubTab.TIMES to "⚽ Times",
                    RodadaSubTab.RESULTADO to "🏆 Resultado",
                ).forEach { (t, label) ->
                    androidx.compose.material3.FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(label) })
                }
            }
        }
        item {
            when (tab) {
                RodadaSubTab.PRESENCA -> PresencaCard(state, vm, onOrderAvulsos = { showAvulsoDialog = true }, context = context)
                RodadaSubTab.TIRA_GOSTO -> TiraGostoCard(vm)
                RodadaSubTab.TIMES -> TimesCard(state, vm, onMatchStarted)
                RodadaSubTab.RESULTADO -> ResultadoCard(state, vm)
            }
        }
        item {
            if (state.locked) {
                Text(
                    "🔒 Rodada encerrada — edições bloqueadas pelo administrador",
                    style = MaterialTheme.typography.bodySmall, color = FutRed,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }
        item {
            if (!state.locked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.lockRodada() }, modifier = Modifier.weight(1f)) { Text("🔒 Encerrar e Bloquear Rodada") }
                    TextButton(onClick = { showDeleteDialog = true }) { Text("Excluir", color = FutRed) }
                }
            } else {
                OutlinedButton(onClick = { vm.unlockRodada() }, modifier = Modifier.fillMaxWidth()) { Text("🔓 Reabrir Rodada para Edição") }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteRodadaDialog(onDismiss = { showDeleteDialog = false }, onConfirm = { password -> vm.deleteRodada(password) { showDeleteDialog = false } })
    }
    if (showAvulsoDialog) {
        AvulsoOrderDialog(state = state, vm = vm, onDismiss = { showAvulsoDialog = false })
    }

    state.whatsAppLink?.let { link ->
        LaunchedEffect(link) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            vm.consumeWhatsAppLink()
        }
    }
}

@Composable
private fun PresencaCard(state: RodadaUiState, vm: RodadaViewModel, onOrderAvulsos: () -> Unit, context: android.content.Context) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("👥 Presença", style = MaterialTheme.typography.titleSmall)
                Text("${state.confirmedIds.size} confirmados", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
            }
            Spacer(Modifier.height(8.dp))
            state.allPlayers.sortedBy { it.name.lowercase() }.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.name, style = MaterialTheme.typography.bodySmall)
                    Checkbox(checked = p.id in state.confirmedIds, onCheckedChange = { if (!state.locked) vm.toggleConfirmed(p.id) }, enabled = !state.locked)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.selectAll() }, enabled = !state.locked) { Text("Todos", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { vm.clearAll() }, enabled = !state.locked) { Text("Limpar", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = onOrderAvulsos) { Text("↕️ Avulsos", style = MaterialTheme.typography.labelSmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.buildConfirmadosWhatsAppLink()))) }) {
                    Text("✅ Confirmados", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                }
                TextButton(onClick = { vm.sendConfirmadosGrupo() }, enabled = !state.sendingWhatsApp) {
                    Text("📣 Grupo", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.buildInviteWhatsAppLink()))) }) {
                    Text("📋 WA", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                }
                TextButton(onClick = { vm.generateConfirmationLinks() }, enabled = !state.sendingWhatsApp) {
                    Text("🔗 Links", style = MaterialTheme.typography.labelSmall, color = FutBlueAccent)
                }
            }
        }
    }
}

@Composable
private fun TiraGostoCard(vm: RodadaViewModel) {
    val state by vm.state.collectAsState()
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🍽️ Tira Gosto", style = MaterialTheme.typography.titleSmall)
                Text("${state.dinnerIds.size} participantes", style = MaterialTheme.typography.labelSmall, color = FutAmber)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.meal, onValueChange = { vm.updateMeal(it) }, label = { Text("Jantar do dia") },
                singleLine = true, enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.dinnerTotalText, onValueChange = { vm.updateDinnerTotal(it) }, label = { Text("Valor (R$)") },
                singleLine = true, enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.saveDinner() }, enabled = !state.locked) { Text("Salvar") }
            }
            if (state.dinnerIds.isNotEmpty() && state.dinnerShare > 0) {
                Text(
                    "${state.dinnerIds.size} pessoas · R$ %.2f por pessoa".format(state.dinnerShare),
                    color = FutAmber, style = MaterialTheme.typography.titleSmall,
                )
            }
            if (state.loucaRotation.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LoucaResponsavelPicker(vm = vm)
            }
            Spacer(Modifier.height(8.dp))
            Text("Participantes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.allPlayers.sortedBy { it.name.lowercase() }.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.name, style = MaterialTheme.typography.bodySmall)
                    Checkbox(checked = p.id in state.dinnerIds, onCheckedChange = { if (!state.locked) vm.toggleDinnerParticipant(p.id) }, enabled = !state.locked)
                }
            }
        }
    }
}

/** Espelha o `rod-louça-select` do painel web (updateLoucaSelectInRodada) — quem
 * lava a louça desta rodada, com a "próxima vez" da fila pré-selecionada. */
@Composable
private fun LoucaResponsavelPicker(vm: RodadaViewModel) {
    val state by vm.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val current = state.loucaResponsavelId ?: state.suggestedLoucaResponsavel
    val currentName = current?.let { id -> state.allPlayers.find { it.id == id }?.name }
    val isSuggestion = state.loucaResponsavelId == null && current != null

    Column {
        Text("🧹 Responsável pela louça", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = (currentName ?: "Ninguém selecionado") + if (isSuggestion) " (sugestão)" else "",
                onValueChange = {},
                readOnly = true,
                enabled = !state.locked,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.locked) {
                Box(modifier = Modifier.fillMaxSize().clickable { expanded = true })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.loucaRotation.forEach { id ->
                    val name = state.allPlayers.find { it.id == id }?.name ?: return@forEach
                    DropdownMenuItem(text = { Text(name) }, onClick = { vm.setLoucaResponsavel(id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun TimesCard(state: RodadaUiState, vm: RodadaViewModel, onMatchStarted: () -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("⚽ Times", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GradientButton(onClick = { vm.generateTeams() }, enabled = !state.locked) { Text("⚡ Gerar Times") }
                OutlinedButton(onClick = { vm.generateTeams() }, enabled = !state.locked) { Text("🔄 Refazer") }
                OutlinedButton(onClick = { vm.toggleTeamsEditMode() }, enabled = !state.locked) {
                    Text(if (state.teamsEditMode) "✅ Concluir" else "✏️ Editar")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!state.teamsGenerated) {
                Text("Confirme a presença e clique em \"Gerar Times\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TeamDragBoard(
                    home = state.home, away = state.away, bench = vm.benchPlayers(),
                    editable = state.teamsEditMode && !state.locked,
                    onMove = { playerId, zone -> vm.movePlayer(playerId, zone) },
                )
                if (state.teamsEditMode) {
                    Text(
                        "Segure e arraste um jogador pra mover entre os times",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    onClick = { vm.startMatch(onMatchStarted) },
                    enabled = !state.startingMatch && !state.locked,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.startingMatch) "Iniciando…" else "▶ Iniciar Partida") }
            }
        }
    }
}

@Composable
private fun ResultadoCard(state: RodadaUiState, vm: RodadaViewModel) {
    var motmExpanded by remember { mutableStateOf(false) }
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🏆 Resultado", style = MaterialTheme.typography.titleSmall)
                val pending = state.homeScoreText.toIntOrNull() == null
                Text(if (pending) "⏳ Pendente" else "Definido", style = MaterialTheme.typography.labelSmall, color = if (pending) FutAmber else FutGreenStart)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("⬛🟡 T. Preto e Amarelo", style = MaterialTheme.typography.labelSmall, color = FutAmber, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.homeScoreText, onValueChange = { vm.updateHomeScore(it) }, singleLine = true,
                        enabled = !state.locked, modifier = Modifier.width(80.dp).padding(top = 6.dp),
                    )
                }
                Text("×", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("🔵 T. Azul", style = MaterialTheme.typography.labelSmall, color = FutBlueAccent, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.awayScoreText, onValueChange = { vm.updateAwayScore(it) }, singleLine = true,
                        enabled = !state.locked, modifier = Modifier.width(80.dp).padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { vm.saveResult() }, enabled = !state.locked) { Text("💾 Salvar placar") }
            }
            Spacer(Modifier.height(8.dp))
            Text("🏆 Melhor da Partida", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.allPlayers.find { it.id == state.motmId }?.name ?: "— Selecionar MVP —",
                    onValueChange = {}, readOnly = true, enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
                )
                if (!state.locked) Box(modifier = Modifier.fillMaxSize().clickable { motmExpanded = true })
                DropdownMenu(expanded = motmExpanded, onDismissRequest = { motmExpanded = false }) {
                    DropdownMenuItem(text = { Text("— Nenhum —") }, onClick = { vm.updateMotm(null); motmExpanded = false })
                    state.allPlayers.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.updateMotm(p.id); motmExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun AvulsoOrderDialog(state: RodadaUiState, vm: RodadaViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordenar avulsos") },
        text = {
            Column {
                if (state.orderedAvulsosConfirmed.isEmpty()) {
                    Text("Nenhum avulso confirmado nesta rodada.", style = MaterialTheme.typography.bodySmall)
                }
                state.orderedAvulsosConfirmed.forEachIndexed { i, p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${p.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.moveAvulsoUp(p.id) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
                        }
                        IconButton(onClick = { vm.moveAvulsoDown(p.id) }, enabled = i < state.orderedAvulsosConfirmed.size - 1) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun DeleteRodadaDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir rodada") },
        text = {
            Column {
                Text("Essa ação não pode ser desfeita. Confirme sua senha:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Senha") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }) { Text("Excluir", color = FutRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
