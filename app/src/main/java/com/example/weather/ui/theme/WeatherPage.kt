package com.example.weather.ui.theme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.weather.R
import com.example.weather.api.NetworkResponseClass
import com.example.weather.api.WeatherModel
import com.example.weather.repository.CityPrediction
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

import kotlin.math.roundToInt


@SuppressLint("SuspiciousIndentation")
@Composable
fun WeatherPage(
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherViewModel,
    fusedLocationClient: FusedLocationProviderClient
) {
    var city by remember { mutableStateOf("") }
    var showPredictions by remember { mutableStateOf(false) }
    var cityPredictions by remember { mutableStateOf<List<CityPrediction>>(emptyList()) }
    var isLoadingPredictions by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val cityName by weatherViewModel.cityResult.collectAsState()
    val weatherResult = weatherViewModel.weatherResult.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val locationState by weatherViewModel.locationState.collectAsState()
    val cityResult by weatherViewModel.cityResult.collectAsState()
    var isUserTyping by remember { mutableStateOf(true) }

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            weatherViewModel.getCurrentLocation(fusedLocationClient, context)
        }
    }

    // Add lifecycle observer to detect when user returns from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check if location was turned on and retry
                if (locationState.error != null) {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                    if (isGpsEnabled || isNetworkEnabled) {
                        // Location is now enabled, retry getting location
                        if (locationPermissions.all { permission ->
                                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                            }) {
                            weatherViewModel.getCurrentLocation(fusedLocationClient, context)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Debounced search function
    LaunchedEffect(city, isUserTyping) {
        if (isUserTyping && city.length >= 2) {
            isLoadingPredictions = true
            delay(500) // Debounce for 500ms
            weatherViewModel.getCityPredictions(city) { predictions ->
                cityPredictions = predictions
                showPredictions = predictions.isNotEmpty()
                isLoadingPredictions = false
            }
        } else if (isUserTyping) {
            showPredictions = false
            cityPredictions = emptyList()
            isLoadingPredictions = false
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissions.all { permission ->
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }) {
            weatherViewModel.getCurrentLocation(fusedLocationClient, context)
        } else {
            launcher.launch(locationPermissions)
        }
    }

//    // Pull to refresh state
//    val pullRefreshState = rememberPullRefreshState(
//        refreshing = isRefreshing,
//        onRefresh = {
//            isRefreshing = true
//            // Refresh current weather data or location
//            if (cityResult != null) {
//                weatherViewModel.refreshWeatherData()
//            } else {
//                // Try to get current location again
//                if (locationPermissions.all { permission ->
//                        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
//                    }) {
//                    weatherViewModel.getCurrentLocation(fusedLocationClient, context)
//                }
//            }
//        }
//    )

    // Stop refreshing when weather result changes
    LaunchedEffect(weatherResult.value) {
        if (weatherResult.value !is NetworkResponseClass.Loading) {
            isRefreshing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
//            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search Section with Predictions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column {
                    // Row for TextField and Search Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(32.dp),
                            value = city,
                            onValueChange = { newValue ->
                                city = newValue
                                isUserTyping = true
                            },
                            label = {
                                Text(text = "Search any Location")
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (city.isNotEmpty()) {
                                        weatherViewModel.getData(city)
                                        keyboardController?.hide()
                                        showPredictions = false
                                        isUserTyping = false
                                    }
                                }
                            ),
                            trailingIcon = {
                                Row {
                                    if (isLoadingPredictions) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            cityName?.let {
                                                weatherViewModel.getData(it)
                                                city = it
                                                isUserTyping = false
                                            }
                                            keyboardController?.hide()
                                            showPredictions = false
                                        }
                                    ) {
                                        Icon(
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .size(32.dp),
                                            painter = painterResource(R.drawable.gps),
                                            contentDescription = "Your Location"
                                        )
                                    }
                                }
                            }
                        )
                        IconButton(
                            onClick = {
                                if (city.isNotEmpty()) {
                                    weatherViewModel.getData(city)
                                    keyboardController?.hide()
                                    showPredictions = false
                                    isUserTyping = false
                                }
                            }
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(36.dp),
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }

                    // City Predictions Dropdown
                    if (showPredictions && cityPredictions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 250.dp)
                            ) {
                                itemsIndexed(cityPredictions) { index, prediction ->
                                    CityPredictionItem(
                                        prediction = prediction,
                                        isLast = index == cityPredictions.lastIndex,
                                        onClick = {
                                            city = prediction.displayName
                                            isUserTyping = false
                                            showPredictions = false
                                            weatherViewModel.getData(prediction.displayName)
                                            keyboardController?.hide()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Error Handling UI - Only show if there's an actual error
            if (locationState.error != null && !locationState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = locationState.error ?: "Location error",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    // Open location settings
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Enable Location")
                            }
                            Button(
                                onClick = {
                                    // Retry getting location
                                    if (locationPermissions.all { permission ->
                                            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                                        }) {
                                        weatherViewModel.getCurrentLocation(fusedLocationClient, context)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Weather result display section
            when (val result = weatherResult.value) {
                is NetworkResponseClass.Error -> {
                    // Only show error if user has attempted to search
                    if (city.isNotEmpty() || cityResult?.isNotEmpty() == true) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = result.message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                NetworkResponseClass.Loading -> {
                    // Only show loading if user has attempted to search or location is being fetched
                    if (city.isNotEmpty() || cityResult?.isNotEmpty() == true || locationState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Getting Weather Data...")
                        }
                    }
                }

                is NetworkResponseClass.Success -> {
                    WeatherDetails(data = result.data)
                }
            }
        }

//        // Pull refresh indicator
//        PullRefreshIndicator(
//            refreshing = isRefreshing,
//            state = pullRefreshState,
//            modifier = Modifier.align(Alignment.TopCenter)
//        )
    }
}

// 5. Updated CityPredictionItem Composable
// Option 1: Pass isLast parameter
@Composable
fun CityPredictionItem(
    prediction: CityPrediction,
    isLast: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = prediction.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val locationDetails = buildString {
                if (prediction.state.isNotEmpty()) {
                    append(prediction.state)
                }
                if (prediction.country.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(prediction.country)
                }
            }
            if (locationDetails.isNotEmpty()) {
                Text(
                    text = locationDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.right),
            contentDescription = "Select",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Only show divider if it's not the last item
    if (!isLast) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
    }
}
@Composable
fun WeatherIcon(condition: String, modifier: Modifier ) {
    val iconResId = when(condition) {
        "Sunny" -> R.drawable.sun
        "Clear" -> R.drawable.moon
        "Partly cloudy" -> R.drawable.clouds
        "Cloudy" -> R.drawable.clouds
        "Overcast" -> R.drawable.overcast
        "Mist" -> R.drawable.mist
        "Patchy rain possible" -> R.drawable.rain
        "Patchy snow possible" -> R.drawable.snow
        "Patchy sleet possible" -> R.drawable.sleet
        "Patchy freezing drizzle possible" -> R.drawable.drizzle
        "Thundery outbreaks possible" -> R.drawable.storm
        "Blowing snow" -> R.drawable.snow
        "Blizzard" -> R.drawable.snow__1_
        "Fog" -> R.drawable.fog
        "Freezing fog" -> R.drawable.fog
        "Patchy light drizzle" -> R.drawable.drizzle
        "Light drizzle" -> R.drawable.drizzle
        "Freezing drizzle" -> R.drawable.drizzle
        "Heavy freezing drizzle" -> R.drawable.drizzle
        "Patchy light rain" -> R.drawable.rain
        "Light rain" -> R.drawable.rain
        "Moderate rain at times" -> R.drawable.rain
        "Moderate rain" -> R.drawable.rain
        "Heavy rain at times" -> R.drawable.rain
        "Heavy rain" -> R.drawable.storm
        "Light freezing rain" -> R.drawable.rain
        "Moderate or heavy freezing rain" -> R.drawable.rain
        "Light sleet" -> R.drawable.sleet
        "Moderate or heavy sleet" -> R.drawable.sleet
        "Patchy light snow" -> R.drawable.snow
        "Light snow" -> R.drawable.snow
        "Patchy moderate snow" -> R.drawable.snow
        "Moderate snow" -> R.drawable.snow
        "Patchy heavy snow" -> R.drawable.snow
        "Heavy snow"-> R.drawable.snow
        "Ice pellets" -> R.drawable.cloud
        "Light rain shower" -> R.drawable.rain
        "Moderate or heavy rain shower" -> R.drawable.storm
        "Torrential rain shower" -> R.drawable.storm
        "Light sleet showers" -> R.drawable.sleet
        "Moderate or heavy sleet showers" -> R.drawable.sleet
        "Light snow showers" -> R.drawable.snow
        "Moderate or heavy snow showers" -> R.drawable.snow
        "Light showers of ice pellets" -> R.drawable.snow
        "Moderate or heavy showers of ice pellets" -> R.drawable.snow
        "Patchy light rain with thunder" -> R.drawable.storm
        "Moderate or heavy rain with thunder" -> R.drawable.storm
        "Patchy light snow with thunder" -> R.drawable.storm
        "Moderate or heavy snow with thunder" -> R.drawable.storm
        else -> R.drawable.clouds
    }
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = condition,
        modifier = Modifier.size(100.dp),
        tint = Color.Unspecified
    )
}

@SuppressLint("NewApi")
@Composable
fun WeatherDetails(data: WeatherModel) {

    Box(

    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Add static content like the location and current weather here
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.padding(8.dp)
                        )
                        Text(
                            text = data.location.name,
                            fontSize = 20.sp
                        )
                        Text(
                            text = (", ${data.location.country}"),
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )

                    }
                    Spacer(Modifier.height(8.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = ("${data.current.temp_c.roundToInt()}\u00B0"),
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = data.current.condition.text,
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize
                                )
                                Spacer(Modifier.height(8.dp))
                                Row {
                                    Text(
                                        text = ("${data.forecast.forecastday[0].day.maxtemp_c.roundToInt()}°/ ${data.forecast.forecastday[0].day.mintemp_c.roundToInt()}°"),
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = ("Feels like: ${data.current.feelslike_c.roundToInt()}°"),
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                                    )
                                }
                                Spacer(Modifier.height(4.dp))

                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                WeatherIcon(
                                    condition = data.current.condition.text,
                                    modifier = Modifier.size(100.dp)
                                )
                            }

                        }

                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row {
                        Icon(
                            painter = painterResource(R.drawable.sunrise__1_),
                            contentDescription = "Sunrise",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = data.forecast.forecastday[0].astro.sunrise
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Row {
                        Icon(
                            painter = painterResource(R.drawable.sunset),
                            contentDescription = "Sunrise",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = data.forecast.forecastday[0].astro.sunset
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            VerticalGrid(
                data = WeatherModel(
                    current = data.current,
                    forecast = data.forecast,
                    location = data.location
                )
            )

            items(data.forecast.forecastday) { forecastDay ->
                // Add forecast day rows here dynamically

                val localDate = LocalDate.parse(forecastDay.date)
                val dayOfWeek =
                    localDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayOfWeek,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.drop),
                                contentDescription = "Precipitation",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = ("${forecastDay.day.daily_will_it_rain}%"),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        AsyncImage(
                            modifier = Modifier.size(28.dp),
                            model = "https:${forecastDay.day.condition.icon}".replace(
                                "64x64",
                                "128x128"
                            ),
                            contentDescription = forecastDay.day.condition.text
                        )
                        Spacer(Modifier.width(28.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = ("${forecastDay.day.maxtemp_c.roundToInt()}°/ ${data.forecast.forecastday[0].day.mintemp_c.roundToInt()}°"),
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleSmall.fontSize,
                            )
                        }
                    }

                }
            }

        }
    }

}

@RequiresApi(Build.VERSION_CODES.O)
fun LazyListScope.VerticalGrid (data: WeatherModel) {
    item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.uv_index),
                                    contentDescription = "UV Index",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "UV Index",
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = data.current.uv.toString(),
                                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.humidity),
                                    contentDescription = "Humidity",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Humidity",
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = ("${data.current.humidity}%"),
                                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row (
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Card (modifier = Modifier
                        .width(160.dp)
                        .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.wind),
                                    contentDescription = "Wind Speed",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Wind Speed",
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row (verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
                                Text(
                                    text = ("${data.current.wind_kph}"),
                                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " km/h",
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize
                                )
                            }

                        }
                    }

                    Card (
                        modifier = Modifier
                            .width(160.dp)
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.dew),
                                    contentDescription = "Dew Point",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Dew point",
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = ("${data.current.dewpoint_c}%"),
                                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row (
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) { Card(
                    modifier = Modifier
                        .width(160.dp)
                        .padding(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            Row (
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.weather),
                                    contentDescription = "Pressure",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Pressure",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = " (mb)",
                                    fontSize = 8.sp

                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = data.current.pressure_mb.toInt().toString(),
                                fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                    Card(
                        modifier = Modifier
                            .width(160.dp)
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.view),
                                    contentDescription = "Visibility",
                                    tint = Color(0xFF89CFF0),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Visibility",
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Row (verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = ("${data.current.vis_km}"),
                                        fontSize = MaterialTheme.typography.displaySmall.fontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = " km",
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                                    )
                                }
                            }
                        }
                    } }
            }

        }
    }



//@Preview(showBackground = true)
//@Composable
//fun WeatherPagePreview() {
//    WeatherPage(modifier = Modifier, weatherViewModel = WeatherViewModel())
//
//}
