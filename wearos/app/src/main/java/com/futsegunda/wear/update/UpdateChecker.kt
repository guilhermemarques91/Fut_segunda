package com.futsegunda.wear.update

import com.futsegunda.wear.BuildConfig
import com.futsegunda.wear.network.AppReleasesResponse
import com.futsegunda.wear.network.ApiClient
import com.futsegunda.wear.network.JsonCodec
import com.futsegunda.wear.network.ServerConfig

/**
 * Compara a versão instalada com a que o painel web publicou. Diferente do
 * app Android, aqui é só um aviso — instalar no relógio ainda exige `adb
 * install` (Wear OS não deixa "tocar e instalar" um apk baixado), então não
 * tem uma ação de "baixar" pra oferecer, só o alerta de que existe versão
 * nova (o admin atualiza depois pelo ADB, ver wearos/README.md).
 */
object UpdateChecker {
    suspend fun newerVersionName(): String? {
        val resp = ApiClient.service.appVersion(apiKey = ServerConfig.API_KEY)
        if (!resp.isSuccessful) return null
        val releases = JsonCodec.decode<AppReleasesResponse>(resp.body()) ?: return null
        val latest = releases.wear ?: return null
        return if (latest.versionCode > BuildConfig.VERSION_CODE) latest.versionName else null
    }
}
