package com.futsegunda.live.ui.rodada

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.viewmodel.RodadaViewModel
import com.futsegunda.live.viewmodel.TeamZone

private enum class RodadaTab { PRESENCA, TIRA_GOSTO, TIMES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RodadaScreen(onMatchStarted: () -> Unit, vm: RodadaViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(RodadaTab.PRESENCA) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rodada — ${state.date}", style = MaterialTheme.typography.titleLarge)
        if (state.locked) {
            Text("🔒 Rodada bloqueada — só leitura", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == RodadaTab.PRESENCA, onClick = { tab = RodadaTab.PRESENCA }, label = { Text("👥 Presença") })
            FilterChip(selected = tab == RodadaTab.TIRA_GOSTO, onClick = { tab = RodadaTab.TIRA_GOSTO }, label = { Text("🍽️ Tira Gosto") })
            FilterChip(selected = tab == RodadaTab.TIMES, onClick = { tab = RodadaTab.TIMES }, label = { Text("⚽ Times") })
        }
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                RodadaTab.PRESENCA -> PresencaTab(state.allPlayers, state.confirmedIds, state.locked, vm::toggleConfirmed)
                RodadaTab.TIRA_GOSTO -> TiraGostoTab(vm)
                RodadaTab.TIMES -> TimesTab(vm, onMatchStarted)
            }
        }

        if (!state.locked) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.lockRodada() }, modifier = Modifier.weight(1f)) { Text("🔒 Encerrar rodada") }
                TextButton(onClick = { showDeleteDialog = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.unlockRodada() }, modifier = Modifier.fillMaxWidth()) { Text("🔓 Reabrir rodada") }
        }
    }

    if (showDeleteDialog) {
        DeleteRodadaDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = { password -> vm.deleteRodada(password) { showDeleteDialog = false } },
        )
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.dismissToast() }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
    state.error?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.dismissError() }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
}

@Composable
private fun PresencaTab(players: List<PlayerDto>, confirmedIds: List<Int>, locked: Boolean, onToggle: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("${confirmedIds.size} confirmados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(players.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.name)
                        Checkbox(checked = p.id in confirmedIds, onCheckedChange = { if (!locked) onToggle(p.id) }, enabled = !locked)
                    }
                }
            }
        }
    }
}

@Composable
private fun TiraGostoTab(vm: RodadaViewModel) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.meal, onValueChange = { vm.updateMeal(it) }, label = { Text("O que vai ter (opcional)") },
            singleLine = true, enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dinnerTotalText, onValueChange = { vm.updateDinnerTotal(it) }, label = { Text("Valor total (R$)") },
            singleLine = true, enabled = !state.locked, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.saveDinner() }, enabled = !state.locked) { Text("Salvar valor") }
        }
        if (state.dinnerIds.isNotEmpty() && state.dinnerShare > 0) {
            Text(
                "${state.dinnerIds.size} pessoas · R$ %.2f por pessoa".format(state.dinnerShare),
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Participantes", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.allPlayers.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.name)
                        Checkbox(
                            checked = p.id in state.dinnerIds,
                            onCheckedChange = { if (!state.locked) vm.toggleDinnerParticipant(p.id) },
                            enabled = !state.locked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimesTab(vm: RodadaViewModel, onMatchStarted: () -> Unit) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(onClick = { vm.generateTeams() }, enabled = !state.locked) { Text("🔀 Gerar times") }
            OutlinedButton(onClick = { vm.toggleTeamsEditMode() }, enabled = !state.locked) {
                Text(if (state.teamsEditMode) "✅ Concluir" else "✏️ Editar")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!state.teamsGenerated) {
            Text("Nenhum time gerado ainda", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(modifier = Modifier.weight(1f)) {
                TeamDragBoard(
                    home = state.home, away = state.away, bench = vm.benchPlayers(),
                    editable = state.teamsEditMode && !state.locked,
                    onMove = { playerId, zone -> vm.movePlayer(playerId, zone) },
                )
            }
            if (state.teamsEditMode) {
                Text(
                    "Segure e arraste um jogador pra mover entre os times",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun DeleteRodadaDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
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
        confirmButton = { TextButton(onClick = { onConfirm(password) }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
