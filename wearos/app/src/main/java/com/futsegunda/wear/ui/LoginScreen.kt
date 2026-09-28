package com.futsegunda.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.app.RemoteInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.futsegunda.wear.viewmodel.LoginViewModel

/**
 * Tela pequena demais para um teclado normal — usa o seletor nativo do
 * Wear OS (voz ou teclado na tela) via RemoteInput, como recomendado pelo
 * próprio Google para telas de login em relógio.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: LoginViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (state.loggedIn) {
        LaunchedEffect(Unit) { onLoggedIn() }
        return
    }

    val usernameLauncher = rememberTextInputLauncher { username = it }
    val passwordLauncher = rememberTextInputLauncher { password = it }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⚽ Fut Segunda")
        Chip(
            onClick = { usernameLauncher.launch(buildTextInputIntent("Usuário")) },
            label = { Text(if (username.isBlank()) "Usuário" else username) },
            colors = ChipDefaults.secondaryChipColors(),
        )
        Chip(
            onClick = { passwordLauncher.launch(buildTextInputIntent("Senha")) },
            label = { Text(if (password.isBlank()) "Senha" else "•".repeat(password.length)) },
            colors = ChipDefaults.secondaryChipColors(),
        )

        if (state.loading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { vm.login(username, password) }) { Text("Entrar") }
        }

        state.error?.let { Text(it) }
    }
}

private const val RESULT_KEY = "fut_wear_text_result"

private fun buildTextInputIntent(label: String) = RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
    val remoteInput = RemoteInput.Builder(RESULT_KEY).setLabel(label).build()
    RemoteInputIntentHelper.putRemoteInputsExtra(this, listOf(remoteInput))
}

@Composable
private fun rememberTextInputLauncher(onResult: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(RESULT_KEY)?.toString()
        if (!text.isNullOrBlank()) onResult(text)
    }
