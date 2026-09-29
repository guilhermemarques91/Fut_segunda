package com.futsegunda.live.domain

import com.futsegunda.live.network.AttendanceDto
import com.futsegunda.live.network.ChargeHistoryDto
import com.futsegunda.live.network.DinnerHistoryDto
import com.futsegunda.live.network.ExpenseDto
import com.futsegunda.live.network.LancamentoDto
import com.futsegunda.live.network.PlayerDto
import com.futsegunda.live.network.RecurringExpenseDto

/** Como quitar 1 item — mesma forma do `it.settle` do painel (buildReceberItems()/buildPagarItems()). */
sealed class SettleAction {
    data class FeeCharge(val playerId: Int, val month: String) : SettleAction()
    data class Fee(val playerId: Int, val ptype: String) : SettleAction()
    data class Dinner(val playerId: Int, val dinnerHistoryId: Int) : SettleAction()
    data class Manual(val lancId: Int) : SettleAction()
    data class Expense(val expId: Int) : SettleAction()
    data class Recurring(val recId: Int) : SettleAction()
}

/** Entidade editável/removível por trás do item — mesma forma de `it.edit` do painel (editActionsHTML()). */
sealed class EditRef {
    data class Manual(val id: Int) : EditRef()
    data class Expense(val id: Int) : EditRef()
    data class Recurring(val id: Int) : EditRef()
}

/** 1 linha por cobrança (mensalidade/avulso/tira-gosto/lançamento manual) — mesma forma de `buildReceberItems()`. */
data class ReceberItem(
    val key: String,
    val status: String, // "pago" | "pendente"
    val badge: String, // "mensalidade" | "avulso" | "tiragosto" | "contribuicao"
    val icon: String,
    val name: String,
    val apelido: String,
    val desc: String,
    val date: String,
    val amount: Double,
    val paidDate: String? = null,
    val playerId: Int? = null,
    val settle: SettleAction? = null,
    val edit: EditRef? = null,
)

/** 1 linha por despesa/recorrente pendente — mesma forma de `buildPagarItems()`. */
data class PagarItem(
    val key: String,
    val status: String,
    val badge: String,
    val icon: String,
    val name: String,
    val apelido: String,
    val desc: String,
    val date: String,
    val amount: Double,
    val paidDate: String? = null,
    val settle: SettleAction? = null,
    val edit: EditRef? = null,
)

/**
 * Port 1:1 de `buildReceberItems()`/`buildPagarItems()` (frontend/index.html:3117-3207) —
 * a fonte única de "quem deve o quê" usada pelo Dashboard (card Pendências/Resumo
 * Financeiro) e pelo Financeiro (aba Lançamentos, Fase 10). Sem isso duplicado em
 * dois lugares, as "pendências fantasma" que o painel web evita não voltam a existir.
 */
object ReceberItems {
    private val MONTH_NAMES_PT = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val LANC_CAT_ICON = mapOf("Janta" to "🍖", "Campo" to "⚽", "Equipamento" to "👕", "Aluguel" to "🏠", "Outros" to "📌")

    fun isFeeExempt(p: PlayerDto, goleiroIsento: Boolean): Boolean =
        p.isIsento || (goleiroIsento && p.position == "Goleiro")

    private fun fmtBR(d: String?): String = d?.split("-")?.asReversed()?.joinToString("/") ?: "—"

    private fun monthLabelBR(m: String?): String {
        if (m.isNullOrBlank()) return ""
        val parts = m.split("-")
        val mm = parts.getOrNull(1)?.toIntOrNull() ?: return m
        return "${MONTH_NAMES_PT.getOrElse(mm - 1) { "" }}/${parts[0]}"
    }

    fun buildReceber(
        players: List<PlayerDto>,
        chargeHistory: List<ChargeHistoryDto>,
        attendances: List<AttendanceDto>,
        dinnerHistory: List<DinnerHistoryDto>,
        lancamentos: List<LancamentoDto>,
        goleiroIsento: Boolean,
    ): List<ReceberItem> {
        val items = mutableListOf<ReceberItem>()

        players.forEach { p ->
            if (!isFeeExempt(p, goleiroIsento)) {
                if (p.isRegular) {
                    // Mensalidades: cada cobrança tem seu próprio status (paid).
                    chargeHistory.forEach { ch ->
                        val c = ch.charges.find { it.playerId == p.id } ?: return@forEach
                        items += ReceberItem(
                            key = "feem-${p.id}-${ch.month}",
                            status = if (c.paid) "pago" else "pendente",
                            badge = "mensalidade", icon = "📆",
                            name = p.name, apelido = p.apelido.orEmpty(),
                            desc = "Mensalidade · ${monthLabelBR(ch.month)}",
                            date = ch.date ?: "${ch.month}-01",
                            amount = c.amount, paidDate = c.paidDate,
                            playerId = p.id,
                            settle = if (c.paid) null else SettleAction.FeeCharge(p.id, ch.month),
                        )
                    }
                } else {
                    // Avulso: 1 linha por jogo, alocação FIFO dos pagamentos já feitos.
                    val feePaid = p.payments.filter { it.type != "Tira Gosto" }.sumOf { it.amount }
                    val charges = attendances.filter { p.id in it.players }
                        .map { Triple(fmtBR(it.date), it.date, p.monthlyFee ?: 0.0) }
                        .sortedBy { it.second }
                    var rem = feePaid
                    charges.forEachIndexed { i, (sub, date, amount) ->
                        val pend: Double
                        if (rem >= amount) { pend = 0.0; rem -= amount } else { pend = amount - rem; rem = 0.0 }
                        items += if (pend > 0.001) {
                            ReceberItem(
                                "fee-${p.id}-$i", "pendente", "avulso", "💵", p.name, p.apelido.orEmpty(), "Jogo Avulso · $sub", date, pend,
                                playerId = p.id, settle = SettleAction.Fee(p.id, "Jogo Avulso"),
                            )
                        } else {
                            ReceberItem("feepg-${p.id}-$i", "pago", "avulso", "💵", p.name, p.apelido.orEmpty(), "Jogo Avulso · $sub", date, amount, playerId = p.id)
                        }
                    }
                }
            }
            // Tira gosto — 1 linha por evento (pendente ou recebido).
            dinnerHistory.forEachIndexed { idx, ev ->
                if (p.id in ev.participants && ev.share > 0) {
                    val pago = p.id in ev.paidBy
                    items += ReceberItem(
                        key = "${if (pago) "dinpg" else "din"}-${p.id}-$idx",
                        status = if (pago) "pago" else "pendente",
                        badge = "tiragosto", icon = "🍽️",
                        name = p.name, apelido = p.apelido.orEmpty(),
                        desc = "Tira Gosto · ${fmtBR(ev.date)}",
                        date = ev.date, amount = ev.share,
                        playerId = p.id,
                        settle = if (pago || ev.id == null) null else SettleAction.Dinner(p.id, ev.id),
                    )
                }
            }
        }

        // Lançamentos manuais (jogador cadastrado ou convidado).
        lancamentos.forEach { l ->
            val badge = when (l.type) { "Mensalidade" -> "mensalidade"; "Jogo Avulso" -> "avulso"; "Tira Gosto" -> "tiragosto"; else -> "contribuicao" }
            val icon = when (badge) { "mensalidade" -> "📆"; "avulso" -> "💵"; "tiragosto" -> "🍽️"; else -> "💚" }
            items += ReceberItem(
                key = "lan-${l.id}",
                status = if (l.paid) "pago" else "pendente",
                badge = badge, icon = icon,
                name = l.name, apelido = if (l.playerId == null) "convidado" else "",
                desc = "${l.type} · ${fmtBR(l.date)}" + (l.notes?.let { " · $it" } ?: ""),
                date = l.date, amount = l.amount, paidDate = l.paidDate,
                playerId = l.playerId,
                settle = if (l.paid || l.id == null) null else SettleAction.Manual(l.id),
                edit = if (l.paid) null else l.id?.let { EditRef.Manual(it) },
            )
        }
        return items
    }

    fun buildPagar(expenses: List<ExpenseDto>, recurringExpenses: List<RecurringExpenseDto>, curMonth: String): List<PagarItem> {
        val items = mutableListOf<PagarItem>()
        expenses.forEach { e ->
            items += PagarItem(
                key = "exp-${e.id}", status = if (e.paid) "pago" else "pendente", badge = "despesa",
                icon = LANC_CAT_ICON[e.category] ?: "📌",
                name = e.description, apelido = "",
                desc = "${e.category.orEmpty()} · ${fmtBR(e.date)}" + (e.notes?.let { " · $it" } ?: ""),
                date = e.date, amount = e.amount, paidDate = e.paidDate,
                settle = if (e.paid || e.id == null) null else SettleAction.Expense(e.id),
                edit = e.id?.let { EditRef.Expense(it) },
            )
        }
        recurringExpenses.filter { it.active && it.lastPaidMonth != curMonth }.forEach { r ->
            items += PagarItem(
                key = "rec-${r.id}", status = "pendente", badge = "despesa",
                icon = LANC_CAT_ICON[r.category] ?: "📌",
                name = r.description, apelido = "recorrente",
                desc = "${r.category.orEmpty()} · 🔁 Recorrente · ${monthLabelBR(curMonth)}",
                date = "$curMonth-01", amount = r.amount,
                settle = r.id?.let { SettleAction.Recurring(it) },
                edit = r.id?.let { EditRef.Recurring(it) },
            )
        }
        return items
    }
}
