package com.futsegunda.live.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.futsegunda.live.data.TokenStore

private const val PANEL_URL = "https://fut.barleiseca.com.br/"

/**
 * Painel completo (Dashboard, Jogadores, Finanças, Rodada/Times, Tira Gosto,
 * Presença, etc.) — em vez de reescrever tudo isso em Kotlin, carrega o
 * próprio frontend/index.html dentro de um WebView e injeta o mesmo token
 * de sessão (fut_token/fut_role/fut_username) que o app guardou no login,
 * pra abrir já logado. A tela "Ao Vivo" nativa continua sendo o jeito
 * recomendado de marcar gol (funciona offline); aqui é só pra tudo o resto.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPanelScreen() {
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    var loading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    var injected = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            val token = tokenStore.token
                            if (!injected && !token.isNullOrBlank()) {
                                injected = true
                                val role = tokenStore.role.orEmpty()
                                val username = tokenStore.username.orEmpty()
                                // Injeta a mesma sessão do app nativo e recarrega uma vez,
                                // pra checkStoredSession() (frontend/index.html:7350) já
                                // encontrar o token pronto na primeira leitura do localStorage.
                                view.evaluateJavascript(
                                    """
                                    localStorage.setItem('fut_token', ${jsString(token)});
                                    localStorage.setItem('fut_role', ${jsString(role)});
                                    localStorage.setItem('fut_username', ${jsString(username)});
                                    """.trimIndent(),
                                ) { view.reload() }
                            }
                        }
                    }
                    loadUrl(PANEL_URL)
                }
            },
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/** Serializa uma string Kotlin como literal JS seguro (usado só pra 3 valores curtos/controlados). */
private fun jsString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    return "'$escaped'"
}
