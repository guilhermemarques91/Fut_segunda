package com.futsegunda.live.ui.players

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.futsegunda.live.domain.calcOverall
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.viewmodel.PLAYER_POSITIONS
import com.futsegunda.live.viewmodel.PlayersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerFormScreen(vm: PlayersViewModel) {
    val state by vm.state.collectAsState()
    val form = state.form ?: return

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Editar jogador" else "Novo jogador") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeForm() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (form.isEditing) {
                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                form.uploadingPhoto -> CircularProgressIndicator()
                                !form.photoUrl.isNullOrBlank() -> AsyncImage(
                                    model = form.photoUrl, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(CircleShape),
                                )
                                else -> Icon(Icons.Filled.Person, contentDescription = "Escolher foto", modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }
                if (!form.photoUrl.isNullOrBlank()) {
                    item {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { vm.removePhoto() }) { Text("Remover foto") }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Salve o jogador pra poder adicionar uma foto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = form.name, onValueChange = { name -> vm.updateForm { it.copy(name = name) } },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = form.apelido, onValueChange = { v -> vm.updateForm { it.copy(apelido = v) } },
                    label = { Text("Apelido (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = form.whatsapp, onValueChange = { v -> vm.updateForm { it.copy(whatsapp = v) } },
                    label = { Text("WhatsApp") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text("Posição", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PLAYER_POSITIONS) { pos ->
                        FilterChip(
                            selected = form.position == pos,
                            onClick = { vm.updateForm { it.copy(position = pos) } },
                            label = { Text(pos) },
                        )
                    }
                }
            }

            item {
                val overall = calcOverall(form.physical, form.tactical, form.technical)
                Text("Atributos — Overall $overall", style = MaterialTheme.typography.titleSmall)
            }
            item { RatingSliderRow("💪 Físico", form.physical) { v -> vm.updateForm { it.copy(physical = v) } } }
            item { RatingSliderRow("🧠 Tático", form.tactical) { v -> vm.updateForm { it.copy(tactical = v) } } }
            item { RatingSliderRow("⚡ Técnico", form.technical) { v -> vm.updateForm { it.copy(technical = v) } } }

            item {
                Column {
                    Text("Tipo de pagamento", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = form.isRegular, onClick = { vm.updateForm { it.copy(isRegular = true) } })
                        Text("Mensal")
                        Spacer(Modifier.height(0.dp))
                        RadioButton(selected = !form.isRegular, onClick = { vm.updateForm { it.copy(isRegular = false) } })
                        Text("Avulso")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = form.isIsento, onCheckedChange = { v -> vm.updateForm { it.copy(isIsento = v) } })
                        Text("Isento (não cobra)")
                    }
                }
            }

            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item {
                GradientButton(
                    onClick = { vm.save() },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "Salvando…" else "Salvar") }
            }
        }
    }
}

@Composable
private fun RatingSliderRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value.toString())
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 1f..99f)
    }
}
