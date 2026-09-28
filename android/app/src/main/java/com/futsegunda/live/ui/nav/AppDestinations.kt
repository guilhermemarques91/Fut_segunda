package com.futsegunda.live.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Web
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rotas do NavHost. Cresce a cada fase — Histórico/Config ainda não têm
 * tela nativa (ver `WebFallback`, que abre o painel web de verdade dentro
 * de um WebView só pro que ainda não foi portado).
 */
sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : AppDestination("dashboard", "Dashboard", Icons.Filled.Home)
    data object LiveMatch : AppDestination("live", "Ao Vivo", Icons.Filled.SportsSoccer)
    data object Rodada : AppDestination("rodada", "Rodada", Icons.Filled.CalendarMonth)
    data object Players : AppDestination("players", "Jogadores", Icons.Filled.People)
    data object Financeiro : AppDestination("financeiro", "Financeiro", Icons.Filled.AttachMoney)
    data object WebFallback : AppDestination("web_fallback", "Painel completo (web)", Icons.Filled.Web)
}

/** Itens do menu lateral (drawer) — os dois de uso mais frequente (Ao Vivo/Rodada) já ficam na bottom bar. */
val DRAWER_DESTINATIONS = listOf(
    AppDestination.Dashboard,
    AppDestination.Players,
    AppDestination.Financeiro,
    AppDestination.WebFallback,
)

val BOTTOM_BAR_DESTINATIONS = listOf(
    AppDestination.LiveMatch,
    AppDestination.Rodada,
)
