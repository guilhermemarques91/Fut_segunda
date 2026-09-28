package com.futsegunda.wear.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

/**
 * Serialização manual em vez de um Converter.Factory do Retrofit — mais
 * simples e sem depender de bibliotecas de terceiros pra isso (o Retrofit
 * já aceita RequestBody/ResponseBody nativamente, sem converter nenhum).
 */
object JsonCodec {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @PublishedApi
    internal val mediaType = "application/json; charset=utf-8".toMediaType()

    inline fun <reified T> body(value: T): RequestBody =
        json.encodeToString(value).toRequestBody(mediaType)

    inline fun <reified T> decode(body: ResponseBody?): T? =
        body?.use { it.string() }?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<T>(it) }
}
