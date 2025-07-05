package com.example.weather.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://api.weatherapi.com/v1/"

    // Create OkHttpClient with extended timeout
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // Time to establish connection
        .readTimeout(30, TimeUnit.SECONDS)      // Time to read data
        .writeTimeout(30, TimeUnit.SECONDS)     // Time to write data
        .retryOnConnectionFailure(true)         // Retry on network failures
        .addInterceptor { chain ->
            var request = chain.request()
            var response = chain.proceed(request)

            // Retry once manually if request fails
            if (!response.isSuccessful) {
                response.close()
                response = chain.proceed(request)
            }

            response
        }
        .build()

    private fun getInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Set custom client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val weatherApi: WeatherApi = getInstance().create(WeatherApi::class.java)
}
