package com.minima.os.capability.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "system"

    override fun supportedActions() = listOf("open_app", "list_apps", "search", "change_setting")

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "open_app" -> openApp(step.params)
            "list_apps" -> listApps()
            "search" -> search(step.params)
            "change_setting" -> changeSetting(step.params)
            else -> StepResult(success = false, error = "Unknown action: ${step.action}")
        }
    }

    private fun openApp(params: Map<String, String>): StepResult {
        val appName = (params["appName"] ?: params["app"] ?: params["app_name"])?.lowercase()?.trim()
        if (appName.isNullOrBlank()) {
            return listApps()
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
        val normalize = { s: String -> s.lowercase().replace(Regex("[\\s\\-_.']+"), "") }
        val normalizedQuery = normalize(appName)

        // Try exact contains first, then normalized, then fuzzy distance
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(appName)
        } ?: apps.firstOrNull {
            normalize(it.loadLabel(pm).toString()).contains(normalizedQuery)
        } ?: apps.firstOrNull {
            normalizedQuery.contains(normalize(it.loadLabel(pm).toString()))
        } ?: apps.firstOrNull {
            val label = normalize(it.loadLabel(pm).toString())
            label.startsWith(normalizedQuery) || normalizedQuery.startsWith(label)
        } ?: run {
            // Fuzzy match: find closest app name by edit distance
            apps.map { it to normalize(it.loadLabel(pm).toString()) }
                .filter { (_, label) -> label.isNotBlank() }
                .minByOrNull { (_, label) -> editDistance(normalizedQuery, label) }
                ?.takeIf { (_, label) ->
                    val dist = editDistance(normalizedQuery, label)
                    dist <= maxOf(2, normalizedQuery.length / 3)
                }?.first
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                StepResult(
                    success = true,
                    data = mapOf("app" to match.loadLabel(pm).toString())
                )
            } else {
                StepResult(success = false, error = "Cannot launch ${match.loadLabel(pm)}")
            }
        } else {
            StepResult(success = false, error = "App '$appName' not found")
        }
    }

    private fun listApps(): StepResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .map { it.loadLabel(pm).toString() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        return StepResult(
            success = true,
            data = mapOf("answer" to "Installed apps (${apps.size}):\n${apps.joinToString(", ")}")
        )
    }

    private fun search(params: Map<String, String>): StepResult {
        val query = params["query"] ?: return StepResult(
            success = false, error = "No search query"
        )

        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(success = true, data = mapOf("query" to query))
        } catch (e: Exception) {
            // Fallback to browser
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                StepResult(success = true, data = mapOf("query" to query))
            } catch (e2: Exception) {
                StepResult(success = false, error = "Failed to search: ${e2.message}")
            }
        }
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
            }
        }
        return dp[a.length][b.length]
    }

    private fun changeSetting(params: Map<String, String>): StepResult {
        val setting = params["setting"]?.lowercase() ?: return StepResult(
            success = false, error = "No setting specified"
        )

        val intent = when {
            setting.contains("wifi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            setting.contains("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            setting.contains("airplane") -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            setting.contains("display") || setting.contains("brightness") -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            setting.contains("sound") || setting.contains("volume") -> Intent(Settings.ACTION_SOUND_SETTINGS)
            setting.contains("dnd") || setting.contains("do not disturb") -> Intent("android.settings.ZEN_MODE_SETTINGS")
            else -> Intent(Settings.ACTION_SETTINGS)
        }

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            StepResult(success = true, data = mapOf("setting" to setting, "status" to "opened_settings"))
        } catch (e: Exception) {
            StepResult(success = false, error = "Failed to open settings: ${e.message}")
        }
    }
}
