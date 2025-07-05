package com.example.weather.api

sealed class NetworkResponseClass<out T> {
    data class Success<out T> (val data: T) : NetworkResponseClass<T>()
    data class Error(val message : String) : NetworkResponseClass<Nothing>()
    object Loading: NetworkResponseClass<Nothing>()
}