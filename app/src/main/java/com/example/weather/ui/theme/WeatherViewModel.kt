package com.example.weather.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weather.api.Constant
import com.example.weather.api.NetworkResponseClass
import com.example.weather.api.RetrofitInstance
import com.example.weather.api.WeatherModel
import com.example.weather.repository.CityPrediction
import com.example.weather.repository.GeocodingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class LocationState(
    val isLoading: Boolean = false,
    val cityName: String? = null,
    val error: String? = null
)

class WeatherViewModel : ViewModel() {

    private val weatherApiViewModel = RetrofitInstance.weatherApi
    private val _weatherResult = MutableStateFlow<NetworkResponseClass<WeatherModel>>(NetworkResponseClass.Error(""))
    val weatherResult : StateFlow<NetworkResponseClass<WeatherModel>> = _weatherResult

    private val _cityResult = MutableStateFlow<String?>(null)
    val cityResult: StateFlow<String?> = _cityResult

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState

    private val geocodingRepository = GeocodingRepository()

    fun getCityPredictions(query: String, callback: (List<CityPrediction>) -> Unit) {
        viewModelScope.launch {
            try {
                val predictions = geocodingRepository.getCityPredictions(query)
                callback(predictions)
            } catch (e: Exception) {
                callback(emptyList())
            }
        }
    }

    // Add method to clear location error
    fun clearLocationError() {
        _locationState.value = _locationState.value.copy(error = null)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(fusedLocationClient: FusedLocationProviderClient, context: Context) {
        viewModelScope.launch {
            try {
                _locationState.value = LocationState(isLoading = true)

                // ✅ Check if location services are enabled
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!isGpsEnabled && !isNetworkEnabled) {
                    _locationState.value = LocationState(
                        isLoading = false,
                        error = "Location is turned off. Please enable GPS or location services."
                    )
                    return@launch
                }

                // Clear any previous errors since location is now enabled
                clearLocationError()

                // ✅ Proceed with getting location
                val location = suspendCoroutine<Location?> { continuation ->
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    ).addOnSuccessListener { loc ->
                        continuation.resume(loc)
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }

                location?.let {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val cityName = getCityName(geocoder, it)
                    _locationState.value = LocationState(
                        isLoading = false,
                        cityName = cityName,
                        error = null
                    )
                    _cityResult.value = cityName
                    cityName?.let { city -> getData(city) }
                } ?: run {
                    _locationState.value = LocationState(
                        isLoading = false,
                        error = "Unable to get your location. Please try again."
                    )
                }
            } catch (e: Exception) {
                _locationState.value = LocationState(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getCityName(geocoder: Geocoder, location: Location): String? {
        return try {
            val addresses = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )
            addresses?.firstOrNull()?.locality
        } catch (e: Exception) {
            null
        }
    }

    fun getData(name: String) {
        _weatherResult.value = NetworkResponseClass.Loading
        viewModelScope.launch {
            try {
                val response = weatherApiViewModel.getWeather(Constant.apiKey, name, "3")
                if (response.isSuccessful) {
                    response.body()?.let {
                        _weatherResult.value = NetworkResponseClass.Success(it)
                    } ?: run {
                        _weatherResult.value = NetworkResponseClass.Error("Empty response body")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"

                    // Check if the error message contains something like "location not found"
                    if (errorBody.contains("location", ignoreCase = true) ||
                        errorBody.contains("not found", ignoreCase = true) ||
                        errorBody.contains("invalid", ignoreCase = true)
                    ) {
                        _weatherResult.value = NetworkResponseClass.Error("No place found with name \"$name\".")
                    } else {
                        _weatherResult.value = NetworkResponseClass.Error("API Error: $errorBody (Code: ${response.code()})")
                    }
                }
            } catch (e: SocketTimeoutException) {
                _weatherResult.value = NetworkResponseClass.Error("Request timed out. Please check your internet connection.")
            } catch (e: IOException) {
                _weatherResult.value = NetworkResponseClass.Error("No internet connection or unstable network.")
            } catch (e: Exception) {
                _weatherResult.value = NetworkResponseClass.Error("Network error: ${e.localizedMessage}")
            }
        }
    }


    // Add refresh method for pull-to-refresh
    fun refreshWeatherData() {
        val currentCity = _cityResult.value
        if (currentCity != null) {
            getData(currentCity)
        }
    }
}