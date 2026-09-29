package com.futsegunda.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.futsegunda.wear.network.PlayerDto
import com.futsegunda.wear.ui.theme.FutAmber
import com.futsegunda.wear.ui.theme.FutBackground
import com.futsegunda.wear.ui.theme.FutBlueAccent
import com.futsegunda.wear.ui.theme.FutRed
import com.futsegunda.wear.ui.theme.FutTeamPreto
import com.futsegunda.wear.update.UpdateChecker
import com.futsegunda.wear.viewmodel.LiveViewModel
import com.futsegunda.wear.viewmodel.TEAM_AWAY
import com.futsegunda.wear.viewmodel.TEAM_HOME

private const val HOME_LABEL = "T. Preto"
private const val AWAY_LABEL = "T. Azul"

private fun periodLabel(periodo: Int?): String = when (periodo) {
    null, 0 -> "Aguardando início"
    1 -> "1º Tempo em andamento"
    2 -> "2º Tempo em andamento"
    else -> "Encerrado"
}

@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var pickingTeam by remember { mutableStateOf<String?>(null) }
    var newVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        newVersion = try { UpdateChecker.newerVersionName() } catch (e: Exception) { null }
    }

    val team = pickingTeam
    if (team != null) {
        ScorerPickerScreen(
            teamLabel = if (team == TEAM_HOME) HOME_LABEL else AWAY_LABEL,
            players = vm.playersOf(team),
            onPick = { scorerId ->
                vm.addGoal(team, scorerId)
                pickingTeam = null
            },
            onCancel = { pickingTeam = null },
        )
        return
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListHeader {
                Column {
                    Text("Fut Segunda")
                    if (state.hasActiveMatch) {
                        Text(
                            periodLabel(state.live?.periodo),
                            style = MaterialTheme.typography.caption2,
                        )
                    }
                }
            }
        }

        newVersion?.let { v ->
            item {
                Chip(
                    onClick = { newVersion = null },
                    label = { Text("Nova versão $v — atualize via ADB") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }

        if (state.loading) {
            item { CircularProgressIndicator() }
            return@ScalingLazyColumn
        }

        if (!state.hasActiveMatch) {
            item { Text("Nenhuma partida ativa") }
            return@ScalingLazyColumn
        }

        if (state.pendingCount > 0) {
            item {
                Text(
                    "${state.pendingCount} gol(s) na fila, sem sinal",
                    color = FutAmber,
                )
            }
        }

        item {
            Chip(
                onClick = { pickingTeam = TEAM_HOME },
                label = { Text("$HOME_LABEL  ${vm.scoreFor(TEAM_HOME)}   ⚽ +1") },
                colors = ChipDefaults.chipColors(backgroundColor = FutTeamPreto, contentColor = Color.White),
            )
        }
        item {
            CompactChip(
                onClick = { vm.undoGoal(TEAM_HOME) },
                label = { Text("↩ Desfazer $HOME_LABEL") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        item {
            Chip(
                onClick = { pickingTeam = TEAM_AWAY },
                label = { Text("$AWAY_LABEL  ${vm.scoreFor(TEAM_AWAY)}   ⚽ +1") },
                colors = ChipDefaults.chipColors(backgroundColor = FutBlueAccent, contentColor = FutBackground),
            )
        }
        item {
            CompactChip(
                onClick = { vm.undoGoal(TEAM_AWAY) },
                label = { Text("↩ Desfazer $AWAY_LABEL") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }

        item { ListHeader { Text("Tempo") } }
        val periodo = state.live?.periodo
        when (periodo) {
            null, 0 -> item {
                Chip(
                    onClick = { vm.periodEvent("start_t1") },
                    label = { Text("▶ Iniciar 1ºT") },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
            1 -> {
                item {
                    Chip(
                        onClick = { vm.periodEvent("end_t1") },
                        label = { Text("⏸ Encerrar 1ºT") },
                        colors = ChipDefaults.chipColors(backgroundColor = FutAmber, contentColor = FutBackground),
                    )
                }
                item {
                    Chip(
                        onClick = { vm.periodEvent("start_t2") },
                        label = { Text("▶ Iniciar 2ºT") },
                        colors = ChipDefaults.primaryChipColors(),
                    )
                }
            }
            2 -> item {
                Chip(
                    onClick = { vm.periodEvent("end_t2") },
                    label = { Text("🏁 Encerrar partida") },
                    colors = ChipDefaults.chipColors(backgroundColor = FutRed, contentColor = Color.White),
                )
            }
        }
    }
}

/**
 * Tela cheia com a lista de jogadores do time pra escolher quem marcou.
 * "Não sei / sem artilheiro" manda o gol mesmo assim (scorerId nulo) — dá
 * pra completar depois pelo painel web ou pelo app Android.
 */
@Composable
private fun ScorerPickerScreen(
    teamLabel: String,
    players: List<PlayerDto>,
    onPick: (Int?) -> Unit,
    onCancel: () -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text("Gol — $teamLabel") } }
        items(players) { p: PlayerDto ->
            Chip(onClick = { onPick(p.id) }, label = { Text(p.name) })
        }
        item {
            Chip(
                onClick = { onPick(null) },
                label = { Text("Não sei quem marcou") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = onCancel,
                label = { Text("Cancelar") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}
