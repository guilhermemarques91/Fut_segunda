package com.futsegunda.live.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.futsegunda.live.network.PlayerDto

/**
 * Espelha o modal "Registrar Gol" do frontend/index.html (openGoalModal /
 * confirmGoalModal, linhas 5108-5197): escolhe artilheiro, assistência
 * opcional e o toggle "Gol Contra" (que troca a lista pro time adversário).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSheet(
    teamLabel: String,
    playersFor: (ownGoal: Boolean) -> List<PlayerDto>,
    onConfirm: (scorerId: Int?, assistId: Int?, ownGoal: Boolean, minute: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var ownGoal by remember { mutableStateOf(false) }
    var scorerId by remember { mutableStateOf<Int?>(null) }
    var assistId by remember { mutableStateOf<Int?>(null) }
    var minuteText by remember { mutableStateOf("") }

    val roster = playersFor(ownGoal)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Gol — $teamLabel", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = ownGoal, onCheckedChange = { ownGoal = it; scorerId = null; assistId = null })
                Text("Gol contra (jogador do time adversário)")
            }

            Spacer(Modifier.height(8.dp))
            Text(if (ownGoal) "Quem marcou contra:" else "Quem marcou:")
            LazyColumn(modifier = Modifier.height(160.dp)) {
                items(roster) { p: PlayerDto ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = scorerId == p.id, onClick = { scorerId = p.id })
                        Text(p.name)
                    }
                }
            }

            if (!ownGoal) {
                Spacer(Modifier.height(8.dp))
                Text("Assistência (opcional):")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = assistId == null, onClick = { assistId = null }, label = { Text("Sem assistência") })
                }
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(roster.filter { it.id != scorerId }) { p: PlayerDto ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(selected = assistId == p.id, onClick = { assistId = p.id })
                            Text(p.name)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = minuteText,
                onValueChange = { if (it.length <= 3) minuteText = it.filter(Char::isDigit) },
                label = { Text("Minuto (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onConfirm(scorerId, if (ownGoal) null else assistId, ownGoal, minuteText.toIntOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmar gol") }
        }
    }
}
