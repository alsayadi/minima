package com.minima.os.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Wraps Android SpeechRecognizer (STT) + TextToSpeech (TTS).
 * Single-shot: tap mic -> listen -> text -> callback.
 * Speak result after voice commands only.
 */
class VoiceManager(private val context: Context) {

    interface Listener {
        fun onListeningStart() {}
        fun onPartialText(text: String) {}
        fun onFinalText(text: String)
        fun onError(message: String)
        fun onListeningEnd() {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false
    private var onSpeakDone: (() -> Unit)? = null

    fun ensureInit() {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    ttsReady = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId?.startsWith("minima-final-") == true) {
                                val cb = onSpeakDone
                                onSpeakDone = null
                                cb?.invoke()
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                }
            }
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(listener: Listener) {
        if (!isAvailable()) {
            listener.onError("Speech recognition not available on this device")
            return
        }
        if (isListening) {
            stopListening()
            return
        }
        ensureInit()

        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener.onListeningStart()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                listener.onError(errorMessage(error))
                listener.onListeningEnd()
                rec.destroy()
                recognizer = null
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty()
                isListening = false
                if (best.isNotBlank()) listener.onFinalText(best)
                else listener.onError("Didn't catch that")
                listener.onListeningEnd()
                rec.destroy()
                recognizer = null
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { listener.onPartialText(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Give the user time to finish speaking
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        try {
            rec.startListening(intent)
        } catch (e: Exception) {
            listener.onError("Failed to start: ${e.message}")
            isListening = false
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        ensureInit()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "minima-${System.currentTimeMillis()}")
    }

    /** Speak a short filler while LLM is processing. Does NOT flush. */
    fun speakFiller(text: String) {
        if (text.isBlank()) return
        ensureInit()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "minima-filler-${System.currentTimeMillis()}")
    }

    /** Speak final result and invoke callback when done (for conversation follow-up). */
    fun speakFinal(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        ensureInit()
        onSpeakDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "minima-final-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech"
        else -> "Speech error $code"
    }
}
