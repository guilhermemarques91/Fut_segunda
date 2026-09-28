package com.futsegunda.live.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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

    /** GET sem `action` — o mesmo blob inteiro que o `loadFromServer()` do painel web lê. */
    @GET("api.php")
    suspend fun fullData(
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun playerSave(
        @Query("action") action: String = "player_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun playerDelete(
        @Query("action") action: String = "player_delete",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun playerRate(
        @Query("action") action: String = "player_rate",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @Multipart
    @POST("api.php")
    suspend fun uploadPlayerPhoto(
        @Query("action") action: String = "upload_player_photo",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Part file: MultipartBody.Part,
        @Part("playerId") playerId: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun deletePlayerPhoto(
        @Query("action") action: String = "delete_player_photo",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun feesSave(
        @Query("action") action: String = "fees_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    // ── Rodada (Fase 2) ──────────────────────────────────

    @POST("api.php")
    suspend fun attendanceSave(
        @Query("action") action: String = "attendance_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun avulsoOrderSave(
        @Query("action") action: String = "avulso_order_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun teamHistorySave(
        @Query("action") action: String = "team_history_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun dinnerSave(
        @Query("action") action: String = "dinner_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun lockedRodadasSave(
        @Query("action") action: String = "locked_rodadas_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun rodadaDelete(
        @Query("action") action: String = "rodada_delete",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    /** Inicia a partida (liveState inteiro) a partir dos times da Rodada — mesma action que o "Ao Vivo" já usa pro timer/placar. */
    @POST("api.php")
    suspend fun liveUpdate(
        @Query("action") action: String = "live_update",
        @Header("X-Api-Key") apiKey: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    // ── Financeiro (Fase 3) ──────────────────────────────

    @POST("api.php")
    suspend fun lancamentoSave(
        @Query("action") action: String = "lancamento_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun lancamentoDelete(
        @Query("action") action: String = "lancamento_delete",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun lancamentoBulkQuitar(
        @Query("action") action: String = "lancamento_bulk_quitar",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun expenseSave(
        @Query("action") action: String = "expense_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun expenseDelete(
        @Query("action") action: String = "expense_delete",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun recurringExpenseSave(
        @Query("action") action: String = "recurring_expense_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun recurringExpenseDelete(
        @Query("action") action: String = "recurring_expense_delete",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun chargeHistorySave(
        @Query("action") action: String = "charge_history_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun resultSave(
        @Query("action") action: String = "result_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    // ── Config (Fase 4) ──────────────────────────────────

    @POST("api.php")
    suspend fun configSave(
        @Query("action") action: String = "config_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @Multipart
    @POST("api.php")
    suspend fun uploadLogo(
        @Query("action") action: String = "upload_logo",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun loucaRotationSave(
        @Query("action") action: String = "louca_rotation_save",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    // ── Usuários — já granular no backend (list_users/create_user/
    // update_user_role/delete_user), só faltava a tela nativa (Fase 4).

    @GET("api.php")
    suspend fun listUsers(
        @Query("action") action: String = "list_users",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun createUser(
        @Query("action") action: String = "create_user",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun updateUserRole(
        @Query("action") action: String = "update_user_role",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>

    @POST("api.php")
    suspend fun deleteUser(
        @Query("action") action: String = "delete_user",
        @Header("X-Api-Key") apiKey: String,
        @Header("X-Auth-Token") authToken: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
