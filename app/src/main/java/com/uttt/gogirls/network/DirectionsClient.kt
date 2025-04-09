package com.uttt.gogirls.network

import com.google.android.gms.maps.model.LatLng
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
// AIzaSyCkvRKi7dmA5h6vpELxJT9UuqWiyXVV_r0
object DirectionsClient {
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/"
    private const val API_KEY = "AIzaSyCkvRKi7dmA5h6vpELxJT9UuqWiyXVV_r0" // 👈 reemplaza por tu key real

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(DirectionsApiService::class.java)

    suspend fun getRoute(origin: LatLng, destination: LatLng): DirectionsResponse {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        return api.getDirections(originStr, destStr, API_KEY)
    }
}