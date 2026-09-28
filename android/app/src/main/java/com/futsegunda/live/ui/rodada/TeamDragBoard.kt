package com.futsegunda.live.ui.rodada

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.viewmodel.TeamZone
import kotlin.math.roundToInt

/**
 * Port do drag-and-drop por toque do painel web (_initTeamDrag,
 * frontend/index.html:4451+): segura o chip do jogador (long-press evita
 * conflito com o scroll da tela) e arrasta pra uma das 3 zonas — solta em
 * cima de uma zona pra mover o jogador pra lá.
 */
@Composable
fun TeamDragBoard(
    home: List<PlayerDto>,
    away: List<PlayerDto>,
    bench: List<PlayerDto>,
    editable: Boolean,
    onMove: (playerId: Int, zone: TeamZone) -> Unit,
) {
    var draggingPlayer by remember { mutableStateOf<PlayerDto?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var hoveredZone by remember { mutableStateOf<TeamZone?>(null) }
    val zoneRects = remember { mutableStateMapOf<TeamZone, Rect>() }

    fun updateHoveredZone(pos: Offset) {
        hoveredZone = zoneRects.entries.firstOrNull { it.value.contains(pos) }?.key
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            DragZoneColumn(
                zone = TeamZone.PRETO, label = "⬛🟡 Time Preto e Amarelo", players = home, editable = editable,
                highlighted = hoveredZone == TeamZone.PRETO, hiddenPlayerId = draggingPlayer?.id,
                onZonePositioned = { zoneRects[TeamZone.PRETO] = it },
                onDragStart = { player, pos -> draggingPlayer = player; dragPosition = pos; updateHoveredZone(pos) },
                onDrag = { delta -> dragPosition += delta; updateHoveredZone(dragPosition) },
                onDragEnd = {
                    hoveredZone?.let { z -> draggingPlayer?.let { p -> onMove(p.id, z) } }
                    draggingPlayer = null; hoveredZone = null
                },
                onDragCancel = { draggingPlayer = null; hoveredZone = null },
            )
            DragZoneColumn(
                zone = TeamZone.AZUL, label = "🔵 Time Azul", players = away, editable = editable,
                highlighted = hoveredZone == TeamZone.AZUL, hiddenPlayerId = draggingPlayer?.id,
                onZonePositioned = { zoneRects[TeamZone.AZUL] = it },
                onDragStart = { player, pos -> draggingPlayer = player; dragPosition = pos; updateHoveredZone(pos) },
                onDrag = { delta -> dragPosition += delta; updateHoveredZone(dragPosition) },
                onDragEnd = {
                    hoveredZone?.let { z -> draggingPlayer?.let { p -> onMove(p.id, z) } }
                    draggingPlayer = null; hoveredZone = null
                },
                onDragCancel = { draggingPlayer = null; hoveredZone = null },
            )
            DragZoneColumn(
                zone = TeamZone.BANCO, label = "🪑 Banco (confirmados fora dos times)", players = bench, editable = editable,
                highlighted = hoveredZone == TeamZone.BANCO, hiddenPlayerId = draggingPlayer?.id,
                onZonePositioned = { zoneRects[TeamZone.BANCO] = it },
                onDragStart = { player, pos -> draggingPlayer = player; dragPosition = pos; updateHoveredZone(pos) },
                onDrag = { delta -> dragPosition += delta; updateHoveredZone(dragPosition) },
                onDragEnd = {
                    hoveredZone?.let { z -> draggingPlayer?.let { p -> onMove(p.id, z) } }
                    draggingPlayer = null; hoveredZone = null
                },
                onDragCancel = { draggingPlayer = null; hoveredZone = null },
            )
        }

        // Chip "fantasma" seguindo o dedo, por cima de tudo.
        draggingPlayer?.let { p ->
            Box(
                modifier = Modifier
                    .zIndex(10f)
                    .offset { IntOffset(dragPosition.x.roundToInt() - 40, dragPosition.y.roundToInt() - 20) }
                    .wrapContentSize()
                    .alpha(0.9f),
            ) {
                AccentCard { Text(p.name, modifier = Modifier.padding(10.dp)) }
            }
        }
    }
}

@Composable
private fun DragZoneColumn(
    zone: TeamZone,
    label: String,
    players: List<PlayerDto>,
    editable: Boolean,
    highlighted: Boolean,
    hiddenPlayerId: Int?,
    onZonePositioned: (Rect) -> Unit,
    onDragStart: (PlayerDto, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .onGloballyPositioned { coords -> onZonePositioned(Rect(coords.positionInRoot(), coords.size.toSize())) },
    ) {
        Text(
            "$label (${players.size})",
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 220.dp)
                .padding(top = 4.dp),
        ) {
            items(players, key = { it.id }) { p ->
                if (p.id != hiddenPlayerId) {
                    PlayerChip(player = p, editable = editable, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd, onDragCancel = onDragCancel)
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(
    player: PlayerDto,
    editable: Boolean,
    onDragStart: (PlayerDto, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    AccentCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onGloballyPositioned { rootPosition = it.positionInRoot() }
            .then(
                if (editable) {
                    Modifier.pointerInput(player.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> onDragStart(player, rootPosition + local) },
                            onDrag = { change, amount -> change.consume(); onDrag(amount) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            "${player.name}${player.overall?.let { " ($it)" } ?: ""}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun androidx.compose.ui.unit.IntSize.toSize() = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
