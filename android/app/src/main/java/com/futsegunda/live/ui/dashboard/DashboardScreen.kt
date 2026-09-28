package com.futsegunda.live.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Atletas", value = state.playerCount.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "Partidas", value = state.matchCount.toString(), modifier = Modifier.weight(1f))
            }
        }
        item {
            AccentCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Caixa total", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "R$ %.2f".format(state.caixaTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.caixaTotal >= 0) FutGreenStart else FutRed,
                    )
                }
            }
        }
        item {
            Text("Últimas partidas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (state.recentResults.isEmpty()) {
            item { Text("Nenhuma partida registrada ainda", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.recentResults) { r -> ResultRow(r) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    AccentCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultRow(r: ResultDto) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(r.date ?: "—")
            Text("${r.homeScore ?: 0} x ${r.awayScore ?: 0}", style = MaterialTheme.typography.titleMedium)
        }
    }
}
