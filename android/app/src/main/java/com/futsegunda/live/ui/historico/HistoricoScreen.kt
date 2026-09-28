package com.futsegunda.live.ui.historico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
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
import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.TeamHistoryDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.viewmodel.HistoricoUiState
import com.futsegunda.live.viewmodel.HistoricoViewModel
import com.futsegunda.live.viewmodel.PlayerTally

/**
 * Resultados (edição manual), Rankings (artilheiro/MOTM, calculados
 * localmente), Times, Presenças e Tira Gosto — tudo derivado do
 * AppDataCache, sem endpoint próprio (exceto a edição de resultado).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(vm: HistoricoViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editingResult by remember { mutableStateOf<ResultDto?>(null) }
    var showResultForm by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Resultados") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Rankings") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Times") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Presenças") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Tira Gosto") })
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else when (tab) {
                0 -> ResultadosTab(state = state, onEdit = { editingResult = it; showResultForm = true })
                1 -> RankingsTab(state)
                2 -> TimesTab(state)
                3 -> PresencasTab(state)
                4 -> TiraGostoTab(state)
            }
        }
    }

    if (showResultForm) {
        editingResult?.let { r ->
            ResultEditDialog(
                result = r,
                players = state.players,
                onDismiss = { showResultForm = false },
                onSave = { home, away, motm, pending ->
                    vm.saveResult(r.date ?: "", home, away, motm, pending)
                    showResultForm = false
                },
            )
        }
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
private fun ResultadosTab(state: HistoricoUiState, onEdit: (ResultDto) -> Unit) {
    val list = state.results.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum resultado registrado ainda") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { r ->
            val motmName = state.players.find { it.id == r.motm }?.name
            AccentCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { onEdit(r) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.date ?: "—", style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(r.homeTeam, r.awayTeam).joinToString(" x "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (motmName != null) {
                            Text("MVP: $motmName", style = MaterialTheme.typography.bodySmall, color = FutAmber)
                        }
                    }
                    Text(
                        if (r.pending) "Pendente" else "${r.homeScore ?: 0} x ${r.awayScore ?: 0}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (r.pending) FutAmber else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingsTab(state: HistoricoUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("⚽ Artilheiro", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (state.goalRanking.isEmpty()) {
            item { Text("Nenhum gol registrado ainda", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp)) }
        } else {
            itemsIndexed(state.goalRanking) { i, t -> RankingRow(i, t, "gol" to "gols", FutGreenStart) }
        }
        item {
            Text("🏆 Melhor da Partida", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        }
        if (state.motmRanking.isEmpty()) {
            item { Text("Nenhum MVP eleito ainda", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            itemsIndexed(state.motmRanking) { i, t -> RankingRow(i, t, "vez" to "vezes", FutAmber) }
        }
        item {
            Text(
                "${state.matchesComputed} partida${if (state.matchesComputed != 1) "s" else ""} computada${if (state.matchesComputed != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun medal(i: Int): String = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }

@Composable
private fun RankingRow(index: Int, tally: PlayerTally, unit: Pair<String, String>, accent: androidx.compose.ui.graphics.Color) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(medal(index), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tally.player.name, style = MaterialTheme.typography.bodyMedium)
                tally.player.position?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(tally.total.toString(), style = MaterialTheme.typography.titleMedium, color = accent)
            Text(" ${if (tally.total == 1) unit.first else unit.second}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimesTab(state: HistoricoUiState) {
    val list = state.teamHistory.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum time montado ainda") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { t -> TeamHistoryRow(t, state) }
    }
}

@Composable
private fun TeamHistoryRow(t: TeamHistoryDto, state: HistoricoUiState) {
    fun names(ids: List<Int>) = ids.mapNotNull { id -> state.players.find { it.id == id }?.name }.joinToString(", ")
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(t.date, style = MaterialTheme.typography.titleSmall)
            Text("Preto: ${names(t.home).ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
            Text("Azul: ${names(t.away).ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PresencasTab(state: HistoricoUiState) {
    val list = state.attendances.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma presença registrada ainda") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.date }) { a -> AttendanceRow(a) }
    }
}

@Composable
private fun AttendanceRow(a: AttendanceDto) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(a.date, style = MaterialTheme.typography.titleSmall)
                a.opponent?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("${a.players.size} confirmados", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TiraGostoTab(state: HistoricoUiState) {
    val list = state.dinnerHistory.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum tira-gosto registrado ainda") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { d -> DinnerRow(d) }
    }
}

@Composable
private fun DinnerRow(d: DinnerHistoryDto) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(d.date, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(d.meal, "${d.participants.size} pessoas").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("R$ %.2f".format(d.total), style = MaterialTheme.typography.bodyMedium)
                Text("R$ %.2f/pessoa".format(d.realShare), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
