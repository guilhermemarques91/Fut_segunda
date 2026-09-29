package com.futsegunda.live.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.ui.nav.AppDestination
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.viewmodel.DashboardUiState
import com.futsegunda.live.viewmodel.DashboardViewModel

/**
 * Espelha renderDashboard() do painel web (frontend/index.html:427-480,1882):
 * grid de 4 stat cards + coluna Pendências/Resumo Financeiro + coluna
 * Últimas Partidas/Destaques (empilhadas verticalmente em tela estreita,
 * igual ao `.dash-main{grid-template-columns:1fr 320px}` que colapsa pra
 * 1 coluna abaixo de 1024px).
 */
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel(), onNavigate: (String) -> Unit = {}) {
    val state by vm.state.collectAsState()

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // .stats-grid do painel: grid 4x1 no desktop, 2x2 abaixo de 720px (frontend/index.html:39-40).
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("💰", "R$ %.2f".format(state.caixaTotal), "Caixa Total", Modifier.weight(1f)) { onNavigate(AppDestination.Financeiro.route) }
                    StatCard("👥", state.players.size.toString(), "Atletas", Modifier.weight(1f)) { onNavigate(AppDestination.Players.route) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("⚠️", state.pendCount.toString(), "Com Pendência", Modifier.weight(1f)) { onNavigate(AppDestination.Financeiro.route) }
                    StatCard(
                        "⚽",
                        state.matchCount.toString() + if (state.pendingResultsCount > 0) " (${state.pendingResultsCount} pend.)" else "",
                        "Partidas",
                        Modifier.weight(1f),
                    ) { onNavigate(AppDestination.Historico.route) }
                }
            }
        }

        item { PendenciasCard(state, onNavigate) }
        item { ResumoFinanceiroCard(state, onNavigate) }
        item { UltimasPartidasCard(state, onNavigate) }
        item { DestaquesCard(state) }
    }
}

@Composable
private fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AccentCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CardHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(actionLabel, style = MaterialTheme.typography.labelSmall, color = FutGreenStart, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
private fun PendenciasCard(state: DashboardUiState, onNavigate: (String) -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            CardHeader("Pendências", "Ver tudo →") { onNavigate(AppDestination.Financeiro.route) }
            if (state.debtorGroups.isEmpty()) {
                Text("Nenhuma pendência", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                state.debtorGroups.take(8).forEach { g ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(AppDestination.Financeiro.route) }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(g.name, style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            g.items.take(3).forEach { it ->
                                Text(
                                    "${it.desc} · R$ %.2f".format(it.amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (it.badge == "tiragosto") FutRed else FutAmber,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumoFinanceiroCard(state: DashboardUiState, onNavigate: (String) -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            CardHeader("Resumo Financeiro", "Financeiro →") { onNavigate(AppDestination.Financeiro.route) }
            SummaryRow("Total recebido", state.caixaTotal, FutGreenStart)
            SummaryRow("Pend. mensalidades", state.totalFeePend, FutAmber)
            SummaryRow("Pend. tira gosto", state.totalDinPend, FutRed)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total a receber", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("R$ %.2f".format(state.totalFeePend + state.totalDinPend), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("R$ %.2f".format(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun UltimasPartidasCard(state: DashboardUiState, onNavigate: (String) -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            CardHeader("Últimas Partidas", "Ver tudo →") { onNavigate(AppDestination.Historico.route) }
            if (state.recentResults.isEmpty()) {
                Text("Sem resultados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.recentResults.forEach { r -> ResultRow(r) { onNavigate(AppDestination.Historico.route) } }
            }
        }
    }
}

@Composable
private fun ResultRow(r: ResultDto, onClick: () -> Unit) {
    val pending = r.pending || r.homeScore == null
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(r.date ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.homeTeam ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (pending) {
                Text("⏳ Pend.", style = MaterialTheme.typography.bodySmall, color = FutAmber, fontWeight = FontWeight.Bold)
            } else {
                Text("${r.homeScore}×${r.awayScore}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(r.awayTeam ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = FutBlueAccent)
        }
    }
}

@Composable
private fun DestaquesCard(state: DashboardUiState) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Destaques", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 10.dp))
            val year = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            if (state.topScorer == null && state.topMotm == null) {
                Text("Sem dados ainda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.topScorer?.let { h ->
                    HighlightRow("⚽", "Artilheiro $year", h.playerName, "${h.count} gol${if (h.count != 1) "s" else ""}", FutGreenStart)
                }
                state.topMotm?.let { h ->
                    HighlightRow("🏆", "MOTM $year", h.playerName, "${h.count}×", FutAmber)
                }
            }
        }
    }
}

@Composable
private fun HighlightRow(icon: String, label: String, name: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
