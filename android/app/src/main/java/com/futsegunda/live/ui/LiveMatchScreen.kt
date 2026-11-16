package com.futsegunda.live.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.futsegunda.live.network.LiveStateDto
import com.futsegunda.live.ui.theme.FutAmber
import com.futsegunda.live.ui.theme.FutBlueAccent
import com.futsegunda.live.ui.theme.FutGreenEnd
import com.futsegunda.live.ui.theme.FutGreenStart
import com.futsegunda.live.ui.theme.FutOnSurface
import com.futsegunda.live.ui.theme.FutOnSurfaceMuted
import com.futsegunda.live.ui.theme.FutRed
import com.futsegunda.live.ui.theme.FutSurfaceVariant
import com.futsegunda.live.viewmodel.LiveMatchViewModel
import com.futsegunda.live.viewmodel.TEAM_AWAY
import com.futsegunda.live.viewmodel.TEAM_HOME
import kotlinx.coroutines.delay
import kotlin.math.max

private const val HOME_LABEL = "T. Preto e Amarelo"
private const val AWAY_LABEL = "T. Azul"

private val LiveGreen = Color(0xFF4ADE80)
private val IntervalAmber = Color(0xFFF59E0B)
private val EndedGray = Color(0xFF475569)

/** Fases da partida, derivadas do liveState — mesma lógica da tela web (_pubStage). */
private enum class Stage(val label: String) {
    PRE("Antes do jogo"), T1("1º Tempo"), INT("Intervalo"), T2("2º Tempo"), END("Encerrado")
}

private fun stageOf(l: LiveStateDto?): Stage = when {
    l == null -> Stage.PRE
    l.t2ended -> Stage.END
    l.t1ms != null && l.periodo == 1 -> Stage.INT
    l.periodo == 2 -> Stage.T2
    !l.timerRunning && l.timerElapsed == 0L -> Stage.PRE
    else -> Stage.T1
}

/** Milissegundos que o relógio deve mostrar agora (intervalo conta o próprio intervalo). */
private fun clockMillis(l: LiveStateDto?, stage: Stage, now: Long): Long {
    if (l == null) return 0L
    return when (stage) {
        Stage.INT -> l.intervaloStart?.let { max(0L, now - it) } ?: 0L
        Stage.END -> l.timerElapsed
        else -> {
            val started = l.timerStartedAt
            l.timerElapsed + if (l.timerRunning && started != null) max(0L, now - started) else 0L
        }
    }
}

private fun fmtClock(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchScreen(onLogout: () -> Unit, vm: LiveMatchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var goalSheetTeam by remember { mutableStateOf<String?>(null) }
    var confirmUndo by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ao vivo — Fut Segunda") },
                actions = {
                    if (state.pendingCount > 0) {
                        Text("${state.pendingCount} pendente(s)", modifier = Modifier.padding(end = 12.dp))
                    }
                    IconButton(onClick = onLogout) { Text("Sair") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !state.hasActiveMatch -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚽", style = MaterialTheme.typography.displayMedium)
                        Text("Nenhuma partida ativa no momento")
                        Text("Inicie a partida pelo painel web (aba Rodada → Iniciar Partida)")
                    }
                }
                else -> {
                    val live = state.live
                    val stage = stageOf(live)
                    val wide = LocalConfiguration.current.screenWidthDp >= 600
                    val lastHome = live?.goalLog?.lastOrNull { it.team == TEAM_HOME }
                    val lastAway = live?.goalLog?.lastOrNull { it.team == TEAM_AWAY }

                    val homeBlock: @Composable (Modifier) -> Unit = { mod ->
                        TeamBlock(
                            label = HOME_LABEL,
                            score = vm.scoreFor(TEAM_HOME),
                            lastWho = lastHome?.let { vm.playerName(it.scorerId) + if (it.ownGoal) " (contra)" else "" },
                            lastAssist = lastHome?.assistId?.let { vm.playerName(it) },
                            home = true,
                            scoreSize = if (wide) 150.sp else 112.sp,
                            onClick = { goalSheetTeam = TEAM_HOME },
                            modifier = mod,
                        )
                    }
                    val awayBlock: @Composable (Modifier) -> Unit = { mod ->
                        TeamBlock(
                            label = AWAY_LABEL,
                            score = vm.scoreFor(TEAM_AWAY),
                            lastWho = lastAway?.let { vm.playerName(it.scorerId) + if (it.ownGoal) " (contra)" else "" },
                            lastAssist = lastAway?.assistId?.let { vm.playerName(it) },
                            home = false,
                            scoreSize = if (wide) 150.sp else 112.sp,
                            onClick = { goalSheetTeam = TEAM_AWAY },
                            modifier = mod,
                        )
                    }
                    val controls: @Composable () -> Unit = {
                        ControlBar(
                            stage = stage,
                            clock = fmtClock(clockMillis(live, stage, now)),
                            running = live?.timerRunning == true,
                            canUndo = !live?.goalLog.isNullOrEmpty(),
                            wide = wide,
                            onPauseResume = { vm.periodEvent(if (live?.timerRunning == true) "pause" else "resume") },
                            onUndo = { confirmUndo = true },
                        )
                    }

                    if (wide) {
                        // Tablet / paisagem: barra de controle no topo, blocos lado a lado com placar maior
                        controls()
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            homeBlock(Modifier.weight(1f).fillMaxSize())
                            awayBlock(Modifier.weight(1f).fillMaxSize())
                        }
                    } else {
                        homeBlock(Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        controls()
                        Spacer(Modifier.height(10.dp))
                        awayBlock(Modifier.weight(1f).fillMaxWidth())
                    }

                    Spacer(Modifier.height(10.dp))
                    StageButton(stage = stage, onEvent = vm::periodEvent)
                    if (stage == Stage.END) {
                        Text(
                            "Salve o resultado pelo painel web (Encerrar partida).",
                            style = MaterialTheme.typography.bodySmall,
                            color = FutOnSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    if (state.lastSyncError) {
                        Text(
                            "Sem conexão com o servidor — tentando de novo...",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    goalSheetTeam?.let { team ->
        GoalSheet(
            teamLabel = if (team == TEAM_HOME) HOME_LABEL else AWAY_LABEL,
            playersFor = { ownGoal -> vm.playersOf(team, ownGoal) },
            onConfirm = { scorerId, assistId, ownGoal, minute ->
                vm.addGoal(team, scorerId, assistId, ownGoal, minute)
                goalSheetTeam = null
            },
            onDismiss = { goalSheetTeam = null },
        )
    }

    if (confirmUndo) {
        val last = state.live?.goalLog?.lastOrNull()
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("Reverter último lançamento?") },
            text = {
                if (last == null) {
                    Text("Nenhum gol para desfazer.")
                } else {
                    val assist = last.assistId?.let { " (assistência: ${vm.playerName(it)})" } ?: ""
                    val teamTxt = if (last.team == TEAM_AWAY) AWAY_LABEL else HOME_LABEL
                    Text("Desfazer o gol${if (last.ownGoal) " (gol contra)" else ""} de ${vm.playerName(last.scorerId)}$assist do $teamTxt?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    vm.undoLastGoal()
                }) { Text("Desfazer gol") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancelar") } },
        )
    }

    state.toast?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            vm.dismissToast()
        }
        Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
    }
}

/** Bloco inteiro do time: tocar em qualquer ponto abre o popup de marcador/assistência. */
@Composable
private fun TeamBlock(
    label: String,
    score: Int,
    lastWho: String?,
    lastAssist: String?,
    home: Boolean,
    scoreSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (home) FutAmber else FutBlueAccent
    val brush = if (home) {
        Brush.linearGradient(listOf(Color(0xFF352A08), Color(0xFF15110A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF10295A), Color(0xFF0A1325)))
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, accent.copy(alpha = 0.34f), shape)
            .clickable(onClickLabel = "Marcar gol do $label", onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            label.uppercase(),
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.TopEnd).widthIn(max = 170.dp)) {
            if (lastWho == null) {
                Text("Sem gols", color = FutOnSurfaceMuted, fontSize = 12.sp, textAlign = TextAlign.End)
            } else {
                Text(lastWho, color = FutOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                if (lastAssist != null) {
                    Text("assist. $lastAssist", color = FutOnSurfaceMuted, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
        Text(
            "$score",
            style = TextStyle(
                fontSize = scoreSize,
                fontWeight = FontWeight.Black,
                color = accent,
                fontFeatureSettings = "tnum",
            ),
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(
                "TOQUE PARA MARCAR GOL",
                color = accent.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

/** Faixa de controle: pausar/retomar à esquerda, cronômetro no centro, reverter à direita. */
@Composable
private fun ControlBar(
    stage: Stage,
    clock: String,
    running: Boolean,
    canUndo: Boolean,
    wide: Boolean,
    onPauseResume: () -> Unit,
    onUndo: () -> Unit,
) {
    val canPause = stage == Stage.T1 || stage == Stage.T2
    val clockColor = when {
        stage == Stage.INT -> IntervalAmber
        stage == Stage.END -> EndedGray
        running -> LiveGreen
        else -> FutOnSurfaceMuted
    }
    val clockBlock: @Composable (Modifier) -> Unit = { mod ->
        Column(modifier = mod, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stage.label.uppercase(),
                color = FutOnSurfaceMuted,
                fontSize = if (wide) 11.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                clock,
                style = TextStyle(
                    fontSize = if (wide) 52.sp else 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = clockColor,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
    val buttonWidth = if (wide) 96.dp else 56.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (wide) Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
    ) {
        RoundButton(
            icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (running) "Pausar cronômetro" else "Retomar cronômetro",
            enabled = canPause,
            onClick = onPauseResume,
            width = buttonWidth,
        )
        clockBlock(if (wide) Modifier.widthIn(min = 200.dp) else Modifier)
        RoundButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            description = "Reverter último lançamento",
            enabled = canUndo,
            onClick = onUndo,
            width = buttonWidth,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, width: androidx.compose.ui.unit.Dp) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = FutSurfaceVariant,
        border = BorderStroke(1.dp, Color(0x29FFFFFF)),
        modifier = Modifier.size(width = width, height = 56.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) FutOnSurface else FutOnSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Botão do rodapé: muda a cada fase (iniciar 1º, encerrar 1º, iniciar 2º, encerrar jogo). */
@Composable
private fun StageButton(stage: Stage, onEvent: (String) -> Unit) {
    val (label, icon, event) = when (stage) {
        Stage.PRE -> Triple("Iniciar 1º Tempo", Icons.Filled.PlayArrow, "start_t1")
        Stage.T1 -> Triple("Encerrar 1º Tempo", Icons.Filled.Stop, "end_t1")
        Stage.INT -> Triple("Iniciar 2º Tempo", Icons.Filled.PlayArrow, "start_t2")
        Stage.T2 -> Triple("Encerrar Jogo", Icons.Filled.Stop, "end_t2")
        Stage.END -> Triple("Partida encerrada", Icons.Filled.Check, null)
    }
    val enabled = event != null
    val brush = when (stage) {
        Stage.PRE, Stage.INT -> Brush.linearGradient(listOf(FutGreenStart, FutGreenEnd))
        Stage.T1 -> Brush.linearGradient(listOf(IntervalAmber, IntervalAmber))
        Stage.T2 -> Brush.linearGradient(listOf(FutRed, FutRed))
        Stage.END -> Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
    }
    val content = if (stage == Stage.T1) Color(0xFF1C1000) else Color.White
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = { event?.let(onEvent) },
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().background(brush),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(label, color = content, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
