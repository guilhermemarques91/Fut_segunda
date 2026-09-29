package com.futsegunda.live.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Equivalente ao `.card` + `.card-title-bar` do painel web — um Card normal
 * com uma barrinha vertical em degradê na borda esquerda. Usado nos cards
 * de destaque (Dashboard, linha de jogador), não em todo Card da tela.
 *
 * IMPORTANTE: os `fillMaxHeight()` internos só se comportam bem quando este
 * card é filho de um `LazyColumn`/`LazyRow` (altura solta/infinita, o
 * fillMaxHeight vira no-op) — como filho direto de um `Column` de altura
 * limitada (ex.: tela cheia), ele estica pro tamanho do Column inteiro. Ao
 * usar fora de uma lista, envolva a chamada com `Modifier.height(IntrinsicSize.Min)`
 * no `Row`/`Column` pai (só quando o conteúdo é simples — Text/Row/Column;
 * evite em conteúdo com TextField/DropdownMenu, cuja medida intrínseca é
 * inconsistente).
 */
@Composable
fun AccentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = FutSurface)) {
        Row {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(Brush.verticalGradient(listOf(FutGreenStart, FutGreenEnd)), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)),
            )
            Box(modifier = Modifier.fillMaxHeight()) {
                content()
            }
        }
    }
}
