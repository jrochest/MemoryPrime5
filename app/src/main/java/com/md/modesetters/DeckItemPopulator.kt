package com.md.modesetters

import android.view.View
import android.widget.TextView
import com.md.R

object DeckItemPopulator {
    fun populate(idx: Int, deckItem: View, state: DeckInfo) {
        deckItem.findViewById<TextView>(R.id.deck_name)?.text = state.deck.name  + "\n" + state.revisionQueue.getSize() + " of " + state.deckCount
    }


}