package com.futsegunda.live.update

import com.futsegunda.live.BuildConfig
import com.futsegunda.live.network.AppReleaseInfo
import com.futsegunda.live.network.AppReleasesResponse
import com.futsegunda.live.network.ApiClient
import com.futsegunda.live.network.JsonCodec
import com.futsegunda.live.network.ServerConfig

/** Link direto pra baixar a build mais recente — funciona num tap no navegador, sem login. */
const val ANDROID_DOWNLOAD_URL = "${ServerConfig.BASE_URL}download_release.php?platform=android"

/**
 * Compara a versão instalada (BuildConfig, gerado a partir de `versionCode`/
 * `versionName` do build.gradle.kts) com a que o painel web publicou
 * (`action=app_version`, alimentado pelo card "Apps — Builds para Download").
 */
object UpdateChecker {
    suspend fun checkForUpdate(): AppReleaseInfo? {
        val resp = ApiClient.service.appVersion(apiKey = ServerConfig.API_KEY)
        if (!resp.isSuccessful) return null
        val releases = JsonCodec.decode<AppReleasesResponse>(resp.body()) ?: return null
        val latest = releases.android ?: return null
        return if (latest.versionCode > BuildConfig.VERSION_CODE) latest else null
    }
}
