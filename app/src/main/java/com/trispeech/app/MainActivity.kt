package com.trispeech.app

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.trispeech.app.ui.TriSpeechApp
import com.trispeech.app.ui.theme.TriSpeechTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val preferences = remember { getSharedPreferences("appearance", MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", systemDark)) }
            TriSpeechTheme(darkTheme) {
                TriSpeechApp(darkTheme = darkTheme) {
                    darkTheme = !darkTheme
                    preferences.edit().putBoolean("dark_theme", darkTheme).apply()
                }
            }
        }
    }
}
