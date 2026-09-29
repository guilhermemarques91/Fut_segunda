package com.futsegunda.live.ui.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.futsegunda.live.domain.ReceberItems
import com.futsegunda.live.network.ConfigDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.OverallRing
import com.futsegunda.live.viewmodel.PLAYER_POSITIONS
import com.futsegunda.live.viewmodel.PlayersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(vm: PlayersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var ratingPlayer by remember { mutableStateOf<PlayerDto?>(null) }

    if (state.form != null) {
        PlayerFormScreen(vm = vm)
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openNewForm() }) { Icon(Icons.Filled.Add, contentDescription = "Novo jogador") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                label = { Text("Buscar por nome ou apelido") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.positionFilter == null,
                        onClick = { vm.setPositionFilter(null) },
                        label = { Text("Todos") },
                    )
                }
                items(PLAYER_POSITIONS) { pos ->
                    FilterChip(
                        selected = state.positionFilter == pos,
                        onClick = { vm.setPositionFilter(if (state.positionFilter == pos) null else pos) },
                        label = { Text(pos) },
                    )
                }
            }

            Spacer(Modifier.padding(top = 4.dp))

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val list = vm.filteredPlayers()
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum jogador encontrado") }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(list, key = { it.id }) { p ->
                            PlayerRow(
                                player = p,
                                config = state.config,
                                onRate = { ratingPlayer = p },
                                onEdit = { vm.openEditForm(p) },
                                onDelete = { vm.delete(p.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    ratingPlayer?.let { p ->
        PlayerRatingSheet(
            player = p,
            onDismiss = { ratingPlayer = null },
            onConfirm = { phy, tac, tec ->
                vm.rateQuick(p.id, phy, tac, tec)
                ratingPlayer = null
            },
        )
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.dismissToast()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
}

/** Espelha o `.player-item` do painel (renderPlayerList(), frontend/index.html:2220-2250). */
@Composable
private fun PlayerRow(player: PlayerDto, config: ConfigDto, onRate: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AccentCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val media = player.video ?: player.photo
            if (!media.isNullOrBlank()) {
                AsyncImage(
                    model = media,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            OverallRing(player.overall ?: 0, size = 40.dp)
            Spacer(Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                val subtitle = listOfNotNull(
                    player.apelido?.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                    player.whatsapp?.takeIf { it.isNotBlank() }?.let { "📱 $it" },
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(player.position.orEmpty().uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("💪${player.attributes.physical}", style = MaterialTheme.typography.labelSmall, color = FutGreenStart)
                    Text("🧠${player.attributes.tactical}", style = MaterialTheme.typography.labelSmall, color = FutBlueAccent)
                    Text("⚡${player.attributes.technical}", style = MaterialTheme.typography.labelSmall, color = FutAmber)
                }
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypeBadge(if (player.isRegular) "Mensal" else "Avulso")
                    if (player.isIsento) TypeBadge("Isento")
                    val exempt = ReceberItems.isFeeExempt(player, config.goleiroIsento)
                    Text(
                        if (exempt) "Sem cobrança" else "R$ ${player.monthlyFee ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onRate) { Icon(Icons.Filled.Star, contentDescription = "Avaliar") }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remover") }
        }
    }
}

@Composable
private fun TypeBadge(text: String) {
    Box(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
