package com.futsegunda.live.ui.nav

/**
 * Rotas do NavHost. Espelha 1:1 o `.mobile-bottom-nav` do painel web
 * (frontend/index.html:7778-7804) — mesma ordem, mesmo emoji, mesmo rótulo.
 * O painel não tem gaveta/menu lateral no mobile (`.nav-links{display:none}`
 * abaixo de 768px) — essa barra única de 7 itens é a navegação inteira.
 */
sealed class AppDestination(val route: String, val label: String, val emoji: String) {
    data object Dashboard : AppDestination("dashboard", "Início", "🏠")
    data object Rodada : AppDestination("rodada", "Rodada", "🗓️")
    data object LiveMatch : AppDestination("live", "Ao Vivo", "▶")
    data object Historico : AppDestination("historico", "Histórico", "📋")
    data object Players : AppDestination("players", "Jogadores", "👥")
    data object Financeiro : AppDestination("financeiro", "Financeiro", "💰")
    data object Config : AppDestination("config", "Config", "⚙️")
}

/** Ordem exata do `.mobile-bottom-nav` do painel web. */
val NAV_DESTINATIONS = listOf(
    AppDestination.Dashboard,
    AppDestination.Rodada,
    AppDestination.LiveMatch,
    AppDestination.Historico,
    AppDestination.Players,
    AppDestination.Financeiro,
    AppDestination.Config,
)

/** Seções que o painel bloqueia pra role=viewer (frontend/index.html:1357 — `showSection` retorna sem trocar de seção). */
private val VIEWER_ALLOWED_ROUTES = setOf(AppDestination.Historico.route, AppDestination.LiveMatch.route)

fun isRouteAllowedForRole(route: String, role: String?): Boolean =
    role != "viewer" || route in VIEWER_ALLOWED_ROUTES
