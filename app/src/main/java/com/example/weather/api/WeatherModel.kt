package com.example.weather.api

data class WeatherModel(
    val current: Current,
    val forecast: Forecast,
    val location: Location
)