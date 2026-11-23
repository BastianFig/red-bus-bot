package com.bastian.redbuswatch.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

const val BASE_URL = "https://red-bus-bot.onrender.com/"

interface BusApiService {
    @GET("api/buses/{paradero}")
    suspend fun getBuses(@Path("paradero") paradero: String): BusResponse

    @GET("api/nearby")
    suspend fun getNearby(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radio") radio: Int
    ): NearbyResponse
}

// El servidor gratuito se duerme tras un rato sin uso; despertarlo puede tardar casi un minuto.
// Con el tiempo de espera por defecto (10 s) la primera consulta fallaría.
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

object BusApi {
    val service: BusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BusApiService::class.java)
    }
}
