package com.bastian.redbuswatch.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val BASE_URL = "https://red-bus-bot.onrender.com/"

interface BusApiService {
    @GET("api/buses/{paradero}")
    suspend fun getBuses(@Path("paradero") paradero: String): BusResponse

    @GET("api/nearby")
    suspend fun getNearby(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): NearbyResponse
}

object BusApi {
    val service: BusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BusApiService::class.java)
    }
}
