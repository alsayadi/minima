package com.minima.os.capability.weather

import android.content.Context
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "weather"
    override fun supportedActions() = listOf("get_weather")

    override suspend fun execute(step: ActionStep): StepResult = withContext(Dispatchers.IO) {
        val cityParam = (step.params["location"] ?: step.params["city"] ?: "").trim()
        val city = if (cityParam.isBlank()) "Dubai" else cityParam
        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1"
            val geoJson = JSONObject(fetch(geoUrl))
            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return@withContext StepResult(success = false, error = "City '$city' not found")
            }
            val loc = results.getJSONObject(0)
            val lat = loc.getDouble("latitude")
            val lon = loc.getDouble("longitude")
            val name = loc.optString("name", city)

            val wxUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,weather_code"
            val wxJson = JSONObject(fetch(wxUrl))
            val current = wxJson.optJSONObject("current")
                ?: return@withContext StepResult(success = false, error = "No weather data")

            val tempRaw = current.optDouble("temperature_2m", Double.NaN)
            val feelRaw = current.optDouble("apparent_temperature", Double.NaN)
            val code = current.optInt("weather_code", 0)
            val t = if (tempRaw.isNaN()) "?" else "${tempRaw.toInt()}°C"
            val feel = if (feelRaw.isNaN()) null else feelRaw.toInt()
            val condition = weatherCode(code)

            val answer = "$condition in $name, $t" + (feel?.let { " (feels $it°C)" } ?: "")
            StepResult(success = true, data = mapOf(
                "answer" to answer,
                "temperature" to t,
                "location" to name,
                "condition" to condition
            ))
        } catch (e: Exception) {
            StepResult(success = false, error = "Weather fetch failed: ${e.message}")
        }
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun weatherCode(c: Int): String = when (c) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }
}
