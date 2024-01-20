package com.md.modesetters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.md.*
import com.md.composeModes.CurrentNotePartManager
import com.md.modesetters.DeckItemPopulator.populate
import com.md.provider.Deck
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject


@ActivityScoped
class DeckLoadManager @Inject constructor(
    @ActivityContext val context: Context,
    val currentNotePartManager: CurrentNotePartManager,
    private val focusedQueueStateModel: FocusedQueueStateModel,) {
    val decks = MutableStateFlow<List<DeckInfo>?>(null)
    val deckIdToDeckSize = MutableStateFlow<Map<Int, Int>?>(null)

    private var needsToLoadDecksFromDatabase = true

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            refreshDeckListAndFocusFirstActiveNonemptyQueue()
        }

        activity.lifecycleScope.launch {
            decks.collect { decks ->
                if (decks.isNullOrEmpty()) {
                    return@collect
                }
                for (deckInfo in decks) {
                    if (deckInfo.isActive && !deckInfo.revisionQueue.isEmpty()) {
                        setDeck(deckInfo)
                        break
                    }
                }
            }
        }
    }

    private fun setDeck(deckInfo: DeckInfo) {
        CategorySingleton.getInstance().setDeckInfo(deckInfo)
        focusedQueueStateModel.deck.value = deckInfo
        val note = deckInfo.revisionQueue.peekQueue()
        currentNotePartManager.changeCurrentNotePart(note, partIsAnswer = false)
    }

    suspend fun refreshDeckListAndFocusFirstActiveNonemptyQueue() {
        withContext(Dispatchers.Main) {
            // Load deck just once and afterward make the in memory the source of truth and keep
            // DB up to date manually.
            if (this@DeckLoadManager.needsToLoadDecksFromDatabase) {
                this@DeckLoadManager.needsToLoadDecksFromDatabase = false
                val queryDeck = DbNoteEditor.instance!!.queryDeck()
                 withContext(Dispatchers.IO) {
                    val deckInfoList = mutableListOf<DeckInfo>()
                    val deckList = mutableListOf<Deck>()
                    for (deck in queryDeck) {
                        deckList.add(Deck(deck.id, deck.name))
                    }

                    for (deck in deckList) {
                        val revisionQueue = RevisionQueue()
                        revisionQueue.populate(DbNoteEditor.instance!!, deck.id)
                        val deckCount = DbNoteEditor.instance!!.getDeckCount(deck.id)
                        // TODOJNOW provide alternative to deck count.
                        val deckInfo = DeckInfo(deck, revisionQueue, 0)
                        deckInfoList.add(deckInfo)
                    }

                    withContext(Dispatchers.Main) {
                        decks.value = deckInfoList
                    }

                    val noteEditor = checkNotNull(DbNoteEditor.instance)
                    val localDeckIdToDeckSize = mutableMapOf<Int, Int>()
                    for (deck in deckList) {
                        val itemsInDeck = noteEditor.getDeckCount(deck.id)
                        localDeckIdToDeckSize[deck.id] = itemsInDeck

                    }
                    withContext(Dispatchers.Main) {
                        deckIdToDeckSize.value = localDeckIdToDeckSize
                    }
                    deckInfoList
                }
            }
        }
    }
}

