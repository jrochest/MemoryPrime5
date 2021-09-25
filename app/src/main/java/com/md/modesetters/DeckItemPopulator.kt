package com.md.modesetters

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.md.R

object DeckItemPopulator {
    fun populate(deckView: View, state: DeckInfo) {
        val dueNoteCount = state.revisionQueue.getSize()
        deckView.findViewById<TextView>(R.id.deck_name)?.text = state.deck.name  + "\n" + dueNoteCount + " of " + state.deckCount
        deckView.findViewById<Button>(R.id.learn_button).text = "Study\n" + dueNoteCount
    }
}