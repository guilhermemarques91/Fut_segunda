package com.futsegunda.live.ui.historico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.ResultDto
import com.futsegunda.live.network.TeamHistoryDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.viewmodel.CalendarDay
import com.futsegunda.live.viewmodel.HistoricoUiState
import com.futsegunda.live.viewmodel.HistoricoViewModel
import com.futsegunda.live.viewmodel.MONTH_NAMES_SHORT
import com.futsegunda.live.viewmodel.MatrixRow
import com.futsegunda.live.viewmodel.PlayerTally

/**
 * Espelha as 5 abas de showHistTab() (frontend/index.html:1401-1411), na
 * mesma ordem: Presenças (calendário+matriz) / Times / Resultados /
 * Rankings / Tira Gosto — tudo derivado do AppDataCache, sem endpoint
 * próprio (exceto a edição manual de resultado).
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
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("👥 Presenças") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("⚽ Times") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("📊 Resultados") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("🏆 Rankings") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("🍽️ Tira Gosto") })
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else when (tab) {
                0 -> PresencasTab(state, vm)
                1 -> TimesTab(state)
                2 -> ResultadosTab(state = state, onEdit = { editingResult = it; showResultForm = true })
                3 -> RankingsTab(state)
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

// ═══════════════════════ PRESENÇAS ═══════════════════════

@Composable
private fun PresencasTab(state: HistoricoUiState, vm: HistoricoViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("📅", state.totalMatchesInFilter.toString(), "Partidas", Modifier.weight(1f))
                StatCard("👥", state.avgAttendance, "Média por Jogo", Modifier.weight(1f))
                StatCard("🏆", state.topAttendee?.let { "${it.player.name} (${it.total}x)" } ?: "—", "Mais Presente", Modifier.weight(1f))
            }
        }
        item { CalendarCard(state, vm) }
        item { DayDetailCard(state) }
        item { MatrixCard(state, vm) }
    }
}

@Composable
private fun StatCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    AccentCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarCard(state: HistoricoUiState, vm: HistoricoViewModel) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Calendário de Jogos", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.calNav(-1) }) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                Text(state.calendarMonthLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { vm.calNav(1) }) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                MONTH_NAMES_SHORT_DOW.forEach {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            state.calendarCells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell -> CalendarCell(cell, onClick = { cell?.let { vm.selectDay(it.date) } }, modifier = Modifier.weight(1f)) }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(FutGreenStart, "Jogo realizado")
                Text("Hoje: contorno", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val MONTH_NAMES_SHORT_DOW = com.futsegunda.live.viewmodel.DOW_NAMES_SHORT

@Composable
private fun CalendarCell(cell: CalendarDay?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(44.dp).padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (cell?.hasMatch == true) Modifier.background(if (cell.isSelected) FutGreenStart.copy(alpha = 0.35f) else FutGreenStart.copy(alpha = 0.15f)) else Modifier,
            )
            .then(if (cell?.isToday == true) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(enabled = cell?.hasMatch == true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cell.day.toString(), style = MaterialTheme.typography.labelSmall)
                if (cell.hasMatch) {
                    Text("${cell.playerCount}👤", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetailCard(state: HistoricoUiState) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Detalhe do Jogo", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val detail = state.selectedDayDetail
            if (detail == null) {
                Text("Selecione um jogo no calendário", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(detail.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "vs ${detail.opponent.ifBlank { "Adversário" }} · ${detail.present.size} presentes · ${detail.absent.size} ausentes",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("✅ Presentes (${detail.present.size})", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                Text(detail.present.joinToString(", ") { it.name }.ifBlank { "Nenhum" }, style = MaterialTheme.typography.bodySmall)
                if (detail.absent.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("❌ Ausentes (${detail.absent.size})", style = MaterialTheme.typography.labelSmall, color = FutRed)
                    Text(detail.absent.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall)
                }
                detail.result?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (r.pending || r.homeScore == null) "⏳ Pendente" else "${r.homeScore}×${r.awayScore}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixCard(state: HistoricoUiState, vm: HistoricoViewModel) {
    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Matriz de Presenças", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(selected = state.histFilter == "all", onClick = { vm.setHistFilter("all") }, label = { Text("Todos") })
                state.availableMonths.forEach { m ->
                    val (y, mo) = m.split("-")
                    FilterChip(
                        selected = state.histFilter == m, onClick = { vm.setHistFilter(m) },
                        label = { Text("${MONTH_NAMES_SHORT[mo.toInt() - 1]} $y") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(FutGreenStart, "Mensal presente")
                LegendDot(FutAmber, "Avulso presente")
            }
            Spacer(Modifier.height(10.dp))
            if (state.filteredGames.isEmpty() || state.players.isEmpty()) {
                Text("Nenhuma presença registrada no período", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.horizontalScroll(scrollState)) {
                    Row {
                        Box(Modifier.width(120.dp)) { Text("Jogador", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        state.filteredGames.forEach { g ->
                            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                Text(g.date.takeLast(5).replace("-", "/"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    state.matrixRows.forEach { row -> MatrixRowView(row, state) }
                }
            }
        }
    }
}

@Composable
private fun MatrixRowView(row: MatrixRow, state: HistoricoUiState) {
    val barColor = if (row.pct >= 75) FutGreenStart else if (row.pct >= 40) FutAmber else FutRed
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp)) {
            Text(row.player.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        state.filteredGames.forEach { g ->
            val present = row.player.id in g.players
            val dotColor = when {
                !present -> MaterialTheme.colorScheme.surfaceVariant
                row.player.isIsento -> Color(0xFF94A3B8)
                row.player.isRegular -> FutGreenStart
                else -> FutAmber
            }
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            }
        }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            Text("${row.count}/${state.totalMatchesInFilter}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = barColor)
        }
    }
}

// ═══════════════════════ TIMES ═══════════════════════

@Composable
private fun TimesTab(state: HistoricoUiState) {
    val list = state.teamHistory.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma rodada salva ainda.") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { t -> TeamHistoryRow(t, state) }
    }
}

@Composable
private fun TeamHistoryRow(t: TeamHistoryDto, state: HistoricoUiState) {
    val homePlayers = t.home.mapNotNull { id -> state.players.find { it.id == id } }
    val awayPlayers = t.away.mapNotNull { id -> state.players.find { it.id == id } }
    val homeAvg = if (homePlayers.isNotEmpty()) homePlayers.sumOf { it.overall ?: 0 } / homePlayers.size else 0
    val awayAvg = if (awayPlayers.isNotEmpty()) awayPlayers.sumOf { it.overall ?: 0 } / awayPlayers.size else 0
    val result = state.results.find { it.date == t.date }
    val att = state.attendances.find { it.date == t.date }
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(t.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(t.opponent?.takeIf { it.isNotBlank() }?.let { "vs $it" }, att?.let { "${it.players.size} presentes" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result != null && !result.pending && result.homeScore != null) {
                    Text("${result.homeScore}×${result.awayScore}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniTeamColumn("⬛🟡 T. Preto e Amarelo", FutAmber, homePlayers, homeAvg, t.homeReserve?.let { id -> state.players.find { it.id == id } }, Modifier.weight(1f))
                MiniTeamColumn("🔵 T. Azul", FutBlueAccent, awayPlayers, awayAvg, t.awayReserve?.let { id -> state.players.find { it.id == id } }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniTeamColumn(label: String, color: Color, players: List<PlayerDto>, avg: Int, reserve: PlayerDto?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Text("Méd. $avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("Goleiro", "Defesa", "Meio", "Ataque").forEach { pos ->
            val pp = players.filter { it.position == pos }
            if (pp.isNotEmpty()) {
                Text(
                    "${pos.take(3)}: ${pp.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        reserve?.let { Text("Res: ${it.name}", style = MaterialTheme.typography.labelSmall, color = FutAmber) }
    }
}

// ═══════════════════════ RESULTADOS ═══════════════════════

@Composable
private fun ResultadosTab(state: HistoricoUiState, onEdit: (ResultDto) -> Unit) {
    val list = state.results.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum resultado registrado") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { r -> ResultHistoryRow(r, state, onEdit) }
    }
}

@Composable
private fun ResultHistoryRow(r: ResultDto, state: HistoricoUiState, onEdit: (ResultDto) -> Unit) {
    val pending = r.pending || r.homeScore == null
    val hw = !pending && (r.homeScore ?: 0) > (r.awayScore ?: 0)
    val aw = !pending && (r.awayScore ?: 0) > (r.homeScore ?: 0)
    val resultLabel = if (pending) "" else if (hw) "⬛🟡 T. Preto e Amarelo venceu" else if (aw) "🔵 T. Azul venceu" else "🤝 Empate"
    val resultColor = if (hw) FutAmber else if (aw) FutBlueAccent else MaterialTheme.colorScheme.onSurfaceVariant
    val goals = r.goals.filter { it.count > 0 }
    val motmPlayer = state.players.find { it.id == r.motm }

    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEdit(r) }) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(r.date ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (resultLabel.isNotEmpty()) Text(resultLabel, style = MaterialTheme.typography.labelSmall, color = resultColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text(r.homeTeam ?: "", style = MaterialTheme.typography.bodySmall, color = if (hw) FutAmber else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(
                    if (pending) "⏳" else "${r.homeScore}×${r.awayScore}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Text(r.awayTeam ?: "", style = MaterialTheme.typography.bodySmall, color = if (aw) FutBlueAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            if (goals.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("⚽ Gols", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    goals.joinToString(" · ") { g -> "${state.players.find { it.id == g.playerId }?.name ?: "?"} ${g.count}" },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            motmPlayer?.let {
                Spacer(Modifier.height(6.dp))
                Text("🏆 MVP: ⭐ ${it.name}", style = MaterialTheme.typography.labelSmall, color = FutAmber, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════ RANKINGS ═══════════════════════

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
private fun RankingRow(index: Int, tally: PlayerTally, unit: Pair<String, String>, accent: Color) {
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

// ═══════════════════════ TIRA GOSTO ═══════════════════════

@Composable
private fun TiraGostoTab(state: HistoricoUiState) {
    val list = state.dinnerHistory.sortedByDescending { it.date }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma janta registrada ainda.") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        items(list, key = { it.id ?: it.date.hashCode() }) { d -> DinnerRow(d) }
    }
}

@Composable
private fun DinnerRow(d: DinnerHistoryDto) {
    val total = d.participants.size
    val paid = d.paidBy.size
    val pct = if (total > 0) paid * 100 / total else 0
    val allPaid = paid >= total && total > 0
    val barColor = if (allPaid) FutGreenStart else if (paid > 0) FutAmber else FutRed
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🍽️ ${d.date}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    d.meal?.takeIf { it.isNotBlank() }?.let { Text("\"$it\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        if (d.total > 0) "$total pessoas · R$ %.2f · R$ %.2f/pessoa".format(d.total, d.share) else "$total pessoas · valor não definido",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d.total > 0) MaterialTheme.colorScheme.onSurfaceVariant else FutAmber,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$paid/$total pagaram", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = barColor)
                    Box(Modifier.width(72.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
                    }
                }
            }
        }
    }
}
