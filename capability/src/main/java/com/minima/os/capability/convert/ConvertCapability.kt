package com.minima.os.capability.convert

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unit + currency conversion.
 * Currency via https://api.frankfurter.app (free, no key).
 * Units via hardcoded factor table.
 */
@Singleton
class ConvertCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "convert"
    override fun supportedActions() = listOf("convert")

    override suspend fun execute(step: ActionStep): StepResult {
        val value = step.params["value"]?.toDoubleOrNull()
            ?: return StepResult(false, error = "No value")
        val from = step.params["from"]?.trim()?.uppercase()
            ?: return StepResult(false, error = "No source unit")
        val to = step.params["to"]?.trim()?.uppercase()
            ?: return StepResult(false, error = "No target unit")

        // Try currency first
        if (from.length == 3 && to.length == 3 && from.all { it.isLetter() } && to.all { it.isLetter() }) {
            return convertCurrency(value, from, to)
        }
        // Else try units
        return convertUnit(value, from, to)
    }

    private suspend fun convertCurrency(value: Double, from: String, to: String): StepResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.frankfurter.app/latest?amount=$value&from=$from&to=$to"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val rates = json.optJSONObject("rates")
                    ?: return@withContext StepResult(false, error = "No rate data")
                val result = rates.optDouble(to, Double.NaN)
                if (result.isNaN()) return@withContext StepResult(false, error = "Rate not found for $to")
                val answer = "${fmt(value)} $from = ${fmt(result)} $to"
                StepResult(true, data = mapOf("answer" to answer, "result" to fmt(result)))
            } catch (e: Exception) {
                StepResult(false, error = "Currency fetch failed: ${e.message}")
            }
        }

    private fun convertUnit(value: Double, from: String, to: String): StepResult {
        // Length (meters base)
        val length = mapOf(
            "M" to 1.0, "METER" to 1.0, "METERS" to 1.0,
            "KM" to 1000.0, "KILOMETER" to 1000.0, "KILOMETERS" to 1000.0,
            "CM" to 0.01, "MM" to 0.001,
            "MI" to 1609.344, "MILE" to 1609.344, "MILES" to 1609.344,
            "FT" to 0.3048, "FOOT" to 0.3048, "FEET" to 0.3048,
            "IN" to 0.0254, "INCH" to 0.0254, "INCHES" to 0.0254,
            "YD" to 0.9144, "YARD" to 0.9144, "YARDS" to 0.9144
        )
        // Weight (grams base)
        val weight = mapOf(
            "G" to 1.0, "GRAM" to 1.0, "GRAMS" to 1.0,
            "KG" to 1000.0, "KILOGRAM" to 1000.0, "KILOGRAMS" to 1000.0,
            "LB" to 453.592, "POUND" to 453.592, "POUNDS" to 453.592, "LBS" to 453.592,
            "OZ" to 28.3495, "OUNCE" to 28.3495, "OUNCES" to 28.3495
        )
        // Volume (liters base)
        val volume = mapOf(
            "L" to 1.0, "LITER" to 1.0, "LITERS" to 1.0, "LITRE" to 1.0, "LITRES" to 1.0,
            "ML" to 0.001, "MILLILITER" to 0.001,
            "GAL" to 3.78541, "GALLON" to 3.78541, "GALLONS" to 3.78541,
            "CUP" to 0.2365882, "CUPS" to 0.2365882
        )
        val categories = listOf(length, weight, volume)
        for (cat in categories) {
            if (from in cat && to in cat) {
                val result = value * cat[from]!! / cat[to]!!
                return StepResult(true, data = mapOf(
                    "answer" to "${fmt(value)} $from = ${fmt(result)} $to",
                    "result" to fmt(result)
                ))
            }
        }
        // Temperature special cases
        val tempResult = convertTemp(value, from, to)
        if (tempResult != null) {
            return StepResult(true, data = mapOf(
                "answer" to "${fmt(value)}°$from = ${fmt(tempResult)}°$to",
                "result" to fmt(tempResult)
            ))
        }
        return StepResult(false, error = "Can't convert $from to $to")
    }

    private fun convertTemp(v: Double, from: String, to: String): Double? {
        val celsius = when (from) {
            "C", "CELSIUS" -> v
            "F", "FAHRENHEIT" -> (v - 32) * 5 / 9
            "K", "KELVIN" -> v - 273.15
            else -> return null
        }
        return when (to) {
            "C", "CELSIUS" -> celsius
            "F", "FAHRENHEIT" -> celsius * 9 / 5 + 32
            "K", "KELVIN" -> celsius + 273.15
            else -> null
        }
    }

    private fun fmt(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else "%.2f".format(d)
    }
}
