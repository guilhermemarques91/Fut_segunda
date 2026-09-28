package com.futsegunda.live.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Espelha as actions de api/api.php. Toda chamada leva o header X-Api-Key;
 * as que exigem login (add/undo gol, período) também levam X-Auth-Token.
 * Corpo das requisições/respostas é JSON cru (RequestBody/ResponseBody),
 * serializado/desserializado à mão via JsonCodec (ver network/JsonCodec.kt).
 */
interface ApiService {

    @GET("api.php")
    suspend fun public(
        @Query("action") action: String = "public",
        @Header("X-Api-Key") apiKey: String,
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun appVersion(
        @Query("action") action: String = "app_version",
        @Header("X-Api-Key") apiKey: String,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun login(
        @Query("action") action: String = "login",
        @Header("X-Api-Key") apiKey: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @GET("api.php")
    suspend fun validate(
        @Query("action") action: String = "validate",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun liveGoalAdd(
        @Query("action") action: String = "live_goal_add",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun liveGoalUndo(
        @Query("action") action: String = "live_goal_undo",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun livePeriod(
        @Query("action") action: String = "live_period",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
