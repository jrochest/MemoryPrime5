package com.md.modesetters

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.md.*
import com.md.composeModes.CurrentNotePartManager
import com.md.provider.Deck
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        val deckInfo = DeckInfo(deck, revisionQueue)
                        deckInfoList.add(deckInfo)
                    }

                    withContext(Dispatchers.Main) {
                        decks.value = deckInfoList
                    }

                    val noteEditor = checkNotNull(DbNoteEditor.instance)
                     // The sizes are not important for starting so delay loading them.
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

