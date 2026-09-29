package com.futsegunda.live.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão do bug encontrado testando a Fase 8 com uma conta real: PHP não
 * distingue "mapa vazio" de "lista vazia" — `json_encode([])` sempre vira
 * `[]`, nunca `{}` — então `loucaOverrides` chega como `[]` sempre que
 * ninguém marcou uma louça manualmente ainda (o estado inicial da
 * funcionalidade). Sem o serializer flexível, isso quebrava a decodificação
 * do AppSnapshotDto INTEIRO — todo o app ficava com dado velho, sem erro
 * visível nenhum pro usuário.
 */
class FlexibleStringBooleanMapSerializerTest {

    @Serializable
    private data class Wrapper(
        @Serializable(with = FlexibleStringBooleanMapSerializer::class)
        val overrides: Map<String, Boolean> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `array vazio do PHP vira mapa vazio`() {
        val decoded = json.decodeFromString<Wrapper>("""{"overrides":[]}""")
        assertTrue(decoded.overrides.isEmpty())
    }

    @Test
    fun `objeto JSON normal continua decodificando certo`() {
        val decoded = json.decodeFromString<Wrapper>("""{"overrides":{"5":true,"12":false}}""")
        assertEquals(mapOf("5" to true, "12" to false), decoded.overrides)
    }

    @Test
    fun `campo ausente usa o default (mapa vazio)`() {
        val decoded = json.decodeFromString<Wrapper>("""{}""")
        assertTrue(decoded.overrides.isEmpty())
    }

    @Test
    fun `serializa sempre como objeto, nunca como array, mesmo vazio`() {
        val encodeDefaultsJson = Json { encodeDefaults = true }
        val encoded = encodeDefaultsJson.encodeToString(Wrapper(overrides = emptyMap()))
        assertEquals("""{"overrides":{}}""", encoded)
    }

    @Test
    fun `round-trip preserva os valores`() {
        val original = Wrapper(overrides = mapOf("1" to true, "2" to false))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Wrapper>(encoded)
        assertEquals(original, decoded)
    }
}
