package com.futsegunda.live

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.network.AppReleaseInfo
import com.futsegunda.live.ui.LiveMatchScreen
import com.futsegunda.live.ui.LoginScreen
import com.futsegunda.live.ui.WebPanelScreen
import com.futsegunda.live.update.ANDROID_DOWNLOAD_URL
import com.futsegunda.live.update.UpdateCheckWorker
import com.futsegunda.live.update.UpdateChecker
import com.futsegunda.live.work.SyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorker.schedule(applicationContext)
        UpdateCheckWorker.schedule(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Tab { AO_VIVO, PAINEL }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }
    var tab by remember { mutableStateOf(Tab.AO_VIVO) }

    // Android 13+ exige essa permissão em runtime pra qualquer notificação aparecer
    // (usada pelo aviso de atualização disponível, tanto o do banner quanto o do
    // UpdateCheckWorker rodando em segundo plano).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* concedida ou não — segue o fluxo normalmente, só não notifica se negada */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!loggedIn) {
        LoginScreen(onLoggedIn = { loggedIn = true })
        return
    }

    var updateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    LaunchedEffect(Unit) {
        updateInfo = try { UpdateChecker.checkForUpdate() } catch (e: Exception) { null }
    }

    Scaffold(
        topBar = {
            updateInfo?.let { info ->
                UpdateBanner(versionName = info.versionName, onDismiss = { updateInfo = null })
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.AO_VIVO,
                    onClick = { tab = Tab.AO_VIVO },
                    icon = { Icon(Icons.Filled.SportsSoccer, contentDescription = null) },
                    label = { Text("Ao vivo") },
                )
                NavigationBarItem(
                    selected = tab == Tab.PAINEL,
                    onClick = { tab = Tab.PAINEL },
                    icon = { Icon(Icons.Filled.Web, contentDescription = null) },
                    label = { Text("Painel completo") },
                )
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (tab) {
                Tab.AO_VIVO -> LiveMatchScreen(onLogout = {
                    tokenStore.clear()
                    loggedIn = false
                })
                Tab.PAINEL -> WebPanelScreen()
            }
        }
    }
}

@Composable
private fun UpdateBanner(versionName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                Text(
                    "Nova versão $versionName disponível",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ANDROID_DOWNLOAD_URL)))
                }) { Text("Atualizar") }
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
        }
    }
}
