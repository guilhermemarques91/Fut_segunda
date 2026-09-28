package com.futsegunda.live.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cores extraídas direto do CSS do painel web (frontend/index.html:12-21) —
 * pra o app parecer o mesmo produto, não um app diferente com as mesmas
 * funções.
 */
val FutBackground = Color(0xFF060D1C) // --bg
val FutSurface = Color(0xFF0A162A) // aproximação sólida de --card (rgba(10,22,42,0.82))
val FutSurfaceVariant = Color(0xFF142440)
val FutBorder = Color(0x1FFFFFFF) // --border (rgba(255,255,255,0.07)-ish, um pouco mais visível em UI nativa)
val FutOnSurface = Color(0xFFE2E8F0) // color do body
val FutOnSurfaceMuted = Color(0xFF94A3B8)
val FutGreenStart = Color(0xFF22C55E) // ponta clara dos degradês (.btn-primary, .nav-link.active, .card-title-bar)
val FutGreenEnd = Color(0xFF15803D) // ponta escura dos degradês
val FutBlueAccent = Color(0xFF60A5FA) // badges/links secundários
val FutAmber = Color(0xFFFBBF24) // pendências/avisos
val FutRed = Color(0xFFEF4444) // .btn-danger / erros
val FutRedLight = Color(0xFFF87171)
