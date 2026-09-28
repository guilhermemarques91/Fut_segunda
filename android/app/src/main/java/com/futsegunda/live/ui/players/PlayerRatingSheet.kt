package com.futsegunda.live.ui.players

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.futsegunda.live.domain.calcOverall
import com.futsegunda.live.network.PlayerDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerRatingSheet(player: PlayerDto, onDismiss: () -> Unit, onConfirm: (physical: Int, tactical: Int, technical: Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var physical by remember { mutableFloatStateOf(player.attributes.physical.toFloat()) }
    var tactical by remember { mutableFloatStateOf(player.attributes.tactical.toFloat()) }
    var technical by remember { mutableFloatStateOf(player.attributes.technical.toFloat()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Avaliar — ${player.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            val overall = calcOverall(physical.toInt(), tactical.toInt(), technical.toInt())
            Text("Overall: $overall", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(12.dp))
            RatingSlider("💪 Físico", physical) { physical = it }
            RatingSlider("🧠 Tático", tactical) { tactical = it }
            RatingSlider("⚡ Técnico", technical) { technical = it }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(physical.toInt(), tactical.toInt(), technical.toInt()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar avaliação") }
        }
    }
}

@Composable
private fun RatingSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value.toInt().toString())
        }
        Slider(value = value, onValueChange = onChange, valueRange = 1f..99f)
    }
}
