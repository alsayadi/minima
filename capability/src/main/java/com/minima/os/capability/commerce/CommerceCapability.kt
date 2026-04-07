package com.minima.os.capability.commerce

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommerceCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "commerce"

    override fun supportedActions() = listOf("open_ride_app", "open_food_app")

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "open_ride_app" -> openRideApp(step.params)
            "open_food_app" -> openFoodApp(step.params)
            else -> StepResult(success = false, error = "Unknown action: ${step.action}")
        }
    }

    private fun openRideApp(params: Map<String, String>): StepResult {
        val destination = params["destination"]

        // Try Uber first, then Lyft, then generic map
        val apps = listOf(
            AppTarget("com.ubercab", "uber://", destination?.let { "uber://?action=setPickup&dropoff[formatted_address]=$it" }),
            AppTarget("com.lyft.android", "lyft://", destination?.let { "lyft://ridetype?id=lyft&destination[address]=$it" }),
        )

        for (app in apps) {
            if (isInstalled(app.packageName)) {
                return launchDeepLink(app.deepLink ?: app.fallbackUri)
            }
        }

        // Fallback to maps
        return if (destination != null) {
            launchDeepLink("google.navigation:q=$destination")
        } else {
            StepResult(success = false, error = "No ride apps installed")
        }
    }

    private fun openFoodApp(params: Map<String, String>): StepResult {
        val restaurant = params["restaurant"]

        val apps = listOf(
            AppTarget("com.ubercab.eats", "ubereats://", restaurant?.let { "ubereats://search?q=$it" }),
            AppTarget("com.dd.doordash", "doordash://", restaurant?.let { "doordash://search?query=$it" }),
        )

        for (app in apps) {
            if (isInstalled(app.packageName)) {
                return launchDeepLink(app.deepLink ?: app.fallbackUri)
            }
        }

        return StepResult(success = false, error = "No food delivery apps installed")
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun launchDeepLink(uri: String): StepResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(success = true, data = mapOf("launched" to uri))
        } catch (e: Exception) {
            StepResult(success = false, error = "Failed to launch: ${e.message}")
        }
    }

    private data class AppTarget(
        val packageName: String,
        val fallbackUri: String,
        val deepLink: String?
    )
}
