package com.futsegunda.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.futsegunda.wear.network.LiveStateDto
import com.futsegunda.wear.network.PlayerDto
import com.futsegunda.wear.ui.theme.FutAmber
import com.futsegunda.wear.ui.theme.FutBlueAccent
import com.futsegunda.wear.ui.theme.FutGreenEnd
import com.futsegunda.wear.ui.theme.FutGreenStart
import com.futsegunda.wear.ui.theme.FutRed
import com.futsegunda.wear.update.UpdateChecker
import com.futsegunda.wear.viewmodel.LiveViewModel
import com.futsegunda.wear.viewmodel.TEAM_AWAY
import com.futsegunda.wear.viewmodel.TEAM_HOME
import kotlinx.coroutines.delay
import kotlin.math.max

private const val HOME_LABEL = "T. Preto"
private const val AWAY_LABEL = "T. Azul"

private val LiveGreen = Color(0xFF4ADE80)
private val IntervalAmber = Color(0xFFF59E0B)
private val EndedGray = Color(0xFF475569)
private val MutedText = Color(0xFF94A3B8)
private val ButtonFill = Color(0xFF1B2740)
private val ButtonBorder = Color(0x2EFFFFFF)

/** Fases da partida, derivadas do liveState (mesma lógica do app do celular e da tela web). */
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

/** Milissegundos que o relógio deve mostrar agora (no intervalo conta o próprio intervalo). */
private fun clockMillis(l: LiveStateDto?, stage: Stage, nowMs: Long): Long {
    if (l == null) return 0L
    return when (stage) {
        Stage.INT -> l.intervaloStart?.let { max(0L, nowMs - it) } ?: 0L
        Stage.END -> l.timerElapsed
        else -> {
            val started = l.timerStartedAt
            l.timerElapsed + if (l.timerRunning && started != null) max(0L, nowMs - started) else 0L
        }
    }
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    val mm = (s / 60).toString().padStart(2, '0')
    val ss = (s % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}

private sealed interface Screen {
    data object Main : Screen
    data object Tempo : Screen
    data object Undo : Screen
    data class Pick(val team: String) : Screen
}

/**
 * Tela Ao Vivo do relógio — modelo "Duelo Vertical": time de cima, time de baixo e uma faixa no
 * meio (pausar · cronômetro · reverter). Tocar num bloco abre a lista de quem marcou; tocar no
 * cronômetro abre a tela Tempo (botão da fase).
 */
@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var newVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        newVersion = try { UpdateChecker.newerVersionName() } catch (e: Exception) { null }
    }

    val live = state.live
    val stage = stageOf(live)

    // Recalcula o cronômetro a cada segundo enquanto estiver rodando (ou contando o intervalo).
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = live?.timerRunning == true || stage == Stage.INT
    LaunchedEffect(ticking, live?.timerStartedAt, live?.intervaloStart) {
        nowMs = System.currentTimeMillis()
        while (ticking) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.hasActiveMatch -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text("Nenhuma partida ativa", textAlign = TextAlign.Center, color = MutedText)
            }
            else -> {
                val playerName: (Int?) -> String = { id -> state.players.find { it.id == id }?.name ?: "?" }
                when (val s = screen) {
                    Screen.Main -> MainScreen(
                        stage = stage,
                        homeScore = vm.scoreFor(TEAM_HOME),
                        awayScore = vm.scoreFor(TEAM_AWAY),
                        clock = fmtMs(clockMillis(live, stage, nowMs)),
                        running = live?.timerRunning == true,
                        pending = state.pendingCount,
                        canUndo = !live?.goalLog.isNullOrEmpty(),
                        onGoal = { team -> screen = Screen.Pick(team) },
                        onPauseResume = { vm.periodEvent(if (live?.timerRunning == true) "pause" else "resume") },
                        onOpenTempo = { screen = Screen.Tempo },
                        onUndo = { screen = Screen.Undo },
                    )
                    Screen.Tempo -> TempoScreen(
                        stage = stage,
                        clock = fmtMs(clockMillis(live, stage, nowMs)),
                        running = live?.timerRunning == true,
                        newVersion = newVersion,
                        onStage = { event ->
                            vm.periodEvent(event)
                            screen = Screen.Main
                        },
                        onPauseResume = { vm.periodEvent(if (live?.timerRunning == true) "pause" else "resume") },
                        onBack = { screen = Screen.Main },
                    )
                    Screen.Undo -> {
                        val last = live?.goalLog?.lastOrNull()
                        UndoScreen(
                            who = last?.let { playerName(it.scorerId) + if (it.ownGoal) " (contra)" else "" },
                            assist = last?.assistId?.let { playerName(it) },
                            home = last?.team != TEAM_AWAY,
                            onNo = { screen = Screen.Main },
                            onYes = {
                                vm.undoLastGoal()
                                screen = Screen.Main
                            },
                        )
                    }
                    is Screen.Pick -> ScorerPickerScreen(
                        teamLabel = if (s.team == TEAM_HOME) HOME_LABEL else AWAY_LABEL,
                        accent = if (s.team == TEAM_HOME) FutAmber else FutBlueAccent,
                        players = vm.playersOf(s.team),
                        onPick = { scorerId ->
                            vm.addGoal(s.team, scorerId)
                            screen = Screen.Main
                        },
                        onCancel = { screen = Screen.Main },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    stage: Stage,
    homeScore: Int,
    awayScore: Int,
    clock: String,
    running: Boolean,
    pending: Int,
    canUndo: Boolean,
    onGoal: (String) -> Unit,
    onPauseResume: () -> Unit,
    onOpenTempo: () -> Unit,
    onUndo: () -> Unit,
) {
    val canPause = stage == Stage.T1 || stage == Stage.T2
    val clockColor = when {
        stage == Stage.INT -> IntervalAmber
        stage == Stage.END -> EndedGray
        running -> LiveGreen
        else -> MutedText
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TeamBlock(
            label = HOME_LABEL, score = homeScore, top = true, home = true,
            onClick = { onGoal(TEAM_HOME) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundButton(
                enabled = canPause,
                description = if (running) "Pausar cronômetro" else "Retomar cronômetro",
                onClick = onPauseResume,
            ) {
                if (running) PauseIcon(Color.White, 18.dp) else PlayIcon(Color.White, 18.dp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Abrir controle de tempo", onClick = onOpenTempo),
            ) {
                Text(
                    if (pending > 0) "$pending na fila" else stage.label.uppercase(),
                    color = if (pending > 0) FutAmber else MutedText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    clock,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = clockColor,
                        fontFeatureSettings = "tnum",
                    ),
                )
            }
            RoundButton(enabled = canUndo, description = "Reverter último lançamento", onClick = onUndo) {
                Text("↩", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        TeamBlock(
            label = AWAY_LABEL, score = awayScore, top = false, home = false,
            onClick = { onGoal(TEAM_AWAY) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** Bloco inteiro do time: tocar em qualquer ponto abre a lista de quem marcou. */
@Composable
private fun TeamBlock(
    label: String,
    score: Int,
    top: Boolean,
    home: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (home) FutAmber else FutBlueAccent
    val colors = if (home) listOf(Color(0xFF352A08), Color(0xFF14100A)) else listOf(Color(0xFF10295A), Color(0xFF0A1325))
    // O bloco de cima acompanha a curva do vidro nos cantos superiores; o de baixo, nos inferiores.
    val shape = if (top) {
        RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 100.dp, bottomEnd = 100.dp)
    }
    val brush = if (top) Brush.verticalGradient(colors) else Brush.verticalGradient(colors.reversed())
    val scoreText: @Composable () -> Unit = {
        Text(
            "$score",
            style = TextStyle(
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                fontFeatureSettings = "tnum",
            ),
        )
    }
    val nameText: @Composable () -> Unit = {
        Text(label.uppercase(), color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, accent.copy(alpha = 0.38f), shape)
            .clickable(role = Role.Button, onClickLabel = "Marcar gol do $label", onClick = onClick)
            .padding(top = if (top) 10.dp else 0.dp, bottom = if (top) 0.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // O nome fica perto do centro da tela, longe da curva do vidro.
        if (top) {
            scoreText(); nameText()
        } else {
            nameText(); scoreText()
        }
    }
}

@Composable
private fun RoundButton(enabled: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(ButtonFill)
            .border(1.dp, ButtonBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PlayIcon(color: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val p = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            lineTo(size.width * 0.86f, size.height * 0.5f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun PauseIcon(color: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width * 0.24f
        val r = CornerRadius(w * 0.3f)
        drawRoundRect(color, topLeft = Offset(size.width * 0.18f, size.height * 0.14f), size = Size(w, size.height * 0.72f), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(size.width * 0.58f, size.height * 0.14f), size = Size(w, size.height * 0.72f), cornerRadius = r)
    }
}

@Composable
private fun StopIcon(color: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            cornerRadius = CornerRadius(size.width * 0.1f),
        )
    }
}

/** Tela Tempo: botão grande da fase (iniciar/encerrar tempo), pausar e voltar. */
@Composable
private fun TempoScreen(
    stage: Stage,
    clock: String,
    running: Boolean,
    newVersion: String?,
    onStage: (String) -> Unit,
    onPauseResume: () -> Unit,
    onBack: () -> Unit,
) {
    val (label, event) = when (stage) {
        Stage.PRE -> "Iniciar 1º Tempo" to "start_t1"
        Stage.T1 -> "Encerrar 1º Tempo" to "end_t1"
        Stage.INT -> "Iniciar 2º Tempo" to "start_t2"
        Stage.T2 -> "Encerrar Jogo" to "end_t2"
        Stage.END -> "Partida encerrada" to null
    }
    val brush = when (stage) {
        Stage.PRE, Stage.INT -> Brush.linearGradient(listOf(FutGreenStart, FutGreenEnd))
        Stage.T1 -> Brush.linearGradient(listOf(IntervalAmber, IntervalAmber))
        Stage.T2 -> Brush.linearGradient(listOf(FutRed, FutRed))
        Stage.END -> Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
    }
    val content = when (stage) {
        Stage.T1 -> Color(0xFF1C1000)
        Stage.END -> MutedText
        else -> Color.White
    }
    val clockColor = when {
        stage == Stage.INT -> IntervalAmber
        stage == Stage.END -> EndedGray
        running -> LiveGreen
        else -> MutedText
    }
    val canPause = stage == Stage.T1 || stage == Stage.T2
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stage.label.uppercase(), color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(
            clock,
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = clockColor, fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(brush)
                .clickable(enabled = event != null, role = Role.Button, onClickLabel = label) { event?.let(onStage) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (stage) {
                    Stage.PRE, Stage.INT -> PlayIcon(content, 14.dp)
                    Stage.T1, Stage.T2 -> StopIcon(content, 14.dp)
                    Stage.END -> Unit
                }
                Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (stage == Stage.END) {
            Text("Salve o resultado pelo painel web", color = MutedText, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canPause) {
                SmallButton(if (running) "Pausar" else "Retomar", Modifier.weight(1f), onPauseResume)
            }
            SmallButton("Voltar", Modifier.weight(1f), onBack)
        }
        if (newVersion != null) {
            Spacer(Modifier.height(6.dp))
            Text("Nova versão $newVersion — atualize via ADB", color = FutAmber, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SmallButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ButtonFill)
            .border(1.dp, ButtonBorder, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Confirmação de reverter: mostra qual gol seria removido antes de desfazer. */
@Composable
private fun UndoScreen(who: String?, assist: String?, home: Boolean, onNo: () -> Unit, onYes: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (who == null) {
            Text("Nenhum gol para desfazer", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            ConfirmCircle("✕", ButtonFill, Color.White, "Voltar", onNo)
        } else {
            Text("Desfazer gol?", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "$who · ${if (home) HOME_LABEL else AWAY_LABEL}",
                color = if (home) FutAmber else FutBlueAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (assist != null) Text("assist. $assist", color = MutedText, fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConfirmCircle("✕", ButtonFill, Color.White, "Cancelar", onNo)
                ConfirmCircle("✓", FutRed, Color.White, "Desfazer gol", onYes)
            }
        }
    }
}

@Composable
private fun ConfirmCircle(glyph: String, fill: Color, textColor: Color, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, ButtonBorder, CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Tela cheia com a lista de jogadores do time pra escolher quem marcou.
 * "Não sei / sem artilheiro" manda o gol mesmo assim (scorerId nulo) — dá
 * pra completar depois pelo painel web ou pelo app Android.
 */
@Composable
private fun ScorerPickerScreen(
    teamLabel: String,
    accent: Color,
    players: List<PlayerDto>,
    onPick: (Int?) -> Unit,
    onCancel: () -> Unit,
) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text("Gol · $teamLabel", color = accent) } }
        items(players) { p: PlayerDto ->
            Chip(onClick = { onPick(p.id) }, label = { Text(p.name) })
        }
        item {
            Chip(
                onClick = { onPick(null) },
                label = { Text("Não sei quem marcou") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = onCancel,
                label = { Text("Cancelar") },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}
