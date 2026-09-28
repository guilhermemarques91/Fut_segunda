package com.futsegunda.live.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.futsegunda.live.ui.LiveMatchScreen
import com.futsegunda.live.ui.WebPanelScreen
import com.futsegunda.live.ui.dashboard.DashboardScreen
import com.futsegunda.live.ui.financeiro.FinanceiroScreen
import com.futsegunda.live.ui.historico.HistoricoScreen
import com.futsegunda.live.ui.players.PlayersScreen
import com.futsegunda.live.ui.rodada.RodadaScreen

@Composable
fun AppNavHost(navController: NavHostController, onLogout: () -> Unit) {
    NavHost(navController = navController, startDestination = AppDestination.LiveMatch.route) {
        composable(AppDestination.LiveMatch.route) {
            LiveMatchScreen(onLogout = onLogout)
        }
        composable(AppDestination.Rodada.route) {
            RodadaScreen(onMatchStarted = {
                navController.navigate(AppDestination.LiveMatch.route) {
                    launchSingleTop = true
                    popUpTo(AppDestination.LiveMatch.route)
                }
            })
        }
        composable(AppDestination.Dashboard.route) {
            DashboardScreen()
        }
        composable(AppDestination.Players.route) {
            PlayersScreen()
        }
        composable(AppDestination.Financeiro.route) {
            FinanceiroScreen()
        }
        composable(AppDestination.Historico.route) {
            HistoricoScreen()
        }
        composable(AppDestination.WebFallback.route) {
            WebPanelScreen()
        }
    }
}
