package com.minima.os.ui.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minima.os.agent.approval.ApprovalEngine
import com.minima.os.agent.approval.ApprovalRequest
import com.minima.os.agent.executor.TaskExecutor
import com.minima.os.core.model.Task
import com.minima.os.core.model.TaskState
import com.minima.os.data.entity.MemoryEntity
import com.minima.os.data.entity.PatternEntity
import com.minima.os.data.entity.PersonEntity
import com.minima.os.data.entity.PlaceEntity
import com.minima.os.data.memory.ContextEngine
import com.minima.os.data.memory.MemoryManager
import com.minima.os.data.memory.MemoryStats
import com.minima.os.data.memory.ProactiveEngine
import com.minima.os.model.provider.CloudModelProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LauncherUiState(
    val commandText: String = "",
    val isProcessing: Boolean = false,
    val currentTask: Task? = null,
    val taskHistory: List<Task> = emptyList(),
    val pendingApproval: ApprovalRequest? = null,
    val installedApps: List<AppInfo> = emptyList(),
    val isListening: Boolean = false,
    val voiceStatus: String = ""
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val taskExecutor: TaskExecutor,
    private val approvalEngine: ApprovalEngine,
    private val cloudModelProvider: CloudModelProvider,
    private val memoryManager: MemoryManager,
    private val contextEngine: ContextEngine,
    private val proactiveEngine: ProactiveEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _contextData = MutableStateFlow<ContextEngine.ContextData?>(null)
    val contextData: StateFlow<ContextEngine.ContextData?> = _contextData.asStateFlow()

    private val _onboardingQuestions = MutableStateFlow<List<ContextEngine.OnboardingQuestion>>(emptyList())
    val onboardingQuestions: StateFlow<List<ContextEngine.OnboardingQuestion>> = _onboardingQuestions.asStateFlow()

    private val _proactiveCards = MutableStateFlow<List<ProactiveEngine.ProactiveCard>>(emptyList())
    val proactiveCards: StateFlow<List<ProactiveEngine.ProactiveCard>> = _proactiveCards.asStateFlow()

    private val _sensitivity = MutableStateFlow(ProactiveEngine.Sensitivity.NORMAL)

    init {
        loadInstalledApps()
        refreshContext()
        refreshProactiveCards()

        // Load saved sensitivity
        val prefs = context.getSharedPreferences("minima_prefs", Context.MODE_PRIVATE)
        val savedSensitivity = prefs.getString("sensitivity", "NORMAL") ?: "NORMAL"
        _sensitivity.value = try { ProactiveEngine.Sensitivity.valueOf(savedSensitivity) }
                             catch (_: Exception) { ProactiveEngine.Sensitivity.NORMAL }

        viewModelScope.launch {
            taskExecutor.currentTask.collect { task ->
                _uiState.value = _uiState.value.copy(
                    currentTask = task,
                    isProcessing = task?.state in listOf(
                        TaskState.CLASSIFYING,
                        TaskState.PLANNING,
                        TaskState.EXECUTING
                    )
                )
            }
        }

        viewModelScope.launch {
            taskExecutor.taskHistory.collect { history ->
                _uiState.value = _uiState.value.copy(taskHistory = history)
            }
        }

        viewModelScope.launch {
            approvalEngine.pendingApprovals.collect { request ->
                _uiState.value = _uiState.value.copy(pendingApproval = request)
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
                .map { AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName
                ) }
                .filter { it.packageName != "com.minima.os" } // Exclude self
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }

            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    fun onCommandTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(commandText = text)
    }

    // Voice input support
    private var voiceManager: com.minima.os.ui.voice.VoiceManager? = null
    private var speakNextResult = false
    private var conversationMode = false

    private fun ensureVoiceManager(): com.minima.os.ui.voice.VoiceManager {
        return voiceManager ?: com.minima.os.ui.voice.VoiceManager(context).also {
            voiceManager = it
            it.ensureInit()
        }
    }

    fun startVoiceInput() {
        val vm = ensureVoiceManager()
        vm.startListening(object : com.minima.os.ui.voice.VoiceManager.Listener {
            override fun onListeningStart() {
                _uiState.value = _uiState.value.copy(isListening = true, voiceStatus = "Listening…")
            }
            override fun onPartialText(text: String) {
                _uiState.value = _uiState.value.copy(commandText = text, voiceStatus = "Listening…")
            }
            override fun onFinalText(text: String) {
                _uiState.value = _uiState.value.copy(
                    commandText = text, isListening = false, voiceStatus = ""
                )
                speakNextResult = true
                conversationMode = true
                taskExecutor.nextTaskIsVoiceInitiated = true
                // Instant acknowledgment while LLM thinks
                vm.speakFiller("one sec")
                onSubmitCommand()
            }
            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(isListening = false, voiceStatus = message)
                // Auto-retry on "no match" / no speech so the user can try again hands-free
                val retry = message.contains("catch that", ignoreCase = true) ||
                            message.contains("No speech", ignoreCase = true)
                if (retry) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(400)
                        startVoiceInput()
                    }
                }
            }
            override fun onListeningEnd() {
                _uiState.value = _uiState.value.copy(isListening = false)
            }
        })
    }

    fun stopVoiceInput() {
        voiceManager?.stopListening()
        conversationMode = false
        _uiState.value = _uiState.value.copy(isListening = false, voiceStatus = "")
    }

    fun onSubmitCommand() {
        val text = _uiState.value.commandText.trim()
        if (text.isBlank()) return

        // Debug time command: "debug time HH" or "debug time HH DOW"
        if (text.startsWith("debug time")) {
            val parts = text.split(" ")
            val hour = parts.getOrNull(2)?.toIntOrNull()
            val dow = parts.getOrNull(3)?.toIntOrNull()
            if (hour != null) {
                setTestTime(hour, dow)
                _uiState.value = _uiState.value.copy(commandText = "")
                return
            }
        }
        if (text == "debug reset") {
            clearTestTime()
            _uiState.value = _uiState.value.copy(commandText = "")
            return
        }

        _uiState.value = _uiState.value.copy(
            commandText = "",
            isProcessing = true
        )

        viewModelScope.launch {
            taskExecutor.execute(text)
            if (speakNextResult) {
                speakNextResult = false
                val latest = taskExecutor.taskHistory.value.firstOrNull()
                val lastStep = latest?.steps?.lastOrNull()
                val rawData = lastStep?.result?.data ?: emptyMap()
                val rawFallback = rawData["answer"]
                    ?: rawData["app"]?.let { "Opening $it" }
                    ?: "Done"

                val naturalReply = try {
                    val dataBlock = rawData.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    val prompt = """User said: "$text"
Task result data: $dataBlock
Success: ${lastStep?.result?.success ?: false}

Respond in ONE short warm sentence as if speaking to a friend. No markdown, no lists, no quotes. Under 20 words. If it's a question, answer it. If it's an action, acknowledge what you did naturally."""
                    cloudModelProvider.draft(prompt).trim().removeSurrounding("\"")
                } catch (_: Exception) { rawFallback }

                val toSpeak = if (naturalReply.isBlank()) rawFallback else naturalReply
                voiceManager?.speakFinal(toSpeak) {
                    // After TTS finishes, reopen mic for follow-up conversation
                    if (conversationMode) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(400)
                            startVoiceInput()
                        }
                    }
                }
            }
            refreshContext()
            refreshProactiveCards()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.shutdown()
        voiceManager = null
    }

    fun onApprove() {
        viewModelScope.launch {
            approvalEngine.submitDecision(approved = true)
            _uiState.value = _uiState.value.copy(pendingApproval = null)
        }
    }

    fun onReject() {
        viewModelScope.launch {
            approvalEngine.submitDecision(approved = false, reason = "User rejected")
            _uiState.value = _uiState.value.copy(pendingApproval = null)
        }
    }

    fun refreshContext() {
        viewModelScope.launch {
            try {
                val data = contextEngine.generateContext()
                _contextData.value = data
                if (data.isNewUser) {
                    _onboardingQuestions.value = contextEngine.getOnboardingQuestions()
                }
            } catch (_: Exception) {}
        }
    }

    fun onOnboardingAnswer(command: String) {
        viewModelScope.launch {
            taskExecutor.execute(command)
            refreshContext()
        }
    }

    fun dismissOnboarding() {
        _onboardingQuestions.value = emptyList()
    }

    fun refreshProactiveCards() {
        viewModelScope.launch {
            try {
                _proactiveCards.value = proactiveEngine.generateCards(_sensitivity.value)
            } catch (_: Exception) {}
        }
    }

    fun dismissProactiveCard(id: String) {
        _proactiveCards.value = _proactiveCards.value.filter { it.id != id }
    }

    fun onSensitivityChanged(sensitivity: String) {
        _sensitivity.value = try { ProactiveEngine.Sensitivity.valueOf(sensitivity) }
                             catch (_: Exception) { ProactiveEngine.Sensitivity.NORMAL }
        refreshProactiveCards()
    }

    /**
     * For testing: simulate a specific hour and day.
     * Call via command "debug time HH DOW" where DOW: 1=Sun,2=Mon,...,7=Sat
     */
    fun setTestTime(hour: Int, dayOfWeek: Int?) {
        proactiveEngine.testHour = hour
        proactiveEngine.testDayOfWeek = dayOfWeek
        contextEngine.testHour = hour
        contextEngine.testDayOfWeek = dayOfWeek
        refreshContext()
        refreshProactiveCards()
    }

    fun clearTestTime() {
        proactiveEngine.testHour = null
        proactiveEngine.testDayOfWeek = null
        contextEngine.testHour = null
        contextEngine.testDayOfWeek = null
        refreshContext()
        refreshProactiveCards()
    }

    fun onApiKeySaved(apiKey: String) {
        if (apiKey.isNotBlank()) {
            // Re-read the full provider selection and apply live
            val prefs = context.getSharedPreferences("minima_prefs", Context.MODE_PRIVATE)
            val providerName = prefs.getString("llm_provider", com.minima.os.model.provider.Provider.OPENAI.name)
                ?: com.minima.os.model.provider.Provider.OPENAI.name
            val selected = try { com.minima.os.model.provider.Provider.valueOf(providerName) }
                           catch (_: Exception) { com.minima.os.model.provider.Provider.OPENAI }
            val model = prefs.getString("llm_model_${selected.name}", null)
            cloudModelProvider.configureProvider(selected, apiKey, model)
        }
    }

    // Memory
    val memories = memoryManager.observeAllMemories()
    val people = memoryManager.observePeople()
    val places = memoryManager.observePlaces()
    val patterns = memoryManager.observePatterns()

    private val _memoryStats = MutableStateFlow<MemoryStats?>(null)
    val memoryStats: StateFlow<MemoryStats?> = _memoryStats.asStateFlow()

    fun refreshMemoryStats() {
        viewModelScope.launch {
            _memoryStats.value = memoryManager.getStats()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memoryManager.forgetMemory(id); refreshMemoryStats() }
    }

    fun deletePerson(id: String) {
        viewModelScope.launch { memoryManager.forgetPerson(id); refreshMemoryStats() }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch { memoryManager.forgetPlace(id); refreshMemoryStats() }
    }

    fun deletePattern(id: String) {
        viewModelScope.launch { memoryManager.forgetPattern(id); refreshMemoryStats() }
    }
}
