package com.futsegunda.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.MaterialTheme
import com.futsegunda.wear.data.TokenStore
import com.futsegunda.wear.ui.LiveScreen
import com.futsegunda.wear.ui.LoginScreen
import com.futsegunda.wear.work.SyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorker.schedule(applicationContext)

        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }

    if (loggedIn) {
        LiveScreen()
    } else {
        LoginScreen(onLoggedIn = { loggedIn = true })
    }
}
