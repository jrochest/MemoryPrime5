package com.md.modesetters

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueue
import com.md.modesetters.DeckItemPopulator.populate
import com.md.modesetters.deckchoose.DeckDeleter
import com.md.modesetters.deckchoose.DeckNameUpdater
import com.md.modesetters.deckchoose.InsertNewHandler
import com.md.provider.Deck
import com.md.utils.ToastSingleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@SuppressLint("StaticFieldLeak")
object DeckChooseModeSetter : ModeSetter() {
    private var mTotalNotes = 0
    @SuppressLint("StaticFieldLeak")
    private var memoryDroid: Activity? = null
    fun setUp(memoryDroid: Activity?,
              modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
        this.memoryDroid = memoryDroid
    }

    override fun switchModeImpl(context: Activity) {
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
        val queryDeck = DbNoteEditor.instance!!.queryDeck()
        for (deck in queryDeck) {
            deckList.add(Deck(deck.id, deck.name))
        }
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

        activity.lifecycleScope.launch(Dispatchers.IO) {
            mTotalNotes = 0
            val childCount = deckList.size
            for (idx in 0 until childCount) {
                // Stop if we aren't loaded anymore.
                if (modeHand!!.whoseOnTop() != modeChooser) {
                    break
                }
                val deck = deckList.elementAt(idx)
                val revisionQueue = RevisionQueue()
                revisionQueue.populate(DbNoteEditor.instance!!, deck.id)

                // Stop if we aren't loaded anymore. We want this before
                // and after the query
                if (modeHand!!.whoseOnTop() != modeChooser) {
                    break
                }

                val deckCount = DbNoteEditor.instance!!.getDeckCount(deck.id)
                val deckInfo = DeckInfo(deck, revisionQueue, deckCount)
                withContext(Dispatchers.Main) {
                    applyDeckInfoToExistingUiElement(deckInfo)
                }
            }

            withContext(Dispatchers.Main) {
                if (modeHand!!.whoseOnTop() === modeChooser) {
                    modeChooser.onComplete()
                }
            }
        }
    }

    private class DeckAdapter(private val decks: List<Deck>, private val context: Activity) : BaseAdapter() {
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
                alert.setNegativeButton("Delete") { _, _ -> DeckDeleter(memoryDroid, info, getInstance()).onClick(v) }
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
                LearningModeSetter.instance.switchMode(memoryDroid!!)
            }

            return view
        }
    }

    fun onComplete() {
        val loadingOrSelect = memoryDroid?.findViewById<View>(R.id.loadingOrSelect) as TextView
        loadingOrSelect.text = "Press or Long Press a Deck"
        loadComplete = true
        ToastSingleton.getInstance().msgCommon("$mTotalNotes notes!", 0f)
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
            currentDeckReviewQueue = deckInfo.revisionQueue
        }
    }

    val nextDeckWithItems: DeckInfo?
        get() {
            if (deckIdToInfo.isEmpty()) {
                return null
            }
            for ((_, value) in deckIdToInfo) {
                if (value.name.contains("inactive")) {
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