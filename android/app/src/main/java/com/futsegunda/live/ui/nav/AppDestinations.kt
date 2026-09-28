package com.futsegunda.live.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Web
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rotas do NavHost. Todas as seções do painel web já têm tela nativa desde
 * a Fase 4 — `WebFallback` fica só pro que segue sendo web-only de propósito
 * (publicar `.apk` em Config/"Builds para Download": não faz sentido subir
 * um build a partir do próprio celular).
 */
sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : AppDestination("dashboard", "Dashboard", Icons.Filled.Home)
    data object LiveMatch : AppDestination("live", "Ao Vivo", Icons.Filled.SportsSoccer)
    data object Rodada : AppDestination("rodada", "Rodada", Icons.Filled.CalendarMonth)
    data object Players : AppDestination("players", "Jogadores", Icons.Filled.People)
    data object Financeiro : AppDestination("financeiro", "Financeiro", Icons.Filled.AttachMoney)
    data object Historico : AppDestination("historico", "Histórico", Icons.Filled.History)
    data object Config : AppDestination("config", "Configurações", Icons.Filled.Settings)
    data object WebFallback : AppDestination("web_fallback", "Painel completo (web)", Icons.Filled.Web)
}

/** Itens do menu lateral (drawer) — os dois de uso mais frequente (Ao Vivo/Rodada) já ficam na bottom bar. */
val DRAWER_DESTINATIONS = listOf(
    AppDestination.Dashboard,
    AppDestination.Players,
    AppDestination.Financeiro,
    AppDestination.Historico,
    AppDestination.Config,
    AppDestination.WebFallback,
)

val BOTTOM_BAR_DESTINATIONS = listOf(
    AppDestination.LiveMatch,
    AppDestination.Rodada,
)
