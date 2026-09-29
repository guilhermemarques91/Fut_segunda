package com.futsegunda.live.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * O backend guarda `loucaOverrides` como array associativo PHP (playerId em
 * string → bool). PHP não distingue "mapa vazio" de "lista vazia" — um
 * array vazio sempre vira `[]` no `json_encode`, nunca `{}` — então esse
 * campo chega como `[]` sempre que ninguém marcou nenhuma louça manualmente
 * ainda (o estado inicial/mais comum da funcionalidade). Sem esse
 * serializer, `Map<String, Boolean>` puro quebra a decodificação do
 * snapshot INTEIRO (todo o app fica com dado velho, sem erro visível) —
 * bug real encontrado testando a Fase 8 com uma conta de verdade.
 */
object FlexibleStringBooleanMapSerializer : KSerializer<Map<String, Boolean>> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        PrimitiveSerialDescriptor("key", PrimitiveKind.STRING),
        PrimitiveSerialDescriptor("value", PrimitiveKind.BOOLEAN),
    )

    override fun deserialize(decoder: Decoder): Map<String, Boolean> {
        val input = decoder as? JsonDecoder ?: error("FlexibleStringBooleanMapSerializer só funciona com JSON")
        val element = input.decodeJsonElement()
        return if (element is JsonObject) {
            element.mapValues { (_, v) -> v.jsonPrimitive.boolean }
        } else {
            emptyMap()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Boolean>) {
        val output = encoder as? JsonEncoder ?: error("FlexibleStringBooleanMapSerializer só funciona com JSON")
        output.encodeJsonElement(JsonObject(value.mapValues { JsonPrimitive(it.value) }))
    }
}
