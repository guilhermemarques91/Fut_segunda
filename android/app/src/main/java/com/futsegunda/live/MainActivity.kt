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
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.network.AppReleaseInfo
import com.futsegunda.live.ui.LoginScreen
import com.futsegunda.live.ui.nav.AppDestination
import com.futsegunda.live.ui.nav.AppNavHost
import com.futsegunda.live.ui.nav.NAV_DESTINATIONS
import com.futsegunda.live.ui.nav.isRouteAllowedForRole
import com.futsegunda.live.ui.theme.FutSegundaTheme
import com.futsegunda.live.ui.theme.GradientButton
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
            FutSegundaTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }

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

    // Mesmo destino padrão do onLoginSuccess() do painel web (frontend/index.html):
    // viewer cai em histórico, o resto cai no dashboard.
    val startRoute = remember { if (tokenStore.role == "viewer") AppDestination.Historico.route else AppDestination.Dashboard.route }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startRoute

    fun logout() {
        tokenStore.clear()
        loggedIn = false
    }

    Scaffold(
        topBar = {
            androidx.compose.foundation.layout.Column {
                TopAppBar(
                    title = { Text("Fut Segunda") },
                    actions = {
                        IconButton(onClick = ::logout) {
                            Icon(Icons.Filled.Logout, contentDescription = "Sair")
                        }
                    },
                )
                updateInfo?.let { info ->
                    UpdateBanner(versionName = info.versionName, onDismiss = { updateInfo = null })
                }
            }
        },
        bottomBar = {
            // Barra única de 7 itens, igual ao `.mobile-bottom-nav` do painel web —
            // sem gaveta/menu lateral (o painel também não tem uma no mobile).
            NavigationBar {
                NAV_DESTINATIONS.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            // Mesma trava de showSection() pro role=viewer (frontend/index.html:1357).
                            if (!isRouteAllowedForRole(dest.route, tokenStore.role)) return@NavigationBarItem
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                                popUpTo(startRoute)
                            }
                        },
                        icon = { Text(dest.emoji, style = MaterialTheme.typography.titleMedium) },
                        label = {
                            // .mnav-btn do painel (frontend/index.html:264) é uma etiqueta minúscula
                            // (.52rem, maiúscula) numa linha só — replica aqui pra não quebrar em 2 linhas.
                            Text(
                                dest.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.1.sp),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            AppNavHost(navController = navController, startDestination = startRoute, onLogout = ::logout)
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
                GradientButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ANDROID_DOWNLOAD_URL)))
                }) { Text("Atualizar") }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
        }
    }
}
