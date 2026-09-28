package com.futsegunda.wear.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ServerConfig {
    const val BASE_URL = "https://fut.barleiseca.com.br/api/"
    const val API_KEY = "fut2-minha-chave-secreta"
}

object ApiClient {
    private val okHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    // Sem converter factory: o corpo (RequestBody/ResponseBody) é serializado/desserializado
    // manualmente via JsonCodec — o Retrofit lida com esses dois tipos nativamente.
    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.BASE_URL)
            .client(okHttp)
            .build()
            .create(ApiService::class.java)
    }
}
