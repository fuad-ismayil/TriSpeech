package com.trispeech.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat

object LocaleController {
    private val supported = setOf("en", "az", "tr", "ru")
    private const val PREFERENCES = "app_locale"
    private const val SELECTED_LANGUAGE = "selected_language"

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences.getString(SELECTED_LANGUAGE, null)
        val previouslySelected = LocaleManagerCompat.getApplicationLocales(context).get(0)?.language
            ?.takeIf(supported::contains)
        val systemLanguage = java.util.Locale.getDefault().language.takeIf(supported::contains)
        val language = stored?.takeIf(supported::contains)
            ?: previouslySelected
            ?: systemLanguage
            ?: "en"
        if (stored != language) {
            preferences.edit().putString(SELECTED_LANGUAGE, language).apply()
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    fun set(context: Context, tag: String) {
        val safeTag = tag.takeIf(supported::contains) ?: "en"
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_LANGUAGE, safeTag)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(safeTag))
    }

    fun currentTag(context: Context): String = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(SELECTED_LANGUAGE, "en") ?: "en"
}
