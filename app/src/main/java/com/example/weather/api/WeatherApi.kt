package com.example.weather.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("/v1/forecast.json")
    suspend fun getWeather(
        @Query("key") apikey : String,
        @Query("q") location : String,
        @Query("days") forecastDays: String
    ) : Response<WeatherModel>

}