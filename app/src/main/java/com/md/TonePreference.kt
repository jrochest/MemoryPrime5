package com.md

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

object TonePreference {
    const val KEY = "tone_pref_key"
    const val TONE_PREF_FILE = "backup_locations_prefs"

    fun get(context: Context): Boolean {
        return context.getSharedPreferences(TONE_PREF_FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, newValue: Boolean) {
        context.getSharedPreferences(TONE_PREF_FILE, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, newValue)
        }
    }
}