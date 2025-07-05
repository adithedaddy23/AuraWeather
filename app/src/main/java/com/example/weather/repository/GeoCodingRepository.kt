package com.example.weather.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CityPrediction(
    val displayName: String,
    val country: String,
    val state: String,
    val lat: Double,
    val lon: Double
)

// Updated data classes to handle optional fields better
data class NominatimResponse(
    val display_name: String,
    val lat: String,
    val lon: String,
    val type: String,
    val class_type: String? = null,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val city: String?,
    val town: String?,
    val village: String?,
    val municipality: String?,
    val state: String?,
    val region: String?,
    val country: String?
)

class GeocodingRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Cache to avoid repeated requests
    private val cache = mutableMapOf<String, List<CityPrediction>>()
    private var lastRequestTime = 0L
    private val MIN_REQUEST_INTERVAL = 1000L // 1 second between requests

    suspend fun getCityPredictions(query: String): List<CityPrediction> {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cacheKey = query.lowercase().trim()
                if (cache.containsKey(cacheKey)) {
                    return@withContext cache[cacheKey] ?: emptyList()
                }

                // Rate limiting - ensure minimum interval between requests
                val currentTime = System.currentTimeMillis()
                val timeSinceLastRequest = currentTime - lastRequestTime
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    val delayTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest
//                    Log.d("GeocodingRepository", "Rate limiting: waiting ${delayTime}ms")
                    delay(delayTime)
                }
                lastRequestTime = System.currentTimeMillis()

//                Log.d("GeocodingRepository", "Searching for: $query")

                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                // Updated URL with better parameters for city search
                val url = "https://nominatim.openstreetmap.org/search" +
                        "?q=$encodedQuery" +
                        "&format=json" +
                        "&limit=5" +
                        "&addressdetails=1" +
                        "&extratags=1" +
                        "&namedetails=1" +
                        "&accept-language=en"

//                Log.d("GeocodingRepository", "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "WeatherApp/1.0")
                    .addHeader("Accept", "application/json")
                    .addHeader("Accept-Language", "en")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

//                Log.d("GeocodingRepository", "Response code: ${response.code}")
//                Log.d("GeocodingRepository", "Response headers: ${response.headers}")

                when (response.code) {
                    200 -> {
                        if (!responseBody.isNullOrEmpty()) {
//                            Log.d("GeocodingRepository", "Response body: ${responseBody.take(200)}...")

                            val type = object : TypeToken<List<NominatimResponse>>() {}.type
                            val nominatimResults: List<NominatimResponse> = gson.fromJson(responseBody, type)

                            val predictions = nominatimResults.mapNotNull { result ->
                                val cityName = result.address?.city
                                    ?: result.address?.town
                                    ?: result.address?.village
                                    ?: result.address?.municipality
                                    ?: result.display_name.split(",")[0].trim()

                                val country = result.address?.country ?: ""
                                val state = result.address?.state ?: result.address?.region ?: ""
                                val lat = result.lat.toDoubleOrNull() ?: 0.0
                                val lon = result.lon.toDoubleOrNull() ?: 0.0

                                // Filter out results that are clearly not cities
                                val validCityTypes = listOf("city", "town", "village", "municipality", "administrative")
                                val isValidCity = result.type in validCityTypes ||
                                        result.class_type == "place" ||
                                        cityName.isNotEmpty()

                                if (cityName.isNotEmpty() && lat != 0.0 && lon != 0.0 && isValidCity) {
                                    CityPrediction(
                                        displayName = cityName,
                                        country = country,
                                        state = state,
                                        lat = lat,
                                        lon = lon
                                    )
                                } else null
                            }.distinctBy { "${it.displayName}, ${it.state}, ${it.country}" }

                            // Cache the results
                            cache[cacheKey] = predictions

//                            Log.d("GeocodingRepository", "Mapped predictions: ${predictions.size}")
                            predictions
                        } else {
//                            Log.w("GeocodingRepository", "Empty response body")
                            emptyList()
                        }
                    }
                    403 -> {
//                        Log.e("GeocodingRepository", "403 Forbidden - Rate limited or blocked")
                        emptyList()
                    }
                    429 -> {
//                        Log.e("GeocodingRepository", "429 Too Many Requests - Rate limited")
                        emptyList()
                    }
                    else -> {
//                        Log.e("GeocodingRepository", "HTTP Error: ${response.code} - ${response.message}")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
//                Log.e("GeocodingRepository", "Error in getCityPredictions", e)
                emptyList()
            }
        }
    }

    // Clear cache periodically to avoid memory issues
    fun clearCache() {
        cache.clear()
    }
}

