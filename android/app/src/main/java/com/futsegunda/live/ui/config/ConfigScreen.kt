package com.futsegunda.live.ui.config

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.futsegunda.live.network.UserDto
import com.futsegunda.live.ui.theme.AccentCard
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.ui.theme.GradientButton
import com.futsegunda.live.viewmodel.ConfigUiState
import com.futsegunda.live.viewmodel.ConfigViewModel

/**
 * Identidade Visual, Rotação de Louça e Usuários — fecha o "App Android
 * nativo completo" do plano (Fase 4, a última).
 */
@Composable
fun ConfigScreen(vm: ConfigViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Scaffold { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { IdentitySection(state = state, onPickLogo = vm::pickLogo, onRemoveLogo = vm::removeLogo, onSave = vm::saveIdentity) }
                item { LoucaSection(state = state, vm = vm) }
                if (state.isAdmin) {
                    item { UsersSection(state = state, vm = vm) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.dismissToast() }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
    state.error?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3500); vm.dismissError() }
        Snackbar(modifier = Modifier.padding(16.dp), containerColor = FutRed) { Text(msg) }
    }
}

@Composable
private fun IdentitySection(
    state: ConfigUiState,
    onPickLogo: (android.net.Uri) -> Unit,
    onRemoveLogo: () -> Unit,
    onSave: (teamName: String, tabTitle: String) -> Unit,
) {
    var teamName by remember(state.config.teamName) { mutableStateOf(state.config.teamName) }
    var tabTitle by remember(state.config.tabTitle) { mutableStateOf(state.config.tabTitle) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickLogo(uri)
    }

    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🎨 Identidade Visual", style = MaterialTheme.typography.titleSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.uploadingLogo -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        !state.config.logo.isNullOrBlank() -> AsyncImage(
                            model = state.config.logo, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape),
                        )
                        else -> Text("🖼️", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    Text("Toque para trocar a logo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!state.config.logo.isNullOrBlank()) {
                        Text("Remover logo", style = MaterialTheme.typography.bodySmall, color = FutRed, modifier = Modifier.clickable { onRemoveLogo() })
                    }
                }
            }

            OutlinedTextField(value = teamName, onValueChange = { teamName = it }, label = { Text("Nome da Pelada") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = tabTitle, onValueChange = { tabTitle = it }, label = { Text("Título da Aba do Navegador") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            GradientButton(onClick = { onSave(teamName, tabTitle) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) "Salvando…" else "💾 Salvar")
            }
        }
    }
}

@Composable
private fun LoucaSection(state: ConfigUiState, vm: ConfigViewModel) {
    val rot = state.loucaRotation
    val derived = state.washedMap()
    val washedCount = rot.count { state.isWashed(it, derived) }
    val allWashed = rot.isNotEmpty() && washedCount == rot.size
    var addMenuExpanded by remember { mutableStateOf(false) }
    val available = state.players.filter { it.id !in rot }.sortedBy { it.name }

    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("🧹 Rotação de Louça", style = MaterialTheme.typography.titleSmall)
                Text(
                    "↺ Reiniciar",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allWashed) FutGreenStart else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { vm.resetCycle() },
                )
            }

            if (rot.isEmpty()) {
                Text("Nenhum jogador na rotação. Adicione abaixo para começar a controlar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    if (allWashed) "✅ Todos lavaram — pronto para reiniciar" else "Ciclo: $washedCount de ${rot.size} lavaram",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allWashed) FutGreenStart else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rot.forEachIndexed { i, id ->
                    val player = state.players.find { it.id == id } ?: return@forEachIndexed
                    val washed = state.isWashed(id, derived)
                    val isNext = !washed && id == state.nextLoucaId
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            player.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        val (badgeText, badgeColor) = when {
                            washed -> "✅ lavou" to FutGreenStart
                            isNext -> "🔜 próxima vez" to MaterialTheme.colorScheme.tertiary
                            else -> "⬜ aguardando" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            badgeText, style = MaterialTheme.typography.bodySmall, color = badgeColor,
                            modifier = Modifier.clickable { vm.toggleWashed(id) }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        IconButton(onClick = { vm.moveRotationUp(i) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir") }
                        IconButton(onClick = { vm.moveRotationDown(i) }, enabled = i < rot.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer") }
                        IconButton(onClick = { vm.removeFromRotation(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Remover") }
                    }
                }
            }

            Box {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable(enabled = available.isNotEmpty()) { addMenuExpanded = true }
                        .padding(12.dp),
                ) {
                    Text(
                        if (available.isEmpty()) "Todos os jogadores já estão na rotação" else "+ Adicionar jogador à rotação…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                    available.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.addToRotation(p.id); addMenuExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersSection(state: ConfigUiState, vm: ConfigViewModel) {
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("viewer") }

    AccentCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("👤 Usuários do Sistema", style = MaterialTheme.typography.titleSmall)

            if (state.usersLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (state.users.isEmpty()) {
                Text("Nenhum usuário", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.users.forEach { u -> UserRow(u, isSelf = u.username == state.currentUsername, vm = vm) }
            }

            Text("Novo Usuário", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Senha (mín. 6 caracteres)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = newRole == "viewer", onClick = { newRole = "viewer" }, label = { Text("👁️ Viewer") })
                FilterChip(selected = newRole == "admin", onClick = { newRole = "admin" }, label = { Text("🔑 Admin") })
            }
            GradientButton(
                onClick = {
                    vm.createUser(newUsername.trim(), newPassword, newRole)
                    newUsername = ""; newPassword = ""
                },
                enabled = newUsername.isNotBlank() && newPassword.length >= 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Criar") }
        }
    }
}

@Composable
private fun UserRow(u: UserDto, isSelf: Boolean, vm: ConfigViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                u.username + if (isSelf) " (você)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FilterChip(
            selected = u.role == "admin",
            onClick = { if (!isSelf) vm.updateUserRole(u.id, if (u.role == "admin") "viewer" else "admin") },
            label = { Text(if (u.role == "admin") "🔑 Admin" else "👁️ Viewer") },
            enabled = !isSelf,
        )
        IconButton(onClick = { vm.deleteUser(u.id) }, enabled = !isSelf) {
            Icon(Icons.Filled.Delete, contentDescription = "Remover usuário")
        }
    }
}
