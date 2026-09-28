package com.futsegunda.wear.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

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
