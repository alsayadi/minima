package com.minima.os.capability.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "contacts"
    override fun supportedActions() = listOf("call", "lookup")

    override suspend fun execute(step: ActionStep): StepResult = when (step.action) {
        "call" -> call(step.params)
        "lookup" -> lookup(step.params)
        else -> StepResult(false, error = "Unknown action: ${step.action}")
    }

    private fun call(params: Map<String, String>): StepResult {
        val name = params["name"] ?: params["contact"] ?: params["recipient"]
            ?: return StepResult(false, error = "No contact name")
        val number = findNumber(name)
            ?: return StepResult(false, error = "Contact '$name' not found")
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(true, data = mapOf("answer" to "Calling $name", "number" to number))
        } catch (e: Exception) {
            StepResult(false, error = "Call failed: ${e.message}")
        }
    }

    private fun lookup(params: Map<String, String>): StepResult {
        val name = params["name"] ?: return StepResult(false, error = "No name")
        val number = findNumber(name) ?: return StepResult(
            false, error = "Contact '$name' not found"
        )
        return StepResult(true, data = mapOf("answer" to "$name: $number", "number" to number))
    }

    private fun findNumber(name: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(name)
            )
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: SecurityException) { null }
        catch (_: Exception) { null }
    }
}
