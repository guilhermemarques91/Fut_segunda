package com.futsegunda.live.ui.historico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.ResultDto

/** Edição manual de um resultado — equivalente ao form de edição do "Histórico" no painel web. */
@Composable
fun ResultEditDialog(
    result: ResultDto,
    players: List<PlayerDto>,
    onDismiss: () -> Unit,
    onSave: (homeScore: Int?, awayScore: Int?, motm: Int?, pending: Boolean?) -> Unit,
) {
    var homeText by remember { mutableStateOf(result.homeScore?.toString() ?: "") }
    var awayText by remember { mutableStateOf(result.awayScore?.toString() ?: "") }
    var motmId by remember { mutableStateOf(result.motm) }
    var pending by remember { mutableStateOf(result.pending) }
    var motmMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.date ?: "Resultado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = homeText, onValueChange = { homeText = it }, label = { Text(result.homeTeam ?: "Casa") }, singleLine = true)
                    OutlinedTextField(value = awayText, onValueChange = { awayText = it }, label = { Text(result.awayTeam ?: "Visitante") }, singleLine = true)
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = players.find { it.id == motmId }?.name ?: "Nenhum",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Melhor da partida (MVP)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.fillMaxSize().clickable { motmMenuExpanded = true })
                    DropdownMenu(expanded = motmMenuExpanded, onDismissRequest = { motmMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Nenhum") }, onClick = { motmId = null; motmMenuExpanded = false })
                        players.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { motmId = p.id; motmMenuExpanded = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pending, onCheckedChange = { pending = it })
                    Text("Pendente (sem placar ainda)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    if (pending) null else homeText.toIntOrNull(),
                    if (pending) null else awayText.toIntOrNull(),
                    motmId,
                    pending,
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
