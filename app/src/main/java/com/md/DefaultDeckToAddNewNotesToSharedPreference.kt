package com.md

import android.content.Context
import androidx.core.content.edit
import java.lang.IllegalArgumentException

object DefaultDeckToAddNewNotesToSharedPreference {
    const val KEY_FOR_DECK_ID = "KEY_FOR_DECK_ID"
    const val KEY_FOR_DECK_NAME = "KEY_FOR_DECK_NAME"
    const val PREF_FILE = "DefaultDeckToAddNewNotesToSharedPreference"

    const val UNSET_ID = -1

    data class DeckNameAndId(val id: Int,
        val name: String) {
    }

    fun getDeck(context: Context): DeckNameAndId? {
        val id = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getInt(KEY_FOR_DECK_ID, UNSET_ID)
        if (id == UNSET_ID) return null
        val name = requireNotNull(context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getString(KEY_FOR_DECK_NAME, ""))
        return DeckNameAndId(id, name)
    }

    fun set(context: Context, newValue: DeckNameAndId) {
        if (newValue.id == -1) {
            throw IllegalArgumentException("No decks should have an ID of -1")
        }

        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit {
            putInt(KEY_FOR_DECK_ID, newValue.id)
            putString(KEY_FOR_DECK_NAME, newValue.name)
        }
    }
}