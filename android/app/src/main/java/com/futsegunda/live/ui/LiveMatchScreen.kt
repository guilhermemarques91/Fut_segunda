package com.futsegunda.live.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.viewmodel.LiveMatchViewModel
import com.futsegunda.live.viewmodel.TEAM_AWAY
import com.futsegunda.live.viewmodel.TEAM_HOME

private const val HOME_LABEL = "T. Preto e Amarelo"
private const val AWAY_LABEL = "T. Azul"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchScreen(onLogout: () -> Unit, vm: LiveMatchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var goalSheetTeam by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ao vivo — Fut Segunda") },
                actions = {
                    if (state.pendingCount > 0) {
                        Text("${state.pendingCount} pendente(s)", modifier = Modifier.padding(end = 12.dp))
                    }
                    IconButton(onClick = onLogout) { Text("Sair") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !state.hasActiveMatch -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚽", style = MaterialTheme.typography.displayMedium)
                        Text("Nenhuma partida ativa no momento")
                        Text("Inicie a partida pelo painel web (aba Rodada → Iniciar Partida)")
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TeamScoreColumn(
                            label = HOME_LABEL,
                            score = vm.scoreFor(TEAM_HOME),
                            onAdd = { goalSheetTeam = TEAM_HOME },
                            onUndo = { vm.undoGoal(TEAM_HOME) },
                        )
                        TeamScoreColumn(
                            label = AWAY_LABEL,
                            score = vm.scoreFor(TEAM_AWAY),
                            onAdd = { goalSheetTeam = TEAM_AWAY },
                            onUndo = { vm.undoGoal(TEAM_AWAY) },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    PeriodControls(periodo = state.live?.periodo, onEvent = vm::periodEvent)

                    Spacer(Modifier.height(16.dp))
                    if (state.lastSyncError) {
                        Text("Sem conexão com o servidor — tentando de novo...", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    goalSheetTeam?.let { team ->
        GoalSheet(
            teamLabel = if (team == TEAM_HOME) HOME_LABEL else AWAY_LABEL,
            playersFor = { ownGoal -> vm.playersOf(team, ownGoal) },
            onConfirm = { scorerId, assistId, ownGoal, minute ->
                vm.addGoal(team, scorerId, assistId, ownGoal, minute)
                goalSheetTeam = null
            },
            onDismiss = { goalSheetTeam = null },
        )
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.dismissToast()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
}

@Composable
private fun TeamScoreColumn(label: String, score: Int, onAdd: () -> Unit, onUndo: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text("$score", style = MaterialTheme.typography.displayLarge)
        Row {
            IconButton(onClick = onUndo) { Icon(Icons.Filled.Remove, contentDescription = "Desfazer gol") }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Marcar gol") }
        }
    }
}

@Composable
private fun PeriodControls(periodo: Int?, onEvent: (String) -> Unit) {
    Column {
        Text("Controle de tempo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (periodo) {
                null, 0 -> Button(onClick = { onEvent("start_t1") }) { Text("Iniciar 1ºT") }
                1 -> OutlinedButton(onClick = { onEvent("end_t1") }) { Text("Encerrar 1ºT") }
                2 -> OutlinedButton(onClick = { onEvent("end_t2") }) { Text("Encerrar partida") }
            }
            if (periodo == 1) {
                Button(onClick = { onEvent("start_t2") }) { Text("Iniciar 2ºT") }
            }
        }
    }
}
