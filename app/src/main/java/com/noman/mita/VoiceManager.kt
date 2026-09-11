package com.noman.mita

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

interface VoiceListener {
    fun onListeningStateChanged(isListening: Boolean)
    fun onSpeechResult(text: String)
    fun onSpeechError(errorMessage: String)
    fun onSpeakingStateChanged(isSpeaking: Boolean)
}

class VoiceManager(
    private val context: Context,
    private val listener: VoiceListener
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        initSpeechRecognizer()
        textToSpeech = TextToSpeech(context, this)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listener.onListeningStateChanged(true)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        listener.onListeningStateChanged(false)
                    }
                    override fun onError(error: Int) {
                        listener.onListeningStateChanged(false)
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "কথা স্পষ্ট বুঝতে পারিনি, আরেকবার বলবে?"
                            SpeechRecognizer.ERROR_NETWORK -> "ইন্টারনেট কানেকশন চেক করুন।"
                            SpeechRecognizer.ERROR_AUDIO -> "মাইক্রোফোনে সমস্যা হয়েছে।"
                            else -> "শুনতে কোনো সমস্যা হয়েছে।"
                        }
                        listener.onSpeechError(msg)
                    }
                    override fun onResults(results: Bundle?) {
                        listener.onListeningStateChanged(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            listener.onSpeechResult(recognizedText)
                        } else {
                            listener.onSpeechError("কিছু শুনতে পাইনি।")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "মিতা শুনছে, বাংলায় কথা বলুন...")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            listener.onSpeechError("মাইক্রোফোন চালু করা যায়নি: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            listener.onListeningStateChanged(false)
        } catch (e: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val banglaLocale = Locale("bn", "BD")
            val result = textToSpeech?.setLanguage(banglaLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to generic Bengali or Default
                textToSpeech?.setLanguage(Locale("bn"))
            }
            textToSpeech?.setPitch(1.05f) // Friendly sweet tone
            textToSpeech?.setSpeechRate(0.95f) // Natural conversational speed

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    listener.onSpeakingStateChanged(true)
                }
                override fun onDone(utteranceId: String?) {
                    listener.onSpeakingStateChanged(false)
                }
                override fun onError(utteranceId: String?) {
                    listener.onSpeakingStateChanged(false)
                }
            })
            isTtsReady = true
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mita_utterance_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
            listener.onSpeakingStateChanged(false)
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
