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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.futsegunda.live.data.TokenStore
import com.futsegunda.live.network.AppReleaseInfo
import com.futsegunda.live.ui.LoginScreen
import com.futsegunda.live.ui.nav.AppDestination
import com.futsegunda.live.ui.nav.AppNavHost
import com.futsegunda.live.ui.nav.BOTTOM_BAR_DESTINATIONS
import com.futsegunda.live.ui.nav.DRAWER_DESTINATIONS
import com.futsegunda.live.update.ANDROID_DOWNLOAD_URL
import com.futsegunda.live.update.UpdateCheckWorker
import com.futsegunda.live.update.UpdateChecker
import com.futsegunda.live.work.SyncWorker
import kotlinx.coroutines.launch

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

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.LiveMatch.route

    fun logout() {
        tokenStore.clear()
        loggedIn = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Fut Segunda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                DRAWER_DESTINATIONS.forEach { dest ->
                    NavigationDrawerItem(
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                                popUpTo(AppDestination.LiveMatch.route)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Logout, contentDescription = null) },
                    label = { Text("Sair") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; logout() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                androidx.compose.foundation.layout.Column {
                    TopAppBar(
                        title = { Text("Fut Segunda") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                    )
                    updateInfo?.let { info ->
                        UpdateBanner(versionName = info.versionName, onDismiss = { updateInfo = null })
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    BOTTOM_BAR_DESTINATIONS.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    launchSingleTop = true
                                    popUpTo(AppDestination.LiveMatch.route)
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                AppNavHost(navController = navController, onLogout = ::logout)
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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar")
                }
            }
        }
    }
}
