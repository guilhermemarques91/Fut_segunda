package com.futsegunda.live.domain

import com.futsegunda.live.network.DinnerHistoryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica que o port de _loucaWashedMapInCycle/_loucaIsWashed/
 * getNextLoucaResponsavel (frontend/index.html:6683-6707) se comporta
 * igual ao original em JS, cenário a cenário — sem precisar de login real
 * nem banco, já que é lógica pura sobre listas/mapas.
 */
class LoucaRotationTest {

    private fun din(date: String, responsavel: Int?) = DinnerHistoryDto(date = date, loucaResponsavel = responsavel)

    @Test
    fun `rotacao vazia nao tem proximo responsavel`() {
        assertNull(LoucaRotation.nextResponsavel(emptyList(), emptyMap(), emptyMap()))
    }

    @Test
    fun `ninguem lavou ainda -- proximo e o primeiro da fila`() {
        val rotation = listOf(1, 2, 3)
        val derived = LoucaRotation.washedMap(emptyList(), cycleStart = null)
        assertTrue(derived.isEmpty())
        assertEquals(1, LoucaRotation.nextResponsavel(rotation, emptyMap(), derived))
    }

    @Test
    fun `registro de tira-gosto apos o inicio do ciclo marca o responsavel como lavado`() {
        val rotation = listOf(1, 2, 3)
        val history = listOf(din("2026-02-10", responsavel = 1))
        val derived = LoucaRotation.washedMap(history, cycleStart = "2026-02-01")
        assertTrue(LoucaRotation.isWashed(1, emptyMap(), derived))
        assertFalse(LoucaRotation.isWashed(2, emptyMap(), derived))
        // 1 já lavou -> próximo é 2, o primeiro ainda não lavado da fila.
        assertEquals(2, LoucaRotation.nextResponsavel(rotation, emptyMap(), derived))
    }

    @Test
    fun `registro anterior ao inicio do ciclo nao conta`() {
        val history = listOf(din("2026-01-15", responsavel = 1)) // antes do cycleStart
        val derived = LoucaRotation.washedMap(history, cycleStart = "2026-02-01")
        assertFalse(LoucaRotation.isWashed(1, emptyMap(), derived))
    }

    @Test
    fun `override manual marca como lavado mesmo sem registro automatico`() {
        val derived = LoucaRotation.washedMap(emptyList(), cycleStart = null)
        val overrides = mapOf("2" to true)
        assertTrue(LoucaRotation.isWashed(2, overrides, derived))
        assertFalse(LoucaRotation.isWashed(1, overrides, derived))
    }

    @Test
    fun `override manual tem prioridade sobre deteccao automatica -- inclusive pra desmarcar`() {
        val history = listOf(din("2026-02-10", responsavel = 1))
        val derived = LoucaRotation.washedMap(history, cycleStart = "2026-02-01")
        assertTrue(derived.containsKey(1)) // detecção automática diz que lavou
        val overrides = mapOf("1" to false) // mas o override diz que não
        assertFalse(LoucaRotation.isWashed(1, overrides, derived))
    }

    @Test
    fun `todos lavaram -- proximo volta pro primeiro da fila`() {
        val rotation = listOf(1, 2, 3)
        val overrides = mapOf("1" to true, "2" to true, "3" to true)
        val derived = LoucaRotation.washedMap(emptyList(), cycleStart = null)
        assertEquals(1, LoucaRotation.nextResponsavel(rotation, overrides, derived))
    }

    @Test
    fun `excludeDate ignora o registro da propria data (rodada atual)`() {
        // Mesmo cenário do seletor dentro da Rodada: ao montar a sugestão pra
        // ESTA rodada, o registro de tira-gosto desta mesma data (se já
        // existir, por causa de auto-save) não deve contar como "já lavou".
        val history = listOf(din("2026-02-10", responsavel = 1))
        val derived = LoucaRotation.washedMap(history, cycleStart = "2026-02-01", excludeDate = "2026-02-10")
        assertFalse(LoucaRotation.isWashed(1, emptyMap(), derived))
    }

    @Test
    fun `varios registros do mesmo responsavel -- so o mais antigo apos o ciclo conta`() {
        val history = listOf(
            din("2026-02-20", responsavel = 1),
            din("2026-02-05", responsavel = 1), // mais antigo, deve ser o escolhido
            din("2026-02-12", responsavel = 1),
        )
        val derived = LoucaRotation.washedMap(history, cycleStart = "2026-02-01")
        assertEquals("2026-02-05", derived[1]?.date)
    }

    @Test
    fun `reiniciar ciclo -- mover cycleStart pra frente invalida registros antigos`() {
        val history = listOf(din("2026-02-10", responsavel = 1))
        val beforeReset = LoucaRotation.washedMap(history, cycleStart = "2026-02-01")
        assertTrue(beforeReset.containsKey(1))

        val afterReset = LoucaRotation.washedMap(history, cycleStart = "2026-02-15") // ciclo reiniciado depois do registro
        assertFalse(afterReset.containsKey(1))
    }
}
