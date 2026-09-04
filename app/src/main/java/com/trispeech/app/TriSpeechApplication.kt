package com.trispeech.app

import android.app.Application
import android.util.Log
import com.trispeech.app.data.TriSpeechDatabase
import com.trispeech.app.nativebridge.WhisperJniBridge
import com.trispeech.app.network.BackendClient

class TriSpeechApplication : Application() {
    val database by lazy { TriSpeechDatabase.get(this) }
    val backend by lazy { BackendClient(this) }
    override fun onCreate() {
        super.onCreate()
        LocaleController.initialize(this)
        Log.i("TriSpeech", "Native Whisper available=${WhisperJniBridge.isAvailable()} ${WhisperJniBridge.unavailableReason()}")
    }
}
