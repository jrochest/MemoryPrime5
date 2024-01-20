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
import com.md.composeModes.DeckMode
import com.md.composeModes.DeckModeStateModel
import com.md.composeModes.Mode
import com.md.composeModes.TopModeViewModel
import com.md.modesetters.DeckItemPopulator.populate
import com.md.modesetters.deckchoose.DeckNameUpdater
import com.md.modesetters.deckchoose.InsertNewHandler
import com.md.provider.Deck
import com.md.utils.ToastSingleton
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
    private val topModeViewModel: TopModeViewModel,
    private val deckModeStateModel: DeckModeStateModel,
    @ActivityContext val context: Context,
    val currentNotePartManager: CurrentNotePartManager,
    private val focusedQueueStateModel: FocusedQueueStateModel,) {
    val decks = MutableStateFlow<List<DeckInfo>?>(null)
    var deckRefreshNeeded = true

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            refreshDeckListAndFocusFirstActiveNonemptyQueue(true)
        }

        activity.lifecycleScope.launch {
            decks.collect { decks ->
                if (decks.isNullOrEmpty()) {
                    return@collect
                }
                chooseDeck(decks)
            }
        }
    }

    fun chooseDeck() {
        val decks = decks.value ?: return
        var foundNonEmptyDeck = false
        for (deckInfo in decks) {
            if (deckInfo.isActive && !deckInfo.revisionQueue.isEmpty()) {
                setDeck(deckInfo)
                foundNonEmptyDeck = true
                break
            }
        }
        if (foundNonEmptyDeck) {
            return
        } // else use an empty deck.
        for (deckInfo in decks) {
            if (deckInfo.isActive) {
                setDeck(deckInfo)
                break
            }
        }
    }

    fun chooseDeck(decks: List<DeckInfo>) {
        for (deckInfo in decks) {
            if (deckInfo.isActive && !deckInfo.revisionQueue.isEmpty()) {
                setDeck(deckInfo)
                break
            }
        }
    }

    private fun setDeck(deckInfo: DeckInfo) {
        CategorySingleton.getInstance().setDeckInfo(deckInfo)
        focusedQueueStateModel.deck.value = deckInfo
        val note = deckInfo.revisionQueue.peekQueue()
        currentNotePartManager.changeCurrentNotePart(note, partIsAnswer = false)
    }

    suspend fun refreshDeckListAndFocusFirstActiveNonemptyQueue(deckRefreshNeeded: Boolean? = null) {
        deckRefreshNeeded?.let { this.deckRefreshNeeded = it }
        withContext(Dispatchers.Main) {
            // Only run this refresh once.
            if (this@DeckLoadManager.deckRefreshNeeded) {
                this@DeckLoadManager.deckRefreshNeeded = false
                val resultingDeckList = withContext(Dispatchers.IO) {
                    val resultingDeckList = mutableListOf<DeckInfo>()
                    val deckList = mutableListOf<Deck>()
                    val queryDeck = DbNoteEditor.instance!!.queryDeck()
                    for (deck in queryDeck) {
                        deckList.add(Deck(deck.id, deck.name))
                    }

                    for (deck in deckList) {
                        val revisionQueue = RevisionQueue()
                        revisionQueue.populate(DbNoteEditor.instance!!, deck.id)
                        val deckCount = DbNoteEditor.instance!!.getDeckCount(deck.id)
                        val deckInfo = DeckInfo(deck, revisionQueue, deckCount)
                        resultingDeckList.add(deckInfo)
                    }

                    withContext(Dispatchers.Main) {
                        decks.value = resultingDeckList
                    }
                    resultingDeckList
                }

                val decks = resultingDeckList
                if (decks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        // Go to Deck Chooser to add a deck.
                        topModeViewModel.modeModel.value = Mode.DeckChooser
                        deckModeStateModel.modeModel.value = DeckMode.AddingDeck
                    }
                    return@withContext
                } else {
                    withContext(Dispatchers.Main) {
                        chooseDeck()
                    }
                }
            }
        }
    }
}
//TODOJNOW hook up the proceed button.

@SuppressLint("StaticFieldLeak")
object DeckChooseModeSetter : ModeSetter() {
    private var mTotalNotes = 0
    @SuppressLint("StaticFieldLeak")
    private var memoryDroid: SpacedRepeaterActivity? = null
    fun setUp(memoryDroid: SpacedRepeaterActivity?,
              modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
        this.memoryDroid = memoryDroid
    }

    override fun onSwitchToMode(context: Activity) {
        commonSetup(context, R.layout.deckchoosetemp)
        setupCreateMode()
    }

    var listView: ListView? = null
    private val deckList = Vector<Deck>()
    private var deckIdToInfo: HashMap<Int, DeckInfo> = LinkedHashMap()
    private var loadComplete = false
    private var progressBar: ProgressBar? = null
    fun setupCreateMode() {
        val activity = checkNotNull(mActivity)

        loadComplete = false
        val insertButton = memoryDroid!!.findViewById<View>(R.id.addNew) as Button
        insertButton.setOnClickListener(InsertNewHandler(memoryDroid, this))
        deckList.clear()

        listView = memoryDroid!!.findViewById<View>(R.id.ListView01) as ListView
        progressBar = memoryDroid!!.findViewById<View>(R.id.progressBar) as ProgressBar
        progressBar!!.max = deckList.size
        progressBar!!.progress = 0
        progressBar!!.visibility = View.VISIBLE

        // By using setAdpater method in listview we an add string array in
        // list.
        listView!!.adapter = DeckAdapter(deckList, mActivity!!)

        //val deckPopulator = DeckPopulator(this)
        //deckPopulator.execute(this)
        val modeChooser = this
    }

    private class DeckAdapter(
        private val decks: List<Deck>,
        private val context: Activity,
    ) : BaseAdapter() {
        override fun getCount(): Int {
            return decks.size
        }

        override fun getItem(position: Int): Any {
            return decks[position]
        }

        override fun getItemId(position: Int): Long {
            return decks[position].id.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: context.layoutInflater.inflate(R.layout.deck_item_for_list, parent, false)

            val deck = decks[position]
            val deckInfo = deckIdToInfo[deck.id]
            val textView = view.findViewById<TextView>(R.id.deck_name)
            if (deckInfo != null) {
                populate(view, deckInfo)
            } else {
                textView.text = deck.name
            }
            textView.setOnLongClickListener { v ->
                val info = deckIdToInfo[deck.id] ?: return@setOnLongClickListener true
                val alert = AlertDialog.Builder(memoryDroid!!)
                alert.setTitle("Choose action or press off screen")
                alert.setPositiveButton("Rename") { _, _ -> DeckNameUpdater(memoryDroid, info, getInstance()).onClick(v) }
                //alert.setNegativeButton("Delete") { _, _ -> DeckDeleter(memoryDroid, info, getInstance()).onClick(v) }
                alert.show()
                true
            }
            view.findViewById<View>(R.id.new_note_button).setOnClickListener {
                val info = deckIdToInfo[deck.id] ?: return@setOnClickListener
                loadDeck(info)
                CreateModeSetter.switchMode(memoryDroid!!)
            }

                view.findViewById<Button>(R.id.learn_button).setOnClickListener {
                val info = deckIdToInfo[deck.id] ?: return@setOnClickListener
                loadDeck(info)
                    memoryDroid?.switchToLearningMode()
            }

            return view
        }
    }

    fun onComplete() {
        val activity = checkNotNull(memoryDroid)
        val deckLoadManager = activity.deckLoadManager()

        activity.lifecycleScope.launch {


        }

        val loadingOrSelect = memoryDroid?.findViewById<View>(R.id.loadingOrSelect) as TextView
        loadingOrSelect.text = "Press or Long Press a Deck"
        loadComplete = true
        ToastSingleton.getInstance().msgCommon("$mTotalNotes notes!")
    }

    fun applyDeckInfoToExistingUiElement(state: DeckInfo) {
        progressBar!!.progress = progressBar!!.progress + 1
        for (idx in deckList.indices) {
            val elementAt = deckList.elementAt(idx)
            if (elementAt.id == state.category) {
                elementAt.setSize(state.deckCount)
                elementAt.setTodayReview(state.revisionQueue.getSize())
                deckIdToInfo[elementAt.id] = state
                // TODO(jrochest) Why is the necessary. It only needs it when returning from
                // a different mode, but it is not needed initially.
                if (listView!!.childCount - 1 >= idx) {
                    populate(listView!!.getChildAt(idx), state)
                }
            }
        }
    }

    fun loadDeck(deckInfo: DeckInfo?) {
        if (deckInfo != null) {
            CategorySingleton.getInstance().setDeckInfo(deckInfo)
        }
    }

    val nextDeckWithItems: DeckInfo?
        get() {
            if (deckIdToInfo.isEmpty()) {
                return null
            }
            for ((_, value) in deckIdToInfo) {
                if (!value.isActive) {
                    continue
                }
                if (value.revisionQueue.getSize() > 0) {
                    return value
                }
            }
            return null
        }

        fun getInstance() = this
}