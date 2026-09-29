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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.futsegunda.live.domain.calcOverall
import com.futsegunda.live.domain.overallLabel
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.ui.theme.OverallRing
import com.futsegunda.live.viewmodel.PLAYER_POSITIONS
import com.futsegunda.live.viewmodel.PlayersViewModel

/**
 * Espelha o card "Cadastrar Jogador" do painel web (frontend/index.html:483-529):
 * foto+vídeo lado a lado, Nome, Apelido+WhatsApp, Posição, sliders de
 * atributo com anel de overall ao vivo (tudo no mesmo formulário — o
 * painel usa essa combinação pro cadastro completo; um botão ⭐ separado
 * na lista abre uma avaliação rápida à parte, ver PlayerRatingSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerFormScreen(vm: PlayersViewModel) {
    val state by vm.state.collectAsState()
    val form = state.form ?: return

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickVideo(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Editar Jogador" else "Cadastrar Jogador") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeForm() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (form.isEditing) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        MediaPicker(
                            previewUrl = form.photoUrl,
                            uploading = form.uploadingPhoto,
                            fallbackIcon = Icons.Filled.Person,
                            label = "📷 Escolher foto",
                            hint = "Foto ou GIF animado",
                            onPick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRemove = { vm.removePhoto() },
                        )
                        MediaPicker(
                            previewUrl = form.video,
                            uploading = form.uploadingVideo,
                            fallbackIcon = Icons.Filled.Movie,
                            label = "🎥 Escolher vídeo",
                            hint = "mp4/webm/mov, até 8MB",
                            onPick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            onRemove = { vm.removeVideo() },
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Salve o jogador pra poder adicionar foto/vídeo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = form.name, onValueChange = { name -> vm.updateForm { it.copy(name = name) } },
                    label = { Text("Nome Completo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.apelido, onValueChange = { v -> vm.updateForm { it.copy(apelido = v) } },
                        label = { Text("Apelido (opcional)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.whatsapp, onValueChange = { v -> vm.updateForm { it.copy(whatsapp = v) } },
                        label = { Text("WhatsApp") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
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

            item { RatingSliderRow("💪 Físico", form.physical, FutGreenStart) { v -> vm.updateForm { it.copy(physical = v) } } }
            item { RatingSliderRow("🧠 Tático", form.tactical, FutBlueAccent) { v -> vm.updateForm { it.copy(tactical = v) } } }
            item { RatingSliderRow("⚡ Técnico", form.technical, FutAmber) { v -> vm.updateForm { it.copy(technical = v) } } }

            item {
                val overall = calcOverall(form.physical, form.tactical, form.technical)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    OverallRing(overall)
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text("OVERALL RATING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(overallLabel(overall), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Column {
                    Text("Tipo de Pagamento", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                        FilterChip(selected = form.isRegular, onClick = { vm.updateForm { it.copy(isRegular = true) } }, label = { Text("📆 Mensal") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = !form.isRegular, onClick = { vm.updateForm { it.copy(isRegular = false) } }, label = { Text("💵 Avulso") }, modifier = Modifier.weight(1f))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { vm.updateForm { it.copy(isIsento = !it.isIsento) } }
                            .padding(10.dp),
                    ) {
                        Checkbox(checked = form.isIsento, onCheckedChange = { v -> vm.updateForm { it.copy(isIsento = v) } })
                        Column {
                            Text("🆓 Isento de pagamento", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("(donos do campo, convidados especiais)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(
                        onClick = { vm.save() },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.saving) "Salvando…" else if (form.isEditing) "💾 Atualizar" else "📥 Cadastrar") }
                    if (form.isEditing) {
                        OutlinedButton(onClick = { vm.closeForm() }) { Text("✕ Cancelar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPicker(
    previewUrl: String?,
    uploading: Boolean,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uploading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                !previewUrl.isNullOrBlank() -> AsyncImage(
                    model = previewUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape),
                )
                else -> Icon(fallbackIcon, contentDescription = label, modifier = Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onPick))
            if (!previewUrl.isNullOrBlank()) {
                Text("✕ Remover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = onRemove))
            }
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RatingSliderRow(label: String, value: Int, color: androidx.compose.ui.graphics.Color, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 1f..99f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
        )
    }
}
